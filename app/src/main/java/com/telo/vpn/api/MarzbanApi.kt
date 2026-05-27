package com.telo.vpn.api

import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MarzbanApi {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun String.normalizeUrl(): String =
        trimEnd('/')

    suspend fun login(panelUrl: String, username: String, password: String): Result<TokenResponse> =
        runCatching {
            val url = "${panelUrl.normalizeUrl()}/api/admin/token"
            val body = FormBody.Builder()
                .add("grant_type", "password")
                .add("username", username)
                .add("password", password)
                .build()
            val req = Request.Builder().url(url).post(body).build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: error("Boş yanıt")
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        json.decodeFromString<ErrorBody>(raw).detail
                    }.getOrDefault("HTTP ${resp.code}")
                    error(msg)
                }
                json.decodeFromString<TokenResponse>(raw)
            }
        }

    suspend fun getUser(panelUrl: String, username: String, token: String): Result<MarzbanUser> =
        runCatching {
            val url = "${panelUrl.normalizeUrl()}/api/user/$username"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: error("Boş yanıt")
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                json.decodeFromString<MarzbanUser>(raw)
            }
        }

    // Subscription URL'den sunucu listesi çeker (base64 veya düz metin)
    suspend fun fetchSubscription(subscriptionUrl: String): Result<String> =
        runCatching {
            val req = Request.Builder()
                .url(subscriptionUrl)
                .header("User-Agent", "TeloVPN/1.0 (Android)")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                resp.body?.string() ?: error("Boş abonelik yanıtı")
            }
        }

    @kotlinx.serialization.Serializable
    private data class ErrorBody(val detail: String = "")
}
