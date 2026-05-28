package com.telo.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telo.vpn.model.ConnectionState
import com.telo.vpn.model.PingedServer
import com.telo.vpn.model.ServerConfig
import com.telo.vpn.ui.MainViewModel
import com.telo.vpn.ui.theme.TeloError
import com.telo.vpn.ui.theme.TeloGreen
import com.telo.vpn.ui.theme.TeloWarning

@Composable
fun ServerListScreen(vm: MainViewModel) {
    val connState by vm.connState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Serverler", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { vm.loadServers() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Täzele")
            }
        }

        when (val s = connState) {
            is ConnectionState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TeloGreen)
                        Spacer(Modifier.height(12.dp))
                        Text("Serverler ýüklenýär...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            is ConnectionState.Testing -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TeloGreen)
                        Spacer(Modifier.height(12.dp))
                        Text("Tizlikler ölçülýär...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            is ConnectionState.Ready -> {
                val selectedId = s.selected.id
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.servers, key = { it.config.id }) { pinged ->
                        ServerRow(
                            pinged = pinged,
                            selected = pinged.config.id == selectedId,
                            onClick = { vm.selectServer(pinged.config) }
                        )
                    }
                }
            }
            is ConnectionState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(s.message, color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.loadServers() }) { Text("Gaýtala") }
                    }
                }
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Server sanawy ýüklenmedik",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    pinged: PingedServer,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.surfaceVariant

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(latencyColor(pinged.latencyMs, pinged.isReachable))
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(pinged.config.remark, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    "${pinged.config.protocol.name} • ${pinged.config.address}:${pinged.config.port}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = TeloGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }

            Text(
                text = if (pinged.isReachable) "${pinged.latencyMs}ms" else "—",
                color = latencyColor(pinged.latencyMs, pinged.isReachable),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

private fun latencyColor(ms: Long, reachable: Boolean): Color = when {
    !reachable -> TeloError
    ms < 150 -> TeloGreen
    ms < 400 -> TeloWarning
    else -> TeloError
}
