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
import com.seyit474.tmvpn.model.ServerConfig
import com.seyit474.tmvpn.ping.ServerPinger
import com.seyit474.tmvpn.ui.theme.*

class MainActivity : ComponentActivity() {
    private val vm: VpnViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) vm.onConnectClicked()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TmVpnTheme {
                HomeScreen(
                    vm          = vm,
                    onConnect   = { requestVpnPermission() },
                    onDisconnect = {
                        vm.markDisconnected()
                        vm.refreshAndPickFastest()
                    },
                )
            }
        }
        vm.refreshAndPickFastest()
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermissionLauncher.launch(intent) else vm.onConnectClicked()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    vm: VpnViewModel,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
    ) {
        // Subtle ambient top glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ColorBlue.copy(alpha = 0.04f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar()

            Spacer(Modifier.height(16.dp))

            StatusBadge(state)

            Spacer(Modifier.height(32.dp))

            ConnectOrb(
                state = state,
                onClick = {
                    when (state) {
                        is VpnViewModel.UiState.Connected -> onDisconnect()
                        is VpnViewModel.UiState.Ready    -> onConnect()
                        else                              -> Unit
                    }
                },
            )

            Spacer(Modifier.height(28.dp))

            // Dynamic lower panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                when (val s = state) {
                    is VpnViewModel.UiState.Ready -> ServerPanel(
                        results    = s.results,
                        selectedId = s.selected.id,
                        onSelect   = vm::selectServer,
                    )
                    is VpnViewModel.UiState.Connected -> ConnectedPanel(s.server)
                    is VpnViewModel.UiState.Loading   -> StatusPanel("Sunucular alınıyor…")
                    is VpnViewModel.UiState.Testing   -> StatusPanel("Hızlar ölçülüyor…")
                    is VpnViewModel.UiState.Error     -> ErrorPanel(s.message, vm::refreshAndPickFastest)
                    else                              -> Unit
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Security,
                contentDescription = null,
                tint     = ColorBlue,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "TmVPN",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = ColorTextPrimary,
            )
        }
        IconButton(onClick = {}) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "Ayarlar",
                tint     = ColorTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(state: VpnViewModel.UiState) {
    val (label, color, icon) = when (state) {
        is VpnViewModel.UiState.Connected  -> Triple("KORUNUYOR",   ColorGreen,         Icons.Rounded.Lock)
        is VpnViewModel.UiState.Connecting -> Triple("BAĞLANIYOR",  ColorAmber,         Icons.Rounded.Shield)
        is VpnViewModel.UiState.Error      -> Triple("HATA",        ColorRed,           Icons.Rounded.Warning)
        else                               -> Triple("KORUNMUYOR",  ColorTextSecondary, Icons.Rounded.LockOpen)
    }

    val tintAnim by animateColorAsState(color, tween(400), label = "badge")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(tintAnim.copy(alpha = 0.10f))
            .border(1.dp, tintAnim.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tintAnim, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color         = tintAnim,
            fontSize      = 12.sp,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Premium connect orb
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectOrb(state: VpnViewModel.UiState, onClick: () -> Unit) {
    val isConnected  = state is VpnViewModel.UiState.Connected
    val isConnecting = state is VpnViewModel.UiState.Connecting
    val isReady      = state is VpnViewModel.UiState.Ready
    val isEnabled    = isConnected || isReady

    val accentColor by animateColorAsState(
        targetValue = when {
            isConnected  -> ColorGreen
            isConnecting -> ColorAmber
            isReady      -> ColorBlue
            else         -> Color(0xFF1A2440)
        },
        animationSpec = tween(500),
        label         = "accent",
    )

    val infinite = rememberInfiniteTransition(label = "orb")

    // Pulse ring scale & alpha (connected state only)
    val pulseScale by infinite.animateFloat(
        initialValue  = 1.00f, targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "scale",
    )
    val pulseAlpha by infinite.animateFloat(
        initialValue  = 0.18f, targetValue = 0.40f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "alpha",
    )

    // Spinner rotation (connecting state only)
    val spinAngle by infinite.animateFloat(
        initialValue  = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label         = "spin",
    )

    Box(modifier = Modifier.size(244.dp), contentAlignment = Alignment.Center) {

        // Glow layers — concentric circles
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val c  = Offset(cx, cy)
            listOf(
                106.dp.toPx() to 0.04f,
                92.dp.toPx()  to 0.08f,
                80.dp.toPx()  to 0.13f,
            ).forEach { (r, a) ->
                drawCircle(accentColor.copy(alpha = a), radius = r, center = c)
            }
        }

        // Spinning arc (connecting only)
        if (isConnecting) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .size(200.dp)
                    .rotate(spinAngle),
            ) {
                drawArc(
                    brush      = Brush.sweepGradient(
                        listOf(Color.Transparent, ColorAmber.copy(0.7f), ColorAmber)
                    ),
                    startAngle = 0f,
                    sweepAngle = 260f,
                    useCenter  = false,
                    style      = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        // Pulse ring (connected only)
        if (isConnected) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(188.dp * pulseScale),
            ) {
                drawCircle(
                    color  = ColorGreen.copy(alpha = pulseAlpha * 0.5f),
                    style  = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        // Static ring border
        androidx.compose.foundation.Canvas(modifier = Modifier.size(188.dp)) {
            drawCircle(
                color = accentColor.copy(alpha = 0.30f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        // Core button
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.22f), accentColor.copy(alpha = 0.06f))
                    )
                )
                .border(
                    width  = 2.dp,
                    brush  = Brush.linearGradient(
                        listOf(accentColor.copy(alpha = 0.80f), accentColor.copy(alpha = 0.25f))
                    ),
                    shape  = CircleShape,
                )
                .clickable(enabled = isEnabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isConnected) Icons.Rounded.CheckCircle else Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint     = if (isEnabled) accentColor else ColorTextSecondary,
                    modifier = Modifier.size(46.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (state) {
                        is VpnViewModel.UiState.Connected  -> "KES"
                        is VpnViewModel.UiState.Connecting -> "• • •"
                        is VpnViewModel.UiState.Ready      -> "BAĞLAN"
                        is VpnViewModel.UiState.Loading    -> "YÜKLENİYOR"
                        is VpnViewModel.UiState.Testing    -> "TEST"
                        else                               -> "HAZIR DEĞİL"
                    },
                    color         = if (isEnabled) accentColor else ColorTextSecondary,
                    fontSize      = 13.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Server panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ServerPanel(
    results: List<ServerPinger.Result>,
    selectedId: String,
    onSelect: (ServerConfig) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(bottom = 10.dp, start = 2.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Sunucular",
                color      = ColorTextPrimary,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f),
            )
            Text(
                "${results.count { it.isReachable }} / ${results.size} erişilebilir",
                color    = ColorTextSecondary,
                fontSize = 12.sp,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.config.id }) { r ->
                ServerRow(
                    result   = r,
                    selected = r.config.id == selectedId,
                    onClick  = { onSelect(r.config) },
                )
            }
        }
    }
}

@Composable
private fun ServerRow(
    result: ServerPinger.Result,
    selected: Boolean,
    onClick: () -> Unit,
) {
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
                width  = if (selected) 1.dp else 0.5.dp,
                color  = if (selected) ColorBlue.copy(0.45f) else ColorCardStroke,
                shape  = RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Icon box
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (result.isReachable) latColor.copy(0.12f)
                        else ColorTextMuted.copy(0.18f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Language,
                    contentDescription = null,
                    tint     = if (result.isReachable) latColor else ColorTextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            // Name + protocol
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
                        color         = ColorBlue.copy(0.9f),
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Medium,
                        modifier      = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ColorBlue.copy(0.10f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    if (result.config.security != "none") {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint     = ColorTextSecondary,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
            // Signal bars + latency
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
                    color      = latColor,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Connected info panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectedPanel(server: ServerConfig) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        border = BorderStroke(1.dp, ColorGreen.copy(0.28f)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint     = ColorGreen,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Bağlı", color = ColorGreen, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(server.remark, color = ColorTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("${server.address}:${server.port}", color = ColorTextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    server.protocol.name,
                    color         = ColorBlue,
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.Medium,
                    modifier      = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ColorBlue.copy(0.10f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                if (server.security != "none") {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Lock, null, tint = ColorGreen, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(server.security, color = ColorTextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading / Error panels
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusPanel(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(top = 16.dp),
    ) {
        CircularProgressIndicator(
            color        = ColorBlue,
            modifier     = Modifier.size(28.dp),
            strokeWidth  = 2.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(message, color = ColorTextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Rounded.Warning, null, tint = ColorRed, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            color     = ColorTextSecondary,
            fontSize  = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onRetry,
            border  = BorderStroke(1.dp, ColorBlue.copy(0.45f)),
            colors  = ButtonDefaults.outlinedButtonColors(contentColor = ColorBlue),
        ) {
            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tekrar dene", fontSize = 13.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

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
