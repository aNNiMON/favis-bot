package com.annimon.favisbot.commands

import com.annimon.tgbotsmodule.services.CommonAbsSender
import org.telegram.telegrambots.meta.api.objects.Message

interface MessageCommand {
    fun run(message: Message, sender: CommonAbsSender)

    fun safeHtml(text: String?): String {
        if (text == null) return ""
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
    }
}