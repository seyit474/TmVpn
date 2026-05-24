package com.seyit474.tmvpn.service

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XrayVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var vpnJob: Job? = null

    companion object {
        private const val TAG = "XrayVpnService"
        private const val CHANNEL_ID = "tmvpn_status"
        private const val NOTIF_ID = 1

        // Intent actions
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"

        // LocalBroadcast action
        const val ACTION_STATUS = "com.seyit474.tmvpn.VPN_STATUS"

        // LocalBroadcast event types
        const val EVENT_CONNECTING = "CONNECTING"
        const val EVENT_CONNECTED = "CONNECTED"
        const val EVENT_DISCONNECTED = "DISCONNECTED"
        const val EVENT_ERROR = "ERROR"

        // Intent / Broadcast extras
        const val EXTRA_EVENT = "event"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_CONFIG_JSON = "config_json"

        fun start(ctx: Context, configJson: String) {
            val i = Intent(ctx, XrayVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG_JSON, configJson)
            }
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, XrayVpnService::class.java).apply {
                action = ACTION_STOP
            }
            ctx.startForegroundService(i)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON)
                if (configJson == null) {
                    Log.e(TAG, "ACTION_START alındı ama config_json yok")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIF_ID, buildNotification("Bağlanıyor..."))
                vpnJob?.cancel()
                vpnJob = serviceScope.launch {
                    startVpn(configJson)
                }
            }
            else -> {
                // Eski davranış: config varsa başlat
                val configJson = intent?.getStringExtra(EXTRA_CONFIG_JSON)
                if (configJson != null) {
                    startForeground(NOTIF_ID, buildNotification("Bağlanıyor..."))
                    vpnJob?.cancel()
                    vpnJob = serviceScope.launch {
                        startVpn(configJson)
                    }
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun sendStatus(event: String, message: String = "") {
        val intent = Intent(ACTION_STATUS).apply {
            putExtra(EXTRA_EVENT, event)
            if (message.isNotEmpty()) putExtra(EXTRA_MESSAGE, message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun startVpn(configJson: String) {
        sendStatus(EVENT_CONNECTING)

        try {
            // Eski TUN arayüzünü kapat
            tunInterface?.close()
            tunInterface = null

            // TUN arayüzü oluştur
            val tun = Builder()
                .setSession("TmVpn")
                .addAddress("10.10.10.1", 32)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .setMtu(1500)
                .establish()

            if (tun == null) {
                Log.e(TAG, "TUN arayüzü kurulamadı (VPN izni yok?)")
                sendStatus(EVENT_ERROR, "TUN arayüzü kurulamadı")
                stopSelf()
                return
            }

            tunInterface = tun

            // Xray çekirdeğini başlat; this (VpnService) socket protection için kullanılır
            val xrayStarted = XrayCoreProxy.start(configJson, this)
            if (!xrayStarted) {
                Log.w(TAG, "Xray başlatılamadı; TUN açık devam ediyor")
            }

            // Bildirimi güncelle
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification("Bağlı"))

            sendStatus(EVENT_CONNECTED)
            Log.i(TAG, "VPN bağlantısı kuruldu")

        } catch (e: Exception) {
            Log.e(TAG, "VPN başlatma hatası: ${e.message}", e)
            sendStatus(EVENT_ERROR, e.message ?: "Bilinmeyen hata")
            stopSelf()
        }
    }

    private fun stopVpn() {
        try {
            XrayCoreProxy.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Xray durdurulamadı: ${e.message}")
        }

        try {
            tunInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "TUN arayüzü kapatılamadı: ${e.message}")
        }
        tunInterface = null

        sendStatus(EVENT_DISCONNECTED)
        Log.i(TAG, "VPN bağlantısı kesildi")

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
            NotificationChannel(CHANNEL_ID, "VPN Durumu", NotificationManager.IMPORTANCE_LOW)
        )

        // "Kes" butonu için PendingIntent
        val stopIntent = Intent(this, XrayVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TmVpn")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_vpn_notification)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Kes", stopPendingIntent)
            .build()
    }
}
