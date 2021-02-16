package com.annimon.favisbot.commands

import com.annimon.favisbot.db.DbItem
import com.annimon.favisbot.db.DbUserSet
import com.annimon.favisbot.db.ItemsRepository
import com.annimon.favisbot.db.UserSetsRepository
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.files.PhotoSize
import com.github.kotlintelegrambot.network.fold
import com.google.inject.Inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

class OnMediaCommand @Inject constructor(
        private val itemsRepository: ItemsRepository,
        private val userSetsRepository: UserSetsRepository
) : MessageCommand {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(OnMediaCommand::class.java)
    }

    override fun run(message: Message, bot: Bot) {
        val (type, fileId, uniqueId, thumb) = getMediaInfo(message) ?: return
        if (type.isEmpty()) return
        log.info("processMedia: $type $fileId (unique: $uniqueId)")
        if (thumb == null) {
            bot.sendMessage(ChatId.fromId(message.from!!.id), "Media without thumbnails are not supported yet")
            return
        }

        // Add to global collection
        if (!itemsRepository.isItemExists(uniqueId)) {
            itemsRepository.addItem(DbItem(
                    id = fileId,
                    type = type,
                    uniqueId = uniqueId,
                    stickerSet = "",
                    animated = 0
            ))
        }

        downloadThumbForMediaType(type, uniqueId, thumb.fileId, bot)
        val status = if (userSetsRepository.isUserSetExists(uniqueId, message.from!!.id.toInt())) {
            "already exists in your collection"
        } else {
            userSetsRepository.addUserSet(DbUserSet(uniqueId, message.from!!.id.toInt(), Instant.now().epochSecond))
            "added to your collection"
        }
        val msg = type.capitalize() + " $status."
        bot.sendMessage(ChatId.fromId(message.from!!.id), msg)
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
                ?.maxByOrNull { max -> max.width * max.height }
                ?.let { max ->
                    val thumb = msg.photo?.minBy { min -> min.width * min.height }
                    return MediaInfo("photo", max.fileId, max.fileUniqueId, thumb)
                }
        msg.video?.let { return MediaInfo("video", it.fileId, it.fileUniqueId, it.thumb) }
        msg.voice?.let { return MediaInfo("voice", it.fileId, it.fileUniqueId, null) }
        return null
    }

    private fun downloadThumbForMediaType(type: String, filename: String, thumbId: String, bot: Bot) {
        val parent = File("public/thumbs/!$type")
        parent.mkdirs()
        val localFile = File(parent, "${filename}.png")
        bot.getFile(thumbId).fold({
            if (it?.ok == false || it?.result == null) return@fold
            bot.downloadFile(it.result!!.filePath!!).fold({ body ->
                localFile.outputStream().use { os ->
                    body!!.byteStream().copyTo(os, 8192)
                }
            })
        })
    }
}