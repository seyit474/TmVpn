package com.seyit474.tmvpn.subscription

import com.seyit474.tmvpn.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Subscription URL'inden config listesini çeker.
 * doc.google.com endpoint'i Türkmenistan'da erişilebilir olduğu için
 * marzban-docs-sync sistemi bu yola yazıyor.
 */
class SubscriptionFetcher(
    private val client: OkHttpClient = defaultClient()
) {

    /** HTTP hata kodlarını UI'da ayrıştırabilmek için tipli exception. */
    class HttpException(val code: Int) : IOException("HTTP $code")

    suspend fun fetch(url: String): Result<List<ServerConfig>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw HttpException(resp.code)
                val body = resp.body?.string().orEmpty()
                ConfigParser.parseSubscription(body)
            }
        }
    }

    companion object {
        private const val USER_AGENT = "TmVpn/0.2"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
