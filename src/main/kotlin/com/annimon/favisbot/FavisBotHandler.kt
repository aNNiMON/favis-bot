package com.annimon.favisbot

import com.annimon.favisbot.commands.*
import com.annimon.favisbot.db.DbUser
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_ALLOWED
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_IGNORED
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_UNKNOWN
import com.annimon.favisbot.db.ItemsRepository
import com.annimon.favisbot.db.UsersRepository
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineQuery
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.inlinequeryresults.InlineQueryResult
import com.github.kotlintelegrambot.entities.inlinequeryresults.InputMessageContent
import com.github.kotlintelegrambot.extensions.filters.Filter
import com.github.kotlintelegrambot.logging.LogLevel
import com.google.inject.Injector
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

class FavisBotHandler(injector: Injector) {
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

    private val bot = bot {
        token = appConfig.botToken
        logLevel = LogLevel.Error
        dispatch {
            callbackQuery {
                update.callbackQuery?.let {
                    val message = it.message ?: return@callbackQuery
                    val params = it.data.split(":".toRegex())
                    processCallback(params, message)
                }
            }

            inlineQuery {
                processInline(inlineQuery)
            }

            command("start") {
                commandStart.run(update.message!!, bot)
            }
            command("register") {
                commandRegister.run(update.message!!, bot)
            }
            command("announce") {
                commandAnnounce.run(update.message!!, bot)
            }
            command("help") {
                commandHelp.run(update.message!!, bot)
            }

            message(Filter.Sticker) {
                onStickerCommand.run(update.message!!, bot)
            }

            message( Filter.Sticker.not() ) {
                onMediaCommand.run(update.message!!, bot)
            }

            telegramError {
                log.error(error.getErrorMessage())
            }
        }
    }

    init {
        bot.startPolling()
    }

    private fun processCallback(params: List<String>, message: Message) {
        // allow or ignore users
        if (params.size != 2) return
        val type = params[0].toLowerCase()
        val id = params[1].toLongOrNull() ?: return
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
            "Unfortunately, the administrator denied you access to the bot." +
            "\nHowever, you can deploy your own bot instance: https://github.com/aNNiMON/favis-bot"
        }
        bot.sendMessage(ChatId.fromId(id), msg)
        bot.editMessageReplyMarkup(ChatId.fromId(message.chat.id), message.messageId)
    }

    private fun answerInlineNoResults(inlineQueryId: String) {
        answerInlineWithText(inlineQueryId, "No results", "There are no results for your query")
    }

    private fun answerInlineWithText(inlineQueryId: String, text: String, message: String?) {
        val results = listOf(InlineQueryResult.Article(
            text.hashCode().toString(),
            text,
            InputMessageContent.Text(message ?: text)
        ))
        bot.answerInlineQuery(inlineQueryId, results, cacheTime = 60, isPersonal = true)
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
            "sticker" -> InlineQueryResult.CachedSticker(
                    id = it.id.hashCode().toString(),
                    stickerFileId = it.id
            )
            "animation" -> InlineQueryResult.CachedMpeg4Gif(
                    id = it.id.hashCode().toString(),
                    mpeg4FileId = it.id
            )
            "document", "gif" -> InlineQueryResult.CachedDocument(
                    id = it.id.hashCode().toString(),
                    title = "",
                    documentFileId = it.id
            )
            "photo" -> InlineQueryResult.CachedPhoto(
                    id = it.id.hashCode().toString(),
                    photoFileId = it.id
            )
            "video" -> InlineQueryResult.CachedVideo(
                    id = it.id.hashCode().toString(),
                    title = "",
                    videoFileId = it.id
            )
            "voice" -> InlineQueryResult.CachedVoice(
                    id = it.id.hashCode().toString(),
                    title = "",
                    voiceFileId = it.id
            )
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
        bot.answerInlineQuery(inlineQuery.id, results,
                cacheTime = 100, isPersonal = true, nextOffset = nextOffset)
    }

    private fun getAllowance(user: DbUser?): Int {
        if (appConfig.adminId == user?.id?.toLong()) return ALLOWANCE_ALLOWED
        return (user?.allowed ?: ALLOWANCE_UNKNOWN)
    }
}
