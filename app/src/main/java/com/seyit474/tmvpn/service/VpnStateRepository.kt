package com.seyit474.tmvpn.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tünelin anlık durumu. Servis yazar, UI okur. */
sealed interface VpnState {
    data object Idle : VpnState
    data class Connecting(val serverRemark: String) : VpnState
    data class Connected(val serverRemark: String, val sinceElapsedMs: Long) : VpnState
    data class Error(val message: String) : VpnState
}

/**
 * Servis ile UI arasında tek yönlü durum köprüsü.
 * Servis ayrı process'te ÇALIŞMADIĞI için process içi StateFlow yeterli;
 * AIDL/Messenger karmaşasına gerek yok.
 */
object VpnStateRepository {
    private val _state = MutableStateFlow<VpnState>(VpnState.Idle)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    fun update(newState: VpnState) {
        _state.value = newState
    }
}
