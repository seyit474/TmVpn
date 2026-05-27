package com.telo.vpn.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telo.vpn.api.SubscriptionUserInfo
import com.telo.vpn.data.MarzbanRepository
import com.telo.vpn.model.ConnectionState
import com.telo.vpn.model.ServerConfig
import com.telo.vpn.model.TrafficStats
import com.telo.vpn.service.XrayVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MarzbanRepository(app)

    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _connState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connState: StateFlow<ConnectionState> = _connState.asStateFlow()

    private val _userInfo = MutableStateFlow(SubscriptionUserInfo())
    val userInfo: StateFlow<SubscriptionUserInfo> = _userInfo.asStateFlow()

    private val _keyError = MutableStateFlow<String?>(null)
    val keyError: StateFlow<String?> = _keyError.asStateFlow()

    private val _isKeyLoading = MutableStateFlow(false)
    val isKeyLoading: StateFlow<Boolean> = _isKeyLoading.asStateFlow()

    val trafficStats: StateFlow<TrafficStats> = XrayVpnService.trafficStats
    val isVpnConnected: StateFlow<Boolean> = XrayVpnService.isConnected

    private var _killSwitch = false
    val killSwitch get() = _killSwitch

    sealed interface Screen {
        data object Loading  : Screen
        data object KeyEntry : Screen
        data object Main     : Screen
    }

    init {
        viewModelScope.launch {
            _killSwitch = repo.prefs.killSwitch.first()
            if (repo.hasKey()) {
                _screen.value = Screen.Main
                loadServers()
            } else {
                _screen.value = Screen.KeyEntry
            }
        }
        viewModelScope.launch {
            repo.prefs.killSwitch.collect { _killSwitch = it }
        }
    }

    fun submitKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch {
            _isKeyLoading.value = true
            _keyError.value = null
            repo.connectWithKey(key)
                .onSuccess { (info, servers) ->
                    _userInfo.value = info
                    val best = servers.firstOrNull { it.isReachable }
                    _connState.value = if (best != null)
                        ConnectionState.Ready(servers, best.config)
                    else
                        ConnectionState.Error("Erişilebilir sunucu bulunamadı")
                    _screen.value = Screen.Main
                }
                .onFailure { _keyError.value = it.message ?: "Geçersiz anahtar" }
            _isKeyLoading.value = false
        }
    }

    fun loadServers() {
        viewModelScope.launch {
            _connState.value = ConnectionState.Loading
            repo.refresh()
                .onSuccess { (info, servers) ->
                    _userInfo.value = info
                    val best = servers.firstOrNull { it.isReachable }
                    _connState.value = if (best != null)
                        ConnectionState.Ready(servers, best.config)
                    else
                        ConnectionState.Error("Erişilebilir sunucu bulunamadı")
                }
                .onFailure {
                    _connState.value = ConnectionState.Error(it.message ?: "Yenileme başarısız")
                }
        }
    }

    fun selectServer(cfg: ServerConfig) {
        val cur = _connState.value as? ConnectionState.Ready ?: return
        _connState.value = cur.copy(selected = cfg)
    }

    fun onConnectClicked() {
        if (_connState.value is ConnectionState.Ready) _connState.value = ConnectionState.Connecting
    }

    fun markConnected(cfg: ServerConfig) { _connState.value = ConnectionState.Connected(cfg) }

    fun onDisconnect() {
        _connState.value = ConnectionState.Idle
        loadServers()
    }

    fun logout() {
        viewModelScope.launch {
            repo.clearKey()
            _screen.value = Screen.KeyEntry
            _connState.value = ConnectionState.Idle
            _userInfo.value = SubscriptionUserInfo()
            _keyError.value = null
        }
    }

    fun getSelectedConfig(): ServerConfig? =
        (_connState.value as? ConnectionState.Ready)?.selected

    fun setKillSwitch(enabled: Boolean) {
        viewModelScope.launch { repo.prefs.setKillSwitch(enabled) }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch { repo.prefs.setAutoConnect(enabled) }
    }
}
