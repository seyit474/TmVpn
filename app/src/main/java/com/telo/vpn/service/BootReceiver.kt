package com.telo.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.telo.vpn.api.SubscriptionApi
import com.telo.vpn.data.VpnRepository
import com.telo.vpn.dataStore
import com.telo.vpn.subscription.ConfigParser
import com.telo.vpn.util.HwidManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.dataStore.data.first()
            val autoConnect = prefs[booleanPreferencesKey("auto_connect")] ?: false
            if (!autoConnect) return@launch

            runCatching {
                val api = SubscriptionApi()
                val result = api.fetchSubscription(VpnRepository.SUB_URL).getOrThrow()
                val hwidUuid = HwidManager.getHwidAsUuid(context)
                val servers = ConfigParser.parseSubscriptionWithHwid(result.rawLinks, hwidUuid)
                val first = servers.firstOrNull() ?: return@runCatching
                val config = XrayConfigBuilder.build(first)
                val killSwitch = prefs[booleanPreferencesKey("kill_switch")] ?: false
                XrayVpnService.start(context, config, first.remark, killSwitch)
            }
        }
    }
}
