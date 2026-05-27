package com.telo.vpn.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telo.vpn.data.MarzbanRepository
import com.telo.vpn.model.ConnectionState
import com.telo.vpn.model.PingedServer
import com.telo.vpn.model.ServerConfig
import com.telo.vpn.model.TrafficStats
import com.telo.vpn.service.XrayVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MarzbanRepository(app)

    private val _connState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connState: StateFlow<ConnectionState> = _connState.asStateFlow()

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    val trafficStats: StateFlow<TrafficStats> = XrayVpnService.trafficStats
    val isVpnConnected: StateFlow<Boolean> = XrayVpnService.isConnected

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private var _killSwitch = false
    val killSwitch get() = _killSwitch

    sealed interface LoginState {
        data object Idle : LoginState
        data object Loading : LoginState
        data class Error(val message: String) : LoginState
        data object Success : LoginState
    }

    init {
        viewModelScope.launch {
            _isLoggedIn.value = repo.isLoggedIn()
            if (_isLoggedIn.value) loadServers()
        }
        viewModelScope.launch {
            repo.getPrefs().killSwitch.collect { _killSwitch = it }
        }
    }

    fun login(panelUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            repo.login(panelUrl, username, password)
                .onSuccess { servers ->
                    _isLoggedIn.value = true
                    _loginState.value = LoginState.Success
                    val best = servers.firstOrNull { it.isReachable }
                    if (best != null) {
                        _connState.value = ConnectionState.Ready(servers, best.config)
                    } else {
                        _connState.value = ConnectionState.Error("Erişilebilir sunucu bulunamadı")
                    }
                }
                .onFailure { e ->
                    _loginState.value = LoginState.Error(e.message ?: "Bilinmeyen hata")
                }
        }
    }

    fun loadServers() {
        viewModelScope.launch {
            _connState.value = ConnectionState.Loading
            repo.refreshServers()
                .onSuccess { servers ->
                    val best = servers.firstOrNull { it.isReachable }
                    if (best != null) {
                        _connState.value = ConnectionState.Ready(servers, best.config)
                    } else {
                        _connState.value = ConnectionState.Error("Hiçbir sunucuya ulaşılamadı")
                    }
                }
                .onFailure { e ->
                    _connState.value = ConnectionState.Error(e.message ?: "Sunucu yüklenemedi")
                }
        }
    }

    fun selectServer(cfg: ServerConfig) {
        val cur = _connState.value as? ConnectionState.Ready ?: return
        _connState.value = cur.copy(selected = cfg)
    }

    fun onConnectClicked() {
        val cur = _connState.value as? ConnectionState.Ready ?: return
        _connState.value = ConnectionState.Connecting
        // Gerçek bağlantı MainActivity'de VPN izni alındıktan sonra başlatılır
    }

    fun markConnected(cfg: ServerConfig) {
        _connState.value = ConnectionState.Connected(cfg)
    }

    fun onDisconnect() {
        _connState.value = ConnectionState.Idle
        viewModelScope.launch { loadServers() }
    }

    fun logout() {
        viewModelScope.launch {
            repo.clearSession()
            _isLoggedIn.value = false
            _connState.value = ConnectionState.Idle
            _loginState.value = LoginState.Idle
        }
    }

    fun getSelectedConfig(): ServerConfig? =
        (_connState.value as? ConnectionState.Ready)?.selected

    fun setKillSwitch(enabled: Boolean) {
        viewModelScope.launch {
            repo.getPrefs().setKillSwitch(enabled)
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            repo.getPrefs().setAutoConnect(enabled)
        }
    }
}
