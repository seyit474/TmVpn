package com.telo.vpn.model

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Loading : ConnectionState
    data object Testing : ConnectionState
    data class Ready(val servers: List<PingedServer>, val selected: ServerConfig) : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val server: ServerConfig, val startTime: Long = System.currentTimeMillis()) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class PingedServer(
    val config: ServerConfig,
    val latencyMs: Long,
    val isReachable: Boolean
)

data class TrafficStats(
    val uploadBps: Long = 0L,
    val downloadBps: Long = 0L,
    val totalUpload: Long = 0L,
    val totalDownload: Long = 0L
)
