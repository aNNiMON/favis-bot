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

        val columns = "`id`, `type`, `animated`"
        val tablesSql = "FROM items INNER JOIN userTags si ON si.itemId = items.uniqueId AND si.userId = ?"
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
}
