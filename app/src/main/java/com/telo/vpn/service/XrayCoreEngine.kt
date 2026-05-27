package com.telo.vpn.service

import android.content.Context
import android.util.Log

/**
 * Xray-core motor soyutlaması.
 * Gerçek libXray.aar (AndroidLibXrayLite) app/libs/ klasörüne
 * yerleştirildiğinde LibXrayEngine aktif edilir.
 */
interface XrayCoreEngine {
    fun start(configJson: String): Boolean
    fun stop()
    fun isRunning(): Boolean
    fun queryStats(tag: String, uplink: Boolean): Long
}

/**
 * libXray.aar olmadığında kullanılan stub.
 * Uygulama derlenir ama gerçek tünel kurulmaz.
 */
class StubXrayEngine : XrayCoreEngine {
    private var running = false
    override fun start(configJson: String): Boolean {
        Log.w("TeloVPN", "StubXrayEngine — gerçek libXray.aar yok, tünel başlatılamıyor")
        running = true
        return true
    }
    override fun stop() { running = false }
    override fun isRunning() = running
    override fun queryStats(tag: String, uplink: Boolean) = 0L
}

/**
 * libXray.aar mevcut olduğunda etkinleştir:
 *
 *   import libv2ray.Libv2ray
 *   import libv2ray.V2RayVPNServiceSupportsSet
 *
 *   class LibXrayEngine(context: Context) : XrayCoreEngine {
 *       private val point = Libv2ray.newV2RayPoint(object : V2RayVPNServiceSupportsSet {
 *           override fun shutdown() = 0L
 *           override fun prepare() = ""
 *           override fun protect(l: Long) = true
 *           override fun onEmitStatus(l: Long, s: String) = 0L
 *           override fun setup(s: String) = 0L
 *       }, false)
 *
 *       override fun start(configJson: String): Boolean {
 *           point.configureFileContent = configJson
 *           point.runLoop(false)
 *           return true
 *       }
 *       override fun stop() = point.stopLoop()
 *       override fun isRunning() = point.isRunning
 *       override fun queryStats(tag: String, uplink: Boolean) =
 *           point.queryStats(tag, if (uplink) "uplink" else "downlink")
 *   }
 */
fun createXrayEngine(context: Context): XrayCoreEngine = StubXrayEngine()
