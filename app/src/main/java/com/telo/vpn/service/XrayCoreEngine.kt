package com.telo.vpn.service

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.lang.reflect.Proxy
import java.net.DatagramSocket

/**
 * Xray-core motor soyutlaması.
 * libXray.aar app/libs/ klasörüne yerleştirildiğinde LibXrayEngine aktif edilir.
 * Derleme sırasında libv2ray.* importu yoktur — tüm çağrılar reflection ile yapılır.
 */
interface XrayCoreEngine {
    fun start(configJson: String, tunFd: Int): Boolean
    fun stop()
    fun isRunning(): Boolean
    fun queryStats(tag: String, uplink: Boolean): Long
}

/**
 * Gerçek motor — libXray.aar (AndroidLibXrayLite) ile çalışır.
 * Tüm libv2ray.* çağrıları reflection üzerinden yapılır; AAR olmadan da derlenir.
 */
class LibXrayEngine(private val service: VpnService) : XrayCoreEngine {

    private var controller: Any? = null

    companion object {
        private const val TAG = "TeloVPN/Xray"
        private var initialized = false

        fun initEnv(context: Context) {
            if (initialized) return
            val libv2ray = Class.forName("libv2ray.Libv2ray")
            libv2ray.getMethod("initCoreEnv", String::class.java, String::class.java)
                .invoke(null, context.filesDir.absolutePath, "")
            val checkVersion = runCatching {
                libv2ray.getMethod("checkVersionX").invoke(null) as String
            }.getOrDefault("?")
            initialized = true
            Log.i(TAG, "Xray env hazır — $checkVersion")
        }
    }

    override fun start(configJson: String, tunFd: Int): Boolean {
        return runCatching {
            val libv2ray = Class.forName("libv2ray.Libv2ray")
            val handlerClass = Class.forName("libv2ray.CoreCallbackHandler")

            val handler = Proxy.newProxyInstance(
                handlerClass.classLoader,
                arrayOf(handlerClass)
            ) { _, method, _ ->
                when (method.name) {
                    "startup" -> runCatching {
                        val s = DatagramSocket()
                        service.protect(s)
                        ParcelFileDescriptor.fromDatagramSocket(s).fd.toLong()
                    }.getOrElse { e ->
                        Log.w(TAG, "Socket protect başarısız: ${e.message}")
                        0L
                    }
                    "shutdown" -> 0L
                    "onEmitStatus" -> 0L
                    else -> null
                }
            }

            val newCoreController = libv2ray.getMethod("newCoreController", handlerClass)
            controller = newCoreController.invoke(null, handler)

            controller!!.javaClass
                .getMethod("startLoop", String::class.java, Int::class.java)
                .invoke(controller, configJson, tunFd)

            Log.i(TAG, "Xray başlatıldı (tunFd=$tunFd)")
            true
        }.onFailure { e ->
            Log.e(TAG, "Xray başlatma hatası: ${e.message}", e)
        }.getOrDefault(false)
    }

    override fun stop() {
        runCatching {
            controller?.javaClass?.getMethod("stopLoop")?.invoke(controller)
        }
        controller = null
        Log.i(TAG, "Xray durduruldu")
    }

    override fun isRunning(): Boolean = runCatching {
        controller?.javaClass?.getMethod("isRunning")?.invoke(controller) as? Boolean
    }.getOrDefault(false) ?: false

    override fun queryStats(tag: String, uplink: Boolean): Long = runCatching {
        controller?.javaClass
            ?.getMethod("queryStats", String::class.java, String::class.java)
            ?.invoke(controller, tag, if (uplink) "uplink" else "downlink") as? Long
    }.getOrDefault(0L) ?: 0L
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
