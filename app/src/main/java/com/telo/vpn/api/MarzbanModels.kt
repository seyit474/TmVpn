package com.telo.vpn.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer"
)

@Serializable
data class MarzbanUser(
    val username: String,
    val status: String = "active",
    @SerialName("subscription_url") val subscriptionUrl: String = "",
    val links: List<String> = emptyList(),
    @SerialName("used_traffic") val usedTraffic: Long = 0,
    @SerialName("data_limit") val dataLimit: Long = 0,
    val expire: Long? = null
)

data class LoginRequest(val panelUrl: String, val username: String, val password: String)
