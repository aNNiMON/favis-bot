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

    // Saved Items

    fun isSavedItemExists(item: DbSavedItem) = db.sql(
            "SELECT COUNT(*) FROM savedItems WHERE itemId = ? AND userId = ?",
            item.itemId, item.userId)
            .first(Int::class.java) != 0

    fun removeSavedItemIfExists(item: DbSavedItem): Boolean {
        if (isSavedItemExists(item)) {
            db.table("savedItems")
                    .where("itemId = ? AND userId = ?", item.itemId, item.userId)
                    .delete()
            return true
        }
        return false
    }

    fun upsertSavedItem(item: DbSavedItem): Boolean {
        if (isSavedItemExists(item)) {
            db.sql("UPDATE savedItems SET tags = ? WHERE itemId = ? AND userId = ?",
                    item.tags, item.itemId, item.userId)
                    .execute()
            return false
        } else {
            db.sql("INSERT INTO savedItems(itemId, userId, tags) VALUES (?, ?, ?)",
                    item.itemId, item.userId, item.tags)
                    .execute()
            return true
        }
    }

    fun searchItems(q: String, userId: Int, limit: Int, offset: Int): Pair<Int, List<DbItemWithTags>> {
        val query = q.replace("[;:\"'`]".toRegex(), "").replace("%", "\\%")
        val columns = "`id`, `type`, `animated`"
        val sql = """
                FROM items
                INNER JOIN savedItems
                    ON savedItems.itemId = items.id AND savedItems.userId = ?
                """.trimIndent()
        return if (query.isBlank() || query == ".all") {
            val count = db.sql("SELECT COUNT(*) $sql", userId).first(Int::class.java)
            val items = db.sql("SELECT $columns $sql LIMIT $limit OFFSET $offset", userId).results(DbItemWithTags::class.java)
            Pair(count, items)
        } else {
            val where = "WHERE tags LIKE ?"
            val count = db.sql("SELECT COUNT(*) $sql $where", userId, "%$q%").first(Int::class.java)
            val items = db.sql("SELECT $columns $sql $where LIMIT $limit OFFSET $offset", userId, "%$q%").results(DbItemWithTags::class.java)
            Pair(count, items)
        }
    }

    // Stickers

    fun findAllStickerSets(): List<String> =
            db.sql("""
                SELECT `stickerSet` FROM items
                GROUP BY `stickerSet`
                ORDER BY `stickerSet`
                """.trimIndent())
            .results(String::class.java)

    fun findAllByStickerSet(userId: Int, stickerSet: String): List<DbItemWithTags> =
            db.sql("""
                SELECT items.*, savedItems.tags FROM items
                LEFT JOIN savedItems
                  ON savedItems.itemId = items.id AND savedItems.userId = ?
                WHERE stickerSet = ?
                """.trimIndent(), userId, stickerSet)
            .results(DbItemWithTags::class.java)

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
        db.sql("""
            CREATE TABLE IF NOT EXISTS savedItems (
              `itemId`      TEXT NOT NULL,
              `userId`      INTEGER NOT NULL,
              `tags`        TEXT NOT NULL,
              PRIMARY KEY (itemId, userId)
            )""".trimIndent()).execute()
        // Index
        db.sql("""
            CREATE INDEX IF NOT EXISTS "idx_sticker" ON "items" (
                "stickerSet" ASC
            );""".trimIndent()).execute()
        db.sql("""
            CREATE UNIQUE INDEX IF NOT EXISTS "idx_guid" ON "users" (
                "guid"
            );""".trimIndent()).execute()
    }
}
