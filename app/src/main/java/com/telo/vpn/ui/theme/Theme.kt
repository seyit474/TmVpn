package com.telo.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Telo VPN marka renkleri
val TeloGreen = Color(0xFF00E5A0)
val TeloGreenDark = Color(0xFF00B37A)
val TeloBackground = Color(0xFF0D1117)
val TeloSurface = Color(0xFF161B22)
val TeloSurfaceVariant = Color(0xFF21262D)
val TeloOnSurface = Color(0xFFE6EDF3)
val TeloOnSurfaceVariant = Color(0xFF8B949E)
val TeloError = Color(0xFFFF6B6B)
val TeloWarning = Color(0xFFFFB347)

val TeloColorScheme = darkColorScheme(
    primary = TeloGreen,
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF00522F),
    onPrimaryContainer = TeloGreen,
    secondary = TeloGreenDark,
    background = TeloBackground,
    surface = TeloSurface,
    surfaceVariant = TeloSurfaceVariant,
    onSurface = TeloOnSurface,
    onSurfaceVariant = TeloOnSurfaceVariant,
    error = TeloError,
    outline = Color(0xFF30363D)
)

@Composable
fun TeloVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TeloColorScheme,
        content = content
    )
}
