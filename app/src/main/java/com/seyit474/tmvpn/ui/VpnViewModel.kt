package com.seyit474.tmvpn.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.seyit474.tmvpn.BuildConfig
import com.seyit474.tmvpn.model.ServerConfig
import com.seyit474.tmvpn.ping.ServerPinger
import com.seyit474.tmvpn.service.XrayConfigBuilder
import com.seyit474.tmvpn.service.XrayVpnService
import com.seyit474.tmvpn.subscription.SubscriptionFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VpnViewModel(
    application: Application,
    private val fetcher: SubscriptionFetcher = SubscriptionFetcher(),
    private val pinger: ServerPinger = ServerPinger()
) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "VpnViewModel"
    }

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState              // subscription çekiliyor
        data object Testing : UiState              // sunucular pingleniyor
        data class Ready(
            val results: List<ServerPinger.Result>,
            val selected: ServerConfig
        ) : UiState
        data object Connecting : UiState
        data class Connected(val server: ServerConfig) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Hangi sunucuya bağlanmaya çalıştığımızı servis eventi gelince bilmek için saklanır. */
    private var pendingConnect: ServerConfig? = null

    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = intent.getStringExtra(XrayVpnService.EXTRA_EVENT) ?: return
            val message = intent.getStringExtra(XrayVpnService.EXTRA_MESSAGE) ?: ""
            Log.d(TAG, "VPN event alındı: $event  msg=$message")

            when (event) {
                XrayVpnService.EVENT_CONNECTING -> {
                    _state.value = UiState.Connecting
                }
                XrayVpnService.EVENT_CONNECTED -> {
                    val cfg = pendingConnect
                    if (cfg != null) {
                        _state.value = UiState.Connected(cfg)
                    } else {
                        Log.w(TAG, "EVENT_CONNECTED alındı ama pendingConnect null")
                        _state.value = UiState.Idle
                    }
                    pendingConnect = null
                }
                XrayVpnService.EVENT_DISCONNECTED -> {
                    pendingConnect = null
                    _state.value = UiState.Idle
                    refreshAndPickFastest()
                }
                XrayVpnService.EVENT_ERROR -> {
                    pendingConnect = null
                    _state.value = UiState.Error(message)
                }
            }
        }
    }

    init {
        LocalBroadcastManager.getInstance(application).registerReceiver(
            vpnStatusReceiver,
            IntentFilter(XrayVpnService.ACTION_STATUS)
        )
    }

    override fun onCleared() {
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(vpnStatusReceiver)
        super.onCleared()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun connectVpn(context: Context) {
        val cur = _state.value as? UiState.Ready ?: return
        val cfg = cur.selected
        pendingConnect = cfg
        val configJson = XrayConfigBuilder.build(cfg)
        XrayVpnService.start(context, configJson)
        // Gerçek durum değişikliği servis broadcast'i gelince yapılır
        _state.value = UiState.Connecting
    }

    fun disconnectVpn(context: Context) {
        XrayVpnService.stop(context)
        // Gerçek Idle geçişi EVENT_DISCONNECTED broadcast'i ile yapılır
    }

    fun refreshAndPickFastest() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val list = fetcher.fetch(BuildConfig.SUBSCRIPTION_URL).getOrElse {
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
        val cur = _state.value
        if (cur is UiState.Ready) {
            _state.value = cur.copy(selected = cfg)
        }
    }
}
