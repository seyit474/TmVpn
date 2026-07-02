package com.seyit474.tmvpn.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Kullanıcı ayarları. Kullanıcının girdiği abonelik adresi burada saklanır ve
 * derleme sırasında gömülen BuildConfig.SUBSCRIPTION_URL'e göre önceliklidir.
 */
class SettingsStore(private val context: Context) {

    private val keySubscriptionUrl = stringPreferencesKey("subscription_url")

    val subscriptionUrl: Flow<String?> =
        context.dataStore.data.map { it[keySubscriptionUrl] }

    suspend fun setSubscriptionUrl(url: String) {
        context.dataStore.edit { it[keySubscriptionUrl] = url }
    }
}
