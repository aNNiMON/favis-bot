package com.annimon.favisbot.commands

import com.annimon.favisbot.db.DbItem
import com.annimon.favisbot.db.DbUserSet
import com.annimon.favisbot.db.ItemsRepository
import com.annimon.favisbot.db.UserSetsRepository
import com.annimon.tgbotsmodule.api.methods.Methods
import com.annimon.tgbotsmodule.services.CommonAbsSender
import com.google.inject.Inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.PhotoSize
import java.io.File
import java.time.Instant

class OnMediaCommand @Inject constructor(
        private val itemsRepository: ItemsRepository,
        private val userSetsRepository: UserSetsRepository
) : MessageCommand {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(OnMediaCommand::class.java)
    }

    override fun run(message: Message, sender: CommonAbsSender) {
        val (type, fileId, uniqueId, thumb) = getMediaInfo(message) ?: return
        if (type.isEmpty()) return
        log.info("processMedia: $type $fileId (unique: $uniqueId)")
        if (thumb == null) {
            val msg = "Media without thumbnails are not supported yet"
            Methods.sendMessage(message.from.id.toLong(), msg).callAsync(sender)
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

        downloadThumbForMediaType(type, uniqueId, thumb.fileId, sender)
        val status = if (userSetsRepository.isUserSetExists(uniqueId, message.from.id)) {
            "already exists in your collection"
        } else {
            userSetsRepository.addUserSet(DbUserSet(uniqueId, message.from.id, Instant.now().epochSecond))
            "added to your collection"
        }
        val msg = type.capitalize() + " $status."
        Methods.sendMessage(message.from.id.toLong(), msg).callAsync(sender)
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

    private fun downloadThumbForMediaType(type: String, filename: String, thumbId: String, sender: CommonAbsSender) {
        val parent = File("public/thumbs/!$type")
        parent.mkdirs()
        val localFile = File(parent, "${filename}.png")
        Methods.getFile(thumbId)
                .callAsync(sender) { tgFile -> sender.downloadFile(tgFile, localFile) }
    }
}