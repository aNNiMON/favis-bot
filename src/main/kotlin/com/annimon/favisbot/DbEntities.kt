package com.annimon.favisbot

import javax.persistence.Id
import javax.persistence.Table

@Table(name = "items")
data class DbItem(
    @JvmField @Id
    var id: String,
    var type: String,
    var stickerSet: String?,
    var animated: Int
) {
    constructor() : this("", "", "", 0)
}

data class DbItemWithTags(
    var id: String,
    var type: String,
    var stickerSet: String?,
    var animated: Int,
    var tags: String?
) {
    constructor() : this("", "", "", 0, "")
}

@Table(name = "users")
data class DbUser(
    @JvmField @Id
    var id: Int,
    var firstName: String,
    var guid: String?,
    var allowed: Int,
    var updatedAt: Long
) {
    constructor() : this(0, "", "", ALLOWANCE_UNKNOWN, 0)

    companion object {
        val ALLOWANCE_UNKNOWN = 0
        val ALLOWANCE_ALLOWED = 1
        val ALLOWANCE_PENDING = 2
        val ALLOWANCE_IGNORED = 3
    }
}

@Table(name = "savedItems")
data class DbSavedItem(
    var itemId: String,
    var userId: Int,
    var tags: String
) {
    constructor() : this("", 0, "")
}