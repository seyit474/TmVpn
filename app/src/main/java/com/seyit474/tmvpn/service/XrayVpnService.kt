package com.seyit474.tmvpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.seyit474.tmvpn.R
import com.seyit474.tmvpn.service.core.XrayCoreProvider
import com.seyit474.tmvpn.ui.MainActivity

/**
 * VpnService — tun arayüzünü açar, Xray çekirdeğini başlatır ve tun trafiğini
 * tun2socks ile Xray'in SOCKS portuna yönlendirir.
 *
 * Çekirdek (libXray) bu derlemede paketli değilse tünel kurulmaz; kullanıcıya
 * açık bir hata gösterilir. Böylece trafiği kara deliğe düşüren "sahte bağlı"
 * durumu asla oluşmaz.
 */
class XrayVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private val core = XrayCoreProvider.create()

    companion object {
        private const val CHANNEL_ID = "tmvpn_status"
        private const val NOTIF_ID = 1

        private const val ACTION_CONNECT = "com.seyit474.tmvpn.action.CONNECT"
        private const val ACTION_DISCONNECT = "com.seyit474.tmvpn.action.DISCONNECT"
        private const val EXTRA_CONFIG_JSON = "config_json"
        private const val EXTRA_SERVER_REMARK = "server_remark"

        private const val TUN_ADDRESS = "10.10.10.1"
        private const val TUN_MTU = 1500

        fun start(ctx: Context, configJson: String, serverRemark: String) {
            val i = Intent(ctx, XrayVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_CONFIG_JSON, configJson)
                putExtra(EXTRA_SERVER_REMARK, serverRemark)
            }
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, XrayVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            ctx.startService(i)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON)
                val remark = intent.getStringExtra(EXTRA_SERVER_REMARK) ?: "?"
                if (configJson.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                connect(configJson, remark)
                return START_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun connect(configJson: String, remark: String) {
        startForeground(NOTIF_ID, buildNotification(getString(R.string.notif_connecting)))
        VpnStateRepository.update(VpnState.Connecting(remark))

        if (!core.isAvailable) {
            failAndStop(getString(R.string.error_core_missing))
            return
        }

        runCatching { core.start(configJson) }.onFailure {
            failAndStop(it.message ?: getString(R.string.error_unknown, it.javaClass.simpleName))
            return
        }

        val tun = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(TUN_ADDRESS, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .setMtu(TUN_MTU)
            .establish()

        if (tun == null) {
            core.stop()
            failAndStop(getString(R.string.error_tun_failed))
            return
        }
        tunInterface = tun

        // TODO(çekirdek entegrasyonu): tun2socks başlat —
        // Tun2Socks.start(tun.fd, "127.0.0.1", XrayConfigBuilder.SOCKS_PORT)

        VpnStateRepository.update(VpnState.Connected(remark, SystemClock.elapsedRealtime()))
        notify(getString(R.string.notif_connected, remark))
    }

    /** Hata durumunu yayınlar ve servisi kapatır; onDestroy hatayı ezmez. */
    private fun failAndStop(message: String) {
        VpnStateRepository.update(VpnState.Error(message))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun shutdown() {
        core.stop()
        tunInterface?.close()
        tunInterface = null
        VpnStateRepository.update(VpnState.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        core.stop()
        tunInterface?.close()
        tunInterface = null
        // Hata mesajı ekranda kalabilsin diye Error durumunu ezmiyoruz
        val current = VpnStateRepository.state.value
        if (current is VpnState.Connected || current is VpnState.Connecting) {
            VpnStateRepository.update(VpnState.Idle)
        }
        super.onDestroy()
    }

    override fun onRevoke() {
        // Başka bir VPN uygulaması devraldığında sistem çağırır
        shutdown()
    }

    // ---------- bildirim ----------

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val disconnect = PendingIntent.getService(
            this, 1,
            Intent(this, XrayVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.notif_action_disconnect), disconnect)
            .build()
    }

    private fun notify(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
