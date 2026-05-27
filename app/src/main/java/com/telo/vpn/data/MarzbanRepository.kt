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

    /**
     * Verilen sub URL (anahtar) ile subscription çeker, parse eder, ping atar.
     * Başarılı olursa anahtarı DataStore'a kaydeder.
     */
    suspend fun connectWithKey(subKey: String): Result<Pair<SubscriptionUserInfo, List<PingedServer>>> =
        runCatching {
            val normalizedKey = normalizeKey(subKey)
            val result = api.fetchSubscription(normalizedKey).getOrThrow()
            val configs = ConfigParser.parseSubscription(result.rawLinks)
            if (configs.isEmpty()) error("Abonelikte sunucu bulunamadı")
            prefs.saveKey(normalizedKey)
            val pinged = pinger.pingAll(configs)
            result.userInfo to pinged
        }

    /** Kayıtlı anahtar ile yenile */
    suspend fun refresh(): Result<Pair<SubscriptionUserInfo, List<PingedServer>>> = runCatching {
        val key = prefs.subKey.first().ifEmpty { error("Anahtar girilmemiş") }
        val result = api.fetchSubscription(key).getOrThrow()
        val configs = ConfigParser.parseSubscription(result.rawLinks)
        if (configs.isEmpty()) error("Abonelikte sunucu bulunamadı")
        pinger.pingAll(configs).let { result.userInfo to it }
    }

    suspend fun hasKey(): Boolean = prefs.subKey.first().isNotEmpty()
    suspend fun clearKey() = prefs.clearKey()

    /** http:// veya https:// ile başlamıyorsa https:// ekle */
    private fun normalizeKey(key: String): String {
        val trimmed = key.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }
}
