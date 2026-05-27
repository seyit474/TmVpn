package com.telo.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.telo.vpn.api.MarzbanApi
import com.telo.vpn.dataStore
import com.telo.vpn.subscription.ConfigParser
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

            // Yeni key-based yaklaşım: sub_key DataStore'dan okunur
            val subKey = prefs[stringPreferencesKey("sub_key")] ?: return@launch
            if (subKey.isEmpty()) return@launch

            runCatching {
                val api = MarzbanApi()
                val result = api.fetchSubscription(subKey).getOrThrow()
                val servers = ConfigParser.parseSubscription(result.rawLinks)
                val first = servers.firstOrNull() ?: return@runCatching
                val config = XrayConfigBuilder.build(first)
                val killSwitch = prefs[booleanPreferencesKey("kill_switch")] ?: false
                XrayVpnService.start(context, config, first.remark, killSwitch)
            }
        }
    }
}
