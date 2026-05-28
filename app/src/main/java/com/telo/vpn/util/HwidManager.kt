package com.telo.vpn.util

import android.content.Context
import android.provider.Settings
import java.util.UUID

object HwidManager {

    fun getHwid(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            return androidId
        }
        return getFallbackId(context)
    }

    private fun getFallbackId(context: Context): String {
        val prefs = context.getSharedPreferences("hwid", Context.MODE_PRIVATE)
        var id = prefs.getString("id", null)
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString("id", id).apply()
        }
        return id
    }
}
