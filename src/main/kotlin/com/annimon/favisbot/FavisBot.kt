package com.annimon.favisbot

import com.annimon.tgbotsmodule.BotModule
import com.annimon.tgbotsmodule.Runner
import com.annimon.tgbotsmodule.services.YamlConfigLoaderService
import com.dieselpoint.norm.Database
import com.fasterxml.jackson.module.kotlin.KotlinModule

object FavisBot {
    @JvmStatic
    fun main(args: Array<String>) {
        val appConfig = loadConfig("favisbot")
        val repository = initDatabase("favisbot")
        Server(appConfig, repository).start()
        Runner.run("", listOf(BotModule {
            FavisBotHandler(appConfig, repository)
        }))
    }

    private fun loadConfig(name: String): AppConfig {
        val configLoader = YamlConfigLoaderService<AppConfig>()
        val configFile = configLoader.configFile(name, "")
        return configLoader.load(configFile, AppConfig::class.java, false) {
            it.registerModule(KotlinModule())
        }
    }

    private fun initDatabase(name: String): DbRepository {
        val db = Database()
        db.setJdbcUrl("jdbc:sqlite:${name}.db")
        val repository = DbRepository(db)
        repository.createTablesIfNotExists()
        return repository
    }
}