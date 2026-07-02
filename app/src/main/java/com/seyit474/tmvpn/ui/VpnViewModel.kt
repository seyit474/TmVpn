package com.seyit474.tmvpn.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seyit474.tmvpn.BuildConfig
import com.seyit474.tmvpn.data.SettingsStore
import com.seyit474.tmvpn.model.ServerConfig
import com.seyit474.tmvpn.ping.ServerPinger
import com.seyit474.tmvpn.service.VpnState
import com.seyit474.tmvpn.service.VpnStateRepository
import com.seyit474.tmvpn.service.XrayConfigBuilder
import com.seyit474.tmvpn.subscription.SubscriptionFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class VpnViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val fetcher = SubscriptionFetcher()
    private val pinger = ServerPinger()

    enum class RefreshPhase { IDLE, FETCHING, TESTING }

    /** UI'da metne çevrilen, Context gerektirmeyen hata tipleri. */
    sealed interface UiError {
        data object MissingUrl : UiError
        data object InvalidUrl : UiError
        data object NoServers : UiError
        data object NoReachableServer : UiError
        data object Network : UiError
        data object Timeout : UiError
        data class Http(val code: Int) : UiError
        data class Unknown(val detail: String) : UiError
    }

    data class UiState(
        val servers: List<ServerPinger.Result> = emptyList(),
        val selectedId: String? = null,
        val refreshPhase: RefreshPhase = RefreshPhase.IDLE,
        val vpnState: VpnState = VpnState.Idle,
        val error: UiError? = null,
        /** Kullanıcının kaydettiği abonelik adresi (dialog ön doldurması için) */
        val subscriptionUrl: String? = null
    ) {
        val selectedServer: ServerConfig?
            get() = servers.firstOrNull { it.config.id == selectedId }?.config
        val isRefreshing: Boolean
            get() = refreshPhase != RefreshPhase.IDLE
        val isConnectedOrConnecting: Boolean
            get() = vpnState is VpnState.Connected || vpnState is VpnState.Connecting
    }

    private val local = MutableStateFlow(UiState())

    val state: StateFlow<UiState> =
        combine(local, VpnStateRepository.state, settings.subscriptionUrl) { ui, vpn, url ->
            ui.copy(vpnState = vpn, subscriptionUrl = url)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        refresh()
    }

    /** Aboneliği çeker, tüm sunucuları pingler, en hızlısını seçili yapar. */
    fun refresh() {
        if (local.value.refreshPhase != RefreshPhase.IDLE) return
        viewModelScope.launch {
            local.update { it.copy(refreshPhase = RefreshPhase.FETCHING, error = null) }

            // Kullanıcının kaydettiği adres öncelikli; yoksa derlemede gömülen
            val url = settings.subscriptionUrl.first()?.takeIf { it.isNotBlank() }
                ?: BuildConfig.SUBSCRIPTION_URL
            if (url.isBlank()) {
                fail(UiError.MissingUrl)
                return@launch
            }

            val servers = fetcher.fetch(url).getOrElse {
                fail(it.toUiError())
                return@launch
            }
            if (servers.isEmpty()) {
                fail(UiError.NoServers)
                return@launch
            }

            local.update { it.copy(refreshPhase = RefreshPhase.TESTING) }
            val results = pinger.pingAll(servers)
            val fastest = results.firstOrNull { it.isReachable }

            local.update { cur ->
                // Kullanıcının önceki seçimi hâlâ listedeyse koru, yoksa en hızlıyı seç
                val keepSelection = cur.selectedId
                    ?.takeIf { id -> results.any { it.config.id == id && it.isReachable } }
                cur.copy(
                    servers = results,
                    selectedId = keepSelection ?: fastest?.config?.id,
                    refreshPhase = RefreshPhase.IDLE,
                    error = if (fastest == null) UiError.NoReachableServer else null
                )
            }
        }
    }

    /** Kullanıcının girdiği/yapıştırdığı abonelik adresini kaydeder ve listeyi yeniler. */
    fun saveSubscriptionUrl(raw: String) {
        val url = raw.trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            local.update { it.copy(error = UiError.InvalidUrl) }
            return
        }
        viewModelScope.launch {
            settings.setSubscriptionUrl(url)
            refresh()
        }
    }

    fun selectServer(cfg: ServerConfig) {
        local.update { it.copy(selectedId = cfg.id) }
    }

    fun consumeError() {
        local.update { it.copy(error = null) }
    }

    /** Seçili sunucu için Xray config JSON'u üretir; Activity servisi başlatır. */
    fun buildConnectionRequest(): ConnectionRequest? {
        val server = state.value.selectedServer ?: return null
        return ConnectionRequest(
            configJson = XrayConfigBuilder.build(server),
            serverRemark = server.remark
        )
    }

    data class ConnectionRequest(val configJson: String, val serverRemark: String)

    private fun fail(error: UiError) {
        local.update { it.copy(refreshPhase = RefreshPhase.IDLE, error = error) }
    }

    private fun Throwable.toUiError(): UiError = when (this) {
        is UnknownHostException -> UiError.Network
        is SocketTimeoutException -> UiError.Timeout
        is SubscriptionFetcher.HttpException -> UiError.Http(code)
        is java.io.IOException -> UiError.Network
        else -> UiError.Unknown(message ?: javaClass.simpleName)
    }
}
