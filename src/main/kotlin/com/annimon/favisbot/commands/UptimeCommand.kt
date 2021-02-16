package com.annimon.favisbot.commands

import com.annimon.favisbot.AppConfig
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.google.inject.Inject
import java.lang.management.ManagementFactory
import java.time.Duration

class UptimeCommand @Inject constructor(
        private val appConfig: AppConfig
) : MessageCommand {

    override fun run(message: Message, bot: Bot) {
        if (appConfig.adminId != message.from?.id) return
        val text = runtime() + "\n\n" + memory()
        bot.sendMessage(ChatId.fromId(message.from!!.id), text, parseMode=ParseMode.MARKDOWN)
    }

    private fun runtime() : String {
        val cb = ManagementFactory.getClassLoadingMXBean()
        val tb = ManagementFactory.getThreadMXBean()
        val rb = ManagementFactory.getRuntimeMXBean()
        val d = Duration.ofMillis(rb.uptime)
        val uptime = if (d.seconds < 60) {
            formatTemporal(d.seconds, "second", "seconds")
        } else {
            formatTemporal(d.toDays(), "day", "days") +
            formatTemporal(d.toHoursPart(), "hour", "hours") +
            formatTemporal(d.toMinutesPart(), "minute", "minutes")
        }
        val threads = "%s now, %s total".format(
            formatNumber(tb.threadCount),
            formatNumber(tb.totalStartedThreadCount) )
        return """
            🖥 *Runtime*
            Uptime: $uptime
            Threads: $threads
            """.trimIndent()
    }

    private fun memory() : String {
        val r = Runtime.getRuntime()
        val used = (r.totalMemory() - r.freeMemory()) / 1048576.0
        val total = r.totalMemory() / 1048576.0
        val max = r.maxMemory() / 1048576.0
        return """
            🎢 *Memory*
            Used: %.2f MiB
            Total: %.2f MiB
            Max: %.2f MiB
            """.trimIndent().format(used, total, max)
    }

    private fun formatTemporal(n: Number, form1: String, form2: String): String {
        return when (n.toLong()) {
            0L -> ""
            -1L, 1L -> "$n $form1"
            else -> "$n $form2"
        }
    }

    private fun formatNumber(num: Number): String {
        val n = num.toLong()
        return when {
            n < 1000 -> n.toString()
            n < 1000000 -> "%.2fK".format(n / 1000.0)
            else -> "%.2fM".format(n / 1000000.0)
        }
    }
}