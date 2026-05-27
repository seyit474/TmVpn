package com.telo.vpn.ping

import com.telo.vpn.model.PingedServer
import com.telo.vpn.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class ServerPinger {

    companion object {
        private const val TIMEOUT_MS = 3000
        private const val MAX_PARALLEL = 10
    }

    suspend fun pingAll(servers: List<ServerConfig>): List<PingedServer> =
        withContext(Dispatchers.IO) {
            servers.chunked(MAX_PARALLEL).flatMap { chunk ->
                chunk.map { cfg ->
                    async {
                        val (latency, ok) = measureTcp(cfg.address, cfg.port)
                        PingedServer(cfg, latency, ok)
                    }
                }.awaitAll()
            }.sortedWith(
                compareByDescending<PingedServer> { it.isReachable }
                    .thenBy { it.latencyMs }
            )
        }

    private fun measureTcp(host: String, port: Int): Pair<Long, Boolean> {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            }
            val ms = System.currentTimeMillis() - start
            ms to true
        } catch (_: Exception) {
            9999L to false
        }
    }
}
