package com.tmvpn.app.service

import android.content.Context
import android.os.ParcelFileDescriptor
import com.tmvpn.app.util.LogBus
import hev.htproxy.TProxyService
import java.io.File

class TProxyController(
    private val context: Context,
    private val tunInterface: ParcelFileDescriptor,
    private val socksPort: Int = 10808,
) {
    companion object { private const val TAG = "TProxy" }

    fun start() {
        val configFile = File(context.filesDir, "hev-socks5-tunnel.yaml").apply {
            writeText(buildYamlConfig())
        }
        LogBus.log(TAG, "TUN fd: ${tunInterface.fd}, SOCKS port: $socksPort")
        try {
            TProxyService.TProxyStartService(configFile.absolutePath, tunInterface.fd)
            LogBus.log(TAG, "tun2socks baslatildi")
        } catch (e: Throwable) {
            LogBus.log(TAG, "tun2socks hatasi: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    fun stop() {
        try {
            TProxyService.TProxyStopService()
            LogBus.log(TAG, "tun2socks durduruldu")
        } catch (e: Throwable) {
            LogBus.log(TAG, "stop hata: ${e.message}")
        }
    }

    fun getStats(): LongArray? = try { TProxyService.TProxyGetStats() } catch (_: Throwable) { null }

    private fun buildYamlConfig() = buildString {
        appendLine("tunnel:")
        appendLine("  mtu: 1500")
        appendLine("  ipv4: 10.10.10.1")
        appendLine("socks5:")
        appendLine("  port: $socksPort")
        appendLine("  address: 127.0.0.1")
        appendLine("  udp: 'udp'")
        appendLine("misc:")
        appendLine("  tcp-read-write-timeout: 300000")
        appendLine("  udp-read-write-timeout: 60000")
        appendLine("  log-level: 'warn'")
    }
}
