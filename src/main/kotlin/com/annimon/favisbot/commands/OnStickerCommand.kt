package com.annimon.favisbot.commands

import com.annimon.favisbot.db.DbItem
import com.annimon.favisbot.db.DbUserSet
import com.annimon.favisbot.db.ItemsRepository
import com.annimon.favisbot.db.UserSetsRepository
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatAction
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.stickers.StickerSet
import com.github.kotlintelegrambot.network.fold
import com.google.inject.Inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
    override fun run(message: Message, bot: Bot) {
        val setName = message.sticker?.setName ?: return
        val fromId = message.from?.id ?: return
        bot.getStickerSet(setName).fold({  r ->
            if (r?.ok == false || r?.result == null) return@fold
            val stickerSet = r.result!!

            if (!userSetsRepository.isUserSetExists(setName, fromId.toInt())) {
                userSetsRepository.addUserSet(DbUserSet(setName, fromId.toInt(), Instant.now().epochSecond))
            }
            downloadThumbs(stickerSet, bot) {
                bot.sendChatAction(ChatId.fromId(fromId), ChatAction.TYPING)
            }

            val newItemsCount = stickerSet.stickers
                .filterNot { itemsRepository.isItemExists(it.fileUniqueId) }
                .map { DbItem(
                    id = it.fileId,
                    type = "sticker",
                    uniqueId = it.fileUniqueId,
                    stickerSet = stickerSet.name,
                    animated = if (it.isAnimated) 1 else 0
                ) }
                .onEach { itemsRepository.addItem(it) }
                .count()
            log.info("processSticker: $setName added $newItemsCount of ${stickerSet.stickers.size}")
            bot.sendMessage(ChatId.fromId(fromId), """Sticker set "${stickerSet.title}" added""")
        })
    }

    private fun downloadThumbs(stickerSet: StickerSet, bot: Bot, callback: () -> Unit) {
        val parent = File("public/thumbs/${stickerSet.name}")
        parent.mkdirs()
        stickerSet.stickers.forEachIndexed { index, sticker ->
            // Every 60th sticker send chat action
            if (index.rem(60) == 0) {
                callback()
            }
            val localFile = File(parent, "${sticker.fileUniqueId}.png")
            bot.getFile(sticker.thumb!!.fileId).fold({
                if (it?.ok == false || it?.result == null) return@fold
                bot.downloadFile(it.result!!.filePath!!).fold({ body ->
                    localFile.outputStream().use { os ->
                        body!!.byteStream().copyTo(os, 8192)
                    }
                })
            })
        }
    }
}