package com.annimon.favisbot

import com.annimon.tgbotsmodule.BotHandler
import com.annimon.tgbotsmodule.api.methods.Methods
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.stickers.StickerSet
import java.io.File

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
            return
        }

        if (message.hasSticker()) {
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

    private fun isUserAllowed(id: Int) = appConfig.allowedUsers.contains(id)
}
