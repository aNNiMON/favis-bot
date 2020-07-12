package com.annimon.favisbot.commands

import com.annimon.favisbot.AppConfig
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_ALLOWED
import com.annimon.favisbot.db.UsersRepository
import com.annimon.tgbotsmodule.api.methods.Methods
import com.annimon.tgbotsmodule.services.CommonAbsSender
import com.google.inject.Inject
import org.telegram.telegrambots.meta.api.objects.Message

class AnnounceCommand @Inject constructor(
        private val appConfig: AppConfig,
        private val usersRepository: UsersRepository
) : MessageCommand {

    override fun run(message: Message, sender: CommonAbsSender) {
        if (message.from.id != appConfig.adminId) return
        if (message.replyToMessage == null || !message.replyToMessage.hasText()) {
            val msg = "To announce message, you must reply to it with /announce command"
            Methods.sendMessage(appConfig.adminId.toLong(), msg)
                    .callAsync(sender)
            return
        }

        val msg = message.replyToMessage.text
        val users = usersRepository.findUsersByAllowance(ALLOWANCE_ALLOWED)
                .filter { user -> user.id != appConfig.adminId }
        Thread {
            val sentCount = users.mapIndexed { index, user ->
                if (index.rem(10) == 0) {
                    Thread.sleep(1000)
                }
                val result = Methods.sendMessage(user.id.toLong(), msg)
                        .enableHtml()
                        .call(sender)
                if (result == null) 0 else 1
            }.sum()
            Methods.sendMessage(appConfig.adminId.toLong(), "Message sent to $sentCount users")
                    .callAsync(sender)
        }.start()
    }
}