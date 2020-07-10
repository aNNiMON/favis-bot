package com.annimon.favisbot.db

import com.dieselpoint.norm.Database
import com.google.inject.Inject

class ItemsRepository @Inject constructor(private val db: Database) {

    fun isItemExists(uniqueId: String): Boolean {
        return db.sql("SELECT COUNT(*) FROM items WHERE uniqueId = ?", uniqueId)
            .first(Int::class.java) != 0
    }

    fun addItem(item: DbItem) {
        db.insert(item)
    }

    fun isItemTagged(item: DbUserTag) = db.sql(
            "SELECT COUNT(*) FROM userTags WHERE itemId = ? AND userId = ?",
            item.itemId, item.userId)
            .first(Int::class.java) != 0

    /**
     * @return true - element was exists, false - otherwise
     */
    fun removeUserTagIfExists(item: DbUserTag): Boolean {
        if (isItemTagged(item)) {
            db.table("userTags")
                    .where("itemId = ? AND userId = ?", item.itemId, item.userId)
                    .delete()
            return true
        }
        return false
    }

    /**
     * @return true - new element, false - update old
     */
    fun replaceUserTags(item: DbUserTag): Boolean {
        val wasExists = removeUserTagIfExists(item)
        item.tag.split(",")
                .map { it.trim() }
                .distinctBy { it.toLowerCase() }
                .forEach { tag ->
                    db.sql("INSERT INTO userTags(itemId, userId, tag) VALUES (?, ?, ?)",
                            item.itemId, item.userId, tag)
                            .execute()
                }
        return !wasExists
    }

    fun searchItems(q: String, userId: Int, limit: Int, offset: Int): Pair<Int, List<DbItemWithTag>> {
        val isExact = q.endsWith(".")
        var query = q.replace("[;:\"'`]".toRegex(), "")

        return when {
            query.isBlank() || query == ".all" -> {
                val count = db.sql("""
                    SELECT COUNT(DISTINCT id) FROM items
                    INNER JOIN userTags t ON t.itemId = items.uniqueId AND t.userId = ?
                    """.trimIndent(), userId).first(Int::class.java)
                val items = db.sql("""
                    SELECT `id`, `type`, `animated` FROM items 
                    INNER JOIN userTags t ON t.itemId = items.uniqueId AND t.userId = ?
                    GROUP BY id
                    LIMIT $limit OFFSET $offset
                    """.trimIndent(), userId).results(DbItemWithTag::class.java)
                Pair(count, items)
            }
            isExact -> {
                query = query.trimEnd('.')
                val count = db.sql("""
                    SELECT COUNT(DISTINCT id) FROM items
                    INNER JOIN userTags t ON t.itemId = items.uniqueId AND t.userId = ?
                    WHERE tag = ?
                    """.trimIndent(), userId, query).first(Int::class.java)
                val items = db.sql("""
                    SELECT `id`, `type`, `animated` FROM items
                    INNER JOIN userTags t ON t.itemId = items.uniqueId AND t.userId = ?
                    WHERE tag = ?
                    LIMIT $limit OFFSET $offset
                    """.trimIndent(), userId, query).results(DbItemWithTag::class.java)
                Pair(count, items)
            }
            else -> {
                query = "%" + query.replace("%", "\\%") + "%"
                val count = db.sql("""
                    SELECT COUNT(*) FROM (
                        SELECT DISTINCT id FROM items
                        INNER JOIN userTags t ON t.itemId = items.uniqueId AND t.userId = ?
                        WHERE tag LIKE ? GROUP BY id
                    ) AS tbl
                    """.trimIndent(), userId, query).first(Int::class.java)
                val items = db.sql("""
                        SELECT `id`, `type`, `animated` FROM items
                        INNER JOIN userTags t ON t.itemId = items.uniqueId AND t.userId = ?
                        WHERE tag LIKE ? GROUP BY id
                        LIMIT $limit OFFSET $offset
                        """.trimIndent(), userId, query).results(DbItemWithTag::class.java)
                Pair(count, items)
            }
        }
    }
}
