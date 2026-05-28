package com.telo.vpn.api

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SubscriptionApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchSubscription(subUrl: String): Result<SubscriptionResult> = runCatching {
        val req = Request.Builder()
            .url(subUrl)
            .header("User-Agent", "TeloVPN/1.0 (Android)")
            .header("Accept", "*/*")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}: Abonelik alınamadı")
            val body = resp.body?.string() ?: error("Boş yanıt")
            val userInfo = parseUserInfoHeader(resp.header("Subscription-Userinfo") ?: "")
            SubscriptionResult(userInfo, body)
        }
    }

    private fun parseUserInfoHeader(header: String): SubscriptionUserInfo {
        if (header.isBlank()) return SubscriptionUserInfo()
        val map = header.split(";")
            .mapNotNull { part ->
                val kv = part.trim().split("=", limit = 2)
                if (kv.size == 2) kv[0].trim() to (kv[1].trim().toLongOrNull() ?: 0L)
                else null
            }.toMap()
        return SubscriptionUserInfo(
            upload = map["upload"] ?: 0L,
            download = map["download"] ?: 0L,
            total = map["total"] ?: 0L,
            expire = map["expire"] ?: 0L
        )
    }
}
