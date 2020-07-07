package com.annimon.favisbot

import com.annimon.favisbot.db.DbRepository
import com.annimon.favisbot.db.DbSchema
import com.annimon.tgbotsmodule.services.YamlConfigLoaderService
import com.dieselpoint.norm.Database
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton


class FavisModule(private val appName: String) : AbstractModule() {

    override fun configure() {
        bind(DbRepository::class.java).`in`(Singleton::class.java)
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