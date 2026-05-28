package com.telo.vpn.data

import android.content.Context
import com.telo.vpn.api.MarzbanApi
import com.telo.vpn.api.SubscriptionUserInfo
import com.telo.vpn.model.PingedServer
import com.telo.vpn.ping.ServerPinger
import com.telo.vpn.subscription.ConfigParser
import kotlinx.coroutines.flow.first

class MarzbanRepository(context: Context) {

    val prefs = AppPreferences(context)
    private val api = MarzbanApi()
    private val pinger = ServerPinger()

    companion object {
        // Admin tarafından sabitlenmiş subscription base URL
        const val BASE_SUB_URL = "http://194.36.89.199:4541/sub/"
    }

    suspend fun connectWithKey(subKey: String): Result<Pair<SubscriptionUserInfo, List<PingedServer>>> =
        runCatching {
            val fullUrl = buildUrl(subKey)
            val result = api.fetchSubscription(fullUrl).getOrThrow()
            val configs = ConfigParser.parseSubscription(result.rawLinks)
            if (configs.isEmpty()) error("Abonelikte sunucu bulunamadı")
            prefs.saveKey(subKey.trim())
            val pinged = pinger.pingAll(configs)
            result.userInfo to pinged
        }

    suspend fun refresh(): Result<Pair<SubscriptionUserInfo, List<PingedServer>>> = runCatching {
        val key = prefs.subKey.first().ifEmpty { error("Anahtar girilmemiş") }
        val result = api.fetchSubscription(buildUrl(key)).getOrThrow()
        val configs = ConfigParser.parseSubscription(result.rawLinks)
        if (configs.isEmpty()) error("Abonelikte sunucu bulunamadı")
        pinger.pingAll(configs).let { result.userInfo to it }
    }

    suspend fun hasKey(): Boolean = prefs.subKey.first().isNotEmpty()
    suspend fun clearKey() = prefs.clearKey()

    /** Token ise base URL ile birleştirir, tam URL ise olduğu gibi kullanır */
    private fun buildUrl(key: String): String {
        val trimmed = key.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
            trimmed
        else
            BASE_SUB_URL + trimmed
    }
}
