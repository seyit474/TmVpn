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
import com.seyit474.tmvpn.subscription.SubscriptionFetcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore("vm_core_settings")

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val fetcher = SubscriptionFetcher()
    private val pinger  = ServerPinger()

    private companion object {
        const val TAG = "VpnViewModel"

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
                    _state.value = UiState.Idle
                    refreshAndPickFastest()
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
        val s = settings.value
        val configJson = XrayConfigBuilder.build(
            cur.selected,
            enableFragment   = s.fragmentEnabled,
            fragmentPackets  = s.fragmentPackets,
            fragmentLength   = s.fragmentLength,
            fragmentInterval = s.fragmentInterval,
            muxEnabled       = s.muxEnabled,
            muxXudpQuic      = s.quicMux,
            blockUdp443      = s.blockUdp443,
            proxyGoogle      = s.forceGoogleProxy,
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
            _state.value = UiState.Loading
            val list = fetcher.fetch(subUrl).getOrElse {
                _state.value = UiState.Error("Abonelik alınamadı: ${it.message}")
                return@launch
            }
            if (list.isEmpty()) {
                _state.value = UiState.Error("Sunucu bulunamadı")
                return@launch
            }
            _state.value = UiState.Testing
            val results = pinger.pingAll(list)
            val fastest = results.firstOrNull { it.isReachable }?.config
            if (fastest == null) {
                _state.value = UiState.Error("Hiçbir sunucuya ulaşılamadı")
                return@launch
            }
            _state.value = UiState.Ready(results, fastest)
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
        trafficJob?.cancel()
        trafficJob = null
        _traffic.value = Traffic()
    }
}
