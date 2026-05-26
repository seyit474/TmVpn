package com.tmvpn.app.ui

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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tmvpn.app.hwid.HwidManager
import com.tmvpn.app.ping.ServerPinger
import com.tmvpn.app.ui.theme.*
import com.tmvpn.app.util.LogBus
import com.tmvpn.app.util.TrafficCounter

// ─── Design tokens ────────────────────────────────────────────────────────────
private val BgDeep    = DarkBackground
private val BgCard    = DarkSurface
private val AccBlue   = AccentBlue
private val AccGreen  = ConnectedGreen
private val AccRed    = ErrorRed
private val AccAmber  = AccentAmber
private val AccOrange = AccentOrange
private val TxtPri    = TextPrimary
private val TxtSec    = TextSecondary
private val TxtMuted  = Color(0xFF4A5568)
private val CardBrd   = GlassBorder

private enum class Tab { HOME, SERVERS, SETTINGS, LOGS }

// ─── Activity ─────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    private val vm: VpnViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> if (result.resultCode == RESULT_OK) vm.connectVpn(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AccBlue,
                    background = BgDeep,
                    surface = BgCard,
                    onPrimary = TxtPri,
                    onBackground = TxtPri,
                    onSurface = TxtPri,
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
            vm.disconnectVpn(this)
            return
        }
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermissionLauncher.launch(intent) else vm.connectVpn(this)
    }
}

// ─── Root scaffold ─────────────────────────────────────────────────────────────
@Composable
private fun AppRoot(vm: VpnViewModel, onConnect: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }

    Scaffold(
        containerColor = BgDeep,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0A1220),
                tonalElevation = 0.dp,
                modifier = Modifier.border(
                    BorderStroke(0.5.dp, GlassBorder),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ),
            ) {
                GlassNavItem(Icons.Filled.Home, "Baş Sahypa", tab == Tab.HOME) { tab = Tab.HOME }
                GlassNavItem(Icons.Filled.Dns, "Serwerleri", tab == Tab.SERVERS) { tab = Tab.SERVERS }
                GlassNavItem(Icons.Filled.Settings, "Sazlamalar", tab == Tab.SETTINGS) { tab = Tab.SETTINGS }
                GlassNavItem(Icons.Filled.Terminal, "Log", tab == Tab.LOGS) { tab = Tab.LOGS }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF080D14), Color(0xFF0D1B2A))
                    )
                )
        ) {
            when (tab) {
                Tab.HOME     -> HomeScreen(vm, onConnect)
                Tab.SERVERS  -> ServersScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
                Tab.LOGS     -> LogScreen()
            }
        }
    }
}

@Composable
private fun RowScope.GlassNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null, modifier = Modifier.size(22.dp)) },
        label = { Text(label, fontSize = 10.sp) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor   = AccBlue,
            selectedTextColor   = AccBlue,
            indicatorColor      = AccBlue.copy(0.12f),
            unselectedIconColor = TxtSec,
            unselectedTextColor = TxtSec,
        ),
    )
}

// ─── Glass card ───────────────────────────────────────────────────────────────
@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = GlassBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = GlassCard),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

// ─── HOME SCREEN ─────────────────────────────────────────────────────────────
@Composable
private fun HomeScreen(vm: VpnViewModel, onConnect: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // TM VPN title with blue glow
            Text(
                text = "TM VPN",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TxtPri,
                letterSpacing = 2.sp,
            )
            ConnectionStatusBadge(state)
        }

        Spacer(Modifier.height(32.dp))

        // Power button
        PowerButton(state, onConnect)

        Spacer(Modifier.height(16.dp))

        // State text
        val stateText = when (state) {
            is VpnViewModel.UiState.Connected -> "BAGLANDY"
            VpnViewModel.UiState.Connecting   -> "BAGLANYLÝAR..."
            is VpnViewModel.UiState.Ready     -> "BAGLAN"
            is VpnViewModel.UiState.Error     -> "NÄSAZLYK"
            else                              -> "BAGLAN"
        }
        val stateColor by animateColorAsState(
            when (state) {
                is VpnViewModel.UiState.Connected -> AccGreen
                VpnViewModel.UiState.Connecting   -> AccAmber
                is VpnViewModel.UiState.Error     -> AccRed
                else                              -> AccBlue
            }, tween(400), label = "stateColor"
        )
        Text(
            text = stateText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = stateColor,
            letterSpacing = 3.sp,
        )

        Spacer(Modifier.height(24.dp))

        // Traffic card (shown when connected)
        if (state is VpnViewModel.UiState.Connected) {
            TrafficCard()
            Spacer(Modifier.height(12.dp))
            ActiveServerCard(
                server = (state as VpnViewModel.UiState.Connected).server,
                isConnected = true,
            )
        } else if (state is VpnViewModel.UiState.Ready) {
            ActiveServerCard(
                server = (state as VpnViewModel.UiState.Ready).selected,
                isConnected = false,
            )
        } else if (state is VpnViewModel.UiState.Loading) {
            LoadingIndicator("Ýüklenilýär...")
        } else if (state is VpnViewModel.UiState.Error) {
            ErrorCard(
                message = "Näsazlyk: ${(state as VpnViewModel.UiState.Error).message}",
                onRetry = { vm.refreshAndPickFastest() },
            )
        }

        Spacer(Modifier.height(16.dp))

        // Refresh button
        OutlinedButton(
            onClick = { vm.refreshAndPickFastest() },
            enabled = state !is VpnViewModel.UiState.Connected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            border = BorderStroke(1.dp, AccBlue.copy(0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccBlue),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Täzelemek", fontSize = 14.sp)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConnectionStatusBadge(state: VpnViewModel.UiState) {
    val (label, color) = when (state) {
        is VpnViewModel.UiState.Connected -> "Işjeň"    to AccGreen
        VpnViewModel.UiState.Connecting   -> "Baglanylýar" to AccAmber
        is VpnViewModel.UiState.Ready     -> "Taýyn"    to AccBlue
        else                              -> "Öçük"     to TxtSec
    }
    val animColor by animateColorAsState(color, tween(400), label = "badge")

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(animColor.copy(0.12f))
            .border(1.dp, animColor.copy(0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(animColor))
        Spacer(Modifier.width(6.dp))
        Text(label, color = animColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PowerButton(state: VpnViewModel.UiState, onClick: () -> Unit) {
    val isConnected  = state is VpnViewModel.UiState.Connected
    val isConnecting = state == VpnViewModel.UiState.Connecting
    val isReady      = state is VpnViewModel.UiState.Ready
    val isEnabled    = isConnected || isReady

    val accentColor by animateColorAsState(
        when {
            isConnected  -> AccGreen
            isConnecting -> AccAmber
            isReady      -> AccBlue
            else         -> Color(0xFF1E2D45)
        },
        tween(500), label = "accent"
    )

    val inf = rememberInfiniteTransition(label = "power")
    val pulseScale by inf.animateFloat(
        1f, 1.15f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        "pulse"
    )
    val pulseAlpha by inf.animateFloat(
        0.15f, 0.40f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        "palpha"
    )
    val spin by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1200, easing = LinearEasing)),
        "spin"
    )

    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer glow rings
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            listOf(108.dp.toPx() to 0.05f, 94.dp.toPx() to 0.09f, 80.dp.toPx() to 0.14f)
                .forEach { (r, a) -> drawCircle(accentColor.copy(a), r, c) }
        }

        // Spinning arc when connecting
        if (isConnecting) {
            Canvas(
                Modifier
                    .size(200.dp)
                    .rotate(spin)
            ) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, AccAmber.copy(0.7f), AccAmber)),
                    0f, 260f, false,
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Pulse ring when connected
        if (isConnected) {
            Canvas(Modifier.size(188.dp * pulseScale)) {
                drawCircle(AccGreen.copy(pulseAlpha * 0.5f), style = Stroke(1.5.dp.toPx()))
            }
        }

        // Static border ring
        Canvas(Modifier.size(188.dp)) {
            drawCircle(accentColor.copy(0.30f), style = Stroke(1.dp.toPx()))
        }

        // Main button circle
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(accentColor.copy(0.22f), accentColor.copy(0.06f))
                    )
                )
                .border(
                    BorderStroke(
                        2.dp,
                        Brush.linearGradient(
                            listOf(accentColor.copy(0.85f), accentColor.copy(0.25f))
                        )
                    ),
                    CircleShape
                )
                .clickable(enabled = isEnabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val iconVec = when {
                    isConnected  -> Icons.Filled.CheckCircle
                    isConnecting -> Icons.Filled.PowerSettingsNew
                    else         -> Icons.Filled.PowerSettingsNew
                }
                Icon(
                    iconVec, null,
                    tint = if (isEnabled) accentColor else TxtSec,
                    modifier = Modifier.size(52.dp),
                )
                if (isConnected) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "KES",
                        color = AccGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficCard() {
    val stats by TrafficCounter.stats.collectAsStateWithLifecycle()

    GlassCard(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)) {
        Text(
            "Trafik Maglumat",
            color = TxtSec,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            TrafficCell(
                icon = Icons.Filled.Schedule,
                value = TrafficCounter.formatDuration(stats.connectedSeconds),
                label = "Wagt",
                color = AccBlue,
            )
            TrafficCell(
                icon = Icons.Filled.ArrowDownward,
                value = TrafficCounter.formatSpeed(stats.downloadSpeed),
                label = "Ýüklemek",
                color = AccGreen,
            )
            TrafficCell(
                icon = Icons.Filled.ArrowUpward,
                value = TrafficCounter.formatSpeed(stats.uploadSpeed),
                label = "Ugratmak",
                color = AccAmber,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(
                "↓ ${TrafficCounter.formatBytes(stats.downloadBytes)}",
                fontSize = 11.sp, color = TxtSec,
            )
            Text(
                "↑ ${TrafficCounter.formatBytes(stats.uploadBytes)}",
                fontSize = 11.sp, color = TxtSec,
            )
        }
    }
}

@Composable
private fun TrafficCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = TxtPri, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TxtSec, fontSize = 10.sp)
    }
}

@Composable
private fun ActiveServerCard(
    server: com.tmvpn.app.model.ServerConfig,
    isConnected: Boolean,
) {
    val (flag, displayName) = remember(server.remark) { extractFlag(server.remark) }
    val borderColor = if (isConnected) AccGreen.copy(0.5f) else GlassBorder

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        borderColor = borderColor,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccBlue.copy(0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (flag != null) {
                    Text(flag, fontSize = 24.sp)
                } else {
                    Icon(Icons.Filled.Public, null, tint = AccBlue, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isConnected) "Işjeň Serwer" else "Saýlanan Serwer",
                    color = TxtSec, fontSize = 11.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(displayName, color = TxtPri, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${server.protocol.name} · ${server.network.uppercase()}",
                    color = TxtSec, fontSize = 12.sp,
                )
            }
            if (isConnected) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AccGreen)
                )
            }
        }
    }
}

// ─── SERVERS SCREEN ──────────────────────────────────────────────────────────
@Composable
private fun ServersScreen(vm: VpnViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "Serwerleri",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TxtPri,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Baglanyşmak üçin serwer saýlaň",
            fontSize = 13.sp, color = TxtSec,
        )
        Spacer(Modifier.height(16.dp))

        val isConnected = state is VpnViewModel.UiState.Connected

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (!isConnected) vm.refreshAndPickFastest() },
                enabled = !isConnected,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, GlassBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSec),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Täzelemek", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = { if (!isConnected) vm.refreshAndPickFastest() },
                enabled = !isConnected,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, GlassBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSec),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.Speed, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Synag et", fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        when (val st = state) {
            is VpnViewModel.UiState.Connected -> {
                GlassCard(Modifier.fillMaxWidth(), borderColor = AccAmber.copy(0.3f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = AccAmber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Baglandy — serwer üýtgetmek üçin ilki kesişi kes",
                            color = AccAmber, fontSize = 13.sp,
                        )
                    }
                }
            }
            is VpnViewModel.UiState.Ready -> {
                Text(
                    "${st.results.size} serwer",
                    fontSize = 12.sp, color = TxtSec,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(st.results, key = { it.config.id }) { r ->
                        ServerRow(
                            result = r,
                            isSelected = r.config.id == st.selected.id,
                            onClick = { vm.selectServer(r.config) },
                        )
                    }
                }
            }
            is VpnViewModel.UiState.Loading -> LoadingIndicator("Ýüklenilýär...")
            is VpnViewModel.UiState.Error   -> ErrorCard(
                message = "Näsazlyk: ${st.message}",
                onRetry = { vm.refreshAndPickFastest() },
            )
            else -> Unit
        }
    }
}

@Composable
private fun ServerRow(
    result: ServerPinger.Result,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val (flag, displayName) = remember(result.config.remark) { extractFlag(result.config.remark) }
    val latColor = latencyColor(result.latencyMs, result.isReachable)
    val bars     = signalBars(result.latencyMs, result.isReachable)
    val dotColor = statusDotColor(result.status)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected)
                    Brush.horizontalGradient(listOf(AccBlue.copy(0.15f), BgCard))
                else
                    Brush.horizontalGradient(listOf(BgCard, BgCard))
            )
            .border(
                BorderStroke(
                    if (isSelected) 1.dp else 0.5.dp,
                    if (isSelected) AccBlue.copy(0.5f) else GlassBorder,
                ),
                RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Flag / icon box
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(dotColor.copy(0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (flag != null) {
                    Text(flag, fontSize = 22.sp)
                } else {
                    Icon(Icons.Filled.Public, null, tint = dotColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            // Name + protocol
            Column(Modifier.weight(1f)) {
                Text(
                    displayName,
                    color = if (isSelected) TxtPri else TxtPri.copy(0.88f),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccBlue.copy(0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            result.config.protocol.name,
                            color = AccBlue.copy(0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(result.config.network.uppercase(), color = TxtMuted, fontSize = 10.sp)
                }
            }
            // Signal bars + ping badge
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    (1..3).forEach { i ->
                        Box(
                            Modifier
                                .width(3.dp)
                                .height((i * 5 + 4).dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (i <= bars) latColor else TxtMuted)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                PingBadge(result)
            }
        }
    }
}

@Composable
private fun PingBadge(result: ServerPinger.Result) {
    val (text, color) = when (result.status) {
        ServerPinger.Status.TESTING     -> "..."             to TxtSec
        ServerPinger.Status.OK          -> "${result.latencyMs}ms" to latencyColor(result.latencyMs, true)
        ServerPinger.Status.TLS_BLOCKED -> "Petiklendi"      to AccOrange
        ServerPinger.Status.UNREACHABLE -> "Elýok"           to AccRed
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

// ─── SETTINGS SCREEN ─────────────────────────────────────────────────────────
@Composable
private fun SettingsScreen(vm: VpnViewModel) {
    val ctx      = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hwid     = remember { HwidManager.getHwid(ctx) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Sazlamalar", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TxtPri)
        Spacer(Modifier.height(4.dp))
        Text("Programma sazlamalary", fontSize = 13.sp, color = TxtSec)
        Spacer(Modifier.height(20.dp))

        // Fragment toggle
        GlassSettingsGroup(title = "Fragmentleme", icon = Icons.Filled.Shield) {
            SettingsToggleRow(
                label       = "Fragment",
                description = "TLS bölme ulgamy",
                checked     = settings.fragmentEnabled,
                onChecked   = { vm.updateSettings { copy(fragmentEnabled = it) } },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Mux toggle
        GlassSettingsGroup(title = "Mux", icon = Icons.Filled.Merge) {
            SettingsToggleRow(
                label       = "Mux",
                description = "Birnäçe kanal birleşimi",
                checked     = settings.muxEnabled,
                onChecked   = { vm.updateSettings { copy(muxEnabled = it) } },
            )
        }

        Spacer(Modifier.height(12.dp))

        // UDP 443 block toggle
        GlassSettingsGroup(title = "Ugurlandyrma", icon = Icons.Filled.Route) {
            SettingsToggleRow(
                label       = "UDP 443 bloklama",
                description = "QUIC protokolyny bloklap, durnuklylygy gowulaşdyrýar",
                checked     = settings.blockUdp443,
                onChecked   = { vm.updateSettings { copy(blockUdp443 = it) } },
            )
        }

        Spacer(Modifier.height(12.dp))

        // HWID card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Fingerprint, null, tint = AccBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Enjam ID:", color = TxtSec, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF060B12))
                    .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    hwid,
                    color = TxtPri,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("hwid", hwid))
                    Toast.makeText(ctx, "Göçürildi", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccBlue.copy(0.15f),
                    contentColor = AccBlue,
                ),
                border = BorderStroke(1.dp, AccBlue.copy(0.4f)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Göçür", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun GlassSettingsGroup(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "ch")

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon, null,
                tint = if (expanded) AccBlue else TxtSec,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                color = TxtPri,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ExpandMore, null,
                tint = if (expanded) AccBlue else TxtSec,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevron),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit    = shrinkVertically(tween(150)) + fadeOut(tween(150)),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = GlassBorder.copy(0.5f), thickness = 0.5.dp)
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TxtPri, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(description, color = TxtSec, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = AccGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = TxtMuted,
            ),
        )
    }
}

// ─── Shared composables ───────────────────────────────────────────────────────
@Composable
private fun LoadingIndicator(message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = AccBlue,
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(message, color = TxtSec, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        borderColor = AccRed.copy(0.4f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Warning, null, tint = AccRed, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                color = TxtSec,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetry,
                border = BorderStroke(1.dp, AccBlue.copy(0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccBlue),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Täzeden synanyş", fontSize = 13.sp)
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
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

private fun statusDotColor(status: ServerPinger.Status): Color = when (status) {
    ServerPinger.Status.OK          -> AccGreen
    ServerPinger.Status.TESTING     -> AccAmber
    ServerPinger.Status.TLS_BLOCKED -> AccOrange
    ServerPinger.Status.UNREACHABLE -> AccRed
}

private fun extractFlag(name: String): Pair<String?, String> {
    if (name.length < 4) return null to name
    val cp1 = name.codePointAt(0)
    if (cp1 !in 0x1F1E6..0x1F1FF) return null to name
    val cp2 = name.codePointAt(2)
    if (cp2 !in 0x1F1E6..0x1F1FF) return null to name
    return name.substring(0, 4) to name.substring(4).trimStart()
}

// ─── LOG SCREEN ───────────────────────────────────────────────────────────────
@Composable
private fun LogScreen() {
    val ctx   = LocalContext.current
    val logs  by LogBus.logs.collectAsStateWithLifecycle()
    val state = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) state.animateScrollToItem(logs.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Log",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TxtPri,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("log", LogBus.allText()))
                    android.widget.Toast.makeText(ctx, "Log kopyalandy", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, null, tint = AccBlue, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { LogBus.clear() }) {
                    Icon(Icons.Filled.DeleteSweep, null, tint = AccRed, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Häzirlikçe log ýok", color = TxtSec, fontSize = 14.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF060B12))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(8.dp),
            ) {
                LazyColumn(state = state) {
                    items(logs) { entry ->
                        val color = when {
                            entry.contains("HATA") -> AccRed.copy(0.9f)
                            entry.contains("OK") || entry.contains("BAGLANDI") -> AccGreen.copy(0.9f)
                            entry.contains("baslatil") || entry.contains("startup") -> AccBlue.copy(0.85f)
                            else -> TxtSec
                        }
                        Text(
                            text = entry,
                            color = color,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}
