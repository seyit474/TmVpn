package com.telo.vpn.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.telo.vpn.data.AppPreferences
import com.telo.vpn.ui.theme.TeloGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

@Composable
fun SplitTunnelingScreen(
    prefs: AppPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val splitApps by prefs.splitTunnelingApps.collectAsState(initial = emptySet())
    val splitMode by prefs.splitMode.collectAsState(initial = "bypass")

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { info ->
                    AppInfo(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = runCatching { pm.getApplicationIcon(info.packageName) }.getOrNull()
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Yzyna")
            }
            Text("Bölünmüş tünel", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // Mode selector
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Režim", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = splitMode == "bypass",
                        onClick = { scope.launch { prefs.setSplitMode("bypass") } },
                        colors = RadioButtonDefaults.colors(selectedColor = TeloGreen)
                    )
                    Text("Saýlananlar VPN'i atlasyn", fontSize = 13.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = splitMode == "allow",
                        onClick = { scope.launch { prefs.setSplitMode("allow") } },
                        colors = RadioButtonDefaults.colors(selectedColor = TeloGreen)
                    )
                    Text("Diňe saýlananlar VPN kullansyn", fontSize = 13.sp)
                }
            }
        }

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = { Text("Uygulama gözle...") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Android, contentDescription = null) }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TeloGreen)
                    Spacer(Modifier.height(8.dp))
                    Text("Uygulamalar ýüklenýär...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val filtered = if (searchQuery.isBlank()) apps
                           else apps.filter {
                               it.label.contains(searchQuery, ignoreCase = true) ||
                               it.packageName.contains(searchQuery, ignoreCase = true)
                           }

            val selectedCount = splitApps.size
            if (selectedCount > 0) {
                Text(
                    "$selectedCount saýlanan",
                    fontSize = 12.sp,
                    color = TeloGreen,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }

            LazyColumn {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in splitApps
                    AppRow(
                        app = app,
                        checked = checked,
                        onToggle = {
                            scope.launch {
                                val newSet = if (it) splitApps + app.packageName
                                             else splitApps - app.packageName
                                prefs.setSplitTunnelingApps(newSet)
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val iconBitmap by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { app.icon?.toBitmap(72, 72)?.asImageBitmap() }.getOrNull()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (iconBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = iconBitmap!!,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(Icons.Default.Android, contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
            Text(app.packageName, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = TeloGreen)
        )
    }
}
