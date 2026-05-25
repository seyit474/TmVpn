package com.tmvpn.app

import android.app.Application
import com.tmvpn.app.util.LogBus

class TmVpnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogBus.log("APP", "=== TM VPN baslatildi (build: 2026-05-25-v8) ===")
    }
}
