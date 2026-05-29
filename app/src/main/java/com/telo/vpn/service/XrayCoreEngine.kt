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
 * Gerçek motor — libXray.aar (AndroidLibXrayLite) ile çalışır.
 * Reflection kullanılır; AAR olmadan da derlenir.
 *
 * Mevcut API (gomobile static methods on libv2ray.Libv2ray):
 *   initCoreEnv(dataDir: String, logDir: String)
 *   startLoop(configPath: String, tunFd: Int): Boolean
 *   stopLoop()
 *   queryStats(tag: String, direct: String): Long
 *   checkVersionX(): String
 */
class LibXrayEngine(private val service: VpnService) : XrayCoreEngine {

    private var running = false

    companion object {
        private const val TAG = "TeloVPN/Xray"
        private var initialized = false

        fun initEnv(context: Context) {
            if (initialized) return
            runCatching {
                val lib = Class.forName("libv2ray.Libv2ray")
                // Mevcut API metodlarını logla — hangi imzanın çalışacağını görmek için
                lib.methods.forEach {
                    Log.d(TAG, "API: ${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})")
                }
                lib.getMethod("initCoreEnv", String::class.java, String::class.java)
                    .invoke(null, context.filesDir.absolutePath, "")
                initialized = true
                val ver = runCatching {
                    lib.getMethod("checkVersionX").invoke(null) as String
                }.getOrDefault("?")
                Log.i(TAG, "Xray env hazır — $ver")
            }.onFailure { Log.e(TAG, "initEnv: ${it.message}") }
        }
    }

    override fun start(configJson: String, tunFd: Int): Boolean {
        return runCatching {
            val lib = Class.forName("libv2ray.Libv2ray")

            // Config JSON dosyaya yaz (Xray genellikle dosya yolu bekler)
            val configFile = File(service.filesDir, "xray_config.json")
            configFile.writeText(configJson)
            val configPath = configFile.absolutePath

            val ok = tryStartLoop(lib, configPath, configJson, tunFd)
            if (ok) { running = true; Log.i(TAG, "Xray başlatıldı (fd=$tunFd)") }
            else Log.e(TAG, "Tüm startLoop imzaları başarısız — logcat'te 'API:' satırlarını kontrol et")
            ok
        }.onFailure { e ->
            Log.e(TAG, "start hatası: ${e.javaClass.simpleName}: ${e.message}", e)
        }.getOrDefault(false)
    }

    /**
     * Farklı imzaları sırayla dener.
     * Hem dosya yolu hem raw JSON string denenir; int ve long fd parametresi denenir.
     */
    private fun tryStartLoop(lib: Class<*>, configPath: String, configJson: String, fd: Int): Boolean {
        // 1) startLoop(String configPath, int fd)
        runCatching {
            return lib.getMethod("startLoop", String::class.java, Int::class.javaPrimitiveType)
                .invoke(null, configPath, fd) as? Boolean ?: true
        }
        // 2) startLoop(String configPath, long fd)
        runCatching {
            return lib.getMethod("startLoop", String::class.java, Long::class.javaPrimitiveType)
                .invoke(null, configPath, fd.toLong()) as? Boolean ?: true
        }
        // 3) startLoop(String configJson, int fd) — raw JSON string
        runCatching {
            return lib.getMethod("startLoop", String::class.java, Int::class.javaPrimitiveType)
                .invoke(null, configJson, fd) as? Boolean ?: true
        }
        // 4) startXray(String configPath, int fd) — alternatif metod adı
        runCatching {
            lib.getMethod("startXray", String::class.java, Int::class.javaPrimitiveType)
                .invoke(null, configPath, fd)
            return true
        }
        return false
    }

    override fun stop() {
        running = false
        runCatching {
            val lib = Class.forName("libv2ray.Libv2ray")
            runCatching { lib.getMethod("stopLoop").invoke(null) }
            runCatching { lib.getMethod("stopXray").invoke(null) }
        }
        Log.i(TAG, "Xray durduruldu")
    }

    override fun isRunning(): Boolean = running

    override fun queryStats(tag: String, uplink: Boolean): Long = runCatching {
        Class.forName("libv2ray.Libv2ray")
            .getMethod("queryStats", String::class.java, String::class.java)
            .invoke(null, tag, if (uplink) "uplink" else "downlink") as? Long
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
