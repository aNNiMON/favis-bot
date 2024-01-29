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
                .distinctBy { it.lowercase() }
                .forEach { tag ->
                    db.sql("INSERT INTO userTags(itemId, userId, tag) VALUES (?, ?, ?)",
                            item.itemId, item.userId, tag)
                            .execute()
                }
        return !wasExists
    }

    fun searchItems(q: String, userId: Long, limit: Int, offset: Int): Pair<Int, List<DbItemWithTag>> {
        val query = q.replace("[;:\"'`]".toRegex(), "")
        val tags = query.split(",\\s*".toRegex()).filterNot(String::isBlank)

        // Show all items
        if (query.isBlank() || query == ".all" || tags.isEmpty()) {
            val count = db.sql("""
                    SELECT COUNT(DISTINCT id) FROM items
                    INNER JOIN userTags t ON t.itemId = items.uniqueId AND t.userId = ?
                    """.trimIndent(), userId).first(Int::class.java)
            val items = db.sql("""
                    SELECT `id`, `type`, `animated` FROM items 
                    INNER JOIN userTags t ON t.itemId = items.uniqueId AND t.userId = ?
                    LEFT JOIN userSets s ON s.setName = 
                        (CASE WHEN items.type = "sticker"
                              THEN items.stickerSet
                              ELSE items.uniqueId
                         END)
                        AND s.userId = ?
                    GROUP BY id
                    ORDER BY updatedAt DESC
                    LIMIT $limit OFFSET $offset
                    """.trimIndent(), userId, userId).results(DbItemWithTag::class.java)
            return Pair(count, items)
        }

        // Search by tags
        val conditions = ArrayList<String>()
        val args = ArrayList<String>()
        for (tag in tags) {
            var tagArg = tag.trim()
            val isExact = tagArg.endsWith(".")
            tagArg = tagArg.trimEnd('.')
            if (isExact) {
                conditions.add("tag = ?")
                args.add(tagArg)
            } else {
                conditions.add("tag LIKE ?")
                args.add("%" + tagArg.replace("%", "\\%") + "%")
            }
        }
        val sqlQuery = """
                SELECT `id`, `type`, `animated`, COUNT(*) AS cnt FROM items
                INNER JOIN userTags t ON t.itemId = items.uniqueId AND t.userId = ?
                WHERE ${conditions.joinToString(" OR ")}
                GROUP BY id
                HAVING cnt = ${tags.count()}
                """.trimIndent()
        val count = db.sql("SELECT COUNT(*) FROM ($sqlQuery) AS tbl",
                userId, *args.toArray()).first(Int::class.java)
        val items = db.sql("$sqlQuery LIMIT $limit OFFSET $offset",
                userId, *args.toArray()).results(DbItemWithTag::class.java)
        return Pair(count, items)
    }
}
