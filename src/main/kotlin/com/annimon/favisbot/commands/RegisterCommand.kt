package com.annimon.favisbot.commands

import com.annimon.favisbot.AppConfig
import com.annimon.favisbot.db.DbUser
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_ALLOWED
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_IGNORED
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_PENDING
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_UNKNOWN
import com.annimon.favisbot.db.UsersRepository
import com.annimon.tgbotsmodule.api.methods.Methods
import com.annimon.tgbotsmodule.services.CommonAbsSender
import com.google.inject.Inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.time.Instant
import java.util.*

class RegisterCommand @Inject constructor(
        private val appConfig: AppConfig,
        private val usersRepository: UsersRepository
) : MessageCommand {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RegisterCommand::class.java)
    }

    override fun run(message: Message, sender: CommonAbsSender) {
        val allowance = if (appConfig.adminId == message.from.id) {
            ALLOWANCE_ALLOWED
        } else {
            val user = usersRepository.findUserById(message.from.id)
            getAllowance(user)
        }
        when (allowance) {
            ALLOWANCE_IGNORED -> return
            ALLOWANCE_PENDING -> return
            ALLOWANCE_UNKNOWN -> {
                log.info("cmdRegister: by unknown ${message.from.id}")
                // send request to admin
                usersRepository.upsertUser(DbUser(
                        id = message.from.id,
                        firstName = message.from.firstName,
                        guid = message.from.id.toString(),
                        allowed = ALLOWANCE_PENDING,
                        updatedAt = Instant.now().epochSecond
                ))
                val fromId = message.from.id
                Methods.sendMessage()
                        .setText("You requested permission to access the bot." +
                                " After the administrator approves the application," +
                                " you will receive a message.")
                        .setChatId(fromId.toLong())
                        .callAsync(sender)

                val msg = "User <a href=\"tg://user?id=${fromId}\">" +
                        safeHtml(message.from.firstName) + "</a>" +
                        " requested access to the bot."
                val markup = InlineKeyboardMarkup()
                markup.keyboard = listOf(listOf(
                        InlineKeyboardButton()
                                .setText("Allow")
                                .setCallbackData("a:$fromId"),
                        InlineKeyboardButton()
                                .setText("Ignore")
                                .setCallbackData("i:$fromId")
                ))
                Methods.sendMessage(appConfig.adminId.toLong(), msg)
                        .enableHtml()
                        .setReplyMarkup(markup)
                        .callAsync(sender)
            }
            ALLOWANCE_ALLOWED -> {
                log.info("cmdRegister: by allowed ${message.from.id}")
                val guid = UUID.randomUUID().toString()
                usersRepository.upsertUser(DbUser(
                        id = message.from.id,
                        firstName = message.from.firstName,
                        guid = guid,
                        allowed = ALLOWANCE_ALLOWED,
                        updatedAt = Instant.now().epochSecond
                ))
                val host = appConfig.host ?: "http://127.0.0.1"
                val port = appConfig.port ?: 9377
                Methods.sendMessage(message.from.id.toLong(),
                        "Here's your link to the web page:\n" +
                                " 🌐 $host:$port/?d=$guid\n" +
                                "You can generate a new link by sending /register again.")
                        .callAsync(sender)
            }
        }
    }

    private fun getAllowance(user: DbUser?): Int {
        if (appConfig.adminId == user?.id) return ALLOWANCE_ALLOWED
        return (user?.allowed ?: ALLOWANCE_UNKNOWN)
    }
}