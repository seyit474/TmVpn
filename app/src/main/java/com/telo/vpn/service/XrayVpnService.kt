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
import com.telo.vpn.model.ServerConfig
import com.telo.vpn.model.TrafficStats
import com.telo.vpn.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
            val i = Intent(ctx, XrayVpnService::class.java).apply {
                putExtra(EXTRA_CONFIG_JSON, configJson)
                putExtra(EXTRA_SERVER_NAME, serverName)
                putExtra(EXTRA_KILL_SWITCH, killSwitch)
            }
            ctx.startForegroundService(i)
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
    private var tun2socksProcess: Process? = null
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
                startVpn(configJson, killSwitch)
                isConnected.value = true
                updateNotification(serverName, "Bağlandı")
                collectTraffic()
            } catch (e: Exception) {
                Log.e(TAG, "VPN başlatma hatası: ${e.message}", e)
                isConnected.value = false
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startVpn(configJson: String, killSwitch: Boolean) {
        // 1. Xray başlat
        xrayEngine = createXrayEngine(this)
        if (!xrayEngine.start(configJson)) error("Xray başlatılamadı")

        // 2. Tun arayüzü kur
        val builder = Builder()
            .setSession("Telo VPN")
            .addAddress("10.10.10.1", 32)
            .addRoute("0.0.0.0", 0)           // tüm IPv4
            .addRoute("::", 0)                 // tüm IPv6
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setMtu(1500)
            .setBlocking(false)
            .allowFamily(android.system.OsConstants.AF_INET)
            .allowFamily(android.system.OsConstants.AF_INET6)

        if (killSwitch) {
            builder.setUnderlyingNetworks(null) // Kill switch: sadece tun arayüzü
        }

        tunInterface = builder.establish() ?: error("Tun arayüzü oluşturulamadı")

        // 3. tun2socks başlat (hev-socks5-tunnel .so gereklidir)
        //    so dosyaları app/src/main/jniLibs/{abi}/ altına yerleştirilmeli
        startTun2Socks()

        Log.i(TAG, "VPN tüneli kuruldu, fd=${tunInterface?.fd}")
    }

    private fun startTun2Socks() {
        // hev-socks5-tunnel kullanımı:
        // libhev-socks5-tunnel.so → JNI üzerinden çağrılır veya subprocess olarak çalıştırılır.
        // Bu stub implementation; gerçek .so dosyaları eklendiğinde aşağıdaki şekilde entegre edilir:
        //
        // val lib = System.loadLibrary("hev-socks5-tunnel")
        // Tun2Socks.start(tunInterface!!.fd, "127.0.0.1", XrayConfigBuilder.SOCKS_PORT)
        //
        // Alternatif: badvpn-tun2socks subprocess
        // val tun2socksPath = extractNativeBinary("libtun2socks.so")
        // tun2socksProcess = ProcessBuilder(
        //     tun2socksPath,
        //     "--tunfd=${tunInterface!!.fd}",
        //     "--netif-ipaddr=10.10.10.2",
        //     "--netif-netmask=255.255.255.0",
        //     "--socks-server-addr=127.0.0.1:${XrayConfigBuilder.SOCKS_PORT}",
        //     "--udpgw-remote-server-addr=127.0.0.1:7300"
        // ).start()
        Log.w(TAG, "tun2socks: gerçek .so eklendiğinde etkinleştirilecek")
    }

    private suspend fun collectTraffic() {
        trafficMonitor.statsFlow().collect { stats ->
            trafficStats.value = stats
        }
    }

    override fun onRevoke() {
        // Kill switch tetiklendiğinde (başka VPN devreye girdiğinde)
        Log.w(TAG, "VPN erişimi iptal edildi (onRevoke)")
        isConnected.value = false
        cleanup()
        super.onRevoke()
    }

    override fun onDestroy() {
        isConnected.value = false
        cleanup()
        scope.cancel()
        super.onDestroy()
    }

    private fun cleanup() {
        runCatching { tun2socksProcess?.destroy() }
        runCatching { if (::xrayEngine.isInitialized) xrayEngine.stop() }
        runCatching { tunInterface?.close() }
        tunInterface = null
        tun2socksProcess = null
        trafficStats.value = TrafficStats()
    }

    private fun buildNotification(serverName: String, status: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "VPN Durumu", NotificationManager.IMPORTANCE_LOW)
        )
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
            .setSmallIcon(android.R.drawable.stat_sys_vpn_ic)
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
