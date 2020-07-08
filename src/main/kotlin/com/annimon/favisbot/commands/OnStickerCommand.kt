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
import org.telegram.telegrambots.meta.api.methods.ActionType
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.stickers.StickerSet
import java.io.File
import java.time.Instant

class OnStickerCommand @Inject constructor(
        private val itemsRepository: ItemsRepository,
        private val userSetsRepository: UserSetsRepository
) : MessageCommand {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(OnStickerCommand::class.java)
    }

    @Suppress("FoldInitializerAndIfToElvis")
    override fun run(message: Message, sender: CommonAbsSender) {
        val setName = message.sticker.setName
        val stickerSet = Methods.Stickers.getStickerSet(setName).call(sender)
        if (stickerSet == null) return

        if (!userSetsRepository.isUserSetExists(setName, message.from.id)) {
            userSetsRepository.addUserSet(DbUserSet(setName, message.from.id, Instant.now().epochSecond))
        }
        downloadThumbs(stickerSet, sender) {
            Methods.sendChatAction(message.from.id.toLong(), ActionType.TYPING)
                    .call(sender)
        }
        val newItemsCount = stickerSet.stickers
                .filterNot { itemsRepository.isItemExists(it.fileUniqueId) }
                .map { DbItem(
                        id = it.fileId,
                        type = "sticker",
                        uniqueId = it.fileUniqueId,
                        stickerSet = stickerSet.name,
                        animated = if (it.animated) 1 else 0
                ) }
                .onEach { itemsRepository.addItem(it) }
                .count()
        log.info("processSticker: $setName added $newItemsCount of ${stickerSet.stickers.size}")
        val msg = """Sticker set "${stickerSet.title}" added"""
        Methods.sendMessage(message.from.id.toLong(), msg).call(sender)
    }

    private fun downloadThumbs(stickerSet: StickerSet, sender: CommonAbsSender, callback: () -> Unit) {
        val parent = File("public/thumbs/${stickerSet.name}")
        parent.mkdirs()
        stickerSet.stickers.forEachIndexed { index, sticker ->
            // Every 60th sticker send chat action
            if (index.rem(60) == 0) {
                callback()
            }
            val localFile = File(parent, "${sticker.fileUniqueId}.png")
            Methods.getFile(sticker.thumb.fileId)
                    .callAsync(sender) { tgFile -> sender.downloadFile(tgFile, localFile) }
        }
    }
}