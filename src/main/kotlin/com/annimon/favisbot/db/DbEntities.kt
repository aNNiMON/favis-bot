@file:Suppress("unused")

package com.annimon.favisbot.db

import javax.persistence.Id
import javax.persistence.Table

@Table(name = "items")
data class DbItem(
    @JvmField @Id
    var id: String,
    var type: String,
    var uniqueId: String,
    var stickerSet: String?,
    var animated: Int
) {
    constructor() : this("", "", "", "", 0)
}

data class DbItemWithTag(
    var id: String,
    var type: String,
    var uniqueId: String,
    var stickerSet: String?,
    var animated: Int,
    var tag: String?
) {
    constructor() : this("", "", "", "", 0, "")
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
        const val ALLOWANCE_UNKNOWN = 0
        const val ALLOWANCE_ALLOWED = 1
        const val ALLOWANCE_PENDING = 2
        const val ALLOWANCE_IGNORED = 3
    }
}

@Table(name = "userTags")
data class DbUserTag(
    var itemId: String,
    var userId: Int,
    var tag: String
) {
    constructor() : this("", 0, "")
}

@Table(name = "userSets")
data class DbUserSet(
        var setName: String,
        var userId: Int,
        var updatedAt: Long
) {
    constructor() : this("", 0, 0)
}