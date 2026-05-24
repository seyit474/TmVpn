package com.seyit474.tmvpn.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object AppSettings {

    // ── Fragment ──────────────────────────────────────────────────────────────
    val FRAGMENT_ENABLED   = booleanPreferencesKey("fragment_enabled")
    val FRAGMENT_PACKETS   = stringPreferencesKey("fragment_packets")
    val FRAGMENT_LENGTH    = stringPreferencesKey("fragment_length")
    val FRAGMENT_INTERVAL  = stringPreferencesKey("fragment_interval")
    val FRAGMENT_MAX_SPLIT = stringPreferencesKey("fragment_max_split")

    // ── Mux ───────────────────────────────────────────────────────────────────
    val MUX_ENABLED          = booleanPreferencesKey("mux_enabled")
    val MUX_CONCURRENCY      = intPreferencesKey("mux_concurrency")
    val MUX_XUDP_CONCURRENCY = intPreferencesKey("mux_xudp_concurrency")
    val MUX_XUDP_QUIC        = stringPreferencesKey("mux_xudp_quic")

    // ── DNS ───────────────────────────────────────────────────────────────────
    val REMOTE_DNS   = stringPreferencesKey("remote_dns")
    val DOMESTIC_DNS = stringPreferencesKey("domestic_dns")
    val VPN_DNS      = stringPreferencesKey("vpn_dns")

    // ── Routing ───────────────────────────────────────────────────────────────
    val BLOCK_UDP_443 = booleanPreferencesKey("block_udp_443")
    val BYPASS_LAN    = booleanPreferencesKey("bypass_lan")
    val PROXY_GOOGLE  = booleanPreferencesKey("proxy_google")

    // ── Sniffing ──────────────────────────────────────────────────────────────
    val SNIFFING_ENABLED = booleanPreferencesKey("sniffing_enabled")
    val ROUTE_ONLY       = booleanPreferencesKey("route_only")

    // ── VPN ───────────────────────────────────────────────────────────────────
    val VPN_MTU               = intPreferencesKey("vpn_mtu")
    val PREFER_IPV6           = booleanPreferencesKey("prefer_ipv6")
    val PREFER_IP_TYPE        = stringPreferencesKey("prefer_ip_type")   // auto | ipv4 | ipv6
    val ALLOW_INSECURE_GLOBAL = booleanPreferencesKey("allow_insecure_global")

    // ── Noise (DPI obfuscation) ───────────────────────────────────────────────
    val NOISES_ENABLED = booleanPreferencesKey("noises_enabled")
    val NOISE_TYPE     = stringPreferencesKey("noise_type")    // rand | str | base64
    val NOISE_PACKET   = stringPreferencesKey("noise_packet")  // size range e.g. "10-50"
    val NOISE_DELAY    = stringPreferencesKey("noise_delay")   // ms range  e.g. "5-20"

    // ── HEV socks5 tunnel ─────────────────────────────────────────────────────
    val HEV_MTU         = intPreferencesKey("hev_mtu")
    val HEV_TCP_TIMEOUT = intPreferencesKey("hev_tcp_timeout")
    val HEV_UDP_TIMEOUT = intPreferencesKey("hev_udp_timeout")

    // ── App ───────────────────────────────────────────────────────────────────
    val LOG_LEVEL   = stringPreferencesKey("log_level")
    val AUTO_SELECT = booleanPreferencesKey("auto_select")
    val PING_TYPE   = stringPreferencesKey("ping_type")   // tcp | proxy

    // ── Defaults ──────────────────────────────────────────────────────────────
    object Defaults {
        const val FRAGMENT_ENABLED   = true
        const val FRAGMENT_PACKETS   = "tlshello"
        const val FRAGMENT_LENGTH    = "1-3"
        const val FRAGMENT_INTERVAL  = "1-1"
        const val FRAGMENT_MAX_SPLIT = "100-200"

        const val MUX_ENABLED          = true
        const val MUX_CONCURRENCY      = 8
        const val MUX_XUDP_CONCURRENCY = 16
        const val MUX_XUDP_QUIC        = "reject"

        const val REMOTE_DNS   = "1.1.1.1"
        const val DOMESTIC_DNS = "223.5.5.5"
        const val VPN_DNS      = "1.1.1.1"

        const val BLOCK_UDP_443 = true
        const val BYPASS_LAN    = true
        const val PROXY_GOOGLE  = true

        const val SNIFFING_ENABLED = true
        const val ROUTE_ONLY       = false

        const val VPN_MTU               = 1500
        const val PREFER_IPV6           = false
        const val PREFER_IP_TYPE        = "auto"
        const val ALLOW_INSECURE_GLOBAL = false

        const val NOISES_ENABLED = false
        const val NOISE_TYPE     = "rand"
        const val NOISE_PACKET   = "10-50"
        const val NOISE_DELAY    = "5-20"

        const val HEV_MTU         = 8500
        const val HEV_TCP_TIMEOUT = 300
        const val HEV_UDP_TIMEOUT = 60

        const val LOG_LEVEL   = "warning"
        const val AUTO_SELECT = true
        const val PING_TYPE   = "tcp"
    }

    // ── API ───────────────────────────────────────────────────────────────────
    fun <T> getSync(context: Context, key: Preferences.Key<T>, default: T): T = runBlocking {
        context.dataStore.data.first()[key] ?: default
    }

    suspend fun getAll(context: Context): Preferences =
        context.dataStore.data.first()

    suspend fun <T> set(context: Context, key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    fun <T> get(context: Context, key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.map { it[key] ?: default }
}
