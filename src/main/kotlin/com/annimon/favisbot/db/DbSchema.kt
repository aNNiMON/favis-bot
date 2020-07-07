package com.annimon.favisbot.db

import com.dieselpoint.norm.Database

class DbSchema(private val db: Database) {

    fun create() {
        items()
        users()
        userTags()
        userSets()
    }

    private fun items() {
        exec("""
            CREATE TABLE IF NOT EXISTS items (
              `id`          TEXT PRIMARY KEY,
              `type`        TEXT NOT NULL,
              `uniqueId`    TEXT NOT NULL UNIQUE,
              `stickerSet`  TEXT,
              `animated`    INTEGER NOT NULL
            )""".trimIndent())
        exec("""
            CREATE INDEX IF NOT EXISTS "idx_sticker" ON "items" (
                "stickerSet" ASC
            );""".trimIndent())
    }

    private fun users() {
        exec("""
            CREATE TABLE IF NOT EXISTS users (
              `id`          INTEGER PRIMARY KEY,
              `firstName`   TEXT NOT NULL,
              `guid`        TEXT NOT NULL,
              `allowed`     INTEGER NOT NULL,
              `updatedAt`   INTEGER NOT NULL
            )""".trimIndent())
        exec("""
            CREATE UNIQUE INDEX IF NOT EXISTS "idx_guid" ON "users" (
                "guid"
            );""".trimIndent())
    }

    private fun userTags() {
        exec("""
            CREATE TABLE IF NOT EXISTS userTags (
              `itemId`      TEXT NOT NULL,
              `userId`      INTEGER NOT NULL,
              `tag`         TEXT NOT NULL
            )""".trimIndent())
        exec("""
            CREATE INDEX IF NOT EXISTS "idx_userTag" ON "userTags" (
                "itemId", "userId"
            );""".trimIndent())
    }

    private fun userSets() {
        exec("""
            CREATE TABLE IF NOT EXISTS userSets (
              `setName`     TEXT NOT NULL,
              `userId`      INTEGER NOT NULL,
              `updatedAt`   INTEGER NOT NULL
            )""".trimIndent())
        exec("""
            CREATE INDEX IF NOT EXISTS "idx_userSet" ON "userSets" (
                "setName"
            );""".trimIndent())
        exec("""
            CREATE UNIQUE INDEX IF NOT EXISTS "idx_userSets" ON "userSets" (
                "setName", "userId"
            );""".trimIndent())
    }

    private fun exec(sql: String) = db.sql(sql).execute()
}