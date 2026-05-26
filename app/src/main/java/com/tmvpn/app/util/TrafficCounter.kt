package com.tmvpn.app.util

import android.net.TrafficStats
import android.os.Process
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TrafficCounter {

    data class Stats(
        val connectedSeconds: Long = 0,
        val downloadSpeed:    Long = 0,
        val uploadSpeed:      Long = 0,
        val downloadBytes:    Long = 0,
        val uploadBytes:      Long = 0,
    )

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        stop()
        _stats.value = Stats()
        LogBus.log("TrafficCounter", "start() cagirildi, job baslatiliyor")
        job = scope.launch {
            val rawTotal = TrafficStats.getTotalRxBytes()
            val rawUid   = TrafficStats.getUidRxBytes(Process.myUid())
            LogBus.log("TrafficCounter", "getTotalRx=$rawTotal getUidRx=$rawUid")

            // prefer uid-based (tracks Xray outbound), fallback to total
            val useUid = rawUid >= 0
            LogBus.log("TrafficCounter", "mod=${if (useUid) "uid" else "total"}")

            val startRx = if (useUid) rawUid else rawTotal.coerceAtLeast(0)
            val startTx = if (useUid)
                TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0)
            else
                TrafficStats.getTotalTxBytes().coerceAtLeast(0)

            var prevRx  = startRx
            var prevTx  = startTx
            var seconds = 0L

            LogBus.log("TrafficCounter", "Basladi startRx=$startRx startTx=$startTx")

            while (isActive) {
                delay(1_000)
                seconds++
                val curRx = if (useUid)
                    TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(prevRx)
                else
                    TrafficStats.getTotalRxBytes().coerceAtLeast(prevRx)
                val curTx = if (useUid)
                    TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(prevTx)
                else
                    TrafficStats.getTotalTxBytes().coerceAtLeast(prevTx)
                _stats.value = Stats(
                    connectedSeconds = seconds,
                    downloadSpeed    = (curRx - prevRx).coerceAtLeast(0),
                    uploadSpeed      = (curTx - prevTx).coerceAtLeast(0),
                    downloadBytes    = (curRx - startRx).coerceAtLeast(0),
                    uploadBytes      = (curTx - startTx).coerceAtLeast(0),
                )
                prevRx = curRx
                prevTx = curTx
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _stats.value = Stats()
    }

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    fun formatSpeed(bytesPerSec: Long): String = when {
        bytesPerSec >= 1_000_000 -> "%.1f MB/s".format(bytesPerSec / 1_000_000.0)
        bytesPerSec >= 1_000     -> "%.1f KB/s".format(bytesPerSec / 1_000.0)
        else                     -> "$bytesPerSec B/s"
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000     -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000         -> "%.1f KB".format(bytes / 1_000.0)
        else                   -> "$bytes B"
    }
}
