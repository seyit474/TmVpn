package com.telo.vpn.api

data class SubscriptionUserInfo(
    val upload: Long = 0L,
    val download: Long = 0L,
    val total: Long = 0L,
    val expire: Long = 0L
) {
    val usedBytes get() = upload + download
    val remainingBytes get() = if (total == 0L) Long.MAX_VALUE else (total - usedBytes).coerceAtLeast(0)
    val remainingGb get() = remainingBytes / 1_073_741_824.0
    val totalGb get() = if (total == 0L) -1.0 else total / 1_073_741_824.0

    val daysLeft: Int get() = when {
        expire == 0L -> -1
        else -> ((expire - System.currentTimeMillis() / 1000) / 86400).toInt().coerceAtLeast(0)
    }

    val isExpired: Boolean get() = expire != 0L && expire < System.currentTimeMillis() / 1000
    val isUnlimited: Boolean get() = total == 0L
}

data class SubscriptionResult(
    val userInfo: SubscriptionUserInfo,
    val rawLinks: String
)
