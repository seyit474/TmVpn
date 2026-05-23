package com.seyit474.tmvpn.service

import android.util.Log

object XrayCoreProxy {
    private const val TAG = "XrayCoreProxy"
    // AndroidLibXrayLite API: libXray.LibXray.startXray(config: String): Boolean
    // AndroidLibXrayLite API: libXray.LibXray.stopXray()

    fun start(configJson: String): Boolean {
        return try {
            val cls = Class.forName("libXray.LibXray")
            val method = cls.getMethod("startXray", String::class.java)
            method.invoke(null, configJson) == true
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "libXray.aar bulunamadı — app/libs/ klasörüne ekleyin")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Xray başlatma hatası: ${e.message}")
            false
        }
    }

    fun stop() {
        try {
            val cls = Class.forName("libXray.LibXray")
            cls.getMethod("stopXray").invoke(null)
        } catch (_: Exception) {}
    }
}
