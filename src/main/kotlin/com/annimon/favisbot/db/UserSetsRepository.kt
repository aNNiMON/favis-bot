package com.annimon.favisbot.db

import com.dieselpoint.norm.Database
import com.google.inject.Inject

class UserSetsRepository @Inject constructor(private val db: Database) {

    fun findAllUserSets(userId: Int): List<String> =
            listOf("!animation", "!document", "!gif", "!photo", "!video") +
            db.sql("""
                SELECT `stickerSet` as setName FROM items
                INNER JOIN userSets ON items.stickerSet = userSets.setName
                WHERE stickerSet != "" AND userSets.userId = ?
                GROUP BY setName
                ORDER BY userSets.updatedAt DESC, setName
                """.trimIndent(), userId)
                .results(String::class.java)

    fun findAllByStickerSet(userId: Int, set: String): List<DbItemWithTag> =
            db.sql("""
                SELECT items.*, GROUP_CONCAT(userTags.tag, ", ") as tag FROM items
                LEFT JOIN userTags
                  ON userTags.itemId = items.uniqueId AND userTags.userId = ?
                GROUP BY id
                HAVING stickerSet = ?
                """.trimIndent(), userId, set)
                .results(DbItemWithTag::class.java)

    fun findAllByType(userId: Int, type: String): List<DbItemWithTag> =
            db.sql("""
                SELECT items.*, GROUP_CONCAT(userTags.tag, ", ") as tag FROM items
                INNER JOIN userSets
                  ON items.uniqueId = userSets.setName AND userSets.userId = ?
                LEFT JOIN userTags
                  ON userTags.itemId = items.uniqueId AND userTags.userId = ?
                GROUP BY id
                HAVING `type` = ?
                """.trimIndent(), userId, userId, type)
                .results(DbItemWithTag::class.java)

    fun isUserSetExists(setName: String, userId: Int) =
            db.sql("""
                SELECT COUNT(*) FROM userSets
                WHERE `setName` = ? AND userId = ?
                """.trimIndent(), setName, userId)
                .first(Int::class.java) != 0

    fun addUserSet(userSet: DbUserSet) {
        db.insert(userSet)
    }
}
