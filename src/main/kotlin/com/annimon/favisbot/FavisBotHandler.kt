package com.annimon.favisbot

import com.annimon.tgbotsmodule.BotHandler
import com.annimon.tgbotsmodule.api.methods.Methods
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.stickers.StickerSet
import java.io.File
import java.time.Instant
import java.util.*

class FavisBotHandler(
    private val appConfig: AppConfig,
    private val repository: ItemsRepository
) : BotHandler() {

    override fun getBotUsername() = appConfig.username

    override fun getBotToken() = appConfig.token

    override fun onUpdate(update: Update): BotApiMethod<*>? {
        if (update.hasMessage()) {
            processMessage(update.message)
        } else if (update.hasInlineQuery()) {

        }
        return null
    }


    private fun processMessage(message: Message) {
        if (!isUserAllowed(message.from.id)) {
            Methods.sendMessage(message.from.id.toLong(),
                "You don't have enough rights to access this bot")
                .callAsync(this)
            // return TODO: check admin and allow register
        }

        if (message.hasText()) {
            val (command, _) = message.text.split(" ".toRegex(), 2)
            when (command.toLowerCase()) {
                "/register" -> {
                    val guid = UUID.randomUUID().toString()
                    repository.addUser(DbUser(
                            id = message.from.id,
                            firstName = message.from.firstName,
                            guid = guid,
                            allowed = 1,
                            updatedAt = Instant.now().epochSecond
                    ))
                    Methods.sendMessage(message.from.id.toLong(),
                            "Your token: {url} $guid")
                            .callAsync(this)
                }
            }
        }

        if (message.hasSticker()) {
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

    private fun isUserAllowed(id: Int) = repository.findUserById(id)?.allowed != 0
}
