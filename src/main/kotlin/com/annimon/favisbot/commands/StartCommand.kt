package com.annimon.favisbot.commands

import com.annimon.favisbot.AppConfig
import com.annimon.tgbotsmodule.api.methods.Methods
import com.annimon.tgbotsmodule.services.CommonAbsSender
import com.google.inject.Inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.objects.Message

class StartCommand @Inject constructor(
        private val appConfig: AppConfig
) : MessageCommand {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(StartCommand::class.java)
    }

    override fun run(message: Message, sender: CommonAbsSender) {
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
                .callAsync(sender)
    }
}