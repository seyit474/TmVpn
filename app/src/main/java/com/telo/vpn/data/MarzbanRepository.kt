package com.telo.vpn.data

import android.content.Context
import com.telo.vpn.api.SubscriptionApi
import com.telo.vpn.api.SubscriptionUserInfo
import com.telo.vpn.model.PingedServer
import com.telo.vpn.ping.ServerPinger
import com.telo.vpn.subscription.ConfigParser
import com.telo.vpn.util.HwidManager

class VpnRepository(private val context: Context) {

    val prefs = AppPreferences(context)
    private val api = SubscriptionApi()
    private val pinger = ServerPinger()

    companion object {
        const val SUB_URL = "http://194.36.89.199:4541/sub/VGVsb3ZwbiwxNzc5OTYyNjEyNXZ0xbpl8V"
    }

    fun getHwid(): String = HwidManager.getHwid(context)

    suspend fun loadSubscription(): Result<Pair<SubscriptionUserInfo, List<PingedServer>>> =
        runCatching {
            val result = api.fetchSubscription(SUB_URL).getOrThrow()
            val hwidUuid = HwidManager.getHwidAsUuid(context)
            val configs = ConfigParser.parseSubscriptionWithHwid(result.rawLinks, hwidUuid)
            if (configs.isEmpty()) error("Abonementde server tapylmady")
            pinger.pingAll(configs).let { result.userInfo to it }
        }

    suspend fun refresh(): Result<Pair<SubscriptionUserInfo, List<PingedServer>>> =
        loadSubscription()
}
