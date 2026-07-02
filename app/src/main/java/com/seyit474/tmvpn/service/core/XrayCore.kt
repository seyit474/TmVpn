package com.seyit474.tmvpn.service.core

import android.net.VpnService

/**
 * Xray-core soyutlaması.
 *
 * Gerçek implementasyon libv2ray.aar (AndroidLibXrayLite gomobile derlemesi)
 * gerektirir. AAR `app/libs/` altına konduğunda Gradle otomatik olarak `xray`
 * kaynak setini derlemeye alır ve buradaki [XrayCoreProvider] gerçek adapter'ı
 * döndürür. AAR yoksa `nocore` kaynak setindeki stub kullanılır — böylece proje
 * çekirdek olmadan da derlenir.
 */
interface XrayCore {
    /** Çekirdek bu derlemede paketli mi? */
    val isAvailable: Boolean

    /**
     * Xray'i verilen JSON config ile başlatır.
     * @param protect çekirdeğin açtığı soketleri VPN tünelinin dışına almak için
     *   [VpnService.protect] çağrısı — yoksa yönlendirme döngüsü oluşur.
     * Hata durumunda exception fırlatır.
     */
    fun start(configJson: String, protect: (Int) -> Boolean)

    fun stop()
}

/**
 * tun2socks soyutlaması — tun arayüzü trafiğini Xray'in SOCKS portuna köprüler.
 * Gerçek implementasyon hev-socks5-tunnel native kütüphanesini (`libhevtun.so`)
 * gerektirir.
 */
interface Tun2Socks {
    val isAvailable: Boolean

    /**
     * @param tunFd VpnService.Builder.establish() ile açılan tun dosya tanıtıcısı
     * @param socksAddress SOCKS proxy adresi (Xray inbound)
     * @param socksPort SOCKS proxy portu
     * @param mtu tun MTU değeri
     */
    fun start(tunFd: Int, socksAddress: String, socksPort: Int, mtu: Int)

    fun stop()
}
