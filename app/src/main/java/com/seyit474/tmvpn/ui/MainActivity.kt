package com.seyit474.tmvpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seyit474.tmvpn.ping.ServerPinger
import com.seyit474.tmvpn.service.XrayConfigBuilder
import com.seyit474.tmvpn.service.XrayVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ─── Premium dark-navy palette ────────────────────────────────────────────────
private val BgDeep     = Color(0xFF070B13)
private val BgCard     = Color(0xFF0D1520)
private val BgSurface  = Color(0xFF111927)
private val AccBlue    = Color(0xFF4A90FF)
private val AccGreen   = Color(0xFF00C96D)
private val AccAmber   = Color(0xFFFFAA00)
private val AccOrange  = Color(0xFFFF6B35)
private val AccRed     = Color(0xFFFF4757)
private val TxtPri     = Color(0xFFE8EDF5)
private val TxtSec     = Color(0xFF6B7A99)
private val TxtMuted   = Color(0xFF3A4560)
private val CardBorder = Color(0xFF1A2440)

// ─── Tab definition ───────────────────────────────────────────────────────────
private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Ana Sayfa", Icons.Filled.Home),
    SERVERS("Sunucular", Icons.Filled.Dns),
    SETTINGS("Ayarlar", Icons.Filled.Settings),
}

// ─── Activity ─────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    private val vm: VpnViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> if (result.resultCode == RESULT_OK) startVpnWithSelected() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary    = AccBlue,
                    background = BgDeep,
                    surface    = BgCard,
                )
            ) {
                AppRoot(vm = vm, onConnect = { onConnectClicked() })
            }
        }
        vm.refreshAndPickFastest()
    }

    private fun onConnectClicked() {
        val state = vm.state.value
        if (state is VpnViewModel.UiState.Connected) {
            XrayVpnService.stop(this)
            vm.markDisconnected()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermissionLauncher.launch(intent) else startVpnWithSelected()
    }

    private fun startVpnWithSelected() {
        val state = vm.state.value as? VpnViewModel.UiState.Ready ?: return
        val cfg = state.selected
        val fragEnabled = com.seyit474.tmvpn.settings.AppSettings.getSync(
            this,
            com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_ENABLED,
            com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_ENABLED,
        )
        val configJson = XrayConfigBuilder.build(
            cfg,
            enableFragment = fragEnabled,
            hwidUuid       = com.seyit474.tmvpn.hwid.HwidManager.getHwid(this),
        )
        XrayVpnService.start(this, configJson, cfg.remark)
        vm.markConnected(cfg)
    }
}

// ─── Root ─────────────────────────────────────────────────────────────────────
@Composable
fun AppRoot(vm: VpnViewModel, onConnect: () -> Unit) {
    var currentTab by remember { mutableStateOf(Tab.HOME) }

    Scaffold(
        containerColor = BgDeep,
        bottomBar = { PremiumBottomBar(currentTab) { currentTab = it } },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgDeep),
        ) {
            when (currentTab) {
                Tab.HOME     -> HomeTab(vm, onConnect)
                Tab.SERVERS  -> ServersTab(vm)
                Tab.SETTINGS -> SettingsTab()
            }
        }
    }
}

// ─── Bottom bar ───────────────────────────────────────────────────────────────
@Composable
private fun PremiumBottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(
        containerColor = BgSurface,
        tonalElevation = 0.dp,
        modifier       = Modifier.border(0.5.dp, CardBorder),
    ) {
        Tab.values().forEach { tab ->
            NavigationBarItem(
                selected = current == tab,
                onClick  = { onSelect(tab) },
                icon     = { Icon(tab.icon, null, modifier = Modifier.size(22.dp)) },
                label    = { Text(tab.label, fontSize = 11.sp) },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor   = AccBlue,
                    selectedTextColor   = AccBlue,
                    indicatorColor      = AccBlue.copy(alpha = 0.12f),
                    unselectedIconColor = TxtSec,
                    unselectedTextColor = TxtSec,
                ),
            )
        }
    }
}

// ─── HOME TAB ─────────────────────────────────────────────────────────────────
@Composable
private fun HomeTab(vm: VpnViewModel, onConnect: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier            = Modifier.fillMaxSize().systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // App header
        Row(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, null, tint = AccBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("TmVPN", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TxtPri)
                    Text(statusLabel(state), fontSize = 12.sp, color = TxtSec)
                }
            }
            StatusBadge(state)
        }

        Spacer(Modifier.height(8.dp))

        ConnectOrb(state = state, onClick = onConnect)

        Spacer(Modifier.height(24.dp))

        when (val s = state) {
            is VpnViewModel.UiState.Connected -> {
                TrafficPanel()
                Spacer(Modifier.height(12.dp))
                ActiveServerCard(s.server, isActive = true)
            }
            is VpnViewModel.UiState.Ready     -> ActiveServerCard(s.selected, isActive = false)
            is VpnViewModel.UiState.Loading   -> StatusPanel("Sunucular alınıyor…")
            is VpnViewModel.UiState.Error     -> ErrorPanel(s.message) { vm.refreshAndPickFastest() }
            else                              -> Unit
        }
    }
}

// ─── Status badge ────────────────────────────────────────────────────────────
@Composable
private fun StatusBadge(state: VpnViewModel.UiState) {
    val (label, color) = when (state) {
        is VpnViewModel.UiState.Connected  -> "Aktif"      to AccGreen
        VpnViewModel.UiState.Connecting    -> "Bağlanıyor" to AccAmber
        is VpnViewModel.UiState.Ready      -> "Hazır"      to AccBlue
        else                               -> "Pasif"      to TxtSec
    }
    val animColor by animateColorAsState(color, tween(400), label = "badge")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(animColor.copy(0.12f))
            .border(1.dp, animColor.copy(0.30f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(animColor))
        Spacer(Modifier.width(5.dp))
        Text(label, color = animColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Connect orb ──────────────────────────────────────────────────────────────
@Composable
private fun ConnectOrb(state: VpnViewModel.UiState, onClick: () -> Unit) {
    val isConnected  = state is VpnViewModel.UiState.Connected
    val isConnecting = state == VpnViewModel.UiState.Connecting
    val isReady      = state is VpnViewModel.UiState.Ready
    val isEnabled    = isConnected || isReady

    val accentColor by animateColorAsState(
        targetValue = when {
            isConnected  -> AccGreen
            isConnecting -> AccAmber
            isReady      -> AccBlue
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            listOf(106.dp.toPx() to 0.04f, 92.dp.toPx() to 0.08f, 80.dp.toPx() to 0.13f)
                .forEach { (r, a) -> drawCircle(accentColor.copy(alpha = a), radius = r, center = c) }
        }
        // Spinning arc (connecting)
        if (isConnecting) {
            Canvas(modifier = Modifier.size(200.dp).rotate(spinAngle)) {
                drawArc(
                    brush       = Brush.sweepGradient(listOf(Color.Transparent, AccAmber.copy(0.7f), AccAmber)),
                    startAngle  = 0f,
                    sweepAngle  = 260f,
                    useCenter   = false,
                    style       = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        // Pulse ring (connected)
        if (isConnected) {
            Canvas(modifier = Modifier.size(188.dp * pulseScale)) {
                drawCircle(AccGreen.copy(alpha = pulseAlpha * 0.5f), style = Stroke(1.5.dp.toPx()))
            }
        }
        // Static ring
        Canvas(modifier = Modifier.size(188.dp)) {
            drawCircle(accentColor.copy(alpha = 0.30f), style = Stroke(1.dp.toPx()))
        }
        // Core button
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(accentColor.copy(0.22f), accentColor.copy(0.06f)))
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
                    imageVector = if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    tint     = if (isEnabled) accentColor else TxtSec,
                    modifier = Modifier.size(46.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        isConnected  -> "BAĞLI"
                        isConnecting -> "• • •"
                        isReady      -> "BAĞLAN"
                        else         -> "BEKLE"
                    },
                    color         = if (isEnabled) accentColor else TxtSec,
                    fontSize      = 14.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

// ─── Traffic panel ────────────────────────────────────────────────────────────
@Composable
private fun TrafficPanel() {
    val stats by com.seyit474.tmvpn.util.TrafficCounter.stats.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = BgCard),
        border   = BorderStroke(0.5.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TrafficCell(
                    Icons.Filled.Schedule,
                    com.seyit474.tmvpn.util.TrafficCounter.formatDuration(stats.connectedSeconds),
                    AccBlue,
                )
                TrafficCell(
                    Icons.Filled.ArrowDownward,
                    com.seyit474.tmvpn.util.TrafficCounter.formatSpeed(stats.downloadSpeed),
                    AccGreen,
                )
                TrafficCell(
                    Icons.Filled.ArrowUpward,
                    com.seyit474.tmvpn.util.TrafficCounter.formatSpeed(stats.uploadSpeed),
                    AccAmber,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Toplam ↓ ${com.seyit474.tmvpn.util.TrafficCounter.formatBytes(stats.downloadBytes)}",
                    fontSize = 11.sp, color = TxtSec,
                )
                Text(
                    "Toplam ↑ ${com.seyit474.tmvpn.util.TrafficCounter.formatBytes(stats.uploadBytes)}",
                    fontSize = 11.sp, color = TxtSec,
                )
            }
        }
    }
}

@Composable
private fun TrafficCell(icon: ImageVector, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = TxtPri, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Active server card ───────────────────────────────────────────────────────
@Composable
private fun ActiveServerCard(server: com.seyit474.tmvpn.model.ServerConfig, isActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = BgCard),
        border   = BorderStroke(0.5.dp, if (isActive) AccGreen.copy(0.25f) else CardBorder),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier           = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(AccBlue.copy(0.12f)),
                contentAlignment   = Alignment.Center,
            ) {
                Icon(Icons.Filled.Public, null, tint = AccBlue, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isActive) "Aktif Sunucu" else "Seçili Sunucu",
                    color = TxtSec, fontSize = 11.sp,
                )
                Text(server.remark, color = TxtPri, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${server.protocol.name} • ${server.network.uppercase()}",
                    color = TxtSec, fontSize = 12.sp,
                )
            }
            if (isActive) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AccGreen))
            }
        }
    }
}

// ─── SERVERS TAB ─────────────────────────────────────────────────────────────
@Composable
private fun ServersTab(vm: VpnViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Sunucular", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TxtPri)
        Spacer(Modifier.height(16.dp))

        val isConnected = state is VpnViewModel.UiState.Connected
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick  = { if (!isConnected) vm.refreshAndPickFastest() },
                enabled  = !isConnected,
                modifier = Modifier.weight(1f),
                border   = BorderStroke(1.dp, CardBorder),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = TxtSec),
                shape    = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Yenile")
            }
            OutlinedButton(
                onClick  = { if (!isConnected) vm.repingExisting() },
                enabled  = !isConnected,
                modifier = Modifier.weight(1f),
                border   = BorderStroke(1.dp, CardBorder),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = TxtSec),
                shape    = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.Speed, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Test Et")
            }
        }

        Spacer(Modifier.height(12.dp))

        when (val s = state) {
            is VpnViewModel.UiState.Connected -> {
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AccAmber.copy(0.08f)),
                    border = BorderStroke(1.dp, AccAmber.copy(0.25f)),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = AccAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Bağlı durumda — değiştirmek için kes", color = AccAmber, fontSize = 13.sp)
                    }
                }
            }
            is VpnViewModel.UiState.Ready -> {
                Text(
                    "${s.results.size} sunucu",
                    fontSize = 12.sp, color = TxtSec,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.results, key = { it.config.id }) { r ->
                        ServerRow(r, r.config.id == s.selected.id) { vm.selectServer(r.config) }
                    }
                }
            }
            is VpnViewModel.UiState.Loading -> StatusPanel("Sunucular alınıyor…")
            is VpnViewModel.UiState.Error   -> ErrorPanel(s.message) { vm.refreshAndPickFastest() }
            else                            -> Unit
        }
    }
}

// ─── Server row ───────────────────────────────────────────────────────────────
@Composable
private fun ServerRow(
    r: ServerPinger.Result,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val latColor = latencyColor(r.latencyMs, r.isReachable)
    val bars     = signalBars(r.latencyMs, r.isReachable)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected)
                    Brush.horizontalGradient(listOf(AccBlue.copy(0.14f), BgCard))
                else
                    Brush.horizontalGradient(listOf(BgCard, BgCard)),
            )
            .border(
                if (selected) 1.dp else 0.5.dp,
                if (selected) AccBlue.copy(0.45f) else CardBorder,
                RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(statusDotColor(r.status).copy(0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Public, null, tint = statusDotColor(r.status), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.config.remark,
                    color      = TxtPri.copy(alpha = if (selected) 1f else 0.88f),
                    fontSize   = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.config.protocol.name,
                        color      = AccBlue.copy(0.9f),
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier   = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccBlue.copy(0.10f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    if (r.config.security != "none") {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Lock, null, tint = TxtSec, modifier = Modifier.size(10.dp))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                // Signal bars
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
                                .background(if (i <= bars) latColor else TxtMuted),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                PingBadge(r)
            }
        }
    }
}

@Composable
private fun PingBadge(r: ServerPinger.Result) {
    val (text, color) = when (r.status) {
        ServerPinger.Status.TESTING     -> "Test…"     to TxtSec
        ServerPinger.Status.OK          -> "${r.latencyMs}ms" to latencyColor(r.latencyMs, true)
        ServerPinger.Status.TLS_BLOCKED -> "Engelli"   to AccOrange
        ServerPinger.Status.UNREACHABLE -> "Yok"       to AccRed
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

// ─── SETTINGS TAB ────────────────────────────────────────────────────────────
@Composable
private fun SettingsTab() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Ayarlar", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TxtPri)
        Spacer(Modifier.height(20.dp))

        SettingsGroup("FRAGMENT (DPI ATLATMA)") {
            ToggleRow(context, scope, Icons.Filled.Shield, "Fragment Aktif", "Türkmenistan için tlshello/1-3/1-1",
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_ENABLED,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_ENABLED)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TextRow(context, scope, Icons.Filled.Code, "Packets",
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_PACKETS,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_PACKETS,
                listOf("tlshello", "1-3", "1-1"))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TextRow(context, scope, Icons.Filled.LinearScale, "Length",
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_LENGTH,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_LENGTH,
                listOf("1-3", "5-10", "10-20", "50-100", "100-200"))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TextRow(context, scope, Icons.Filled.Timer, "Interval",
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_INTERVAL,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_INTERVAL,
                listOf("1-1", "5-10", "10-20", "20-40"))
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("MUX") {
            ToggleRow(context, scope, Icons.Filled.Merge, "Mux Aktif", "TCP/UDP çoğullama",
                com.seyit474.tmvpn.settings.AppSettings.MUX_ENABLED,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.MUX_ENABLED)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TextRow(context, scope, Icons.Filled.Tag, "QUIC Mux",
                com.seyit474.tmvpn.settings.AppSettings.MUX_XUDP_QUIC,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.MUX_XUDP_QUIC,
                listOf("reject", "allow", "skip"))
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("ROUTING") {
            ToggleRow(context, scope, Icons.Filled.Block, "UDP 443 Bloklama", "QUIC engelleme (TikTok TCP'ye düşsün)",
                com.seyit474.tmvpn.settings.AppSettings.BLOCK_UDP_443,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.BLOCK_UDP_443)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            ToggleRow(context, scope, Icons.Filled.Search, "Google Proxy Zorla", "geosite:google → proxy",
                com.seyit474.tmvpn.settings.AppSettings.PROXY_GOOGLE,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.PROXY_GOOGLE)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            ToggleRow(context, scope, Icons.Filled.Wifi, "LAN Bypass", "Lokal ağlar VPN dışında",
                com.seyit474.tmvpn.settings.AppSettings.BYPASS_LAN,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.BYPASS_LAN)
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("SNİFFİNG") {
            ToggleRow(context, scope, Icons.Filled.Visibility, "Sniffing Aktif", "Paketten domain tespiti",
                com.seyit474.tmvpn.settings.AppSettings.SNIFFING_ENABLED,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.SNIFFING_ENABLED)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            ToggleRow(context, scope, Icons.Filled.Route, "Route Only", "Sniffed domain sadece routing için",
                com.seyit474.tmvpn.settings.AppSettings.ROUTE_ONLY,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.ROUTE_ONLY)
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("DNS") {
            TextRow(context, scope, Icons.Filled.Public, "Remote DNS",
                com.seyit474.tmvpn.settings.AppSettings.REMOTE_DNS,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.REMOTE_DNS,
                listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "208.67.222.222"))
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("VPN") {
            TextRow(context, scope, Icons.Filled.NetworkCheck, "MTU",
                com.seyit474.tmvpn.settings.AppSettings.VPN_MTU,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.VPN_MTU,
                listOf(1500, 1420, 1400, 1380))
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("UYGULAMA") {
            ToggleRow(context, scope, Icons.Filled.AutoMode, "Otomatik Seçim", "Test bitince en hızlısını seç",
                com.seyit474.tmvpn.settings.AppSettings.AUTO_SELECT,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.AUTO_SELECT)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TextRow(context, scope, Icons.Filled.BugReport, "Log Seviyesi",
                com.seyit474.tmvpn.settings.AppSettings.LOG_LEVEL,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.LOG_LEVEL,
                listOf("warning", "info", "debug", "error"))
        }

        Spacer(Modifier.height(16.dp))

        AccountSection(context)

        Spacer(Modifier.height(16.dp))

        SettingsGroup("HAKKINDA") {
            InfoRow(Icons.Filled.Smartphone, "Sürüm", "0.1.0")
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            InfoRow(Icons.Filled.Person, "Geliştirici", "TM Oğuz")
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        color         = TxtSec,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier      = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(0.5.dp, CardBorder),
    ) {
        Column(content = content)
    }
}

@Composable
private fun ToggleRow(
    context:  Context,
    scope:    CoroutineScope,
    icon:     ImageVector,
    label:    String,
    subtitle: String,
    key:      androidx.datastore.preferences.core.Preferences.Key<Boolean>,
    default:  Boolean,
) {
    val value by com.seyit474.tmvpn.settings.AppSettings.get(context, key, default)
        .collectAsStateWithLifecycle(initialValue = default)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { scope.launch { com.seyit474.tmvpn.settings.AppSettings.set(context, key, !value) } }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AccBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TxtPri, fontSize = 14.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, color = TxtSec, fontSize = 11.sp)
        }
        Switch(
            checked         = value,
            onCheckedChange = { scope.launch { com.seyit474.tmvpn.settings.AppSettings.set(context, key, it) } },
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = AccGreen,
                uncheckedTrackColor = TxtMuted,
            ),
        )
    }
}

@Composable
private inline fun <reified T> TextRow(
    context:  Context,
    scope:    CoroutineScope,
    icon:     ImageVector,
    label:    String,
    key:      androidx.datastore.preferences.core.Preferences.Key<T>,
    default:  T,
    options:  List<T>,
) {
    val value by com.seyit474.tmvpn.settings.AppSettings.get(context, key, default)
        .collectAsStateWithLifecycle(initialValue = default)
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AccBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TxtPri, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value.toString(), color = AccBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            null, tint = AccBlue, modifier = Modifier.size(18.dp),
        )
    }
    if (expanded) {
        Column(modifier = Modifier.fillMaxWidth().background(BgDeep.copy(0.6f))) {
            options.forEach { opt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { com.seyit474.tmvpn.settings.AppSettings.set(context, key, opt) }
                            expanded = false
                        }
                        .padding(horizontal = 36.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (opt == value) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                        null,
                        tint     = if (opt == value) AccBlue else TxtSec,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(opt.toString(), color = TxtPri, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AccBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TxtPri, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TxtSec, fontSize = 13.sp)
    }
}

// ─── Account section ──────────────────────────────────────────────────────────
@Composable
private fun AccountSection(context: Context) {
    val hesapId = remember { com.seyit474.tmvpn.hwid.HwidManager.getHwid(context) }

    Text(
        "HESAP",
        color         = TxtSec,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier      = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Card(
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = BgCard),
        border   = BorderStroke(0.5.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountCircle, null, tint = AccBlue, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Hesap", color = TxtPri, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgDeep)
                    .padding(14.dp),
            ) {
                Column {
                    Text("Hesap ID", color = TxtSec, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(hesapId, color = TxtPri, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Hesap ID", hesapId))
                    Toast.makeText(context, "Hesap ID kopyalandı", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = AccBlue, contentColor = Color.White),
                shape    = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kopyala", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Bu ID'yi sağlayıcınıza gönderin. Hesabınız aktif edildikten sonra VPN'i kullanabilirsiniz.",
                color    = TxtSec,
                fontSize = 12.sp,
            )
        }
    }
}

// ─── Shared: Status / Error ───────────────────────────────────────────────────
@Composable
private fun StatusPanel(msg: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(top = 24.dp),
    ) {
        CircularProgressIndicator(color = AccBlue, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(12.dp))
        Text(msg, color = TxtSec, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorPanel(msg: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(24.dp),
    ) {
        Icon(Icons.Filled.Warning, null, tint = AccRed, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text(msg, color = TxtSec, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onRetry,
            border  = BorderStroke(1.dp, AccBlue.copy(0.45f)),
            colors  = ButtonDefaults.outlinedButtonColors(contentColor = AccBlue),
        ) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tekrar dene")
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────
private fun statusLabel(s: VpnViewModel.UiState): String = when (s) {
    VpnViewModel.UiState.Idle        -> "Hazır"
    VpnViewModel.UiState.Loading     -> "Yükleniyor"
    is VpnViewModel.UiState.Ready    -> s.selected.remark
    VpnViewModel.UiState.Connecting  -> "Bağlanıyor"
    is VpnViewModel.UiState.Connected -> "Bağlı"
    is VpnViewModel.UiState.Error    -> "Hata"
}

private fun latencyColor(ms: Long, reachable: Boolean): Color = when {
    !reachable -> AccRed
    ms < 150   -> AccGreen
    ms < 400   -> AccAmber
    else       -> AccRed
}

private fun signalBars(ms: Long, reachable: Boolean): Int = when {
    !reachable -> 0
    ms < 150   -> 3
    ms < 400   -> 2
    else       -> 1
}

private fun statusDotColor(s: ServerPinger.Status): Color = when (s) {
    ServerPinger.Status.OK          -> AccGreen
    ServerPinger.Status.TESTING     -> AccAmber
    ServerPinger.Status.TLS_BLOCKED -> AccOrange
    ServerPinger.Status.UNREACHABLE -> AccRed
}
