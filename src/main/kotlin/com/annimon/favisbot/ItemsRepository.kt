package com.annimon.favisbot

import com.dieselpoint.norm.Database

class ItemsRepository(private val db: Database) {

    fun isItemExists(id: String): Boolean {
        return db.sql("SELECT COUNT(*) FROM items WHERE id = ?", id)
            .first(Int::class.java) != 0
    }

    fun addItem(item: DbItem) {
        db.insert(item)
    }

    fun findAll() = db.results(DbItem::class.java)

    fun findById(id: String) = db.where("id = ?", id).first(DbItem::class.java)

    fun findAllStickerSets() = db.sql("""
            SELECT `stickerSet` FROM items
            GROUP BY `stickerSet`
            ORDER BY `stickerSet`
            """.trimIndent()).results(String::class.java)

    fun createTableIfNotExists() {
        db.sql("""
            CREATE TABLE IF NOT EXISTS items (
              `id`          TEXT PRIMARY KEY,
              `type`        TEXT NOT NULL,
              `stickerSet`  TEXT,
              `animated`    INTEGER NOT NULL
            )""".trimIndent()).execute()
    }
}
