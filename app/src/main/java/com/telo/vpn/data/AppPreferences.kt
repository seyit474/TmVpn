package com.telo.vpn.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.telo.vpn.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_KILL_SWITCH  = booleanPreferencesKey("kill_switch")
        val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
    }

    val killSwitch:  Flow<Boolean> = context.dataStore.data.map { it[KEY_KILL_SWITCH]  ?: false }
    val autoConnect: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_CONNECT] ?: false }

    suspend fun setKillSwitch(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KILL_SWITCH] = enabled }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_CONNECT] = enabled }
    }
}
