package com.telo.vpn.service

import android.content.Context
import android.net.VpnService
import android.util.Log
import java.io.File

interface XrayCoreEngine {
    fun start(configJson: String, tunFd: Int): Boolean
    fun stop()
    fun isRunning(): Boolean
    fun queryStats(tag: String, uplink: Boolean): Long
}

/**
 * Gerçek API (libv2ray.aar içinden javap ile doğrulandı):
 *   Libv2ray.newCoreController(CoreCallbackHandler) → CoreController
 *   CoreController.startLoop(String configPath, int tunFd) throws Exception
 *   CoreController.stopLoop() throws Exception
 *   CoreController.queryStats(String tag, String direction) → Long
 *   CoreController.getIsRunning() → Boolean
 *   Libv2ray.initCoreEnv(String dataDir, String logDir)
 *   Libv2ray.checkVersionX() → String
 */
class LibXrayEngine(private val service: VpnService) : XrayCoreEngine {

    private var controller: Any? = null  // CoreController instance

    companion object {
        private const val TAG = "TeloVPN/Xray"
        private var initialized = false

        fun initEnv(context: Context) {
            if (initialized) return
            runCatching {
                val lib = Class.forName("libv2ray.Libv2ray")
                lib.getMethod("initCoreEnv", String::class.java, String::class.java)
                    .invoke(null, context.filesDir.absolutePath, context.filesDir.absolutePath)
                initialized = true
                val ver = runCatching {
                    lib.getMethod("checkVersionX").invoke(null) as String
                }.getOrDefault("?")
                Log.i(TAG, "Xray env hazır — $ver")
            }.onFailure { Log.e(TAG, "initEnv başarısız: ${it.message}") }
        }
    }

    override fun start(configJson: String, tunFd: Int): Boolean {
        return runCatching {
            val lib = Class.forName("libv2ray.Libv2ray")
            val callbackClass = Class.forName("libv2ray.CoreCallbackHandler")
            val controllerClass = Class.forName("libv2ray.CoreController")

            // Config JSON → dosyaya yaz
            val configFile = File(service.filesDir, "xray_config.json")
            configFile.writeText(configJson)

            // CoreCallbackHandler proxy oluştur (Java dynamic proxy)
            val handler = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { _, method, args ->
                when (method.name) {
                    "startup"        -> { Log.i(TAG, "Xray startup"); 0L }
                    "shutdown"       -> { Log.i(TAG, "Xray shutdown"); 0L }
                    "onEmitStatus"   -> {
                        val status = args?.getOrNull(1) as? String ?: ""
                        Log.d(TAG, "Xray status: $status")
                        0L
                    }
                    else -> null
                }
            }

            // newCoreController(handler) → CoreController
            val ctrl = lib.getMethod("newCoreController", callbackClass)
                .invoke(null, handler)
            controller = ctrl

            // startLoop(configPath, tunFd)
            controllerClass.getMethod("startLoop", String::class.java, Int::class.javaPrimitiveType)
                .invoke(ctrl, configFile.absolutePath, tunFd)

            Log.i(TAG, "Xray başlatıldı (fd=$tunFd, config=${configFile.absolutePath})")
            true
        }.onFailure { e ->
            Log.e(TAG, "start hatası: ${e.javaClass.simpleName}: ${e.message}", e)
        }.getOrDefault(false)
    }

    override fun stop() {
        runCatching {
            val controllerClass = Class.forName("libv2ray.CoreController")
            controller?.let {
                controllerClass.getMethod("stopLoop").invoke(it)
            }
        }.onFailure { Log.e(TAG, "stop hatası: ${it.message}") }
        controller = null
        Log.i(TAG, "Xray durduruldu")
    }

    override fun isRunning(): Boolean = runCatching {
        val controllerClass = Class.forName("libv2ray.CoreController")
        controller?.let {
            controllerClass.getMethod("getIsRunning").invoke(it) as? Boolean
        } ?: false
    }.getOrDefault(false)

    override fun queryStats(tag: String, uplink: Boolean): Long = runCatching {
        val controllerClass = Class.forName("libv2ray.CoreController")
        controller?.let {
            controllerClass.getMethod("queryStats", String::class.java, String::class.java)
                .invoke(it, tag, if (uplink) "uplink" else "downlink") as? Long
        } ?: 0L
    }.getOrDefault(0L) ?: 0L
}

/** libXray.aar olmadığında kullanılan stub. */
class StubXrayEngine : XrayCoreEngine {
    private var running = false
    override fun start(configJson: String, tunFd: Int): Boolean {
        Log.w("TeloVPN", "StubXrayEngine — libXray.aar yok")
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
        Log.w("TeloVPN", "libv2ray bulunamadı → StubXrayEngine")
        StubXrayEngine()
    }
}
