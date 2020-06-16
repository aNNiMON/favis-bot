package com.annimon.favisbot

import com.annimon.tgbotsmodule.BotModule
import com.annimon.tgbotsmodule.Runner
import com.annimon.tgbotsmodule.services.YamlConfigLoaderService
import com.dieselpoint.norm.Database
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location

object FavisBot {
    @JvmStatic
    fun main(args: Array<String>) {
        val appConfig = loadConfig("favisbot")
        val repository = initDatabase("favisbot")
        initServer(appConfig, repository)

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

    private fun initDatabase(name: String): ItemsRepository {
        val db = Database()
        db.setJdbcUrl("jdbc:sqlite:${name}.db")
        val repository = ItemsRepository(db)
        repository.createTableIfNotExists()
        return repository
    }

    private fun initServer(appConfig: AppConfig, repository: ItemsRepository) {
        val app = Javalin.create {
            it.addStaticFiles("/", "public", Location.EXTERNAL)
        }.apply {
            exception(Exception::class.java) { e, ctx -> e.printStackTrace() }
            error(404) { ctx -> ctx.json("not found") }
        }.start(appConfig.port ?: 9377)

        app.routes {
            get("/meta") { ctx -> ctx.json(hashMapOf(
                "appName" to (appConfig.appName ?: appConfig.username),
                "username" to appConfig.username,
                "stickerSets" to repository.findAllStickerSets()
            )) }

            get("/items") { ctx ->
                ctx.json(repository.findAll())
            }
            get("/items/:id") { ctx ->
                val id = ctx.pathParam("id")
                ctx.json(repository.findById(id))
            }
        }
    }
}