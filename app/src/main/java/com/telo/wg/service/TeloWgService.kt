package com.telo.wg.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import androidx.core.app.NotificationCompat
import com.telo.wg.data.AppPreferences
import com.telo.wg.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.zaneschepke.amneziawg.backend.GoBackend
import com.zaneschepke.amneziawg.backend.Tunnel
import com.zaneschepke.amneziawg.backend.TunnelActionHandler
import com.zaneschepke.amneziawg.config.Config

class TeloWgService : VpnService() {

    companion object {
        const val ACTION_START = "com.telo.wg.START"
        const val ACTION_STOP = "com.telo.wg.STOP"
        private const val CHANNEL_ID = "telo_vpn_channel"
        private const val NOTIF_ID = 1

        private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
        val connectionState: StateFlow<ConnectionState> = _connectionState

        private val _rxBytes = MutableStateFlow(0L)
        val rxBytes: StateFlow<Long> = _rxBytes

        private val _txBytes = MutableStateFlow(0L)
        val txBytes: StateFlow<Long> = _txBytes
    }

    enum class ConnectionState { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }

    private var backend: GoBackend? = null
    private var activeTunnel: Tunnel? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var statsJob: Job? = null

    private val noOpHandler = object : TunnelActionHandler {
        override fun runPreUp(scripts: Collection<String>) {}
        override fun runPostUp(scripts: Collection<String>) {}
        override fun runPreDown(scripts: Collection<String>) {}
        override fun runPostDown(scripts: Collection<String>) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        return when (intent?.action) {
            ACTION_START -> { scope.launch { startVpn() }; START_STICKY }
            ACTION_STOP -> { scope.launch { stopVpn() }; START_NOT_STICKY }
            else -> START_NOT_STICKY
        }
    }

    private suspend fun startVpn() {
        try {
            _connectionState.value = ConnectionState.CONNECTING
            startForeground(NOTIF_ID, buildNotification("Birikdirilýär..."))

            val prefs = AppPreferences(applicationContext)
            val dns = prefs.customDns.first()
            val savedTunnel = prefs.activeTunnel.first()

            val configString = savedTunnel?.configText?.let { applyDnsOverride(it, dns) }
                ?: buildFallbackConfigString(dns)

            val config = Config.parse(configString.byteInputStream())

            // Pass `this` (VpnService) as the Context so GoBackend can use VpnService.Builder
            val be = GoBackend(this@TeloWgService, noOpHandler)
            backend = be

            val tun = object : Tunnel {
                override fun getName(): String = "telo0"
                override fun onStateChange(newState: Tunnel.State) {
                    _connectionState.value = when (newState) {
                        Tunnel.State.UP -> ConnectionState.CONNECTED
                        Tunnel.State.DOWN -> ConnectionState.DISCONNECTED
                    }
                    updateNotification()
                }
                override fun isIpv4ResolutionPreferred(): Boolean = false
                override fun isMetered(): Boolean = false
            }
            activeTunnel = tun

            be.setState(tun, Tunnel.State.UP, config)
            startStatsCollection(be, tun)

        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            updateNotification()
            stopSelf()
        }
    }

    private suspend fun stopVpn() {
        statsJob?.cancel()
        statsJob = null
        try {
            val t = activeTunnel
            val be = backend
            if (t != null && be != null) {
                be.setState(t, Tunnel.State.DOWN, null)
            }
        } catch (_: Exception) {}
        _connectionState.value = ConnectionState.DISCONNECTED
        _rxBytes.value = 0L
        _txBytes.value = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startStatsCollection(be: GoBackend, tun: Tunnel) {
        var prevRx = 0L
        var prevTx = 0L
        statsJob = scope.launch {
            while (true) {
                try {
                    val stats = be.getStatistics(tun)
                    val rx = stats.totalRx()
                    val tx = stats.totalTx()
                    _rxBytes.value = (rx - prevRx).coerceAtLeast(0L)
                    _txBytes.value = (tx - prevTx).coerceAtLeast(0L)
                    prevRx = rx
                    prevTx = tx
                } catch (_: Exception) {}
                delay(1000L)
            }
        }
    }

    private fun applyDnsOverride(configText: String, dns: String): String {
        val hasDns = configText.lines().any { it.trimStart().startsWith("DNS", ignoreCase = true) }
        return if (hasDns) {
            configText.replace(Regex("(?m)^DNS\\s*=.*$"), "DNS = $dns")
        } else {
            configText.replace(Regex("(?m)^(\\[Peer\\])"), "DNS = $dns\n\n[Peer]")
        }
    }

    private fun buildFallbackConfigString(dns: String): String = """
[Interface]
PrivateKey = PLACEHOLDER_PRIVATE_KEY_BASE64==
Address = 10.0.0.2/32
DNS = $dns
Jc = 4
Jmin = 40
Jmax = 70
S1 = 0
S2 = 0
H1 = 1
H2 = 2
H3 = 3
H4 = 4

[Peer]
PublicKey = PLACEHOLDER_PUBLIC_KEY_BASE64==
Endpoint = PLACEHOLDER_SERVER_IP:51820
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
""".trimIndent()

    private fun buildNotification(text: String): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TeloWG")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val text = when (_connectionState.value) {
            ConnectionState.CONNECTED -> "Birikdirildi"
            ConnectionState.CONNECTING -> "Birikdirilýär..."
            ConnectionState.DISCONNECTED, ConnectionState.IDLE -> "Birikdirilmeýär"
            ConnectionState.ERROR -> "Ýalňyşlyk"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "TeloWG VPN",
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
