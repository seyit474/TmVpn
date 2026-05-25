package com.tmvpn.app.hwid

import android.content.Context
import android.provider.Settings

object HwidManager {
    fun getHwid(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
}
