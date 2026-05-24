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

    companion object {
        private const val TAG = "XrayVpnService"
        private const val CHANNEL_ID = "tmvpn_status"
        private const val NOTIF_ID = 1

        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_STATUS = "com.seyit474.tmvpn.VPN_STATUS"
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
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON) ?: run {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIF_ID, buildNotification("Baglaniyor..."))
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
        sendStatus(EVENT_CONNECTING)
        try {
            tproxyController?.stop()
            tproxyController = null
            try { coreController?.stopLoop() } catch (_: Exception) {}
            coreController = null
            tunInterface?.close()
            tunInterface = null

            val tun = Builder()
                .setSession("TM VPN")
                .addAddress("10.10.10.1", 32)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .setMtu(1500)
                .addDisallowedApplication(packageName)  // Xray'in kendi trafiği TUN'u bypass eder
                .establish() ?: run {
                    sendStatus(EVENT_ERROR, "TUN arayüzü kurulamadı (VPN izni gerekli)")
                    stopSelf()
                    return
                }
            tunInterface = tun
            Log.i(TAG, "TUN arayüzü kuruldu, fd=${tun.fd}")

            Seq.setContext(this)
            val service = this
            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long {
                    Log.i(TAG, "Xray status: $p1")
                    return 0
                }
                override fun shutdown(): Long {
                    Log.i(TAG, "Xray shutdown callback")
                    serviceScope.launch { stopVpn(); stopSelf() }
                    return 0
                }
                override fun startup(): Long {
                    Log.i(TAG, "Xray startup callback")
                    return 0
                }
            }

            val controller = Libv2ray.newCoreController(callback)
            controller.startLoop(configJson, XrayConfigBuilder.SOCKS_PORT)
            coreController = controller
            Log.i(TAG, "Xray core başlatıldı (SOCKS port: ${XrayConfigBuilder.SOCKS_PORT})")

            val tproxy = TProxyController(this, tun, XrayConfigBuilder.SOCKS_PORT)
            tproxy.start()
            tproxyController = tproxy
            Log.i(TAG, "hev-socks5-tunnel başlatıldı")

            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification("Bağlı ✓"))
            sendStatus(EVENT_CONNECTED)
            Log.i(TAG, "VPN bağlantısı kuruldu")

        } catch (e: Exception) {
            Log.e(TAG, "VPN başlatma hatası: ${e.message}", e)
            sendStatus(EVENT_ERROR, e.message ?: "Bilinmeyen hata")
            tproxyController?.stop()
            tproxyController = null
            tunInterface?.close()
            tunInterface = null
            stopSelf()
        }
    }

    private fun stopVpn() {
        try { tproxyController?.stop() } catch (_: Exception) {}
        tproxyController = null

        try { coreController?.stopLoop() } catch (_: Exception) {}
        coreController = null

        try { tunInterface?.close() } catch (_: Exception) {}
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
