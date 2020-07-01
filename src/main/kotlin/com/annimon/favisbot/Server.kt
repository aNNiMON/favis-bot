package com.annimon.favisbot

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.Context
import io.javalin.http.staticfiles.Location
import org.jetbrains.annotations.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.min

class Server(private val appConfig: AppConfig,
             private val repository: DbRepository) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(Server::class.java)
    }

    fun start() {
        val app = Javalin.create {
            it.addStaticFiles("/", "public", Location.EXTERNAL)
        }.apply {
            exception(Exception::class.java) { e, ctx -> log.error("javalin", e) }
            error(404) { ctx -> ctx.json("not found") }
        }.start(appConfig.port ?: 9377)

        app.routes {
            get("/meta/:guid", ::getMeta)
            before(::authUserByGUID)
            get("/items/:set", ::getItemsInSet)
            post("/items", ::updateSavedItem)
        }
    }

    /**
     * Returns meta info: app and bot names, user info, set names
     */
    private fun getMeta(ctx: @NotNull Context) {
        val user = repository.findUserByGUID(ctx.pathParam("guid"))
        val sets = if (user == null) emptyList()
                   else repository.findAllUserSets(user.id)
        ctx.json(hashMapOf(
                "appName" to (appConfig.appName ?: appConfig.botUsername),
                "bot" to appConfig.botUsername,
                "user" to (user?.firstName ?: ""),
                "stickerSets" to sets
        ))
    }

    /**
     * Finds a user by guid or denies the request
     */
    private fun authUserByGUID(ctx: @NotNull Context) {
        if (!ctx.path().startsWith("/items"))
            return
        val guid = ctx.header("guid") ?: ""
        if (guid.isEmpty()) {
            ctx.status(401)
            return
        }
        val user = repository.findUserByGUID(guid)
        if (user == null) {
            ctx.status(401)
            return
        }
        ctx.attribute("user", user)
    }

    /**
     * Returns items in set with user tags
     */
    private fun getItemsInSet(ctx: @NotNull Context) {
        val setName = ctx.pathParam("set")
        val user: DbUser = ctx.attribute("user")!!
        if (setName.startsWith("!")) {
            ctx.json(repository.findAllByType(user.id, setName.trimStart('!')))
        } else {
            ctx.json(repository.findAllByStickerSet(user.id, setName))
        }
    }

    /**
     * Update or remove item
     */
    private fun updateSavedItem(ctx: @NotNull Context) {
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

    data class BodyItem(val id: String, var tags: String)
}