package com.seyit474.tmvpn.ping

import com.seyit474.tmvpn.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Sunucuların gerçek bağlantı gecikmesini ölçer.
 *
 * ICMP ping Android'de root gerektirir, o yüzden TCP ping kullanıyoruz:
 * SYN gönder → SYN/ACK gelene kadar geçen süre. Bu, gerçek kullanım
 * gecikmesine en yakın metrik.
 *
 * Sunucular paralel test edilir; eşzamanlılık [maxConcurrency] ile sınırlıdır
 * ki yüzlerce sunuculuk aboneliklerde soket/FD tükenmesin ve ölçümler
 * birbirini yavaşlatmasın.
 */
class ServerPinger(
    private val timeoutMs: Int = 3000,
    private val attempts: Int = 2,
    private val maxConcurrency: Int = 16
) {

    data class Result(
        val config: ServerConfig,
        val latencyMs: Long,        // -1L = ulaşılamadı
        val isReachable: Boolean
    )

    suspend fun pingAll(configs: List<ServerConfig>): List<Result> = coroutineScope {
        val limiter = Semaphore(maxConcurrency)
        configs.map { cfg ->
            async(Dispatchers.IO) {
                limiter.withPermit { pingOne(cfg) }
            }
        }.map { it.await() }
            .sortedWith(
                compareByDescending<Result> { it.isReachable }
                    .thenBy { it.latencyMs }
            )
    }

    suspend fun pickFastest(configs: List<ServerConfig>): ServerConfig? =
        pingAll(configs).firstOrNull { it.isReachable }?.config

    private suspend fun pingOne(cfg: ServerConfig): Result = withContext(Dispatchers.IO) {
        var best = Long.MAX_VALUE
        var reachable = false
        repeat(attempts) {
            val t = measureTcpHandshake(cfg.address, cfg.port)
            if (t != null) {
                reachable = true
                if (t < best) best = t
            }
        }
        Result(
            config = cfg,
            latencyMs = if (reachable) best else -1L,
            isReachable = reachable
        )
    }

    private suspend fun measureTcpHandshake(host: String, port: Int): Long? =
        withTimeoutOrNull(timeoutMs.toLong()) {
            runCatching {
                Socket().use { socket ->
                    // Monotonik saat — duvar saati (currentTimeMillis) NTP
                    // senkronunda geriye kayabilir, ölçümü bozar
                    val start = System.nanoTime()
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    (System.nanoTime() - start) / 1_000_000
                }
            }.getOrNull()
        }
}
