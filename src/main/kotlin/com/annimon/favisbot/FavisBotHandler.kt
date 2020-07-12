package com.annimon.favisbot

import com.annimon.favisbot.commands.*
import com.annimon.favisbot.db.DbUser
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_ALLOWED
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_IGNORED
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_UNKNOWN
import com.annimon.favisbot.db.ItemsRepository
import com.annimon.favisbot.db.UsersRepository
import com.annimon.tgbotsmodule.BotHandler
import com.annimon.tgbotsmodule.api.methods.Methods
import com.google.inject.Injector
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.*
import java.time.Instant

class FavisBotHandler(injector: Injector) : BotHandler() {
    private val appConfig = injector.getInstance(AppConfig::class.java)
    private val itemsRepository = injector.getInstance(ItemsRepository::class.java)
    private val usersRepository = injector.getInstance(UsersRepository::class.java)
    private val commandStart = injector.getInstance(StartCommand::class.java)
    private val commandRegister = injector.getInstance(RegisterCommand::class.java)
    private val commandAnnounce = injector.getInstance(AnnounceCommand::class.java)
    private val commandHelp = injector.getInstance(HelpCommand::class.java)
    private val onStickerCommand = injector.getInstance(OnStickerCommand::class.java)
    private val onMediaCommand = injector.getInstance(OnMediaCommand::class.java)

    companion object {
        private val log: Logger = LoggerFactory.getLogger(FavisBotHandler::class.java)
    }

    override fun getBotUsername() = appConfig.botUsername

    override fun getBotToken() = appConfig.botToken

    override fun onUpdate(update: Update): BotApiMethod<*>? {
        if (update.hasCallbackQuery() && update.callbackQuery.message != null) {
            val params = (update.callbackQuery.data ?: "").split(":".toRegex())
            processCallback(params, update.callbackQuery.message)
            return null
        }
        if (update.hasInlineQuery()) {
            processInline(update.inlineQuery)
            return null
        }
        if (update.hasMessage() && update.message.hasText()) {
            val message = update.message
            val (command, _) = message.text.split(" ".toRegex(), 2)
            when (command.toLowerCase()) {
                "/start" -> commandStart.run(message, this)
                "/register" -> commandRegister.run(message, this)
                "/announce" -> commandAnnounce.run(message, this)
                "/help" -> commandHelp.run(message, this)
                else -> log.info("Unknown command: $command")
            }
            return null
        }
        if (update.hasMessage() && update.message.hasSticker()) {
            onStickerCommand.run(update.message, this)
            return null
        }
        if (update.hasMessage()) {
            onMediaCommand.run(update.message, this)
            return null
        }

        if (update.hasChosenInlineQuery()) {
            // TODO rating
            log.info("Chosen inline query: ${update.chosenInlineQuery}")
        }
        return null
    }

    private fun processCallback(params: List<String>, message: Message) {
        // allow or ignore users
        if (params.size != 2) return
        val type = params[0].toLowerCase()
        val id = params[1].toIntOrNull() ?: return
        val user = usersRepository.findUserById(id) ?: return
        user.allowed = when (type) {
            "a" -> ALLOWANCE_ALLOWED
            "i" -> ALLOWANCE_IGNORED
            else -> return
        }
        user.updatedAt = Instant.now().epochSecond
        usersRepository.upsertUser(user)
        val msg = if (type == "a") {
            "Congratulations, the administrator has granted access to the bot." +
            " Call the /register command again to get the link."
        } else {
            "Unfortunately, the administrator denied you access to the bot."
        }
        Methods.sendMessage(id.toLong(), msg)
                .callAsync(this)
        Methods.editMessageReplyMarkup(message.chatId, message.messageId)
                .setReplyMarkup(null)
                .callAsync(this)
    }

    private fun answerInlineNoResults(inlineQueryId: String) {
        answerInlineWithText(inlineQueryId, "No results", "There are no results for your query")
    }

    private fun answerInlineWithText(inlineQueryId: String, text: String, message: String?) {
        val result = listOf(InlineQueryResultArticle().apply {
            id = text.hashCode().toString()
            title = text
            inputMessageContent = InputTextMessageContent().apply {
                messageText = message ?: text
            }
        })
        Methods.answerInlineQuery(inlineQueryId, result)
                .setCacheTime(60)
                .setPersonal(true)
                .call(this)
    }

    private fun processInline(inlineQuery: InlineQuery) {
        val user = usersRepository.findUserById(inlineQuery.from.id)
        val allowance = getAllowance(user)
        if (allowance == ALLOWANCE_IGNORED) return
        if (allowance != ALLOWANCE_ALLOWED) {
            log.info("processInline: by unknown ${inlineQuery.from.id}")
            answerInlineWithText(inlineQuery.id,
                    "You don't have enough rights to access this bot",
                    "You can request access to the bot by sending /register command in PM @${appConfig.botUsername}")
            return
        }

        val entriesPerPage = 25
        val offset = inlineQuery.offset.toIntOrNull() ?: 0
        val query = inlineQuery.query
        val (count, items) = itemsRepository.searchItems(query, inlineQuery.from.id, entriesPerPage, offset)
        val results = items.mapNotNull { when (it.type) {
            "sticker" -> InlineQueryResultCachedSticker().apply {
                id = it.id.hashCode().toString()
                stickerFileId = it.id
            }
            "animation" -> InlineQueryResultCachedMpeg4Gif().apply {
                id = it.id.hashCode().toString()
                mpeg4FileId = it.id
            }
            "document", "gif" -> InlineQueryResultCachedDocument().apply {
                id = it.id.hashCode().toString()
                documentFileId = it.id
            }
            "photo" -> InlineQueryResultCachedPhoto().apply {
                id = it.id.hashCode().toString()
                photoFileId = it.id
            }
            "video" -> InlineQueryResultCachedVideo().apply {
                id = it.id.hashCode().toString()
                videoFileId = it.id
            }
            "voice" -> InlineQueryResultCachedVoice().apply {
                id = it.id.hashCode().toString()
                voiceFileId = it.id
            }
            else -> null
        } }
        if (results.isEmpty()) {
            answerInlineNoResults(inlineQuery.id)
            return
        }
        var nextOffset = ""
        if (offset + entriesPerPage < count) {
            nextOffset = (offset + entriesPerPage).toString()
        }
        Methods.answerInlineQuery(inlineQuery.id, results)
                .setCacheTime(100)
                .setPersonal(true)
                .setNextOffset(nextOffset)
                .call(this)
    }

    private fun getAllowance(user: DbUser?): Int {
        if (appConfig.adminId == user?.id) return ALLOWANCE_ALLOWED
        return (user?.allowed ?: ALLOWANCE_UNKNOWN)
    }
}
