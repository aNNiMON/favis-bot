package com.annimon.favisbot.commands

import com.annimon.favisbot.AppConfig
import com.annimon.favisbot.db.DbUser
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_ALLOWED
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_IGNORED
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_PENDING
import com.annimon.favisbot.db.DbUser.Companion.ALLOWANCE_UNKNOWN
import com.annimon.favisbot.db.UsersRepository
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.google.inject.Inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

class RegisterCommand @Inject constructor(
        private val appConfig: AppConfig,
        private val usersRepository: UsersRepository
) : MessageCommand {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RegisterCommand::class.java)
    }

    override fun run(message: Message, bot: Bot) {
        val fromId = message.from!!.id
        val allowance = if (appConfig.adminId == fromId) {
            ALLOWANCE_ALLOWED
        } else {
            val user = usersRepository.findUserById(fromId)
            getAllowance(user)
        }
        when (allowance) {
            ALLOWANCE_IGNORED -> return
            ALLOWANCE_PENDING -> return
            ALLOWANCE_UNKNOWN -> {
                log.info("cmdRegister: by unknown $fromId")
                // send request to admin
                usersRepository.upsertUser(DbUser(
                        id = fromId.toInt(),
                        firstName = message.from!!.firstName,
                        guid = fromId.toString(),
                        allowed = ALLOWANCE_PENDING,
                        updatedAt = Instant.now().epochSecond
                ))
                bot.sendMessage(fromId, "You requested permission to access the bot." +
                        " After the administrator approves the application," +
                        " you will receive a message.")

                val msg = "User <a href=\"tg://user?id=$fromId\">" +
                        safeHtml(message.from!!.firstName) + "</a>" +
                        " requested access to the bot."
                bot.sendMessage(appConfig.adminId, msg,
                    parseMode = ParseMode.HTML,
                    replyMarkup = InlineKeyboardMarkup(listOf(
                        listOf(
                            InlineKeyboardButton("Allow", callbackData = "a:$fromId"),
                            InlineKeyboardButton("Ignore", callbackData = "i:$fromId")
                        )
                    )))
            }
            ALLOWANCE_ALLOWED -> {
                log.info("cmdRegister: by allowed $fromId")
                val guid = UUID.randomUUID().toString()
                usersRepository.upsertUser(DbUser(
                        id = fromId.toInt(),
                        firstName = message.from!!.firstName,
                        guid = guid,
                        allowed = ALLOWANCE_ALLOWED,
                        updatedAt = Instant.now().epochSecond
                ))
                val host = appConfig.host ?: "http://127.0.0.1"
                val port = appConfig.port ?: 9377
                bot.sendMessage(fromId,
                        "Here's your link to the web page.\n" +
                        "You can generate a new link by sending /register again.",
                        replyMarkup = InlineKeyboardMarkup.createSingleButton(
                            InlineKeyboardButton("Profile page", "$host:$port/?d=$guid")
                        ))
            }
        }
    }

    private fun getAllowance(user: DbUser?): Int {
        if (appConfig.adminId == user?.id?.toLong()) return ALLOWANCE_ALLOWED
        return (user?.allowed ?: ALLOWANCE_UNKNOWN)
    }
}