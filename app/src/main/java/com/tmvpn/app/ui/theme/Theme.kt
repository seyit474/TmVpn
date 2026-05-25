package com.tmvpn.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary   = AccentBlue,
    background = DarkBackground,
    surface    = DarkSurface,
    onPrimary  = TextPrimary,
    onBackground = TextPrimary,
    onSurface  = TextPrimary,
)

@Composable
fun TmVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content     = content,
    )
}
