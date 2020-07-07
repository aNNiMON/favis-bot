package com.annimon.favisbot

import com.annimon.tgbotsmodule.BotModule
import com.annimon.tgbotsmodule.Runner
import com.google.inject.Guice

object FavisBot {
    @JvmStatic
    fun main(args: Array<String>) {
        val injector = Guice.createInjector(FavisModule("favisbot"));
        Server(injector).start()
        Runner.run("", listOf(BotModule {
            FavisBotHandler(injector)
        }))
    }
}