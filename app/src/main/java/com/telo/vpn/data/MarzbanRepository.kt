package com.telo.vpn.data

import android.content.Context
import com.telo.vpn.api.MarzbanApi
import com.telo.vpn.api.SubscriptionUserInfo
import com.telo.vpn.model.PingedServer
import com.telo.vpn.ping.ServerPinger
import com.telo.vpn.subscription.ConfigParser
import com.telo.vpn.util.HwidManager

class MarzbanRepository(private val context: Context) {

    val prefs = AppPreferences(context)
    private val api = MarzbanApi()
    private val pinger = ServerPinger()

    companion object {
        const val BASE_SUB_URL = "http://194.36.89.199:4541/sub/"
    }

    fun getHwid(): String = HwidManager.getHwid(context)

    suspend fun loadSubscription(): Result<Pair<SubscriptionUserInfo, List<PingedServer>>> =
        runCatching {
            val url = BASE_SUB_URL + getHwid()
            val result = api.fetchSubscription(url).getOrThrow()
            val configs = ConfigParser.parseSubscription(result.rawLinks)
            if (configs.isEmpty()) error("Abonementde server tapylmady")
            pinger.pingAll(configs).let { result.userInfo to it }
        }

    suspend fun refresh(): Result<Pair<SubscriptionUserInfo, List<PingedServer>>> =
        loadSubscription()
}
