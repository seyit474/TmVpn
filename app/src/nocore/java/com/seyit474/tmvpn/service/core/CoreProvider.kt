package com.seyit474.tmvpn.service.core

/**
 * `nocore` kaynak seti — libv2ray.aar YOKKEN derlenir.
 *
 * Çekirdek paketlenmediği için stub'lar döner; [XrayCore.isAvailable] false
 * olduğundan VpnService bağlanmayı reddeder ve kullanıcıya açık hata gösterir.
 * "Sahte bağlı" durumu asla oluşmaz.
 */

private object StubXrayCore : XrayCore {
    override val isAvailable = false
    override fun start(configJson: String, protect: (Int) -> Boolean) =
        error("libv2ray bu derlemede yok")
    override fun stop() = Unit
}

private object StubTun2Socks : Tun2Socks {
    override val isAvailable = false
    override fun start(tunFd: Int, socksAddress: String, socksPort: Int, mtu: Int) =
        error("tun2socks bu derlemede yok")
    override fun stop() = Unit
}

object XrayCoreProvider {
    fun createXray(): XrayCore = StubXrayCore
    fun createTun2Socks(): Tun2Socks = StubTun2Socks
}
