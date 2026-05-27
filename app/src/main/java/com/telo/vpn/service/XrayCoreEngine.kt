package com.telo.vpn.service

import android.content.Context
import android.net.VpnService
import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * Xray-core motor soyutlaması.
 * libXray.aar app/libs/ klasörüne yerleştirildiğinde LibXrayEngine aktif edilir.
 */
interface XrayCoreEngine {
    fun start(configJson: String, tunFd: Int): Boolean
    fun stop()
    fun isRunning(): Boolean
    fun queryStats(tag: String, uplink: Boolean): Long
}

/**
 * Gerçek motor — libXray.aar (AndroidLibXrayLite) ile çalışır.
 * startLoop(configJson, tunFd): Go core tun arayüzünü doğrudan yönetir,
 * ayrı tun2socks gerekmez.
 */
class LibXrayEngine(
    private val service: VpnService
) : XrayCoreEngine {

    private var controller: CoreController? = null

    companion object {
        private const val TAG = "TeloVPN/Xray"
        private var initialized = false

        fun initEnv(context: Context) {
            if (initialized) return
            Libv2ray.initCoreEnv(context.filesDir.absolutePath, "")
            initialized = true
            Log.i(TAG, "Xray env hazır — ${Libv2ray.checkVersionX()}")
        }
    }

    override fun start(configJson: String, tunFd: Int): Boolean {
        return runCatching {
            val handler = object : CoreCallbackHandler {
                override fun startup(): Long {
                    // Xray outbound socketlerini VPN tünelinden muaf tut
                    // Go core bu callback'ten dönen fd'yi korur (protect)
                    // Şimdilik 0 döndür; outbound socket leak'i önlemek için
                    // builder.allowBypass() yeterli
                    return 0L
                }
                override fun shutdown(): Long = 0L
                override fun onEmitStatus(l: Long, s: String): Long {
                    Log.d(TAG, "Xray[$l]: $s")
                    return 0L
                }
            }
            controller = Libv2ray.newCoreController(handler)
            controller!!.startLoop(configJson, tunFd)
            Log.i(TAG, "Xray başlatıldı (tunFd=$tunFd)")
            true
        }.onFailure { e ->
            Log.e(TAG, "Xray başlatma hatası: ${e.message}", e)
        }.getOrDefault(false)
    }

    override fun stop() {
        runCatching { controller?.stopLoop() }
        controller = null
        Log.i(TAG, "Xray durduruldu")
    }

    override fun isRunning() = controller?.isRunning ?: false

    override fun queryStats(tag: String, uplink: Boolean) =
        controller?.queryStats(tag, if (uplink) "uplink" else "downlink") ?: 0L
}

/**
 * libXray.aar olmadığında kullanılan stub — uygulama derlenir ama gerçek tünel kurulmaz.
 */
class StubXrayEngine : XrayCoreEngine {
    private var running = false
    override fun start(configJson: String, tunFd: Int): Boolean {
        Log.w("TeloVPN", "StubXrayEngine — libXray.aar yok, tünel başlatılamıyor")
        running = true
        return true
    }
    override fun stop() { running = false }
    override fun isRunning() = running
    override fun queryStats(tag: String, uplink: Boolean) = 0L
}

fun createXrayEngine(context: Context): XrayCoreEngine {
    return try {
        Class.forName("libv2ray.Libv2ray")
        LibXrayEngine.initEnv(context)
        LibXrayEngine(context as VpnService)
    } catch (_: ClassNotFoundException) {
        Log.w("TeloVPN", "libv2ray sınıfı bulunamadı → StubXrayEngine")
        StubXrayEngine()
    }
}
