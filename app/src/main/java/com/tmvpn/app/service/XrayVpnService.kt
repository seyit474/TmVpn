package com.tmvpn.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.seyit474.tmvpn.R
import com.tmvpn.app.util.LogBus
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import kotlinx.coroutines.*

class XrayVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private var tproxyController: TProxyController? = null
    private var coreController: CoreController? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var vpnJob: Job? = null
    private var vpnRunning = false

    companion object {
        private const val TAG = "XrayVpnService"
        private const val CHANNEL_ID = "tmvpn_status"
        private const val NOTIF_ID = 1

        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_STATUS = "com.tmvpn.app.VPN_STATUS"
        const val EVENT_CONNECTING = "CONNECTING"
        const val EVENT_CONNECTED = "CONNECTED"
        const val EVENT_DISCONNECTED = "DISCONNECTED"
        const val EVENT_ERROR = "ERROR"
        const val EXTRA_EVENT = "event"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_CONFIG_JSON = "config_json"

        fun start(ctx: Context, configJson: String, remark: String = "") {
            ctx.startForegroundService(Intent(ctx, XrayVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG_JSON, configJson)
            })
        }

        fun stop(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, XrayVpnService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                LogBus.log(TAG, "Durdurma komutu alindi")
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON) ?: run {
                    LogBus.log(TAG, "HATA: config_json yok")
                    stopSelf()
                    return START_NOT_STICKY
                }
                LogBus.log(TAG, "Baslama komutu alindi, config uzunlugu=${configJson.length}")
                LogBus.log(TAG, "CFG-A: ${configJson.take(200)}")
                LogBus.log(TAG, "CFG-B: ${configJson.drop(200).take(200)}")
                startForeground(NOTIF_ID, buildNotification("Baglanylýar..."))
                vpnJob?.cancel()
                vpnJob = serviceScope.launch { startVpn(configJson) }
            }
        }
        return START_STICKY
    }

    private fun sendStatus(event: String, message: String = "") {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(ACTION_STATUS).apply {
                putExtra(EXTRA_EVENT, event)
                if (message.isNotEmpty()) putExtra(EXTRA_MESSAGE, message)
            }
        )
    }

    private fun startVpn(configJson: String) {
        vpnRunning = true
        LogBus.log(TAG, "=== VPN baslatiliyor ===")
        sendStatus(EVENT_CONNECTING)
        try {
            LogBus.log(TAG, "Onceki oturum temizleniyor...")
            tproxyController?.stop()
            tproxyController = null
            try { coreController?.stopLoop() } catch (_: Exception) {}
            coreController = null
            tunInterface?.close()
            tunInterface = null

            LogBus.log(TAG, "TUN arayuzu olusturuluyor...")
            val tun = Builder()
                .setSession("TM VPN")
                .addAddress("10.10.10.1", 32)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .setMtu(1500)
                .addDisallowedApplication(packageName)
                .establish() ?: run {
                    LogBus.log(TAG, "HATA: TUN arayuzu kurulamadi (izin yok?)")
                    sendStatus(EVENT_ERROR, "TUN arayüzü kurulamadı (VPN izni gerekli)")
                    stopSelf()
                    return
                }
            tunInterface = tun
            LogBus.log(TAG, "TUN olusturuldu: fd=${tun.fd}")

            LogBus.log(TAG, "Xray core baslatiliyor (SOCKS port: ${XrayConfigBuilder.SOCKS_PORT})...")
            Seq.setContext(this)
            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long {
                    LogBus.log("XrayCore", "durum: $p1")
                    return 0
                }
                override fun shutdown(): Long {
                    LogBus.log("XrayCore", "shutdown callback")
                    serviceScope.launch { stopVpn(); stopSelf() }
                    return 0
                }
                override fun startup(): Long {
                    LogBus.log("XrayCore", "startup callback - hazir")
                    return 0
                }
            }

            val controller = Libv2ray.newCoreController(callback)
            controller.startLoop(configJson, XrayConfigBuilder.SOCKS_PORT)
            coreController = controller
            LogBus.log(TAG, "Xray core baslatildi OK")

            LogBus.log(TAG, "TProxyController baslatiliyor...")
            val tproxy = TProxyController(this, tun, XrayConfigBuilder.SOCKS_PORT)
            tproxy.start()
            tproxyController = tproxy
            LogBus.log(TAG, "TProxyController baslatildi OK")

            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification("Baglandy"))
            com.tmvpn.app.util.TrafficCounter.start()
            sendStatus(EVENT_CONNECTED)
            LogBus.log(TAG, "=== VPN BAGLANDI ===")

        } catch (e: Exception) {
            val msg = "${e.javaClass.simpleName}: ${e.message}"
            LogBus.log(TAG, "HATA: $msg")
            e.stackTrace.take(5).forEach { LogBus.log(TAG, "  at $it") }
            Log.e(TAG, "VPN baslatma hatasi", e)
            sendStatus(EVENT_ERROR, e.message ?: "Näbelli yalnyslyk")
            tproxyController?.stop()
            tproxyController = null
            tunInterface?.close()
            tunInterface = null
            stopSelf()
        }
    }

    private fun stopVpn() {
        if (!vpnRunning) {
            LogBus.log(TAG, "stopVpn cagirildi ama zaten durdurulmus, atlaniyor")
            return
        }
        vpnRunning = false
        com.tmvpn.app.util.TrafficCounter.stop()
        LogBus.log(TAG, "VPN durduruluyor...")
        try { tproxyController?.stop() } catch (_: Exception) {}
        tproxyController = null

        try { coreController?.stopLoop() } catch (_: Exception) {}
        coreController = null

        try { tunInterface?.close() } catch (_: Exception) {}
        tunInterface = null

        sendStatus(EVENT_DISCONNECTED)
        LogBus.log(TAG, "VPN durduruldu")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        vpnJob?.cancel()
        serviceJob.cancel()
        stopVpn()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "VPN Ýagdaýy", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, XrayVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TM VPN")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_vpn_notification)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Kes", stopPi)
            .build()
    }
}
