package com.seyit474.tmvpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Marka paleti
val Emerald = Color(0xFF10B981)
val EmeraldDim = Color(0xFF065F46)
val Amber = Color(0xFFF59E0B)
val Crimson = Color(0xFFEF4444)
val Navy = Color(0xFF0F172A)
val NavySurface = Color(0xFF1E293B)
val NavySurfaceHigh = Color(0xFF334155)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)

private val TmVpnColorScheme = darkColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = EmeraldDim,
    onPrimaryContainer = TextPrimary,
    secondary = Amber,
    onSecondary = Navy,
    background = Navy,
    onBackground = TextPrimary,
    surface = Navy,
    onSurface = TextPrimary,
    surfaceVariant = NavySurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = NavySurfaceHigh,
    error = Crimson,
    onError = Color.White,
    outline = NavySurfaceHigh
)

/** Uygulama koyu temalı tasarlandı; sistem temasından bağımsız tek şema kullanılır. */
@Composable
fun TmVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TmVpnColorScheme,
        content = content
    )
}
