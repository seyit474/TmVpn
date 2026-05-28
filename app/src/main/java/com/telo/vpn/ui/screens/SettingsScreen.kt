package com.telo.vpn.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telo.vpn.data.AppPreferences
import com.telo.vpn.ui.MainViewModel
import com.telo.vpn.ui.theme.TeloGreen

@Composable
fun SettingsScreen(vm: MainViewModel, prefs: AppPreferences) {
    val killSwitch by prefs.killSwitch.collectAsState(initial = false)
    val autoConnect by prefs.autoConnect.collectAsState(initial = false)
    val clipboard = LocalClipboardManager.current
    var hwidCopied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Sazlamalar", fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        SectionHeader("Enjam")

        // HWID kartı
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = TeloGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Enjam HWID", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        vm.hwid,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(vm.hwid))
                    hwidCopied = true
                }) {
                    Icon(
                        if (hwidCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Kopyala",
                        tint = if (hwidCopied) TeloGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (hwidCopied) {
            Text(
                "Göçürildi — Marzban-da bu HWID bilen ulanyjy dörediň",
                fontSize = 11.sp,
                color = TeloGreen,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        SectionHeader("VPN")

        ToggleRow(
            icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = TeloGreen) },
            title = "Kill Switch",
            subtitle = "VPN kesilende internet birikdirmesini kes",
            checked = killSwitch,
            onCheckedChange = { vm.setKillSwitch(it) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        ToggleRow(
            icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Açylanda birikdir",
            subtitle = "Enjam açylanda awtomatik VPN birikdirmesini gur",
            checked = autoConnect,
            onCheckedChange = { vm.setAutoConnect(it) }
        )

        Spacer(Modifier.height(24.dp))
        SectionHeader("Goşundy")

        InfoRow(
            icon = { Icon(Icons.Default.Info, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Wersiýa",
            value = "2.0.0"
        )
        InfoRow(
            icon = { Icon(Icons.Default.Security, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Protokol",
            value = "VLESS / VMess / Shadowsocks / Trojan"
        )
        InfoRow(
            icon = { Icon(Icons.Default.Code, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Binýat",
            value = "Xray-core + Marzban"
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun ToggleRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TeloGreen,
                checkedTrackColor = TeloGreen.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun InfoRow(
    icon: @Composable () -> Unit,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
