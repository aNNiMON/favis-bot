package com.annimon.favisbot

import com.annimon.favisbot.commands.HelpCommand
import com.annimon.favisbot.commands.RegisterCommand
import com.annimon.favisbot.commands.StartCommand
import com.annimon.favisbot.db.ItemsRepository
import com.annimon.favisbot.db.DbSchema
import com.annimon.favisbot.db.UserSetsRepository
import com.annimon.favisbot.db.UsersRepository
import com.annimon.tgbotsmodule.BotModule
import com.annimon.tgbotsmodule.Runner
import com.annimon.tgbotsmodule.services.YamlConfigLoaderService
import com.dieselpoint.norm.Database
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Provides
import com.google.inject.Singleton

class FavisBot(private val appName: String) : AbstractModule() {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val injector = Guice.createInjector(FavisBot("favisbot"))
            val server = injector.getInstance(Server::class.java)
            server.start()
            Runner.run("", listOf(BotModule {
                FavisBotHandler(injector)
            }))
        }
    }

    override fun configure() {
        bind(ItemsRepository::class.java).`in`(Singleton::class.java)
        bind(UserSetsRepository::class.java).`in`(Singleton::class.java)
        bind(UsersRepository::class.java).`in`(Singleton::class.java)
        bind(Server::class.java).`in`(Singleton::class.java)
        // Bot commands
        bind(StartCommand::class.java).`in`(Singleton::class.java)
        bind(RegisterCommand::class.java).`in`(Singleton::class.java)
        bind(HelpCommand::class.java).`in`(Singleton::class.java)
    }

    @Provides
    @Singleton
    fun getDatabase(): Database {
        val db = Database()
        db.setJdbcUrl("jdbc:sqlite:${appName}.db")
        DbSchema(db).create()
        return db
    }

    @Provides
    @Singleton
    fun getAppConfig(): AppConfig {
        val configLoader = YamlConfigLoaderService<AppConfig>()
        val configFile = configLoader.configFile(appName, "")
        return configLoader.load(configFile, AppConfig::class.java, false) {
            it.registerModule(KotlinModule())
        }
    }
}