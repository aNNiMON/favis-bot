package com.annimon.favisbot

import com.annimon.favisbot.db.*
import com.google.inject.Inject
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.Context
import io.javalin.http.staticfiles.Location
import org.jetbrains.annotations.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.min

class Server @Inject constructor(
        private val appConfig: AppConfig,
        private val itemsRepository: ItemsRepository,
        private val userSetsRepository: UserSetsRepository,
        private val usersRepository: UsersRepository
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(Server::class.java)
    }

    private lateinit var app: Javalin

    fun start() {
        app = Javalin.create {
            it.addStaticFiles("/", "public", Location.EXTERNAL)
        }.apply {
            exception(Exception::class.java) { e, ctx -> log.error("javalin", e) }
            error(404) { ctx -> ctx.json("not found") }
        }.start(appConfig.port ?: 9377)

        app.routes {
            get("/meta/:guid", ::getMeta)
            before(::authUserByGUID)
            get("/items/:set", ::getItemsInSet)
            post("/items", ::updateUserTag)
        }
    }

    fun stop() = app.stop()

    /**
     * Returns meta info: app and bot names, user info, set names
     */
    private fun getMeta(ctx: @NotNull Context) {
        val user = usersRepository.findUserByGUID(ctx.pathParam("guid"))
        val sets = if (user == null) emptyList()
                   else userSetsRepository.findAllUserSets(user.id)
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
        val user = usersRepository.findUserByGUID(guid)
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
            ctx.json(userSetsRepository.findAllByType(user.id, setName.trimStart('!')))
        } else {
            ctx.json(userSetsRepository.findAllByStickerSet(user.id, setName))
        }
    }

    /**
     * Update or remove item
     */
    private fun updateUserTag(ctx: @NotNull Context) {
        val body = ctx.body<BodyItem>()
        val user: DbUser = ctx.attribute("user")!!
        val savedItem = DbUserTag(body.uniqueId, user.id, body.tags)
        if (body.tags.isBlank()) {
            val removed = itemsRepository.removeUserTagIfExists(savedItem)
            ctx.status(if (removed) 205 else 204)
        } else {
            body.tags = body.tags.substring(0, min(body.tags.length, 255))
            val created = itemsRepository.replaceUserTags(savedItem)
            ctx.status(if (created) 201 else 200)
        }
    }

    data class BodyItem(val uniqueId: String, var tags: String)
}