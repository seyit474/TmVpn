package com.seyit474.tmvpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    primary             = ColorBlue,
    secondary           = ColorGreen,
    background          = ColorBackground,
    surface             = ColorSurface,
    surfaceVariant      = ColorCard,
    onPrimary           = Color.White,
    onSecondary         = Color.White,
    onBackground        = ColorTextPrimary,
    onSurface           = ColorTextPrimary,
    onSurfaceVariant    = ColorTextSecondary,
    error               = ColorRed,
    primaryContainer    = ColorBlueDeep,
    onPrimaryContainer  = ColorBlue,
)

@Composable
fun TmVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content     = content,
    )
}
