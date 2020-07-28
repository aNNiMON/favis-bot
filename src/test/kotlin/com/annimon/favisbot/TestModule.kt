package com.annimon.favisbot

import com.annimon.favisbot.commands.AnnounceCommand
import com.annimon.favisbot.commands.HelpCommand
import com.annimon.favisbot.commands.RegisterCommand
import com.annimon.favisbot.commands.StartCommand
import com.annimon.favisbot.db.ItemsRepository
import com.annimon.favisbot.db.UserSetsRepository
import com.annimon.favisbot.db.UsersRepository
import com.dieselpoint.norm.Database
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import io.mockk.mockk

class TestModule : AbstractModule() {

    private val database = mockk<Database>(relaxed = true)

    override fun configure() {
        bind(ItemsRepository::class.java).`in`(Singleton::class.java)
        bind(UserSetsRepository::class.java).`in`(Singleton::class.java)
        bind(UsersRepository::class.java).`in`(Singleton::class.java)
        bind(Server::class.java).`in`(Singleton::class.java)
        // Bot commands
        bind(StartCommand::class.java).`in`(Singleton::class.java)
        bind(RegisterCommand::class.java).`in`(Singleton::class.java)
        bind(AnnounceCommand::class.java).`in`(Singleton::class.java)
        bind(HelpCommand::class.java).`in`(Singleton::class.java)
    }

    @Provides
    @Singleton
    fun getDatabase() = database

    @Provides
    @Singleton
    fun getAppConfig(): AppConfig = AppConfig(
        botToken = "botToken",
        botUsername = "botUsername",
        adminId = 12345,
        host = null,
        port = null,
        appName = "appName"
    )
}