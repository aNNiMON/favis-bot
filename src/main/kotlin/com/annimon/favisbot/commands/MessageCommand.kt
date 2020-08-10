package com.annimon.favisbot.commands

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.Message

interface MessageCommand {
    fun run(message: Message, bot: Bot)

    fun safeHtml(text: String?): String {
        if (text == null) return ""
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
    }
}