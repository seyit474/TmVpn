package com.telo.vpn.data

import android.content.Context
import com.telo.vpn.api.MarzbanApi
import com.telo.vpn.model.PingedServer
import com.telo.vpn.model.ServerConfig
import com.telo.vpn.ping.ServerPinger
import com.telo.vpn.subscription.ConfigParser
import kotlinx.coroutines.flow.first

class MarzbanRepository(context: Context) {

    private val prefs = AppPreferences(context)
    private val api = MarzbanApi()
    private val pinger = ServerPinger()

    /** Login → token kaydet → subscription URL al → sunucu listesi döndür */
    suspend fun login(
        panelUrl: String,
        username: String,
        password: String
    ): Result<List<PingedServer>> = runCatching {
        val token = api.login(panelUrl, username, password).getOrThrow().accessToken
        val user = api.getUser(panelUrl, username, token).getOrThrow()
        val subUrl = user.subscriptionUrl.ifEmpty {
            error("Bu kullanıcı için abonelik URL'i bulunamadı")
        }
        prefs.saveSession(panelUrl, username, token, subUrl)
        fetchAndPingServers(subUrl)
    }

    /** Kayıtlı session'dan sunucu listesi güncelle */
    suspend fun refreshServers(): Result<List<PingedServer>> = runCatching {
        val subUrl = prefs.subUrl.first().ifEmpty { error("Oturum açılmamış") }
        fetchAndPingServers(subUrl)
    }

    suspend fun isLoggedIn(): Boolean = prefs.subUrl.first().isNotEmpty()

    suspend fun getPrefs() = prefs

    private suspend fun fetchAndPingServers(subUrl: String): List<PingedServer> {
        val raw = api.fetchSubscription(subUrl).getOrThrow()
        val configs = ConfigParser.parseSubscription(raw)
        if (configs.isEmpty()) error("Abonelikte sunucu bulunamadı")
        return pinger.pingAll(configs)
    }

    suspend fun clearSession() = prefs.clearSession()
}
