package com.seyit474.tmvpn.ping

import com.seyit474.tmvpn.model.ServerConfig
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit
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
                val sorted: List<Result>
                synchronized(results) {
                    results[i] = r
                    sorted = results.sortedWith(
                        compareByDescending<Result> { it.isReachable }
                            .thenBy { if (it.latencyMs < 0) Long.MAX_VALUE else it.latencyMs }
                    )
                }
                onUpdate(sorted)
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
        val addr = resolveHost(cfg.address)
            ?: return@withContext Result(cfg, -1L, Status.UNREACHABLE)

        return@withContext when (cfg.security) {
            "tls" -> {
                // TCP + TLS handshake — most realistic, matches what users actually experience
                val sni = cfg.sni ?: cfg.address
                val latency = tlsHandshakeMs(addr, cfg.port, sni)
                if (latency != null) Result(cfg, latency, Status.OK)
                else Result(cfg, -1L, Status.TLS_BLOCKED)
            }
            "reality" -> {
                // Reality rejects standard TLS — fall back to TCP only
                val latency = medianTcpMs(addr, cfg.port)
                    ?: return@withContext Result(cfg, -1L, Status.UNREACHABLE)
                Result(cfg, latency, Status.OK)
            }
            else -> {
                val latency = medianTcpMs(addr, cfg.port)
                    ?: return@withContext Result(cfg, -1L, Status.UNREACHABLE)
                Result(cfg, latency, Status.OK)
            }
        }
    }

    // TCP + TLS handshake time — measures what users actually feel (DNS excluded)
    private suspend fun tlsHandshakeMs(addr: InetAddress, port: Int, sni: String): Long? =
        withTimeoutOrNull(timeoutMs.toLong()) {
            runCatching {
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val start = System.nanoTime()
                (factory.createSocket(addr, port) as SSLSocket).use { ssl ->
                    ssl.soTimeout = timeoutMs
                    ssl.sslParameters = ssl.sslParameters.also {
                        it.serverNames = listOf(javax.net.ssl.SNIHostName(sni))
                    }
                    ssl.startHandshake()
                    (System.nanoTime() - start) / 1_000_000L
                }
            }.getOrNull()
        }

    // 3 TCP SYN→ACK attempts; returns median in ms, null if all failed
    private suspend fun medianTcpMs(addr: InetAddress, port: Int): Long? {
        val samples = mutableListOf<Long>()
        repeat(3) {
            val t = tcpHandshakeMs(addr, port)
            if (t != null) samples.add(t)
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

    // Proxy GET ping — measures real latency through the running Xray SOCKS5 tunnel
    // Only works when VPN is connected (port 10808 open). Returns null if not available.
    suspend fun proxyPing(
        testUrl: String = "https://www.gstatic.com/generate_204",
        proxyPort: Int  = 10808,
    ): Long? = withContext(Dispatchers.IO) {
        runCatching {
            withTimeoutOrNull(timeoutMs.toLong()) {
                val client = OkHttpClient.Builder()
                    .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", proxyPort)))
                    .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    .build()
                val start = System.nanoTime()
                val resp = client.newCall(Request.Builder().url(testUrl).head().build()).execute()
                val ms = (System.nanoTime() - start) / 1_000_000L
                resp.close()
                ms
            }
        }.getOrNull()
    }
}
