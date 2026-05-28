package com.telo.vpn.service

import android.net.TrafficStats
import com.telo.vpn.model.TrafficStats as VpnTrafficStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TrafficMonitor(private val uid: Int = android.os.Process.myUid()) {

    private var lastRx = 0L
    private var lastTx = 0L
    private var totalRx = 0L
    private var totalTx = 0L
    private var baseRx = 0L
    private var baseTx = 0L

    fun reset() {
        baseRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
        baseTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
        lastRx = baseRx
        lastTx = baseTx
        totalRx = 0
        totalTx = 0
    }

    fun statsFlow(intervalMs: Long = 1000L): Flow<VpnTrafficStats> = flow {
        reset()
        while (true) {
            delay(intervalMs)
            val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
            val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
            val dlSpeed = ((rx - lastRx) * 1000 / intervalMs).coerceAtLeast(0)
            val ulSpeed = ((tx - lastTx) * 1000 / intervalMs).coerceAtLeast(0)
            totalRx = rx - baseRx
            totalTx = tx - baseTx
            lastRx = rx
            lastTx = tx
            emit(VpnTrafficStats(ulSpeed, dlSpeed, totalTx, totalRx))
        }
    }
}
