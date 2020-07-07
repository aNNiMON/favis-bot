package com.annimon.favisbot

import com.annimon.favisbot.db.DbItem
import com.annimon.favisbot.db.DbRepository
import com.annimon.favisbot.db.DbUser
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_ALLOWED
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_IGNORED
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_PENDING
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_UNKNOWN
import com.annimon.favisbot.db.DbUserSet
import com.annimon.tgbotsmodule.BotHandler
import com.annimon.tgbotsmodule.api.methods.Methods
import com.google.inject.Injector
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.methods.ActionType
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.PhotoSize
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.*
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.stickers.StickerSet
import java.io.File
import java.time.Instant
import java.util.*

class FavisBotHandler(injector: Injector) : BotHandler() {
    private val appConfig = injector.getInstance(AppConfig::class.java)
    private val repository = injector.getInstance(DbRepository::class.java)

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
                "/start" -> cmdStart(message)
                "/register" -> cmdRegister(message)
                "/help" -> cmdHelp(message)
                else -> log.info("Unknown command: $command")
            }
            return null
        }
        if (update.hasMessage() && update.message.hasSticker()) {
            processSticker(update.message)
            return null
        }
        if (update.hasMessage()) {
            processMedia(update.message)
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
                .call(this)
    }

    private fun processInline(inlineQuery: InlineQuery) {
        val user = repository.findUserById(inlineQuery.from.id)
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
        val (count, items) = repository.searchItems(query, inlineQuery.from.id, entriesPerPage, offset)
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

    private fun cmdStart(message: Message) {
        log.info("cmdStart: by ${message.from.id}")
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
                log.info("cmdRegister: by unknown ${message.from.id}")
                // send request to admin
                repository.upsertUser(DbUser(
                        id = message.from.id,
                        firstName = message.from.firstName,
                        guid = message.from.id.toString(),
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
                log.info("cmdRegister: by allowed ${message.from.id}")
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

    private fun cmdHelp(message: Message) {
        log.info("cmdHelp: by ${message.from.id}")
        val bot = "@${appConfig.botUsername}"
        val text = """
            *Usage*

            You need permission to use this bot. To request permission, send /register.
            
            Once you get access, you can fully use the bot: add sticker packs, animations, photos, and videos to your collection.
            To add a sticker pack, just send one sticker from the set.
            
            After that, you can log in to the web-form to tag stickers and media from your collection.
            Send /register to generate or regenerate a unique link to web-form.


            *Web-form*

            In the web-form you can choose sticker set or media type and start tagging items with comma-separated values.
            To delete an item, just clear tags and press Apply.
            
            Your tagged stickers and media will be available in inline mode.
            
            
            *Inline mode*
            
            Type `$bot` in any chat to access your tagged collection.
            Type `$bot term` to show items containing the substring "term" tag/
            For exact match only, use `.` at the end of your query:  `$bot term.`
            To show all your tagged collection you can use `.all` query.
            """.trimIndent()
        Methods.sendMessage()
                .setChatId(message.from.id.toLong())
                .setText(text)
                .enableMarkdown()
                .callAsync(this)
    }

    private fun processMedia(message: Message) {
        val (type, fileId, uniqueId, thumb) = getMediaInfo(message) ?: return
        if (type.isEmpty()) return
        log.info("processMedia: $type $fileId (unique: $uniqueId)")
        if (thumb == null) {
            val msg = "Media without thumbnails are not supported yet"
            Methods.sendMessage(message.from.id.toLong(), msg).callAsync(this)
            return
        }

        // Add to global collection
        if (!repository.isItemExists(uniqueId)) {
            repository.addItem(DbItem(
                    id = fileId,
                    type = type,
                    uniqueId = uniqueId,
                    stickerSet = "",
                    animated = 0
            ))
        }

        downloadThumbForMediaType(type, uniqueId, thumb.fileId)
        val status = if (repository.isUserSetExists(uniqueId, message.from.id)) {
            "already exists in your collection"
        } else {
            repository.addUserSet(DbUserSet(uniqueId, message.from.id, Instant.now().epochSecond))
            "added to your collection"
        }
        val msg = type.capitalize() + " $status."
        Methods.sendMessage(message.from.id.toLong(), msg).callAsync(this)
    }

    data class MediaInfo(val type: String, val fileId: String, val uniqueId: String, val thumb: PhotoSize?)

    private fun getMediaInfo(msg: Message): MediaInfo? {
        msg.animation?.let { return MediaInfo("animation", it.fileId, it.fileUniqueId, it.thumb) }
        msg.document?.let { return MediaInfo("document", it.fileId, it.fileUniqueId, it.thumb) }
        msg.document
                ?.takeIf { it.mimeType == "image/gif" }
                ?.let {
                    return MediaInfo("gif", it.fileId, it.fileUniqueId, it.thumb)
                }
        msg.photo
                ?.maxBy { max -> max.width * max.height }
                ?.let { max ->
                    val thumb = msg.photo.minBy { min -> min.width * min.height }
                    return MediaInfo("photo", max.fileId, max.fileUniqueId, thumb)
                }
        msg.video?.let { return MediaInfo("video", it.fileId, it.fileUniqueId, it.thumb) }
        msg.voice?.let { return MediaInfo("voice", it.fileId, it.fileUniqueId, null) }
        return null
    }

    @Suppress("FoldInitializerAndIfToElvis")
    private fun processSticker(message: Message) {
        val setName = message.sticker.setName
        val stickerSet = Methods.Stickers.getStickerSet(setName).call(this)
        if (stickerSet == null) return

        if (!repository.isUserSetExists(setName, message.from.id)) {
            repository.addUserSet(DbUserSet(setName, message.from.id, Instant.now().epochSecond))
        }
        downloadThumbs(stickerSet) {
            Methods.sendChatAction(message.from.id.toLong(), ActionType.TYPING)
                    .call(this)
        }
        val newItemsCount = stickerSet.stickers
                .filterNot { repository.isItemExists(it.fileUniqueId) }
                .map {
                    DbItem(
                            id = it.fileId,
                            type = "sticker",
                            uniqueId = it.fileUniqueId,
                            stickerSet = stickerSet.name,
                            animated = if (it.animated) 1 else 0
                    )
                }
                .onEach { repository.addItem(it) }
                .count()
        log.info("processSticker: $setName added $newItemsCount of ${stickerSet.stickers.size}")
        val msg = """Sticker set "${stickerSet.title}" added"""
        Methods.sendMessage(message.from.id.toLong(), msg).call(this)
    }

    private fun downloadThumbs(stickerSet: StickerSet, callback: () -> Unit) {
        val parent = File("public/thumbs/${stickerSet.name}")
        parent.mkdirs()
        stickerSet.stickers.forEachIndexed { index, sticker ->
            // Every 60th sticker send chat action
            if (index.rem(60) == 0) {
                callback()
            }
            val localFile = File(parent, "${sticker.fileUniqueId}.png")
            Methods.getFile(sticker.thumb.fileId)
                    .callAsync(this) { tgFile -> downloadFile(tgFile, localFile) }
        }
    }

    private fun downloadThumbForMediaType(type: String, filename: String, thumbId: String) {
        val parent = File("public/thumbs/!$type")
        parent.mkdirs()
        val localFile = File(parent, "${filename}.png")
        Methods.getFile(thumbId)
                .callAsync(this) { tgFile -> downloadFile(tgFile, localFile) }
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
