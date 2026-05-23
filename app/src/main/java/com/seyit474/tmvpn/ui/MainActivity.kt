package com.seyit474.tmvpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
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

// ─── Palette ─────────────────────────────────────────────────────────────────
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

// ─── Language strings ─────────────────────────────────────────────────────────
data class Str(
    val tabHome: String, val tabServers: String, val tabSettings: String,
    val statusActive: String, val statusConnecting: String,
    val statusReady: String, val statusIdle: String, val statusError: String,
    val orbConnected: String, val orbConnect: String, val orbWait: String,
    val activeServer: String, val selectedServer: String,
    val loadingServers: String, val testingPing: String,
    val pingBlocked: String, val pingUnreachable: String,
    val btnRefresh: String, val btnTest: String,
    val connectedWarning: String,
    val serverCount: (Int) -> String,
    val retry: String,
    val grpFragment: String, val grpMux: String, val grpRouting: String,
    val grpDns: String, val grpVpn: String, val grpApp: String,
    val grpAbout: String, val grpAccount: String, val grpLanguage: String,
    val settingFragment: String, val settingPackets: String,
    val settingLength: String, val settingInterval: String,
    val settingMux: String, val settingQuicMux: String,
    val settingUdp443: String, val settingGoogle: String, val settingLan: String,
    val settingSniffing: String, val settingRouteOnly: String,
    val settingRemoteDns: String, val settingMtu: String,
    val settingAutoSelect: String, val settingLogLevel: String,
    val accountId: String, val copy: String, val copied: String,
    val version: String, val developer: String,
    val langLabel: String,
    val langTurkmen: String, val langTurkish: String, val langRussian: String,
)

private val strTr = Str(
    tabHome = "Ana Sayfa", tabServers = "Sunucular", tabSettings = "Ayarlar",
    statusActive = "Aktif", statusConnecting = "Bağlanıyor",
    statusReady = "Hazır", statusIdle = "Pasif", statusError = "Hata",
    orbConnected = "BAĞLI", orbConnect = "BAĞLAN", orbWait = "BEKLE",
    activeServer = "Aktif Sunucu", selectedServer = "Seçili Sunucu",
    loadingServers = "Sunucular alınıyor…", testingPing = "Test ediliyor…",
    pingBlocked = "Engelli", pingUnreachable = "Yok",
    btnRefresh = "Yenile", btnTest = "Test",
    connectedWarning = "Bağlı — sunucu değiştirmek için önce kes",
    serverCount = { "$it sunucu" },
    retry = "Tekrar dene",
    grpFragment = "FRAGMENT", grpMux = "MUX", grpRouting = "YÖNLENDİRME",
    grpDns = "DNS", grpVpn = "VPN", grpApp = "UYGULAMA",
    grpAbout = "HAKKINDA", grpAccount = "HESAP", grpLanguage = "DİL",
    settingFragment = "Fragment", settingPackets = "Paketler",
    settingLength = "Uzunluk", settingInterval = "Aralık",
    settingMux = "Mux", settingQuicMux = "QUIC Mux",
    settingUdp443 = "UDP 443 Blokla", settingGoogle = "Google Proxy",
    settingLan = "LAN Bypass",
    settingSniffing = "Sniffing", settingRouteOnly = "Sadece Yönlendirme",
    settingRemoteDns = "Remote DNS", settingMtu = "MTU",
    settingAutoSelect = "Otomatik Seçim", settingLogLevel = "Log",
    accountId = "Hesap ID", copy = "Kopyala", copied = "Kopyalandı",
    version = "Sürüm", developer = "Geliştirici",
    langLabel = "Dil Seçimi",
    langTurkmen = "Türkmençe", langTurkish = "Türkçe", langRussian = "Rusça",
)

private val strTk = Str(
    tabHome = "Baş Sahypa", tabServers = "Serwerler", tabSettings = "Sazlamalar",
    statusActive = "Işjeň", statusConnecting = "Baglanylýar",
    statusReady = "Taýyn", statusIdle = "Işjeň däl", statusError = "Ýalňyşlyk",
    orbConnected = "BAGLANDY", orbConnect = "BAGLAN", orbWait = "GARAŞ",
    activeServer = "Işjeň Serwer", selectedServer = "Saýlanan Serwer",
    loadingServers = "Serwerler ýüklenýär…", testingPing = "Synag edilýär…",
    pingBlocked = "Petiklenen", pingUnreachable = "Elýok",
    btnRefresh = "Täzele", btnTest = "Synag",
    connectedWarning = "Baglandy — üýtgetmek üçin kes",
    serverCount = { "$it serwer" },
    retry = "Täzeden synanyş",
    grpFragment = "BÖLÜNME", grpMux = "MUX", grpRouting = "UGUR",
    grpDns = "DNS", grpVpn = "VPN", grpApp = "PROGRAMMA",
    grpAbout = "BARADA", grpAccount = "HASAP", grpLanguage = "DIL",
    settingFragment = "Fragment", settingPackets = "Paketler",
    settingLength = "Uzynlyk", settingInterval = "Aralyk",
    settingMux = "Mux", settingQuicMux = "QUIC Mux",
    settingUdp443 = "UDP 443 Bloklama", settingGoogle = "Google Proxy",
    settingLan = "LAN Bypass",
    settingSniffing = "Sniffing", settingRouteOnly = "Diňe Ugur",
    settingRemoteDns = "Remote DNS", settingMtu = "MTU",
    settingAutoSelect = "Awtomatik Saýlaw", settingLogLevel = "Log",
    accountId = "Hasap ID", copy = "Göçür", copied = "Göçürildi",
    version = "Wersiýa", developer = "Işläp düzüji",
    langLabel = "Dil saýlawy",
    langTurkmen = "Türkmençe", langTurkish = "Türkçe", langRussian = "Rusça",
)

private val strRu = Str(
    tabHome = "Главная", tabServers = "Серверы", tabSettings = "Настройки",
    statusActive = "Активен", statusConnecting = "Подключение",
    statusReady = "Готов", statusIdle = "Неактивен", statusError = "Ошибка",
    orbConnected = "ПОДКЛЮЧЁН", orbConnect = "ПОДКЛЮЧИТЬ", orbWait = "ОЖИДАЙТЕ",
    activeServer = "Активный сервер", selectedServer = "Выбранный сервер",
    loadingServers = "Загрузка серверов…", testingPing = "Тестирование…",
    pingBlocked = "Заблокирован", pingUnreachable = "Недоступен",
    btnRefresh = "Обновить", btnTest = "Тест",
    connectedWarning = "Подключено — отключитесь для смены",
    serverCount = { "$it серверов" },
    retry = "Повторить",
    grpFragment = "ФРАГМЕНТ", grpMux = "MUX", grpRouting = "МАРШРУТИЗАЦИЯ",
    grpDns = "DNS", grpVpn = "VPN", grpApp = "ПРИЛОЖЕНИЕ",
    grpAbout = "О ПРОГРАММЕ", grpAccount = "АККАУНТ", grpLanguage = "ЯЗЫК",
    settingFragment = "Фрагмент", settingPackets = "Пакеты",
    settingLength = "Длина", settingInterval = "Интервал",
    settingMux = "Mux", settingQuicMux = "QUIC Mux",
    settingUdp443 = "Блок UDP 443", settingGoogle = "Google Proxy",
    settingLan = "LAN Bypass",
    settingSniffing = "Сниффинг", settingRouteOnly = "Только маршрут",
    settingRemoteDns = "Remote DNS", settingMtu = "MTU",
    settingAutoSelect = "Авто-выбор", settingLogLevel = "Лог",
    accountId = "ID аккаунта", copy = "Копировать", copied = "Скопировано",
    version = "Версия", developer = "Разработчик",
    langLabel = "Выбор языка",
    langTurkmen = "Туркменский", langTurkish = "Турецкий", langRussian = "Русский",
)

private val LocalStr = compositionLocalOf { strTr }
private const val PREF_LANG = "app_lang"

// ─── Tab ─────────────────────────────────────────────────────────────────────
private enum class Tab { HOME, SERVERS, SETTINGS }

// ─── Activity ─────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    private val vm: VpnViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> if (result.resultCode == RESULT_OK) startVpnWithSelected() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("tmvpn_ui", Context.MODE_PRIVATE)
        setContent {
            var langCode by remember { mutableStateOf(prefs.getString(PREF_LANG, "tr") ?: "tr") }
            val strings = when (langCode) { "tk" -> strTk; "ru" -> strRu; else -> strTr }

            CompositionLocalProvider(LocalStr provides strings) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary    = AccBlue,
                        background = BgDeep,
                        surface    = BgCard,
                    )
                ) {
                    AppRoot(
                        vm        = vm,
                        prefs     = prefs,
                        langCode  = langCode,
                        onLangChange = { code ->
                            langCode = code
                            prefs.edit().putString(PREF_LANG, code).apply()
                        },
                        onConnect = { onConnectClicked() },
                    )
                }
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
fun AppRoot(
    vm: VpnViewModel,
    prefs: SharedPreferences,
    langCode: String,
    onLangChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    val s = LocalStr.current
    var currentTab by remember { mutableStateOf(Tab.HOME) }

    Scaffold(
        containerColor = BgDeep,
        bottomBar = {
            NavigationBar(
                containerColor = BgSurface,
                tonalElevation = 0.dp,
                modifier       = Modifier.border(0.5.dp, CardBorder),
            ) {
                NavItem(Icons.Filled.Home, s.tabHome, currentTab == Tab.HOME) { currentTab = Tab.HOME }
                NavItem(Icons.Filled.Dns, s.tabServers, currentTab == Tab.SERVERS) { currentTab = Tab.SERVERS }
                NavItem(Icons.Filled.Settings, s.tabSettings, currentTab == Tab.SETTINGS) { currentTab = Tab.SETTINGS }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(BgDeep),
        ) {
            when (currentTab) {
                Tab.HOME     -> HomeTab(vm, onConnect)
                Tab.SERVERS  -> ServersTab(vm)
                Tab.SETTINGS -> SettingsTab(langCode, onLangChange)
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected, onClick = onClick,
        icon     = { Icon(icon, null, modifier = Modifier.size(22.dp)) },
        label    = { Text(label, fontSize = 11.sp) },
        colors   = NavigationBarItemDefaults.colors(
            selectedIconColor   = AccBlue, selectedTextColor = AccBlue,
            indicatorColor      = AccBlue.copy(alpha = 0.12f),
            unselectedIconColor = TxtSec, unselectedTextColor = TxtSec,
        ),
    )
}

// ─── HOME TAB ─────────────────────────────────────────────────────────────────
@Composable
private fun HomeTab(vm: VpnViewModel, onConnect: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = LocalStr.current

    Column(
        modifier            = Modifier.fillMaxSize().systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, null, tint = AccBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("TmVPN", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TxtPri)
                    Text(statusSubtitle(state, s), fontSize = 12.sp, color = TxtSec)
                }
            }
            StatusBadge(state, s)
        }

        Spacer(Modifier.height(8.dp))
        ConnectOrb(state = state, s = s, onClick = onConnect)
        Spacer(Modifier.height(24.dp))

        when (val st = state) {
            is VpnViewModel.UiState.Connected -> {
                TrafficPanel()
                Spacer(Modifier.height(12.dp))
                ActiveServerCard(st.server, true, s)
            }
            is VpnViewModel.UiState.Ready -> ActiveServerCard(st.selected, false, s)
            is VpnViewModel.UiState.Loading -> LoadingPanel(s.loadingServers)
            is VpnViewModel.UiState.Error   -> ErrorPanel(st.message, s.retry) { vm.refreshAndPickFastest() }
            else -> Unit
        }
    }
}

@Composable
private fun StatusBadge(state: VpnViewModel.UiState, s: Str) {
    val (label, color) = when (state) {
        is VpnViewModel.UiState.Connected -> s.statusActive    to AccGreen
        VpnViewModel.UiState.Connecting   -> s.statusConnecting to AccAmber
        is VpnViewModel.UiState.Ready     -> s.statusReady     to AccBlue
        else                              -> s.statusIdle      to TxtSec
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

@Composable
private fun ConnectOrb(state: VpnViewModel.UiState, s: Str, onClick: () -> Unit) {
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
        0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing)), "spin",
    )

    Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            listOf(106.dp.toPx() to 0.04f, 92.dp.toPx() to 0.08f, 80.dp.toPx() to 0.13f)
                .forEach { (r, a) -> drawCircle(accentColor.copy(alpha = a), radius = r, center = c) }
        }
        if (isConnecting) {
            Canvas(modifier = Modifier.size(200.dp).rotate(spinAngle)) {
                drawArc(
                    brush      = Brush.sweepGradient(listOf(Color.Transparent, AccAmber.copy(0.7f), AccAmber)),
                    startAngle = 0f, sweepAngle = 260f, useCenter = false,
                    style      = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        if (isConnected) {
            Canvas(modifier = Modifier.size(188.dp * pulseScale)) {
                drawCircle(AccGreen.copy(alpha = pulseAlpha * 0.5f), style = Stroke(1.5.dp.toPx()))
            }
        }
        Canvas(modifier = Modifier.size(188.dp)) {
            drawCircle(accentColor.copy(alpha = 0.30f), style = Stroke(1.dp.toPx()))
        }
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(accentColor.copy(0.22f), accentColor.copy(0.06f))))
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
                        isConnected  -> s.orbConnected
                        isConnecting -> "• • •"
                        isReady      -> s.orbConnect
                        else         -> s.orbWait
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TrafficCell(Icons.Filled.Schedule,
                    com.seyit474.tmvpn.util.TrafficCounter.formatDuration(stats.connectedSeconds), AccBlue)
                TrafficCell(Icons.Filled.ArrowDownward,
                    com.seyit474.tmvpn.util.TrafficCounter.formatSpeed(stats.downloadSpeed), AccGreen)
                TrafficCell(Icons.Filled.ArrowUpward,
                    com.seyit474.tmvpn.util.TrafficCounter.formatSpeed(stats.uploadSpeed), AccAmber)
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("↓ ${com.seyit474.tmvpn.util.TrafficCounter.formatBytes(stats.downloadBytes)}",
                    fontSize = 11.sp, color = TxtSec)
                Text("↑ ${com.seyit474.tmvpn.util.TrafficCounter.formatBytes(stats.uploadBytes)}",
                    fontSize = 11.sp, color = TxtSec)
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

@Composable
private fun ActiveServerCard(server: com.seyit474.tmvpn.model.ServerConfig, isActive: Boolean, s: Str) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = BgCard),
        border   = BorderStroke(0.5.dp, if (isActive) AccGreen.copy(0.25f) else CardBorder),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(AccBlue.copy(0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Public, null, tint = AccBlue, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isActive) s.activeServer else s.selectedServer, color = TxtSec, fontSize = 11.sp)
                Text(server.remark, color = TxtPri, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${server.protocol.name} • ${server.network.uppercase()}", color = TxtSec, fontSize = 12.sp)
            }
            if (isActive) Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AccGreen))
        }
    }
}

// ─── SERVERS TAB ──────────────────────────────────────────────────────────────
@Composable
private fun ServersTab(vm: VpnViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = LocalStr.current

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(s.tabServers, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TxtPri)
        Spacer(Modifier.height(16.dp))

        val isConnected = state is VpnViewModel.UiState.Connected
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Text(s.btnRefresh)
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
                Text(s.btnTest)
            }
        }

        Spacer(Modifier.height(12.dp))

        when (val st = state) {
            is VpnViewModel.UiState.Connected -> {
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AccAmber.copy(0.08f)),
                    border = BorderStroke(1.dp, AccAmber.copy(0.25f)),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = AccAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.connectedWarning, color = AccAmber, fontSize = 13.sp)
                    }
                }
            }
            is VpnViewModel.UiState.Ready -> {
                Text(s.serverCount(st.results.size), fontSize = 12.sp, color = TxtSec,
                    modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(st.results, key = { it.config.id }) { r ->
                        ServerRow(r, r.config.id == st.selected.id, s) { vm.selectServer(r.config) }
                    }
                }
            }
            is VpnViewModel.UiState.Loading -> LoadingPanel(s.loadingServers)
            is VpnViewModel.UiState.Error   -> ErrorPanel(st.message, s.retry) { vm.refreshAndPickFastest() }
            else -> Unit
        }
    }
}

@Composable
private fun ServerRow(r: ServerPinger.Result, selected: Boolean, s: Str, onClick: () -> Unit) {
    val latColor = latencyColor(r.latencyMs, r.isReachable)
    val bars     = signalBars(r.latencyMs, r.isReachable)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Brush.horizontalGradient(listOf(AccBlue.copy(0.14f), BgCard))
                else          Brush.horizontalGradient(listOf(BgCard, BgCard))
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
                modifier         = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(statusDotColor(r.status).copy(0.12f)),
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
                    Spacer(Modifier.width(4.dp))
                    Text(r.config.network.uppercase(), color = TxtMuted, fontSize = 10.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                    (1..3).forEach { i ->
                        Box(
                            modifier = Modifier
                                .width(3.dp).height((i * 5 + 3).dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (i <= bars) latColor else TxtMuted),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                PingBadge(r, s)
            }
        }
    }
}

@Composable
private fun PingBadge(r: ServerPinger.Result, s: Str) {
    val (text, color) = when (r.status) {
        ServerPinger.Status.TESTING     -> "…"               to TxtSec
        ServerPinger.Status.OK          -> "${r.latencyMs}ms" to latencyColor(r.latencyMs, true)
        ServerPinger.Status.TLS_BLOCKED -> s.pingBlocked     to AccOrange
        ServerPinger.Status.UNREACHABLE -> s.pingUnreachable  to AccRed
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
private fun SettingsTab(langCode: String, onLangChange: (String) -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val s       = LocalStr.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(s.tabSettings, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TxtPri)
        Spacer(Modifier.height(20.dp))

        // ── Language ──────────────────────────────────────────────────────────
        SGroup(s.grpLanguage) {
            LangRow(Icons.Filled.Language, s.langTurkmen, "tk", langCode, onLangChange)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            LangRow(Icons.Filled.Language, s.langTurkish, "tr", langCode, onLangChange)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            LangRow(Icons.Filled.Language, s.langRussian, "ru", langCode, onLangChange)
        }

        Spacer(Modifier.height(16.dp))

        // ── Fragment ──────────────────────────────────────────────────────────
        SGroup(s.grpFragment) {
            TRow(context, scope, Icons.Filled.Shield, s.settingFragment,
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_ENABLED,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_ENABLED)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(context, scope, Icons.Filled.Code, s.settingPackets,
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_PACKETS,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_PACKETS,
                listOf("tlshello", "1-3", "1-1"))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(context, scope, Icons.Filled.LinearScale, s.settingLength,
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_LENGTH,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_LENGTH,
                listOf("1-3", "5-10", "10-20", "50-100", "100-200"))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(context, scope, Icons.Filled.Timer, s.settingInterval,
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_INTERVAL,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_INTERVAL,
                listOf("1-1", "5-10", "10-20", "20-40"))
        }

        Spacer(Modifier.height(16.dp))

        // ── MUX ──────────────────────────────────────────────────────────────
        SGroup(s.grpMux) {
            TRow(context, scope, Icons.Filled.Merge, s.settingMux,
                com.seyit474.tmvpn.settings.AppSettings.MUX_ENABLED,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.MUX_ENABLED)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(context, scope, Icons.Filled.Tag, s.settingQuicMux,
                com.seyit474.tmvpn.settings.AppSettings.MUX_XUDP_QUIC,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.MUX_XUDP_QUIC,
                listOf("reject", "allow", "skip"))
        }

        Spacer(Modifier.height(16.dp))

        // ── Routing ───────────────────────────────────────────────────────────
        SGroup(s.grpRouting) {
            TRow(context, scope, Icons.Filled.Block, s.settingUdp443,
                com.seyit474.tmvpn.settings.AppSettings.BLOCK_UDP_443,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.BLOCK_UDP_443)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TRow(context, scope, Icons.Filled.Search, s.settingGoogle,
                com.seyit474.tmvpn.settings.AppSettings.PROXY_GOOGLE,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.PROXY_GOOGLE)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TRow(context, scope, Icons.Filled.Wifi, s.settingLan,
                com.seyit474.tmvpn.settings.AppSettings.BYPASS_LAN,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.BYPASS_LAN)
        }

        Spacer(Modifier.height(16.dp))

        // ── Sniffing ──────────────────────────────────────────────────────────
        SGroup("SNIFFING") {
            TRow(context, scope, Icons.Filled.Visibility, s.settingSniffing,
                com.seyit474.tmvpn.settings.AppSettings.SNIFFING_ENABLED,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.SNIFFING_ENABLED)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TRow(context, scope, Icons.Filled.Route, s.settingRouteOnly,
                com.seyit474.tmvpn.settings.AppSettings.ROUTE_ONLY,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.ROUTE_ONLY)
        }

        Spacer(Modifier.height(16.dp))

        // ── DNS ───────────────────────────────────────────────────────────────
        SGroup(s.grpDns) {
            DRow(context, scope, Icons.Filled.Public, s.settingRemoteDns,
                com.seyit474.tmvpn.settings.AppSettings.REMOTE_DNS,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.REMOTE_DNS,
                listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"))
        }

        Spacer(Modifier.height(16.dp))

        // ── VPN ───────────────────────────────────────────────────────────────
        SGroup(s.grpVpn) {
            DRow(context, scope, Icons.Filled.NetworkCheck, s.settingMtu,
                com.seyit474.tmvpn.settings.AppSettings.VPN_MTU,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.VPN_MTU,
                listOf(1500, 1420, 1400, 1380))
        }

        Spacer(Modifier.height(16.dp))

        // ── App ───────────────────────────────────────────────────────────────
        SGroup(s.grpApp) {
            TRow(context, scope, Icons.Filled.AutoMode, s.settingAutoSelect,
                com.seyit474.tmvpn.settings.AppSettings.AUTO_SELECT,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.AUTO_SELECT)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(context, scope, Icons.Filled.BugReport, s.settingLogLevel,
                com.seyit474.tmvpn.settings.AppSettings.LOG_LEVEL,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.LOG_LEVEL,
                listOf("warning", "info", "debug", "error"))
        }

        Spacer(Modifier.height(16.dp))

        // ── Account ───────────────────────────────────────────────────────────
        AccountSection(context, s)

        Spacer(Modifier.height(16.dp))

        // ── About ─────────────────────────────────────────────────────────────
        SGroup(s.grpAbout) {
            IRow(Icons.Filled.Smartphone, s.version, "1.0.0")
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            IRow(Icons.Filled.Person, s.developer, "TM Oğuz")
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun LangRow(icon: ImageVector, label: String, code: String, current: String, onChange: (String) -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth().clickable { onChange(code) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AccBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TxtPri, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (code == current) {
            Icon(Icons.Filled.CheckCircle, null, tint = AccGreen, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title, color = TxtSec, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(0.5.dp, CardBorder),
    ) { Column(content = content) }
}

@Composable
private fun TRow(
    context: Context, scope: CoroutineScope,
    icon: ImageVector, label: String,
    key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
    default: Boolean,
) {
    val value by com.seyit474.tmvpn.settings.AppSettings.get(context, key, default)
        .collectAsStateWithLifecycle(initialValue = default)
    Row(
        modifier          = Modifier.fillMaxWidth()
            .clickable { scope.launch { com.seyit474.tmvpn.settings.AppSettings.set(context, key, !value) } }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AccBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TxtPri, fontSize = 14.sp, modifier = Modifier.weight(1f))
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
private inline fun <reified T> DRow(
    context: Context, scope: CoroutineScope,
    icon: ImageVector, label: String,
    key: androidx.datastore.preferences.core.Preferences.Key<T>,
    default: T, options: List<T>,
) {
    val value by com.seyit474.tmvpn.settings.AppSettings.get(context, key, default)
        .collectAsStateWithLifecycle(initialValue = default)
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier          = Modifier.fillMaxWidth().clickable { expanded = !expanded }
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
                    modifier          = Modifier.fillMaxWidth()
                        .clickable { scope.launch { com.seyit474.tmvpn.settings.AppSettings.set(context, key, opt) }; expanded = false }
                        .padding(horizontal = 36.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (opt == value) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                        null, tint = if (opt == value) AccBlue else TxtSec, modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(opt.toString(), color = TxtPri, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun IRow(icon: ImageVector, label: String, value: String) {
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

@Composable
private fun AccountSection(context: Context, s: Str) {
    val hesapId = remember { com.seyit474.tmvpn.hwid.HwidManager.getHwid(context) }

    SGroup(s.grpAccount) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BgDeep).padding(12.dp),
            ) {
                Column {
                    Text(s.accountId, color = TxtSec, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(hesapId, color = TxtPri, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("id", hesapId))
                    Toast.makeText(context, s.copied, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = AccBlue, contentColor = Color.White),
                shape    = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.copy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Shared panels ───────────────────────────────────────────────────────────
@Composable
private fun LoadingPanel(msg: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(top = 32.dp),
    ) {
        CircularProgressIndicator(color = AccBlue, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(12.dp))
        Text(msg, color = TxtSec, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorPanel(msg: String, retryLabel: String, onRetry: () -> Unit) {
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
            Text(retryLabel)
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────
private fun statusSubtitle(s: VpnViewModel.UiState, str: Str): String = when (s) {
    VpnViewModel.UiState.Idle         -> str.statusIdle
    VpnViewModel.UiState.Loading      -> str.loadingServers
    is VpnViewModel.UiState.Ready     -> s.selected.remark
    VpnViewModel.UiState.Connecting   -> str.statusConnecting
    is VpnViewModel.UiState.Connected -> s.server.remark
    is VpnViewModel.UiState.Error     -> str.statusError
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
