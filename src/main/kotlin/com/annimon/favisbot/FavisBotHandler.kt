package com.annimon.favisbot

import com.annimon.favisbot.DbUser.Companion.ALLOWANCE_ALLOWED
import com.annimon.favisbot.DbUser.Companion.ALLOWANCE_IGNORED
import com.annimon.favisbot.DbUser.Companion.ALLOWANCE_PENDING
import com.annimon.favisbot.DbUser.Companion.ALLOWANCE_UNKNOWN
import com.annimon.tgbotsmodule.BotHandler
import com.annimon.tgbotsmodule.api.methods.Methods
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.*
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedDocument
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedMpeg4Gif
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedPhoto
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedSticker
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedVideo
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedVoice
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.stickers.StickerSet
import java.io.File
import java.time.Instant
import java.util.*

class FavisBotHandler(
    private val appConfig: AppConfig,
    private val repository: DbRepository
) : BotHandler() {

    override fun getBotUsername() = appConfig.botUsername

    override fun getBotToken() = appConfig.botToken

    override fun onUpdate(update: Update): BotApiMethod<*>? {
        if (update.hasMessage() && update.message.hasText()) {
            val message = update.message
            val reply = message.replyToMessage
            if (reply != null) {
                processReply(message, reply)
                return null
            }
            val (command, _) = message.text.split(" ".toRegex(), 2)
            when (command.toLowerCase()) {
                "/start" -> cmdStart(message)
                "/register" -> cmdRegister(message)
            }
            return null
        }
        if (update.hasMessage() && update.message.hasSticker()) {
            processSticker(update.message)
            return null
        }
        if (update.hasCallbackQuery() && update.callbackQuery.message != null) {
            val params = (update.callbackQuery.data ?: "").split(":".toRegex())
            processCallback(params, update.callbackQuery.message)
            return null
        }
        if (update.hasInlineQuery()) {
            processInline(update.inlineQuery)
            return null
        }
        return null
    }

    private fun processCallback(params: List<String>, message: Message) {
        // allow or ignore users
        if (params.size != 2) return
        val type = params[0].toLowerCase()
        val id = params[1].toIntOrNull() ?: return
        val user = repository.findUserById(id) ?: return
        user.allowed = when (type) {
            "a" -> ALLOWANCE_ALLOWED
            "i" -> ALLOWANCE_IGNORED
            else -> return
        }
        user.updatedAt = Instant.now().epochSecond
        repository.upsertUser(user)
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
                .call(this);
    }

    private fun processInline(inlineQuery: InlineQuery) {
        val user = repository.findUserById(inlineQuery.from.id)
        val allowance = user?.allowed ?: ALLOWANCE_PENDING
        if (allowance == ALLOWANCE_IGNORED) return
        if (allowance != ALLOWANCE_ALLOWED) {
            answerInlineWithText(inlineQuery.id,
                    "You don't have enough rights to access this bot",
                    "You can request access to the bot by sending /register command in PM @${appConfig.botUsername}")
            return
        }
        if (user == null) {
            answerInlineNoResults(inlineQuery.id)
            return
        }

        val entriesPerPage = 25
        val offset = inlineQuery.offset.toIntOrNull() ?: 0
        val query = inlineQuery.query
        val (count, items) = repository.searchItems(query, inlineQuery.from.id)
        val results = items.mapNotNull { when (it.type) {
            "sticker" -> InlineQueryResultCachedSticker().apply {
                id = it.id.hashCode().toString()
                stickerFileId = it.id
            }
            "animation", "gif" -> InlineQueryResultCachedMpeg4Gif().apply {
                id = it.id.hashCode().toString()
                mpeg4FileId = it.id
            }
            "document" -> InlineQueryResultCachedDocument().apply {
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

    private fun cmdStart(message: Message) {
        var text = "With this bot you can send your favorite stickers in inline mode. " +
                   "You can define tags in a web-form, then search stickers by these tags.\n\n"
        if (appConfig.adminId != message.from.id) {
            text += "You need administrator permission to access this bot. " +
                    "Send /register command to request access.\n\n"
        }
        // text += "Source code: https://github.com/"
        Methods.sendMessage()
            .setChatId(message.from.id.toLong())
            .setText(text)
            .callAsync(this)
    }

    private fun cmdRegister(message: Message) {
        val user = repository.findUserById(message.from.id)
        when (getAllowance(user)) {
            ALLOWANCE_IGNORED -> return
            ALLOWANCE_PENDING -> return
            ALLOWANCE_UNKNOWN -> {
                // send request to admin
                repository.upsertUser(DbUser(
                        id = message.from.id,
                        firstName = message.from.firstName,
                        guid = "",
                        allowed = ALLOWANCE_PENDING,
                        updatedAt = Instant.now().epochSecond
                ))
                val fromId = message.from.id
                Methods.sendMessage()
                        .setText("You requested permission to access the bot." +
                                " After the administrator approves the application," +
                                " you will receive a message.")
                        .setChatId(fromId.toLong())
                        .callAsync(this)

                val msg = "User <a href=\"tg://user?id=${fromId}\">" +
                        safeHtml(message.from.firstName) + "</a>" +
                        " requested access to the bot."
                val markup = InlineKeyboardMarkup()
                markup.keyboard = listOf(listOf(
                        InlineKeyboardButton()
                                .setText("Allow")
                                .setCallbackData("a:$fromId"),
                        InlineKeyboardButton()
                                .setText("Ignore")
                                .setCallbackData("i:$fromId")
                ))
                Methods.sendMessage(appConfig.adminId.toLong(), msg)
                        .enableHtml()
                        .setReplyMarkup(markup)
                        .callAsync(this)
            }
            ALLOWANCE_ALLOWED -> {
                val guid = UUID.randomUUID().toString()
                repository.upsertUser(DbUser(
                        id = message.from.id,
                        firstName = message.from.firstName,
                        guid = guid,
                        allowed = ALLOWANCE_ALLOWED,
                        updatedAt = Instant.now().epochSecond
                ))
                val host = appConfig.host ?: "http://127.0.0.1"
                val port = appConfig.port ?: 9377
                Methods.sendMessage(message.from.id.toLong(),
                        "Here's your link to the web page:\n" +
                        " 🌐 $host:$port/?d=$guid\n" +
                        "You can generate a new link by sending /register again.")
                        .callAsync(this)
            }
        }
    }

    private fun processReply(message: Message, reply: Message) {
        val text = message.text ?: ""
        if (text == "") return

        // Get type and fileId
        var info = Pair("", "")
        reply.animation?.let { info = Pair("animation", it.fileId) }
        reply.document?.let { info = Pair("document", it.fileId) }
        reply.document?.takeIf { it.mimeType == "video/mp4" } ?.let { info = Pair("gif", it.fileId) }
        reply.photo?.maxBy { it.width * it.height } ?.let { info = Pair("photo", it.fileId) }
        reply.video?.let { info = Pair("video", it.fileId) }
        reply.voice?.let { info = Pair("voice", it.fileId) }
        val (type, fileId) = info
        if (type == "") return

        // Add or remove savedItem
        val savedItem = DbSavedItem(fileId, message.from.id, text)
        val msg = if (text in arrayOf("/rm", "/del")) {
            repository.removeSavedItemIfExists(savedItem)
            type.capitalize() + " removed."
        } else {
            // Add item if not exists
            if (!repository.isItemExists(fileId)) {
                repository.addItem(DbItem(fileId, type, "", 0))
            }
            repository.upsertSavedItem(savedItem)
            type.capitalize() + " added.\nTo remove it, just reply to it again with command /rm or /del."
        }
        Methods.sendMessage(message.from.id.toLong(), msg).callAsync(this)
    }

    private fun processSticker(message: Message) {
        val setName = message.sticker.setName
        val stickerSet = Methods.Stickers.getStickerSet(setName).call(this)
        if (stickerSet == null) return
        downloadThumbs(stickerSet)
        val newItemsCount = stickerSet.stickers
                .filterNot { repository.isItemExists(it.fileId) }
                .map { DbItem(it.fileId, "sticker", stickerSet.name, if (it.animated) 1 else 0) }
                .onEach { repository.addItem(it) }
                .count()
        Methods.sendMessage(message.from.id.toLong(),
                "Added $newItemsCount stickers")
                .call(this)
    }

    private fun downloadThumbs(stickerSet: StickerSet) {
        val parent = File("public/thumbs/${stickerSet.name}")
        parent.mkdirs()
        for (sticker in stickerSet.stickers) {
            val localFile = File(parent, "${sticker.fileId}.png")
            Methods.getFile(sticker.thumb.fileId)
                    .callAsync(this) { tgFile -> downloadFile(tgFile, localFile) }
        }
    }

    private fun getAllowance(user: DbUser?): Int {
        if (appConfig.adminId == user?.id) return ALLOWANCE_ALLOWED
        return (user?.allowed ?: ALLOWANCE_UNKNOWN)
    }

    private fun safeHtml(text: String?): String {
        if (text == null) return ""
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
    }
}
