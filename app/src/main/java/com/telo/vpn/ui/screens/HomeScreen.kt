package com.telo.vpn.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telo.vpn.api.SubscriptionUserInfo
import com.telo.vpn.model.ConnectionState
import com.telo.vpn.model.TrafficStats
import com.telo.vpn.ui.MainViewModel
import com.telo.vpn.ui.theme.*

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onConnectClick: () -> Unit,
    onDisconnect: () -> Unit
) {
    val connState by vm.connState.collectAsState()
    val isConnected by vm.isVpnConnected.collectAsState()
    val traffic by vm.trafficStats.collectAsState()
    val userInfo by vm.userInfo.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "telo VPN",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TeloGreen
            )
            if (connState is ConnectionState.Ready || connState is ConnectionState.Error) {
                IconButton(onClick = { vm.loadServers() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Täzele",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        UserInfoCard(userInfo)

        Spacer(Modifier.height(8.dp))

        StatusText(connState, isConnected)

        Spacer(Modifier.height(40.dp))

        ConnectButton(
            state = connState,
            isConnected = isConnected,
            onConnect = onConnectClick,
            onDisconnect = onDisconnect
        )

        Spacer(Modifier.height(32.dp))

        when {
            isConnected && connState is ConnectionState.Connected -> {
                val cfg = (connState as ConnectionState.Connected).server
                ConnectedServerCard(serverName = cfg.remark, protocol = cfg.protocol.name)
                Spacer(Modifier.height(16.dp))
                TrafficStatsCard(traffic)
            }
            connState is ConnectionState.Ready -> {
                val s = connState as ConnectionState.Ready
                SelectedServerCard(
                    serverName = s.selected.remark,
                    protocol = s.selected.protocol.name,
                    latency = s.servers.find { it.config.id == s.selected.id }?.latencyMs
                )
            }
            connState is ConnectionState.Error -> {
                ErrorCard((connState as ConnectionState.Error).message) {
                    vm.loadServers()
                }
            }
            connState is ConnectionState.Loading || connState is ConnectionState.Testing -> {
                LoadingCard(connState)
            }
            else -> {}
        }
    }
}

@Composable
private fun UserInfoCard(info: SubscriptionUserInfo) {
    if (info.daysLeft == -1 && info.isUnlimited) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (info.daysLeft == -1) "∞" else "${info.daysLeft}",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TeloGreen
                )
                Text("gün galdy", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            VerticalDivider(modifier = Modifier.height(40.dp),
                color = MaterialTheme.colorScheme.outline)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (info.isUnlimited) {
                    Text("∞", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TeloGreen)
                } else {
                    Text(
                        text = "%.1f GB".format(info.remainingGb),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = when {
                            info.remainingGb < 0.5 -> TeloError
                            info.remainingGb < 2.0 -> TeloWarning
                            else -> TeloGreen
                        }
                    )
                }
                Text("galan maglumat", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            VerticalDivider(modifier = Modifier.height(40.dp),
                color = MaterialTheme.colorScheme.outline)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatBytes(info.usedBytes),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Text("ulanylan", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (info.isExpired) {
            Text(
                "Aboneligiňiziň möhleti doldy",
                color = TeloError,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun StatusText(state: ConnectionState, isConnected: Boolean) {
    val (text, color) = when {
        isConnected -> "Birikdirildi" to TeloGreen
        state is ConnectionState.Connecting -> "Birikdirilýär..." to TeloWarning
        state is ConnectionState.Loading -> "Serverler ýüklenýär..." to TeloOnSurfaceVariant
        state is ConnectionState.Testing -> "Tizlikler ölçülýär..." to TeloOnSurfaceVariant
        state is ConnectionState.Ready -> {
            val s = state as ConnectionState.Ready
            "Taýyn — ${s.selected.remark}" to TeloOnSurfaceVariant
        }
        state is ConnectionState.Error -> "Ýalňyşlyk" to TeloError
        else -> "Birikdirilmedi" to TeloOnSurfaceVariant
    }
    Text(text = text, color = color, fontSize = 14.sp)
}

@Composable
private fun ConnectButton(
    state: ConnectionState,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isAnimating = state is ConnectionState.Connecting ||
        state is ConnectionState.Loading ||
        state is ConnectionState.Testing

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (isAnimating) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val btnColor by animateColorAsState(
        targetValue = when {
            isConnected -> TeloGreen
            state is ConnectionState.Connecting -> TeloWarning
            state is ConnectionState.Ready -> Color(0xFF1F6B4A)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(500),
        label = "btnColor"
    )

    val enabled = state is ConnectionState.Ready || isConnected

    Box(
        modifier = Modifier
            .size(180.dp)
            .scale(scale)
            .clip(CircleShape)
            .border(
                width = 3.dp,
                color = if (isConnected) TeloGreen else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .background(btnColor)
            .then(
                if (enabled) Modifier.clickable {
                    if (isConnected) onDisconnect() else onConnect()
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isAnimating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = TeloGreen,
                    strokeWidth = 3.dp
                )
            } else {
                val label = when {
                    isConnected -> "KES"
                    state is ConnectionState.Ready -> "BIRIKDIR"
                    else -> "..."
                }
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ConnectedServerCard(serverName: String, protocol: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(TeloGreen)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(serverName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(protocol, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SelectedServerCard(serverName: String, protocol: String, latency: Long?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Saýlanan server", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Text(serverName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(protocol, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            latency?.let {
                Text(
                    text = "${it}ms",
                    color = latencyColor(it, true),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun TrafficStatsCard(stats: TrafficStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TrafficItem("⬆", stats.uploadBps, stats.totalUpload)
            VerticalDivider(
                modifier = Modifier.height(40.dp),
                color = MaterialTheme.colorScheme.outline
            )
            TrafficItem("⬇", stats.downloadBps, stats.totalDownload)
        }
    }
}

@Composable
private fun TrafficItem(arrow: String, speed: Long, total: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(arrow, fontSize = 16.sp)
        Text(
            text = formatSpeed(speed),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = TeloGreen
        )
        Text(
            text = formatBytes(total),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null,
                tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("Gaýtala") }
        }
    }
}

@Composable
private fun LoadingCard(state: ConnectionState) {
    val text = if (state is ConnectionState.Loading) "Serverler ýüklenýär..."
               else "Tizlikler ölçülýär..."
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                color = TeloGreen)
            Spacer(Modifier.width(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun latencyColor(ms: Long, reachable: Boolean): Color = when {
    !reachable -> TeloError
    ms < 150 -> TeloGreen
    ms < 400 -> TeloWarning
    else -> TeloError
}

private fun formatSpeed(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000f)
    bps >= 1_000 -> "%.1f KB/s".format(bps / 1_000f)
    else -> "$bps B/s"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824f)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576f)
    bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024f)
    else -> "$bytes B"
}
