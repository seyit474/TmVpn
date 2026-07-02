package com.seyit474.tmvpn.service.core

import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet

/**
 * `xray` kaynak seti — libv2ray.aar (AndroidLibXrayLite) mevcutken derlenir.
 *
 * Bu dosya yalnızca `app/libs/libv2ray.aar` varken Gradle kaynak setine eklenir
 * (bkz. app/build.gradle.kts). AAR'ı GitHub Actions release'ten indirip koyar.
 */

private class LibV2RayCore : XrayCore {

    override val isAvailable = true

    private var point: V2RayPoint? = null
    @Volatile private var protectFn: ((Int) -> Boolean)? = null

    // libv2ray'in çağırdığı geri bildirim arayüzü. En kritik metot protect():
    // çekirdeğin dış bağlantı soketlerini VPN tünelinin dışına alır, yoksa döngü.
    private val supportSet = object : V2RayVPNServiceSupportsSet {
        override fun setup(s: String): Long = 0
        override fun prepare(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(l: Long, s: String): Long = 0
        override fun protect(l: Long): Boolean =
            protectFn?.invoke(l.toInt()) ?: true
    }

    override fun start(configJson: String, protect: (Int) -> Boolean) {
        protectFn = protect
        val p = Libv2ray.newV2RayPoint(supportSet, false)
        p.configureFileContent = configJson
        // domainName boş bırakılırsa çekirdek config'teki outbound'u kullanır
        p.domainName = ""
        p.runLoop(false)
        point = p
    }

    override fun stop() {
        runCatching { point?.stopLoop() }
        point = null
        protectFn = null
    }
}

/**
 * tun2socks henüz paketlenmedi — hev-socks5-tunnel `.so` dosyaları
 * `app/src/main/jniLibs/` altına eklendiğinde gerçek köprü devreye girecek.
 * O zamana kadar stub kalır; VpnService her iki bileşen de hazır olmadan
 * bağlanmayı reddeder.
 */
private object StubTun2Socks : Tun2Socks {
    override val isAvailable = false
    override fun start(tunFd: Int, socksAddress: String, socksPort: Int, mtu: Int) =
        error("tun2socks bu derlemede yok")
    override fun stop() = Unit
}

object XrayCoreProvider {
    fun createXray(): XrayCore = LibV2RayCore()
    fun createTun2Socks(): Tun2Socks = StubTun2Socks
}
