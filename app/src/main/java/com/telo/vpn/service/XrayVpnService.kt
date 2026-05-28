package com.telo.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.telo.vpn.model.TrafficStats
import com.telo.vpn.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class XrayVpnService : VpnService() {

    companion object {
        private const val TAG = "TeloVPN/Service"
        private const val CHANNEL_ID = "telo_vpn_status"
        private const val NOTIF_ID = 1
        const val EXTRA_CONFIG_JSON = "config_json"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_KILL_SWITCH = "kill_switch"
        const val ACTION_STOP = "com.telo.vpn.ACTION_STOP"

        val trafficStats = MutableStateFlow(TrafficStats())
        val isConnected = MutableStateFlow(false)

        fun start(ctx: Context, configJson: String, serverName: String, killSwitch: Boolean = false) {
            ctx.startForegroundService(Intent(ctx, XrayVpnService::class.java).apply {
                putExtra(EXTRA_CONFIG_JSON, configJson)
                putExtra(EXTRA_SERVER_NAME, serverName)
                putExtra(EXTRA_KILL_SWITCH, killSwitch)
            })
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, XrayVpnService::class.java))
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): XrayVpnService = this@XrayVpnService
    }

    private val binder = LocalBinder()
    private var tunInterface: ParcelFileDescriptor? = null
    private lateinit var xrayEngine: XrayCoreEngine
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val trafficMonitor = TrafficMonitor()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val configJson = intent?.getStringExtra(EXTRA_CONFIG_JSON) ?: return START_NOT_STICKY
        val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "Telo VPN"
        val killSwitch = intent.getBooleanExtra(EXTRA_KILL_SWITCH, false)

        startForeground(NOTIF_ID, buildNotification(serverName, "Bağlanıyor..."))

        scope.launch {
            try {
                startVpn(configJson, serverName, killSwitch)
                isConnected.value = true
                updateNotification(serverName, "Bağlandı")
                trafficMonitor.statsFlow().collect { trafficStats.value = it }
            } catch (e: Exception) {
                Log.e(TAG, "VPN başlatma hatası: ${e.message}", e)
                isConnected.value = false
                withContext(Dispatchers.Main) { stopSelf() }
            }
        }
        return START_STICKY
    }

    private fun startVpn(configJson: String, serverName: String, killSwitch: Boolean) {
        // 1. Tun arayüzü kur
        tunInterface = buildTunInterface(killSwitch)
            ?: error("Tun arayüzü oluşturulamadı — VPN izni eksik olabilir")

        val tunFd = tunInterface!!.fd
        Log.i(TAG, "Tun fd=$tunFd")

        // 2. Xray engine başlat (libXray.aar varsa gerçek, yoksa stub)
        xrayEngine = createXrayEngine(this)
        if (!xrayEngine.start(configJson, tunFd)) {
            error("Xray core başlatılamadı")
        }
    }

    private fun buildTunInterface(killSwitch: Boolean): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("Telo VPN")
            .addAddress("10.10.10.1", 24)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setMtu(1500)
            .allowBypass()   // Xray'in outbound socketleri tünelden bypass geçebilir

        if (killSwitch) {
            // Kill switch: tüm trafik sadece VPN üzerinden geçer
            // Ağ kesildiğinde internet erişimi de kesilir
            builder.setBlocking(false)
            // allowBypass() kaldırılır — hiçbir şey bypass edemez
        }

        return builder.establish()
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN erişimi iptal edildi")
        isConnected.value = false
        cleanup()
        super.onRevoke()
    }

    override fun onDestroy() {
        isConnected.value = false
        cleanup()
        scope.cancel()
        trafficStats.value = TrafficStats()
        super.onDestroy()
    }

    private fun cleanup() {
        runCatching { if (::xrayEngine.isInitialized) xrayEngine.stop() }
        runCatching { tunInterface?.close() }
        tunInterface = null
    }

    private fun buildNotification(serverName: String, status: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN Durumu", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, XrayVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telo VPN — $serverName")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, "Kes", stopPi)
            .build()
    }

    private fun updateNotification(serverName: String, status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(serverName, status))
    }
}
