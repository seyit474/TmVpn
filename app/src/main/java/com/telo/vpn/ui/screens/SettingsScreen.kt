package com.telo.vpn.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telo.vpn.data.AppPreferences
import com.telo.vpn.ui.MainViewModel
import com.telo.vpn.ui.theme.TeloError
import com.telo.vpn.ui.theme.TeloGreen

@Composable
fun SettingsScreen(vm: MainViewModel, prefs: AppPreferences) {
    val killSwitch by prefs.killSwitch.collectAsState(initial = false)
    val autoConnect by prefs.autoConnect.collectAsState(initial = false)
    val panelUrl by prefs.panelUrl.collectAsState(initial = "")
    val username by prefs.username.collectAsState(initial = "")

    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Ayarlar", fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))

        // Hesap bilgisi
        SectionHeader("Hesap")
        if (panelUrl.isNotEmpty()) {
            InfoRow(
                icon = { Icon(Icons.Default.Language, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                title = "Panel",
                value = panelUrl
            )
            InfoRow(
                icon = { Icon(Icons.Default.Person, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                title = "Kullanıcı",
                value = username
            )
        }

        Spacer(Modifier.height(8.dp))

        // VPN Ayarları
        SectionHeader("VPN")

        ToggleRow(
            icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = TeloGreen) },
            title = "Kill Switch",
            subtitle = "VPN kesildiğinde internet bağlantısını kes",
            checked = killSwitch,
            onCheckedChange = { vm.setKillSwitch(it) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        ToggleRow(
            icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Önyüklemede Bağlan",
            subtitle = "Cihaz açıldığında otomatik VPN bağlantısı kur",
            checked = autoConnect,
            onCheckedChange = { vm.setAutoConnect(it) }
        )

        Spacer(Modifier.height(24.dp))

        // Hakkında
        SectionHeader("Uygulama")
        InfoRow(
            icon = { Icon(Icons.Default.Info, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Versiyon",
            value = "1.0.0"
        )
        InfoRow(
            icon = { Icon(Icons.Default.Security, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Protokol",
            value = "VLESS / VMess / Shadowsocks"
        )
        InfoRow(
            icon = { Icon(Icons.Default.Code, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Altyapı",
            value = "Xray-core + Marzban"
        )

        Spacer(Modifier.weight(1f))

        // Çıkış butonu
        OutlinedButton(
            onClick = { showLogoutDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TeloError)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Oturumu Kapat")
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Oturumu Kapat") },
            text = { Text("Marzban oturumunuz kapatılacak ve sunucu listesi temizlenecek.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        vm.logout()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TeloError)
                ) { Text("Kapat") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("İptal") }
            }
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
