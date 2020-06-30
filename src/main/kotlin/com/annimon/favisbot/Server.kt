package com.annimon.favisbot

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder
import io.javalin.http.Context
import io.javalin.http.staticfiles.Location
import org.jetbrains.annotations.NotNull
import kotlin.math.min

class Server(val appConfig: AppConfig, val repository: DbRepository) {

    fun start() {
        val app = Javalin.create {
            it.addStaticFiles("/", "public", Location.EXTERNAL)
        }.apply {
            exception(Exception::class.java) { e, ctx -> e.printStackTrace() }
            error(404) { ctx -> ctx.json("not found") }
        }.start(appConfig.port ?: 9377)

        app.routes {
            ApiBuilder.get("/meta/:guid") { ctx ->
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

            ApiBuilder.before { ctx ->
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
            ApiBuilder.get("/items/:stickerSet") { ctx ->
                val stickerSet = ctx.pathParam("stickerSet")
                val user: DbUser = ctx.attribute("user")!!
                ctx.json(repository.findAllByStickerSet(user.id, stickerSet))
            }
            ApiBuilder.post("/items") { ctx ->
                val body = ctx.body<BodyItem>()
                val user: DbUser = ctx.attribute("user")!!
                val savedItem = DbSavedItem(body.id, user.id, body.tags)
                if (body.tags.isBlank()) {
                    val removed = repository.removeSavedItemIfExists(savedItem)
                    ctx.status(if (removed) 205 else 204)
                } else {
                    body.tags = body.tags.substring(0, min(body.tags.length, 255))
                    val created = repository.upsertSavedItem(savedItem)
                    ctx.status(if (created) 201 else 200)
                }
            }
        }
    }

    data class BodyItem(val id: String, var tags: String)
}