package com.annimon.favisbot.commands

import com.annimon.favisbot.AppConfig
import com.annimon.tgbotsmodule.api.methods.Methods
import com.annimon.tgbotsmodule.services.CommonAbsSender
import com.google.inject.Inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.objects.Message

class HelpCommand @Inject constructor(
        private val appConfig: AppConfig
) : MessageCommand {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(HelpCommand::class.java)
    }

    override fun run(message: Message, sender: CommonAbsSender) {
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
            Type `$bot term` to show items containing the substring "term" tag.
            For exact match only, use `.` at the end of your query:  `$bot term.`
            To show all your tagged collection you can use `.all` query.
            """.trimIndent()
        Methods.sendMessage()
                .setChatId(message.from.id.toLong())
                .setText(text)
                .enableMarkdown()
                .callAsync(sender)
    }
}