package com.telo.vpn.api

/**
 * Marzban subscription response header'ından parse edilen kullanıcı bilgisi.
 * Header formatı: "upload=0; download=1234567; total=10737418240; expire=1735689600"
 */
data class SubscriptionUserInfo(
    val upload: Long = 0L,
    val download: Long = 0L,
    val total: Long = 0L,       // 0 = sınırsız
    val expire: Long = 0L       // Unix timestamp, 0 = süresi yok
) {
    val usedBytes get() = upload + download
    val remainingBytes get() = if (total == 0L) Long.MAX_VALUE else (total - usedBytes).coerceAtLeast(0)
    val remainingGb get() = remainingBytes / 1_073_741_824.0
    val totalGb get() = if (total == 0L) -1.0 else total / 1_073_741_824.0

    /** Kaç gün kaldı, -1 ise sınırsız */
    val daysLeft: Int get() = when {
        expire == 0L -> -1
        else -> ((expire - System.currentTimeMillis() / 1000) / 86400).toInt().coerceAtLeast(0)
    }

    val isExpired: Boolean get() = expire != 0L && expire < System.currentTimeMillis() / 1000
    val isUnlimited: Boolean get() = total == 0L
}

data class SubscriptionResult(
    val userInfo: SubscriptionUserInfo,
    val rawLinks: String      // base64 veya düz link listesi
)
