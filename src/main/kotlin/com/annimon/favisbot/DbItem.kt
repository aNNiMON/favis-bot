package com.annimon.favisbot

import javax.persistence.Id
import javax.persistence.Table

@Table(name = "items")
data class DbItem(
    @Id
    var id: String,
    var type: String,
    var stickerSet: String?,
    var animated: Int
) {
    constructor() : this("", "", "", 0)
}