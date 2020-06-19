package com.annimon.favisbot

import com.annimon.tgbotsmodule.BotModule
import com.annimon.tgbotsmodule.Runner
import com.annimon.tgbotsmodule.services.YamlConfigLoaderService
import com.dieselpoint.norm.Database
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
import kotlin.math.min

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

    private fun initDatabase(name: String): DbRepository {
        val db = Database()
        db.setJdbcUrl("jdbc:sqlite:${name}.db")
        val repository = DbRepository(db)
        repository.createTablesIfNotExists()
        return repository
    }

    private fun initServer(appConfig: AppConfig, repository: DbRepository) {
        // TODO: separate class
        val app = Javalin.create {
            it.addStaticFiles("/", "public", Location.EXTERNAL)
        }.apply {
            exception(Exception::class.java) { e, ctx -> e.printStackTrace() }
            error(404) { ctx -> ctx.json("not found") }
        }.start(appConfig.port ?: 9377)

        app.routes {
            get("/meta/:guid") { ctx ->
                val user = repository.findUserByGUID(ctx.pathParam("guid"))
                val sets = if (user == null) emptyList()
                           else repository.findAllStickerSets()
                ctx.json(hashMapOf(
                        "appName" to (appConfig.appName ?: appConfig.botUsername),
                        "bot" to appConfig.botUsername,
                        "user" to (user?.firstName ?: ""),
                        "stickerSets" to sets
                ))
            }

            before { ctx ->
                if (!ctx.path().startsWith("/items"))
                    return@before
                val guid = ctx.header("guid") ?: ""
                if (guid.isEmpty()) {
                    ctx.status(401)
                    return@before
                }
                val user = repository.findUserByGUID(guid)
                if (user == null) {
                    ctx.status(401)
                    return@before
                }
                ctx.attribute("user", user)
            }
            get("/items/:stickerSet") { ctx ->
                val stickerSet = ctx.pathParam("stickerSet")
                val user: DbUser = ctx.attribute("user")!!
                ctx.json(repository.findAllByStickerSet(user.id, stickerSet))
            }
            post("/items") { ctx ->
                val body = ctx.body<BodyItem>()
                val user: DbUser = ctx.attribute("user")!!
                val savedItem = DbSavedItem(body.id, user.id, body.tags)
                if (body.tags.isBlank()) {
                    val removed = repository.removeSavedItemIfExists(savedItem)
                    ctx.status(if (removed) 205 else 204)
                } else {
                    body.tags = body.tags.substring(0, min(body.tags.length, 255))
                    val created  = repository.upsertSavedItem(savedItem)
                    ctx.status(if (created) 201 else 200)
                }
            }
        }
    }

    data class BodyItem(val id: String, var tags: String)
}