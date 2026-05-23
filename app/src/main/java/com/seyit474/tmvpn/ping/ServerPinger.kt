package com.seyit474.tmvpn.ping

import com.seyit474.tmvpn.model.ServerConfig
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class ServerPinger(private val timeoutMs: Int = 4000) {

    enum class Status { TESTING, OK, TLS_BLOCKED, UNREACHABLE }

    data class Result(
        val config: ServerConfig,
        val latencyMs: Long = -1L,
        val status: Status = Status.TESTING,
    ) {
        val isReachable: Boolean get() = status == Status.OK || status == Status.TLS_BLOCKED
        val tlsOk: Boolean get() = status == Status.OK
    }

    // Live streaming ping — calls onUpdate each time a result arrives
    fun pingAllStreaming(
        configs: List<ServerConfig>,
        scope: CoroutineScope,
        onUpdate: (List<Result>) -> Unit,
    ): Job = scope.launch {
        val results = configs.map { Result(it, status = Status.TESTING) }.toMutableList()
        onUpdate(results.toList())

        configs.mapIndexed { i, cfg ->
            async(Dispatchers.IO) {
                val r = pingOne(cfg)
                synchronized(results) { results[i] = r }
                onUpdate(results.toList())
            }
        }.awaitAll()
    }

    // Blocking all-at-once (kept for backward compat)
    suspend fun pingAll(configs: List<ServerConfig>): List<Result> = coroutineScope {
        configs.map { cfg -> async(Dispatchers.IO) { pingOne(cfg) } }
            .awaitAll()
            .sortedWith(
                compareByDescending<Result> { it.isReachable }
                    .thenBy { if (it.latencyMs < 0) Long.MAX_VALUE else it.latencyMs }
            )
    }

    suspend fun pickFastest(configs: List<ServerConfig>): ServerConfig? =
        pingAll(configs).firstOrNull { it.isReachable }?.config

    // ── Core ping logic ──────────────────────────────────────────────────────

    private suspend fun pingOne(cfg: ServerConfig): Result = withContext(Dispatchers.IO) {
        // Pre-resolve hostname once so TCP samples don't include DNS
        val addr = resolveHost(cfg.address)
            ?: return@withContext Result(cfg, -1L, Status.UNREACHABLE)

        val latency = medianTcpMs(addr, cfg.port)
            ?: return@withContext Result(cfg, -1L, Status.UNREACHABLE)

        val needsTls = cfg.security in listOf("tls", "reality") &&
                cfg.network in listOf("tcp", "grpc", "h2", "http", "httpupgrade")

        val status = if (needsTls) {
            if (tlsHandshake(addr, cfg.port, cfg.sni ?: cfg.address)) Status.OK
            else Status.TLS_BLOCKED
        } else {
            Status.OK
        }

        Result(cfg, latency, status)
    }

    // 3 TCP SYN→ACK attempts; returns median in ms, null if all failed
    private suspend fun medianTcpMs(addr: InetAddress, port: Int): Long? {
        val samples = mutableListOf<Long>()
        repeat(3) {
            val t = tcpHandshakeMs(addr, port)
            if (t != null) samples.add(t)
            if (samples.size == 1 && it == 0) return@repeat // first success: continue for more
        }
        if (samples.isEmpty()) return null
        samples.sort()
        return samples[samples.size / 2]
    }

    private suspend fun tcpHandshakeMs(addr: InetAddress, port: Int): Long? =
        withTimeoutOrNull(timeoutMs.toLong()) {
            runCatching {
                Socket().use { socket ->
                    val start = System.nanoTime()
                    socket.connect(InetSocketAddress(addr, port), timeoutMs)
                    (System.nanoTime() - start) / 1_000_000L
                }
            }.getOrNull()
        }

    private suspend fun resolveHost(host: String): InetAddress? =
        withTimeoutOrNull(timeoutMs.toLong()) {
            runCatching { InetAddress.getByName(host) }.getOrNull()
        }

    private suspend fun tlsHandshake(addr: InetAddress, port: Int, sni: String): Boolean =
        withTimeoutOrNull(timeoutMs.toLong()) {
            runCatching {
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                (factory.createSocket(addr, port) as SSLSocket).use { ssl ->
                    ssl.soTimeout = timeoutMs
                    ssl.sslParameters = ssl.sslParameters.also { it.serverNames = listOf(
                        javax.net.ssl.SNIHostName(sni)
                    )}
                    ssl.startHandshake()
                    true
                }
            }.getOrDefault(false)
        } ?: false
}
