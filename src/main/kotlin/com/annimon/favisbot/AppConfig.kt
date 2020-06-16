package com.annimon.favisbot

import com.fasterxml.jackson.annotation.JsonProperty

data class AppConfig(
    @JsonProperty(required = true)
    val token: String,

    @JsonProperty(required = true)
    val username: String,

    @JsonProperty(required = true)
    val allowedUsers: Set<Int>,

    @JsonProperty
    val port: Int?,

    @JsonProperty
    val appName: String?,

    @JsonProperty
    val secret: String?
)