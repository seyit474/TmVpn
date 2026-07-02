package com.seyit474.tmvpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyit474.tmvpn.R
import com.seyit474.tmvpn.model.ServerConfig
import com.seyit474.tmvpn.ping.ServerPinger
import com.seyit474.tmvpn.service.VpnState
import com.seyit474.tmvpn.ui.theme.Amber
import com.seyit474.tmvpn.ui.theme.Crimson
import com.seyit474.tmvpn.ui.theme.Emerald
import com.seyit474.tmvpn.ui.theme.NavySurfaceHigh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: VpnViewModel.UiState,
    onRefresh: () -> Unit,
    onSelectServer: (ServerConfig) -> Unit,
    onToggleConnection: () -> Unit,
    onErrorConsumed: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    state.error?.let { error ->
        val message = errorMessage(error)
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message)
            onErrorConsumed()
        }
    }

    // Servis kaynaklı hatalar (ör. çekirdek eksik) zaten yerelleştirilmiş gelir
    (state.vpnState as? VpnState.Error)?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(error.message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = Emerald
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    RefreshAction(
                        isRefreshing = state.isRefreshing,
                        enabled = !state.isRefreshing && !state.isConnectedOrConnecting,
                        onClick = onRefresh
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            ConnectButton(
                state = state,
                onClick = onToggleConnection
            )

            Spacer(Modifier.height(20.dp))

            Text(
                statusLabel(state),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(24.dp))

            if (state.servers.isNotEmpty()) {
                ServerList(
                    results = state.servers,
                    selectedId = state.selectedId,
                    selectionEnabled = !state.isConnectedOrConnecting,
                    onSelect = onSelectServer
                )
            }
        }
    }
}

@Composable
private fun RefreshAction(isRefreshing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "refresh")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "refreshAngle"
    )
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = stringResource(R.string.action_refresh),
            modifier = if (isRefreshing) Modifier.rotate(angle) else Modifier
        )
    }
}

@Composable
private fun ConnectButton(state: VpnViewModel.UiState, onClick: () -> Unit) {
    val vpn = state.vpnState
    val isConnecting = vpn is VpnState.Connecting
    val isConnected = vpn is VpnState.Connected
    val canConnect = !state.isRefreshing && state.selectedServer != null &&
        (vpn is VpnState.Idle || vpn is VpnState.Error)
    val enabled = canConnect || isConnected || isConnecting

    val targetColor = when {
        isConnected -> Emerald
        isConnecting -> Amber
        canConnect -> Emerald
        else -> NavySurfaceHigh
    }
    val color by animateColorAsState(targetColor, tween(300), label = "connectColor")

    // Bağlanırken nabız animasyonu
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseScale"
    )
    val scale = if (isConnecting) pulse else 1f

    val label = when {
        isConnected -> stringResource(R.string.action_disconnect)
        isConnecting -> stringResource(R.string.status_connecting)
        else -> stringResource(R.string.action_connect)
    }

    Box(
        modifier = Modifier
            .size(190.dp)
            .scale(scale)
            .clip(CircleShape)
            .border(6.dp, color.copy(alpha = 0.25f), CircleShape)
            .padding(12.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.isRefreshing) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ServerList(
    results: List<ServerPinger.Result>,
    selectedId: String?,
    selectionEnabled: Boolean,
    onSelect: (ServerConfig) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Text(
            stringResource(R.string.servers_header, results.size),
            fontWeight = FontWeight.SemiBold
        )
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(results, key = { it.config.id }) { result ->
            ServerRow(
                result = result,
                selected = result.config.id == selectedId,
                enabled = selectionEnabled && result.isReachable,
                onClick = { onSelect(result.config) }
            )
        }
    }
}

@Composable
private fun ServerRow(
    result: ServerPinger.Result,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(result.config.remark, fontWeight = FontWeight.Medium)
                Text(
                    "${result.config.protocol.name} • ${result.config.address}:${result.config.port}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (result.isReachable) {
                    stringResource(R.string.latency_ms, result.latencyMs)
                } else {
                    stringResource(R.string.latency_unreachable)
                },
                color = latencyColor(result.latencyMs, result.isReachable),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun latencyColor(ms: Long, reachable: Boolean): Color = when {
    !reachable -> Crimson
    ms < 150 -> Emerald
    ms < 400 -> Amber
    else -> Crimson
}

@Composable
private fun statusLabel(state: VpnViewModel.UiState): String = when (val vpn = state.vpnState) {
    is VpnState.Connecting -> stringResource(R.string.status_connecting)
    is VpnState.Connected -> stringResource(R.string.status_connected, vpn.serverRemark)
    else -> when {
        state.refreshPhase == VpnViewModel.RefreshPhase.FETCHING ->
            stringResource(R.string.status_fetching)
        state.refreshPhase == VpnViewModel.RefreshPhase.TESTING ->
            stringResource(R.string.status_testing)
        state.selectedServer != null ->
            stringResource(R.string.status_ready, state.selectedServer!!.remark)
        state.servers.isEmpty() -> stringResource(R.string.status_idle)
        else -> stringResource(R.string.status_no_selection)
    }
}

@Composable
private fun errorMessage(error: VpnViewModel.UiError): String = when (error) {
    VpnViewModel.UiError.MissingUrl -> stringResource(R.string.error_missing_url)
    VpnViewModel.UiError.NoServers -> stringResource(R.string.error_no_servers)
    VpnViewModel.UiError.NoReachableServer -> stringResource(R.string.error_no_reachable)
    VpnViewModel.UiError.Network -> stringResource(R.string.error_network)
    VpnViewModel.UiError.Timeout -> stringResource(R.string.error_timeout)
    is VpnViewModel.UiError.Http -> stringResource(R.string.error_http, error.code)
    is VpnViewModel.UiError.Unknown -> stringResource(R.string.error_unknown, error.detail)
}
