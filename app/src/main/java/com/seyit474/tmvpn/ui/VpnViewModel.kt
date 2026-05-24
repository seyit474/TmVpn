package com.seyit474.tmvpn.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.Process
import android.util.Log
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.seyit474.tmvpn.BuildConfig
import com.seyit474.tmvpn.model.AppSettings
import com.seyit474.tmvpn.model.ServerConfig
import com.seyit474.tmvpn.ping.ServerPinger
import com.seyit474.tmvpn.service.XrayConfigBuilder
import com.seyit474.tmvpn.service.XrayVpnService
import com.seyit474.tmvpn.subscription.ConfigParser
import com.seyit474.tmvpn.subscription.SubscriptionFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore("vm_core_settings")

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val fetcher = SubscriptionFetcher()
    private val pinger  = ServerPinger()

    private val cache by lazy {
        getApplication<Application>().getSharedPreferences("vpn_sub_cache", Context.MODE_PRIVATE)
    }

    private companion object {
        const val TAG = "VpnViewModel"
        const val KEY_SUB_BODY = "sub_body"

        val KEY_FRAGMENT_ENABLED  = booleanPreferencesKey("fragment_enabled")
        val KEY_FRAGMENT_PACKETS  = stringPreferencesKey("fragment_packets")
        val KEY_FRAGMENT_LENGTH   = stringPreferencesKey("fragment_length")
        val KEY_FRAGMENT_INTERVAL = stringPreferencesKey("fragment_interval")
        val KEY_MUX_ENABLED       = booleanPreferencesKey("mux_enabled")
        val KEY_QUIC_MUX          = stringPreferencesKey("quic_mux")
        val KEY_BLOCK_UDP443      = booleanPreferencesKey("block_udp443")
        val KEY_FORCE_GOOGLE      = booleanPreferencesKey("force_google_proxy")
    }

    // ─── VPN state ───────────────────────────────────────────────────────────

    sealed interface UiState {
        data object Idle       : UiState
        data object Loading    : UiState
        data object Testing    : UiState
        data class Ready(
            val results:  List<ServerPinger.Result>,
            val selected: ServerConfig,
        ) : UiState
        data object Connecting : UiState
        data class Connected(val server: ServerConfig) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pendingConnect: ServerConfig? = null
    private var lastReady: UiState.Ready? = null  // keeps last ping results in memory

    // ─── Traffic stats ───────────────────────────────────────────────────────

    data class Traffic(
        val elapsedSeconds:    Long = 0,
        val downloadBytesPerSec: Long = 0,
        val uploadBytesPerSec:   Long = 0,
        val totalDownloadBytes:  Long = 0,
        val totalUploadBytes:    Long = 0,
    )

    private val _traffic = MutableStateFlow(Traffic())
    val traffic: StateFlow<Traffic> = _traffic.asStateFlow()
    private var trafficJob: Job? = null

    // ─── Settings ────────────────────────────────────────────────────────────

    val settings: StateFlow<AppSettings> = application.settingsDataStore.data
        .map { p ->
            AppSettings(
                fragmentEnabled  = p[KEY_FRAGMENT_ENABLED]  ?: false,
                fragmentPackets  = p[KEY_FRAGMENT_PACKETS]  ?: "tlshello",
                fragmentLength   = p[KEY_FRAGMENT_LENGTH]   ?: "1-3",
                fragmentInterval = p[KEY_FRAGMENT_INTERVAL] ?: "1-1",
                muxEnabled       = p[KEY_MUX_ENABLED]       ?: true,
                quicMux          = p[KEY_QUIC_MUX]          ?: "reject",
                blockUdp443      = p[KEY_BLOCK_UDP443]      ?: true,
                forceGoogleProxy = p[KEY_FORCE_GOOGLE]      ?: true,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    // ─── Broadcast receiver ──────────────────────────────────────────────────

    private val vpnReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val event   = intent.getStringExtra(XrayVpnService.EXTRA_EVENT)   ?: return
            val message = intent.getStringExtra(XrayVpnService.EXTRA_MESSAGE) ?: ""
            Log.d(TAG, "VPN event: $event  msg=$message")
            when (event) {
                XrayVpnService.EVENT_CONNECTING   -> _state.value = UiState.Connecting
                XrayVpnService.EVENT_CONNECTED    -> {
                    val cfg = pendingConnect
                    if (cfg != null) {
                        _state.value = UiState.Connected(cfg)
                        startTrafficMonitor()
                    } else {
                        _state.value = UiState.Idle
                    }
                    pendingConnect = null
                }
                XrayVpnService.EVENT_DISCONNECTED -> {
                    pendingConnect = null
                    stopTrafficMonitor()
                    // Restore last server list instantly, no re-fetch needed
                    val last = lastReady
                    if (last != null) {
                        _state.value = last
                    } else {
                        _state.value = UiState.Idle
                        loadCachedServers()
                    }
                }
                XrayVpnService.EVENT_ERROR        -> {
                    pendingConnect = null
                    stopTrafficMonitor()
                    _state.value = UiState.Error(message)
                }
            }
        }
    }

    init {
        LocalBroadcastManager.getInstance(application).registerReceiver(
            vpnReceiver,
            IntentFilter(XrayVpnService.ACTION_STATUS),
        )
        loadCachedServers()
    }

    private fun loadCachedServers() {
        val body = cache.getString(KEY_SUB_BODY, null) ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val servers = runCatching { ConfigParser.parseSubscription(body) }.getOrNull()
            if (servers.isNullOrEmpty()) return@launch
            if (_state.value !is UiState.Idle) return@launch
            // Show cached servers immediately, then ping in background
            val initial = servers.map { ServerPinger.Result(it, -1L, ServerPinger.Status.TESTING) }
            _state.value = UiState.Ready(initial, initial.first().config)
            pinger.pingAllStreaming(servers, this) { results ->
                val selected = results.firstOrNull { it.isReachable }?.config ?: results.first().config
                val ready = UiState.Ready(results, selected)
                lastReady = ready
                _state.value = ready
            }.join()
        }
    }

    override fun onCleared() {
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(vpnReceiver)
        stopTrafficMonitor()
        super.onCleared()
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    fun connectVpn(context: Context) {
        val cur = _state.value as? UiState.Ready ?: return
        pendingConnect = cur.selected
        val as_ = com.seyit474.tmvpn.settings.AppSettings
        val configJson = XrayConfigBuilder.build(
            cur.selected,
            hwidUuid         = com.seyit474.tmvpn.hwid.HwidManager.getHwid(context),
            enableFragment   = as_.getSync(context, as_.FRAGMENT_ENABLED,   as_.Defaults.FRAGMENT_ENABLED),
            fragmentPackets  = as_.getSync(context, as_.FRAGMENT_PACKETS,   as_.Defaults.FRAGMENT_PACKETS),
            fragmentLength   = as_.getSync(context, as_.FRAGMENT_LENGTH,    as_.Defaults.FRAGMENT_LENGTH),
            fragmentInterval = as_.getSync(context, as_.FRAGMENT_INTERVAL,  as_.Defaults.FRAGMENT_INTERVAL),
            fragmentMaxSplit = as_.getSync(context, as_.FRAGMENT_MAX_SPLIT, as_.Defaults.FRAGMENT_MAX_SPLIT),
            noisesEnabled    = as_.getSync(context, as_.NOISES_ENABLED,     as_.Defaults.NOISES_ENABLED),
            noiseType        = as_.getSync(context, as_.NOISE_TYPE,         as_.Defaults.NOISE_TYPE),
            noisePacket      = as_.getSync(context, as_.NOISE_PACKET,       as_.Defaults.NOISE_PACKET),
            noiseDelay       = as_.getSync(context, as_.NOISE_DELAY,        as_.Defaults.NOISE_DELAY),
            preferIpType     = as_.getSync(context, as_.PREFER_IP_TYPE,     as_.Defaults.PREFER_IP_TYPE),
            muxEnabled       = as_.getSync(context, as_.MUX_ENABLED,        as_.Defaults.MUX_ENABLED),
            muxConcurrency   = as_.getSync(context, as_.MUX_CONCURRENCY,    as_.Defaults.MUX_CONCURRENCY),
            muxXudpQuic      = as_.getSync(context, as_.MUX_XUDP_QUIC,     as_.Defaults.MUX_XUDP_QUIC),
            blockUdp443      = as_.getSync(context, as_.BLOCK_UDP_443,      as_.Defaults.BLOCK_UDP_443),
            proxyGoogle      = as_.getSync(context, as_.PROXY_GOOGLE,       as_.Defaults.PROXY_GOOGLE),
            bypassLan        = as_.getSync(context, as_.BYPASS_LAN,         as_.Defaults.BYPASS_LAN),
            sniffingEnabled  = as_.getSync(context, as_.SNIFFING_ENABLED,   as_.Defaults.SNIFFING_ENABLED),
            logLevel         = as_.getSync(context, as_.LOG_LEVEL,          as_.Defaults.LOG_LEVEL),
            remoteDns        = as_.getSync(context, as_.REMOTE_DNS,         as_.Defaults.REMOTE_DNS),
        )
        XrayVpnService.start(context, configJson)
        _state.value = UiState.Connecting
    }

    fun disconnectVpn(context: Context) {
        XrayVpnService.stop(context)
    }

    fun refreshAndPickFastest() {
        viewModelScope.launch {
            val subUrl = BuildConfig.SUBSCRIPTION_URL
            if (subUrl.isBlank()) {
                _state.value = UiState.Error("Abonelik URL'si ayarlanmamış")
                return@launch
            }
            // Only show Loading if no cached servers are already displayed
            if (_state.value !is UiState.Ready) _state.value = UiState.Loading
            val (rawBody, list) = fetcher.fetchWithBody(subUrl).getOrElse {
                if (_state.value !is UiState.Ready)
                    _state.value = UiState.Error("Abonelik alınamadı: ${it.message}")
                return@launch
            }
            withContext(Dispatchers.IO) {
                cache.edit().putString(KEY_SUB_BODY, rawBody).apply()
            }
            if (list.isEmpty()) {
                _state.value = UiState.Error("Sunucu bulunamadı")
                return@launch
            }
            // Show all servers immediately as TESTING — list never disappears
            val testing = list.map { ServerPinger.Result(it, -1L, ServerPinger.Status.TESTING) }
            val prevSelected = (_state.value as? UiState.Ready)?.selected ?: testing.first().config
            _state.value = UiState.Ready(testing, prevSelected)

            // Ping all servers with live streaming updates
            pinger.pingAllStreaming(list, this) { results ->
                val selected = results.firstOrNull { it.isReachable }?.config ?: prevSelected
                val ready = UiState.Ready(results, selected)
                lastReady = ready
                _state.value = ready
            }.join()
        }
    }

    fun selectServer(cfg: ServerConfig) {
        val cur = _state.value as? UiState.Ready ?: return
        _state.value = cur.copy(selected = cfg)
    }

    fun updateSettings(block: AppSettings.() -> AppSettings) {
        viewModelScope.launch {
            val next = settings.value.block()
            getApplication<Application>().settingsDataStore.edit { p ->
                p[KEY_FRAGMENT_ENABLED]  = next.fragmentEnabled
                p[KEY_FRAGMENT_PACKETS]  = next.fragmentPackets
                p[KEY_FRAGMENT_LENGTH]   = next.fragmentLength
                p[KEY_FRAGMENT_INTERVAL] = next.fragmentInterval
                p[KEY_MUX_ENABLED]       = next.muxEnabled
                p[KEY_QUIC_MUX]          = next.quicMux
                p[KEY_BLOCK_UDP443]      = next.blockUdp443
                p[KEY_FORCE_GOOGLE]      = next.forceGoogleProxy
            }
        }
    }

    // ─── Traffic monitor ─────────────────────────────────────────────────────

    private fun startTrafficMonitor() {
        com.seyit474.tmvpn.util.TrafficCounter.start()
        stopTrafficMonitor()
        _traffic.value = Traffic()
        trafficJob = viewModelScope.launch {
            val uid      = Process.myUid()
            val startRx  = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
            val startTx  = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
            var prevRx   = startRx
            var prevTx   = startTx
            var seconds  = 0L
            while (isActive) {
                delay(1_000)
                seconds++
                val curRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
                val curTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
                _traffic.value = Traffic(
                    elapsedSeconds       = seconds,
                    downloadBytesPerSec  = (curRx - prevRx).coerceAtLeast(0),
                    uploadBytesPerSec    = (curTx - prevTx).coerceAtLeast(0),
                    totalDownloadBytes   = (curRx - startRx).coerceAtLeast(0),
                    totalUploadBytes     = (curTx - startTx).coerceAtLeast(0),
                )
                prevRx = curRx
                prevTx = curTx
            }
        }
    }

    private fun stopTrafficMonitor() {
        com.seyit474.tmvpn.util.TrafficCounter.stop()
        trafficJob?.cancel()
        trafficJob = null
        _traffic.value = Traffic()
    }
}
