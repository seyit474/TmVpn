package com.seyit474.tmvpn.subscription

import com.seyit474.tmvpn.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Subscription URL'inden config listesini çeker.
 * doc.google.com endpoint'i Türkmenistan'da erişilebilir olduğu için
 * marzban-docs-sync sistemi bu yola yazıyor.
 */
class SubscriptionFetcher(
    private val client: OkHttpClient = defaultClient()
) {

    suspend fun fetch(url: String): Result<List<ServerConfig>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "TmVpn/0.1")
                .build()
            client.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                val body = resp.body?.string().orEmpty()
                ConfigParser.parseSubscription(body)
            }
        }
    }

    suspend fun fetchWithBody(url: String): Result<Pair<String, List<ServerConfig>>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "TmVpn/0.1")
                .build()
            client.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                val body = resp.body?.string().orEmpty()
                Pair(body, ConfigParser.parseSubscription(body))
            }
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
