package com.annimon.favisbot

import com.fasterxml.jackson.annotation.JsonProperty

data class AppConfig(
    @JsonProperty(required = true)
    val botToken: String,

    @JsonProperty(required = true)
    val botUsername: String,

    @JsonProperty(required = true)
    val adminId: Int,

    @JsonProperty
    val port: Int?,

    @JsonProperty
    val appName: String?
)