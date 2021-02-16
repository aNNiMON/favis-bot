package com.annimon.favisbot.commands

import com.annimon.favisbot.AppConfig
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_ALLOWED
import com.annimon.favisbot.db.UsersRepository
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.google.inject.Inject
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.network.bimap
import kotlin.concurrent.thread

class AnnounceCommand @Inject constructor(
        private val appConfig: AppConfig,
        private val usersRepository: UsersRepository
) : MessageCommand {

    override fun run(message: Message, bot: Bot) {
        if (message.from!!.id != appConfig.adminId) return
        if (message.replyToMessage?.text == null) {
            val msg = "To announce message, you must reply to it with /announce command"
            bot.sendMessage(ChatId.fromId(appConfig.adminId), msg)
            return
        }

        val msg = message.replyToMessage?.text ?: ""
        val users = usersRepository.findUsersByAllowance(ALLOWANCE_ALLOWED)
        thread {
            val sentCount = users.mapIndexed { index, user ->
                if (index.rem(10) == 0) {
                    Thread.sleep(1000)
                }
                val result = bot.sendMessage(ChatId.fromId(user.id.toLong()), msg, parseMode = ParseMode.HTML)
                result.bimap({ 0 }, { 1 })
            }.sum()
            bot.sendMessage(ChatId.fromId(appConfig.adminId), "Message sent to $sentCount users")
        }
    }
}