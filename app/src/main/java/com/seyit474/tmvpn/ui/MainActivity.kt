package com.seyit474.tmvpn.ui

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seyit474.tmvpn.model.AppSettings
import com.seyit474.tmvpn.model.ServerConfig
import com.seyit474.tmvpn.ping.ServerPinger
import com.seyit474.tmvpn.ui.theme.*

class MainActivity : ComponentActivity() {
    private val vm: VpnViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) vm.connectVpn(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TmVpnTheme {
                AppRoot(
                    vm          = vm,
                    onConnect   = { requestVpnPermission() },
                    onDisconnect = { vm.disconnectVpn(this) },
                )
            }
        }
        vm.refreshAndPickFastest()
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermissionLauncher.launch(intent) else vm.connectVpn(this)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root — 3-tab navigation
// ─────────────────────────────────────────────────────────────────────────────

private enum class AppTab { HOME, SERVERS, SETTINGS }

@Composable
fun AppRoot(vm: VpnViewModel, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val state    by vm.state.collectAsStateWithLifecycle()
    val traffic  by vm.traffic.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(AppTab.HOME) }

    Scaffold(
        containerColor = ColorBackground,
        bottomBar = {
            PremiumBottomBar(
                current  = currentTab,
                onSelect = { currentTab = it },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ColorBackground),
        ) {
            when (currentTab) {
                AppTab.HOME    -> HomeTab(state, traffic, onConnect, onDisconnect)
                AppTab.SERVERS -> ServersTab(state, vm)
                AppTab.SETTINGS -> SettingsTab(settings, vm::updateSettings)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PremiumBottomBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(
        containerColor = ColorSurface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(
            width = 0.5.dp,
            color = ColorCardStroke,
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
        ),
    ) {
        listOf(
            Triple(AppTab.HOME,     Icons.Rounded.Home,     "Ana Sayfa"),
            Triple(AppTab.SERVERS,  Icons.Rounded.Dns,      "Sunucular"),
            Triple(AppTab.SETTINGS, Icons.Rounded.Settings, "Ayarlar"),
        ).forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected      = current == tab,
                onClick       = { onSelect(tab) },
                icon          = {
                    Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
                },
                label         = { Text(label, fontSize = 11.sp) },
                colors        = NavigationBarItemDefaults.colors(
                    selectedIconColor      = ColorBlue,
                    selectedTextColor      = ColorBlue,
                    indicatorColor         = ColorBlue.copy(alpha = 0.12f),
                    unselectedIconColor    = ColorTextSecondary,
                    unselectedTextColor    = ColorTextSecondary,
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HOME TAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeTab(
    state: VpnViewModel.UiState,
    traffic: VpnViewModel.Traffic,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Security, null, tint = ColorBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("TmVPN", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary)
                    val subText = when (state) {
                        is VpnViewModel.UiState.Connected -> "Bağlı"
                        else                              -> "Bağlı değil"
                    }
                    Text(subText, fontSize = 12.sp, color = ColorTextSecondary)
                }
            }
            // Status badge
            val (badgeLabel, badgeColor) = when (state) {
                is VpnViewModel.UiState.Connected -> "Aktif" to ColorGreen
                is VpnViewModel.UiState.Connecting -> "Bağlanıyor" to ColorAmber
                else -> "Pasif" to ColorTextSecondary
            }
            val badgeAnim by animateColorAsState(badgeColor, tween(400), label = "badge")
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(badgeAnim.copy(0.12f))
                    .border(1.dp, badgeAnim.copy(0.30f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(badgeAnim),
                )
                Spacer(Modifier.width(5.dp))
                Text(badgeLabel, color = badgeAnim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Connect orb
        ConnectOrb(
            state = state,
            onClick = {
                when (state) {
                    is VpnViewModel.UiState.Connected -> onDisconnect()
                    is VpnViewModel.UiState.Ready     -> onConnect()
                    else                              -> Unit
                }
            },
        )

        Spacer(Modifier.height(24.dp))

        // Traffic stats card (connected only) or loading indicator
        when (state) {
            is VpnViewModel.UiState.Connected  -> {
                TrafficCard(traffic)
                Spacer(Modifier.height(12.dp))
                ActiveServerCard(state.server)
            }
            is VpnViewModel.UiState.Loading    -> StatusPanel("Sunucular alınıyor…")
            is VpnViewModel.UiState.Testing    -> StatusPanel("Hızlar ölçülüyor…")
            is VpnViewModel.UiState.Error      -> ErrorPanel(state.message, onRetry = {})
            else                               -> Unit
        }
    }
}

// ─── traffic card ────────────────────────────────────────────────────────────

@Composable
private fun TrafficCard(t: VpnViewModel.Traffic) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        border = BorderStroke(0.5.dp, ColorCardStroke),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Time
            TrafficStat(
                icon   = Icons.Rounded.Schedule,
                iconTint = ColorBlue,
                top    = formatTime(t.elapsedSeconds),
                bottom = "",
            )
            // Download
            TrafficStat(
                icon   = Icons.Rounded.ArrowDownward,
                iconTint = ColorGreen,
                top    = formatSpeed(t.downloadBytesPerSec),
                bottom = "Toplam ↓ ${formatBytes(t.totalDownloadBytes)}",
            )
            // Upload
            TrafficStat(
                icon   = Icons.Rounded.ArrowUpward,
                iconTint = ColorAmber,
                top    = formatSpeed(t.uploadBytesPerSec),
                bottom = "Toplam ↑ ${formatBytes(t.totalUploadBytes)}",
            )
        }
    }
}

@Composable
private fun TrafficStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    top: String,
    bottom: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(top, color = ColorTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (bottom.isNotEmpty()) {
            Text(bottom, color = ColorTextSecondary, fontSize = 10.sp)
        }
    }
}

// ─── active server card ──────────────────────────────────────────────────────

@Composable
private fun ActiveServerCard(server: ServerConfig) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        border = BorderStroke(0.5.dp, ColorGreen.copy(0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ColorBlue.copy(0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Language, null, tint = ColorBlue, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Aktif Sunucu", color = ColorTextSecondary, fontSize = 11.sp)
                Text(server.remark, color = ColorTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${server.protocol.name} • ${server.network.uppercase()}",
                    color = ColorTextSecondary, fontSize = 12.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ColorGreen),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SERVERS TAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ServersTab(state: VpnViewModel.UiState, vm: VpnViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Sunucular", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary)
        Spacer(Modifier.height(16.dp))

        // Yenile / Test Et buttons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val isConnected = state is VpnViewModel.UiState.Connected
            OutlinedButton(
                onClick  = { if (!isConnected) vm.refreshAndPickFastest() },
                enabled  = !isConnected,
                modifier = Modifier.weight(1f),
                border   = BorderStroke(1.dp, ColorCardStroke),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextSecondary),
                shape    = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Yenile")
            }
            OutlinedButton(
                onClick  = { if (!isConnected) vm.refreshAndPickFastest() },
                enabled  = !isConnected,
                modifier = Modifier.weight(1f),
                border   = BorderStroke(1.dp, ColorCardStroke),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextSecondary),
                shape    = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Test Et")
            }
        }

        Spacer(Modifier.height(12.dp))

        when (state) {
            is VpnViewModel.UiState.Connected -> {
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ColorAmber.copy(0.08f)),
                    border = BorderStroke(1.dp, ColorAmber.copy(0.25f)),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Info, null, tint = ColorAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Bağlı durumda — değiştirmek için kes",
                            color = ColorAmber, fontSize = 13.sp,
                        )
                    }
                }
            }
            is VpnViewModel.UiState.Ready -> {
                ServerPanel(
                    results    = state.results,
                    selectedId = state.selected.id,
                    onSelect   = vm::selectServer,
                )
            }
            is VpnViewModel.UiState.Loading -> StatusPanel("Sunucular alınıyor…")
            is VpnViewModel.UiState.Testing -> StatusPanel("Hızlar test ediliyor…")
            is VpnViewModel.UiState.Error   -> ErrorPanel(state.message, vm::refreshAndPickFastest)
            else                            -> Unit
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SETTINGS TAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsTab(
    settings: AppSettings,
    update: AppSettings.(AppSettings.() -> AppSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Ayarlar", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary)
        Spacer(Modifier.height(20.dp))

        // ── Fragment (DPI atlatma) ────────────────────────────────────────────
        SettingsSection(title = "FRAGMENT (DPI ATLATMA)") {
            SettingsToggle(
                icon    = Icons.Rounded.Shield,
                label   = "Fragment Aktif",
                sublabel = "Türkmenistan için tlshello/1-3/1-1",
                checked = settings.fragmentEnabled,
                onChange = { update { copy(fragmentEnabled = it) } },
            )
            if (settings.fragmentEnabled) {
                SettingsDropdown(
                    icon    = Icons.Rounded.Code,
                    label   = "Packets",
                    value   = settings.fragmentPackets,
                    options = listOf("tlshello", "1-3", "1-5", "all"),
                    onChange = { update { copy(fragmentPackets = it) } },
                )
                SettingsDropdown(
                    icon    = Icons.Rounded.LinearScale,
                    label   = "Length",
                    value   = settings.fragmentLength,
                    options = listOf("1-3", "1-5", "10-30", "100-200"),
                    onChange = { update { copy(fragmentLength = it) } },
                )
                SettingsDropdown(
                    icon    = Icons.Rounded.Timer,
                    label   = "Interval",
                    value   = settings.fragmentInterval,
                    options = listOf("1-1", "1-3", "1-5"),
                    onChange = { update { copy(fragmentInterval = it) } },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── MUX ──────────────────────────────────────────────────────────────
        SettingsSection(title = "MUX") {
            SettingsToggle(
                icon    = Icons.Rounded.CallSplit,
                label   = "Mux Aktif",
                sublabel = "TCP/UDP çoğullama",
                checked = settings.muxEnabled,
                onChange = { update { copy(muxEnabled = it) } },
            )
            if (settings.muxEnabled) {
                SettingsDropdown(
                    icon    = Icons.Rounded.Tag,
                    label   = "QUIC Mux",
                    value   = settings.quicMux,
                    options = listOf("reject", "disable"),
                    onChange = { update { copy(quicMux = it) } },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Routing ───────────────────────────────────────────────────────────
        SettingsSection(title = "ROUTING") {
            SettingsToggle(
                icon    = Icons.Rounded.Block,
                label   = "UDP 443 Bloklama",
                sublabel = "QUIC engelleme (TikTok TCP'ye düşsün)",
                checked = settings.blockUdp443,
                onChange = { update { copy(blockUdp443 = it) } },
            )
            SettingsToggle(
                icon    = Icons.Rounded.Search,
                label   = "Google Proxy Zorla",
                sublabel = "geosite:google → proxy",
                checked = settings.forceGoogleProxy,
                onChange = { update { copy(forceGoogleProxy = it) } },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        color         = ColorTextSecondary,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier      = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        border = BorderStroke(0.5.dp, ColorCardStroke),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sublabel: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ColorBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = ColorTextPrimary, fontSize = 14.sp)
            if (sublabel.isNotEmpty()) {
                Text(sublabel, color = ColorTextSecondary, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor  = Color.White,
                checkedTrackColor  = ColorGreen,
                uncheckedTrackColor = ColorTextMuted,
            ),
        )
    }
    HorizontalDivider(color = ColorCardStroke, thickness = 0.5.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = ColorBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = ColorTextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(value, color = ColorBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ArrowDropDown, null, tint = ColorBlue, modifier = Modifier.size(18.dp))
        }
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            containerColor   = ColorCard,
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text    = { Text(opt, color = if (opt == value) ColorBlue else ColorTextPrimary) },
                    onClick = { onChange(opt); expanded = false },
                )
            }
        }
    }
    HorizontalDivider(color = ColorCardStroke, thickness = 0.5.dp)
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared: ConnectOrb
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectOrb(state: VpnViewModel.UiState, onClick: () -> Unit) {
    val isConnected  = state is VpnViewModel.UiState.Connected
    val isConnecting = state is VpnViewModel.UiState.Connecting
    val isReady      = state is VpnViewModel.UiState.Ready
    val isEnabled    = isConnected || isReady

    val accentColor by animateColorAsState(
        targetValue   = when {
            isConnected  -> ColorGreen
            isConnecting -> ColorAmber
            isReady      -> ColorBlue
            else         -> Color(0xFF1A2440)
        },
        animationSpec = tween(500), label = "accent",
    )

    val infinite = rememberInfiniteTransition(label = "orb")
    val pulseScale by infinite.animateFloat(
        1.00f, 1.14f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "scale",
    )
    val pulseAlpha by infinite.animateFloat(
        0.18f, 0.40f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "alpha",
    )
    val spinAngle by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1300, easing = LinearEasing)), "spin",
    )

    Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        // Glow halos
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            listOf(106.dp.toPx() to 0.04f, 92.dp.toPx() to 0.08f, 80.dp.toPx() to 0.13f)
                .forEach { (r, a) -> drawCircle(accentColor.copy(alpha = a), radius = r, center = c) }
        }
        // Spinner (connecting)
        if (isConnecting) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(200.dp).rotate(spinAngle)) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, ColorAmber.copy(0.7f), ColorAmber)),
                    startAngle = 0f, sweepAngle = 260f, useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        // Pulse ring (connected)
        if (isConnected) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(188.dp * pulseScale)) {
                drawCircle(ColorGreen.copy(alpha = pulseAlpha * 0.5f), style = Stroke(1.5.dp.toPx()))
            }
        }
        // Static ring
        androidx.compose.foundation.Canvas(modifier = Modifier.size(188.dp)) {
            drawCircle(accentColor.copy(alpha = 0.30f), style = Stroke(1.dp.toPx()))
        }
        // Core button
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(accentColor.copy(0.22f), accentColor.copy(0.06f))
                    )
                )
                .border(
                    2.dp,
                    Brush.linearGradient(listOf(accentColor.copy(0.80f), accentColor.copy(0.25f))),
                    CircleShape,
                )
                .clickable(enabled = isEnabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (isConnected) Icons.Rounded.CheckCircle else Icons.Rounded.PowerSettingsNew,
                    null,
                    tint     = if (isEnabled) accentColor else ColorTextSecondary,
                    modifier = Modifier.size(46.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (state) {
                        is VpnViewModel.UiState.Connected  -> "BAĞLI"
                        is VpnViewModel.UiState.Connecting -> "• • •"
                        is VpnViewModel.UiState.Ready      -> "BAĞLAN"
                        else                               -> "BEKLE"
                    },
                    color         = if (isEnabled) accentColor else ColorTextSecondary,
                    fontSize      = 14.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared: Server panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ServerPanel(
    results: List<ServerPinger.Result>,
    selectedId: String,
    onSelect: (ServerConfig) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(results, key = { it.config.id }) { r ->
            ServerRow(r, r.config.id == selectedId) { onSelect(r.config) }
        }
    }
}

@Composable
private fun ServerRow(result: ServerPinger.Result, selected: Boolean, onClick: () -> Unit) {
    val latColor = latencyColor(result.latencyMs, result.isReachable)
    val bars     = signalBars(result.latencyMs, result.isReachable)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected)
                    Brush.horizontalGradient(listOf(ColorBlue.copy(0.14f), ColorCard))
                else
                    Brush.horizontalGradient(listOf(ColorCard, ColorCard))
            )
            .border(
                if (selected) 1.dp else 0.5.dp,
                if (selected) ColorBlue.copy(0.45f) else ColorCardStroke,
                RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (result.isReachable) latColor.copy(0.12f) else ColorTextMuted.copy(0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Language, null,
                    tint     = if (result.isReachable) latColor else ColorTextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    result.config.remark,
                    color      = ColorTextPrimary.copy(if (selected) 1f else 0.88f),
                    fontSize   = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        result.config.protocol.name,
                        color      = ColorBlue.copy(0.9f),
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier   = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ColorBlue.copy(0.10f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    if (result.config.security != "none") {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Lock, null, tint = ColorTextSecondary, modifier = Modifier.size(10.dp))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment     = Alignment.Bottom,
                ) {
                    (1..3).forEach { i ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height((i * 5 + 3).dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (i <= bars) latColor else ColorTextMuted),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    if (result.isReachable) "${result.latencyMs} ms" else "—",
                    color = latColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared: Status / Error
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusPanel(msg: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(top = 24.dp),
    ) {
        CircularProgressIndicator(color = ColorBlue, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(12.dp))
        Text(msg, color = ColorTextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorPanel(msg: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(24.dp),
    ) {
        Icon(Icons.Rounded.Warning, null, tint = ColorRed, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text(msg, color = ColorTextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onRetry,
            border  = BorderStroke(1.dp, ColorBlue.copy(0.45f)),
            colors  = ButtonDefaults.outlinedButtonColors(contentColor = ColorBlue),
        ) {
            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tekrar dene")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Formatters & helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatSpeed(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
    bps >= 1_000     -> "%.1f KB/s".format(bps / 1_000.0)
    else             -> "$bps B/s"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000     -> "%.1f KB".format(bytes / 1_000.0)
    else               -> "$bytes B"
}

private fun formatTime(sec: Long): String =
    "%02d:%02d".format(sec / 60, sec % 60)

private fun latencyColor(ms: Long, reachable: Boolean): Color = when {
    !reachable -> ColorRed
    ms < 150   -> ColorGreen
    ms < 400   -> ColorAmber
    else       -> ColorRed
}

private fun signalBars(ms: Long, reachable: Boolean): Int = when {
    !reachable -> 0
    ms < 150   -> 3
    ms < 400   -> 2
    else       -> 1
}
