package com.seyit474.tmvpn.service.core

/**
 * Xray-core soyutlaması.
 *
 * Gerçek implementasyon libXray.aar (gomobile derlemesi) gerektirir.
 * AAR `app/libs/` altına konduğunda bu arayüzü saran bir adapter yazılıp
 * [XrayCoreProvider.create] içinde döndürülür — servis koduna dokunmak gerekmez.
 */
interface XrayCore {
    /** Çekirdek bu derlemede paketli mi? */
    val isAvailable: Boolean

    /** Xray'i verilen JSON config ile başlatır. Hata durumunda exception fırlatır. */
    fun start(configJson: String)

    fun stop()
}

/** libXray henüz paketlenmediği için kullanılan yer tutucu. */
private object StubXrayCore : XrayCore {
    override val isAvailable = false
    override fun start(configJson: String) =
        error("libXray bu derlemede yok")

    override fun stop() = Unit
}

object XrayCoreProvider {
    fun create(): XrayCore = StubXrayCore
}
