package com.tmvpn.app.hwid

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object HwidManager {

    fun getHwid(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "tmvpndefault"
        return toUuid(androidId)
    }

    private fun toUuid(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x30).toByte() // UUID v3
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte() // variant
        val h = bytes.joinToString("") { "%02x".format(it) }
        return "${h.substring(0,8)}-${h.substring(8,12)}-${h.substring(12,16)}-${h.substring(16,20)}-${h.substring(20,32)}"
    }
}
