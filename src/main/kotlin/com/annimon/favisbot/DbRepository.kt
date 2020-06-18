package com.annimon.favisbot

import com.dieselpoint.norm.Database

class DbRepository(private val db: Database) {

    fun isItemExists(id: String): Boolean {
        return db.sql("SELECT COUNT(*) FROM items WHERE id = ?", id)
            .first(Int::class.java) != 0
    }

    fun addItem(item: DbItem) {
        db.insert(item)
    }

    // Stockers

    fun findAllStickerSets(): List<String> = db.sql("""
            SELECT `stickerSet` FROM items
            GROUP BY `stickerSet`
            ORDER BY `stickerSet`
            """.trimIndent())
            .results(String::class.java)

    fun findAllByStickerSet(stickerSet: String): List<DbItem> = db.sql("""
            SELECT * FROM items
            WHERE `stickerSet` = ?
            """.trimIndent(), stickerSet)
            .results(DbItem::class.java)

    // Users

    fun isUserExists(id: Int): Boolean {
        return db.sql("SELECT COUNT(*) FROM users WHERE id = ?", id)
                .first(Int::class.java) != 0
    }

    fun findUserById(id: Int): DbUser? = db.where("id = ?", id).first(DbUser::class.java)

    fun findUserByGUID(guid: String): DbUser? = db.where("guid = ?", guid).first(DbUser::class.java)

    fun upsertUser(user: DbUser) {
        if (isUserExists(user.id)) {
            db.update(user)
        } else {
            db.insert(user)
        }
    }

    // Other

    fun createTablesIfNotExists() {
        db.sql("""
            CREATE TABLE IF NOT EXISTS items (
              `id`          TEXT PRIMARY KEY,
              `type`        TEXT NOT NULL,
              `stickerSet`  TEXT,
              `animated`    INTEGER NOT NULL
            )""".trimIndent()).execute()
        db.sql("""
            CREATE TABLE IF NOT EXISTS users (
              `id`          INTEGER PRIMARY KEY,
              `firstName`   TEXT NOT NULL,
              `guid`        TEXT NOT NULL,
              `allowed`     INTEGER NOT NULL,
              `updatedAt`   INTEGER NOT NULL
            )""".trimIndent()).execute()
        // TODO index
    }
}
