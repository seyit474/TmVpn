package com.telo.vpn.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.telo.vpn.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_PANEL_URL = stringPreferencesKey("panel_url")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_SUB_URL = stringPreferencesKey("sub_url")
        private val KEY_KILL_SWITCH = booleanPreferencesKey("kill_switch")
        private val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
    }

    val panelUrl: Flow<String> = context.dataStore.data.map { it[KEY_PANEL_URL] ?: "" }
    val username: Flow<String> = context.dataStore.data.map { it[KEY_USERNAME] ?: "" }
    val token: Flow<String> = context.dataStore.data.map { it[KEY_TOKEN] ?: "" }
    val subUrl: Flow<String> = context.dataStore.data.map { it[KEY_SUB_URL] ?: "" }
    val killSwitch: Flow<Boolean> = context.dataStore.data.map { it[KEY_KILL_SWITCH] ?: false }
    val autoConnect: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_CONNECT] ?: false }

    suspend fun saveSession(panelUrl: String, username: String, token: String, subUrl: String) {
        context.dataStore.edit {
            it[KEY_PANEL_URL] = panelUrl
            it[KEY_USERNAME] = username
            it[KEY_TOKEN] = token
            it[KEY_SUB_URL] = subUrl
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(KEY_PANEL_URL)
            it.remove(KEY_USERNAME)
            it.remove(KEY_TOKEN)
            it.remove(KEY_SUB_URL)
        }
    }

    suspend fun setKillSwitch(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KILL_SWITCH] = enabled }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_CONNECT] = enabled }
    }
}
