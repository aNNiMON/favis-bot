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

    /**
     * @return true - element was exists, false - otherwise
     */
    fun removeSavedItemIfExists(item: DbSavedItem): Boolean {
        if (isSavedItemExists(item)) {
            db.table("savedItems")
                    .where("itemId = ? AND userId = ?", item.itemId, item.userId)
                    .delete()
            return true
        }
        return false
    }

    /**
     * @return true - new element, false - update old
     */
    fun upsertSavedItem(item: DbSavedItem): Boolean {
        val wasExists = removeSavedItemIfExists(item)
        item.tag.split(",")
                .map { it.trim() }
                .distinctBy { it.toLowerCase() }
                .forEach { tag ->
                    db.sql("INSERT INTO savedItems(itemId, userId, tag) VALUES (?, ?, ?)",
                            item.itemId, item.userId, tag)
                            .execute()
                }
        return !wasExists
    }

    fun searchItems(q: String, userId: Int, limit: Int, offset: Int): Pair<Int, List<DbItemWithTag>> {
        val isExact = q.endsWith(".")
        var query = q.replace("[;:\"'`]".toRegex(), "")
        
        val columns = "`id`, `type`, `animated`"
        val tablesSql = "FROM items INNER JOIN savedItems si ON si.itemId = items.id AND si.userId = ?"
        val limitSql = "LIMIT $limit OFFSET $offset"
        return when {
            query.isBlank() || query == ".all" -> {
                val count = db.sql("SELECT COUNT(DISTINCT id) $tablesSql", userId).first(Int::class.java)
                val items = db.sql("SELECT $columns $tablesSql GROUP BY id $limitSql", userId)
                        .results(DbItemWithTag::class.java)
                Pair(count, items)
            }
            isExact -> {
                query = query.trimEnd('.')
                val searchSql = "WHERE tag = ?"
                val count = db.sql("SELECT COUNT(DISTINCT id) $tablesSql $searchSql", userId, query).first(Int::class.java)
                val items = db.sql("SELECT $columns $tablesSql $searchSql $limitSql", userId, query)
                        .results(DbItemWithTag::class.java)
                Pair(count, items)
            }
            else -> {
                query = "%" + query.replace("%", "\\%") + "%"
                val searchSql = "WHERE tag LIKE ? GROUP BY id"
                val innerSql = "SELECT DISTINCT id $tablesSql $searchSql"
                val count = db.sql("SELECT COUNT(*) FROM ( $innerSql ) AS tbl", userId, query).first(Int::class.java)
                val items = db.sql("SELECT $columns $tablesSql $searchSql $limitSql", userId, query)
                        .results(DbItemWithTag::class.java)
                Pair(count, items)
            }
        }
    }

    // Sets

    fun findAllSets(): List<String> =
            db.sql("""
                SELECT ("!" || `type`) as `set` FROM items
                WHERE `type` IN ("gif")
                UNION
                SELECT `stickerSet` as `set` FROM items
                WHERE stickerSet != ""
                GROUP BY `set`
                ORDER BY `set`
                """.trimIndent())
            .results(String::class.java)

    fun findAllByStickerSet(userId: Int, set: String): List<DbItemWithTag> =
            db.sql("""
                SELECT items.*, GROUP_CONCAT(savedItems.tag, ", ") as tag FROM items
                LEFT JOIN savedItems
                  ON savedItems.itemId = items.id AND savedItems.userId = ?
                GROUP BY id
                HAVING stickerSet = ?
                """.trimIndent(), userId, set)
                .results(DbItemWithTag::class.java)

    fun findAllByType(userId: Int, type: String): List<DbItemWithTag> =
            db.sql("""
                SELECT items.*, GROUP_CONCAT(savedItems.tag, ", ") as tag FROM items
                LEFT JOIN savedItems
                  ON savedItems.itemId = items.id AND savedItems.userId = ?
                GROUP BY id
                HAVING `type` = ?
                """.trimIndent(), userId, type)
                .results(DbItemWithTag::class.java)

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
              `tag`         TEXT NOT NULL
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
        db.sql("""
            CREATE INDEX IF NOT EXISTS "idx_userItem" ON "savedItems" (
                "itemId", "userId"
            );""".trimIndent()).execute()
    }
}
