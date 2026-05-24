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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

// ─── Strings ─────────────────────────────────────────────────────────────────
data class Str(
    val tabHome: String, val tabServers: String, val tabSettings: String,
    val statusActive: String, val statusConnecting: String,
    val statusReady: String, val statusIdle: String, val statusError: String,
    val orbConnected: String, val orbConnect: String, val orbWait: String,
    val activeServer: String, val selectedServer: String,
    val loadingServers: String,
    val pingBlocked: String, val pingUnreachable: String,
    val btnRefresh: String, val btnTest: String,
    val connectedWarning: String,
    val serverCount: (Int) -> String,
    val retry: String,
    val grpLanguage: String, val grpFragment: String, val grpMux: String,
    val grpRouting: String, val grpDns: String, val grpVpn: String,
    val grpApp: String, val grpAccount: String, val grpAbout: String,
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
    val confirm: String,
    val updateCheck: String, val updateAvailable: String,
    val updateDownloading: String, val updateInstall: String, val updateUpToDate: String,
)

private val strTr = Str(
    tabHome = "Ana Sayfa", tabServers = "Sunucular", tabSettings = "Ayarlar",
    statusActive = "Aktif", statusConnecting = "Bağlanıyor",
    statusReady = "Hazır", statusIdle = "Pasif", statusError = "Hata",
    orbConnected = "BAĞLI", orbConnect = "BAĞLAN", orbWait = "BEKLE",
    activeServer = "Aktif Sunucu", selectedServer = "Seçili Sunucu",
    loadingServers = "Sunucular alınıyor…",
    pingBlocked = "Engelli", pingUnreachable = "Yok",
    btnRefresh = "Yenile", btnTest = "Test",
    connectedWarning = "Bağlı — sunucu değiştirmek için önce kes",
    serverCount = { "$it sunucu" }, retry = "Tekrar dene",
    grpLanguage = "Dil", grpFragment = "Fragment", grpMux = "Mux",
    grpRouting = "Yönlendirme", grpDns = "DNS", grpVpn = "VPN",
    grpApp = "Uygulama", grpAccount = "Hesap", grpAbout = "Hakkında",
    settingFragment = "Fragment", settingPackets = "Paketler",
    settingLength = "Uzunluk", settingInterval = "Aralık",
    settingMux = "Mux", settingQuicMux = "QUIC Mux",
    settingUdp443 = "UDP 443 Blokla", settingGoogle = "Google Proxy",
    settingLan = "LAN Bypass", settingSniffing = "Sniffing",
    settingRouteOnly = "Sadece Yönlendirme", settingRemoteDns = "Remote DNS",
    settingMtu = "MTU", settingAutoSelect = "Otomatik Seçim",
    settingLogLevel = "Log", accountId = "Hesap ID",
    copy = "Kopyala", copied = "Kopyalandı",
    version = "Sürüm", developer = "Geliştirici",
    langLabel = "Dil Seçimi",
    langTurkmen = "Türkmençe", langTurkish = "Türkçe", langRussian = "Rusça",
    confirm = "Onayla",
    updateCheck = "Güncelleme Kontrol", updateAvailable = "Güncelleme Mevcut",
    updateDownloading = "İndiriliyor", updateInstall = "Kur", updateUpToDate = "Güncel",
)

private val strTk = Str(
    tabHome = "Baş Sahypa", tabServers = "Serwerler", tabSettings = "Sazlamalar",
    statusActive = "Işjeň", statusConnecting = "Baglanylýar",
    statusReady = "Taýyn", statusIdle = "Işjeň däl", statusError = "Ýalňyşlyk",
    orbConnected = "BAGLANDY", orbConnect = "BAGLAN", orbWait = "GARAŞ",
    activeServer = "Işjeň Serwer", selectedServer = "Saýlanan Serwer",
    loadingServers = "Serwerler ýüklenýär…",
    pingBlocked = "Petiklenen", pingUnreachable = "Elýok",
    btnRefresh = "Täzele", btnTest = "Synag",
    connectedWarning = "Baglandy — üýtgetmek üçin kes",
    serverCount = { "$it serwer" }, retry = "Täzeden synanyş",
    grpLanguage = "Dil", grpFragment = "Fragment", grpMux = "Mux",
    grpRouting = "Ugur", grpDns = "DNS", grpVpn = "VPN",
    grpApp = "Programma", grpAccount = "Hasap", grpAbout = "Barada",
    settingFragment = "Fragment", settingPackets = "Paketler",
    settingLength = "Uzynlyk", settingInterval = "Aralyk",
    settingMux = "Mux", settingQuicMux = "QUIC Mux",
    settingUdp443 = "UDP 443 Bloklama", settingGoogle = "Google Proxy",
    settingLan = "LAN Bypass", settingSniffing = "Sniffing",
    settingRouteOnly = "Diňe Ugur", settingRemoteDns = "Remote DNS",
    settingMtu = "MTU", settingAutoSelect = "Awtomatik Saýlaw",
    settingLogLevel = "Log", accountId = "Hasap ID",
    copy = "Göçür", copied = "Göçürildi",
    version = "Wersiýa", developer = "Işläp düzüji",
    langLabel = "Dil saýlawy",
    langTurkmen = "Türkmençe", langTurkish = "Türkçe", langRussian = "Rusça",
    confirm = "Tassykla",
    updateCheck = "Täzeleme barla", updateAvailable = "Täze wersiýa bar",
    updateDownloading = "Ýüklenýär", updateInstall = "Gur", updateUpToDate = "Täze",
)

private val strRu = Str(
    tabHome = "Главная", tabServers = "Серверы", tabSettings = "Настройки",
    statusActive = "Активен", statusConnecting = "Подключение",
    statusReady = "Готов", statusIdle = "Неактивен", statusError = "Ошибка",
    orbConnected = "ПОДКЛЮЧЁН", orbConnect = "ПОДКЛЮЧИТЬ", orbWait = "ОЖИДАЙТЕ",
    activeServer = "Активный сервер", selectedServer = "Выбранный сервер",
    loadingServers = "Загрузка серверов…",
    pingBlocked = "Заблокирован", pingUnreachable = "Недоступен",
    btnRefresh = "Обновить", btnTest = "Тест",
    connectedWarning = "Подключено — отключитесь для смены",
    serverCount = { "$it серверов" }, retry = "Повторить",
    grpLanguage = "Язык", grpFragment = "Фрагмент", grpMux = "Mux",
    grpRouting = "Маршрутизация", grpDns = "DNS", grpVpn = "VPN",
    grpApp = "Приложение", grpAccount = "Аккаунт", grpAbout = "О программе",
    settingFragment = "Фрагмент", settingPackets = "Пакеты",
    settingLength = "Длина", settingInterval = "Интервал",
    settingMux = "Mux", settingQuicMux = "QUIC Mux",
    settingUdp443 = "Блок UDP 443", settingGoogle = "Google Proxy",
    settingLan = "LAN Bypass", settingSniffing = "Сниффинг",
    settingRouteOnly = "Только маршрут", settingRemoteDns = "Remote DNS",
    settingMtu = "MTU", settingAutoSelect = "Авто-выбор",
    settingLogLevel = "Лог", accountId = "ID аккаунта",
    copy = "Копировать", copied = "Скопировано",
    version = "Версия", developer = "Разработчик",
    langLabel = "Выбор языка",
    langTurkmen = "Туркменский", langTurkish = "Турецкий", langRussian = "Русский",
    confirm = "Подтвердить",
    updateCheck = "Проверить обновление", updateAvailable = "Доступно обновление",
    updateDownloading = "Загрузка", updateInstall = "Установить", updateUpToDate = "Актуально",
)

private val LocalStr = compositionLocalOf { strTr }
private const val PREF_LANG = "app_lang"

// ─── Tabs ─────────────────────────────────────────────────────────────────────
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
                    colorScheme = darkColorScheme(primary = AccBlue, background = BgDeep, surface = BgCard)
                ) {
                    AppRoot(
                        vm = vm,
                        langCode = langCode,
                        onLangChange = { code -> langCode = code; prefs.edit().putString(PREF_LANG, code).apply() },
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
        val A  = com.seyit474.tmvpn.settings.AppSettings
        val D  = com.seyit474.tmvpn.settings.AppSettings.Defaults
        fun <T> g(k: androidx.datastore.preferences.core.Preferences.Key<T>, d: T) = A.getSync(this, k, d)

        val configJson = XrayConfigBuilder.build(
            cfg,
            enableFragment   = g(A.FRAGMENT_ENABLED,   D.FRAGMENT_ENABLED),
            hwidUuid         = com.seyit474.tmvpn.hwid.HwidManager.getHwid(this),
            fragmentPackets  = g(A.FRAGMENT_PACKETS,   D.FRAGMENT_PACKETS),
            fragmentLength   = g(A.FRAGMENT_LENGTH,    D.FRAGMENT_LENGTH),
            fragmentInterval = g(A.FRAGMENT_INTERVAL,  D.FRAGMENT_INTERVAL),
            fragmentMaxSplit = g(A.FRAGMENT_MAX_SPLIT, D.FRAGMENT_MAX_SPLIT),
            noisesEnabled    = g(A.NOISES_ENABLED,     D.NOISES_ENABLED),
            noiseType        = g(A.NOISE_TYPE,         D.NOISE_TYPE),
            noisePacket      = g(A.NOISE_PACKET,       D.NOISE_PACKET),
            noiseDelay       = g(A.NOISE_DELAY,        D.NOISE_DELAY),
            preferIpType     = g(A.PREFER_IP_TYPE,     D.PREFER_IP_TYPE),
            muxEnabled       = g(A.MUX_ENABLED,        D.MUX_ENABLED),
            muxConcurrency   = g(A.MUX_CONCURRENCY,    D.MUX_CONCURRENCY),
            muxXudpQuic      = g(A.MUX_XUDP_QUIC,      D.MUX_XUDP_QUIC),
            blockUdp443      = g(A.BLOCK_UDP_443,       D.BLOCK_UDP_443),
            proxyGoogle      = g(A.PROXY_GOOGLE,        D.PROXY_GOOGLE),
            bypassLan        = g(A.BYPASS_LAN,          D.BYPASS_LAN),
            sniffingEnabled  = g(A.SNIFFING_ENABLED,    D.SNIFFING_ENABLED),
            logLevel         = g(A.LOG_LEVEL,           D.LOG_LEVEL),
            remoteDns        = g(A.REMOTE_DNS,          D.REMOTE_DNS),
        )
        XrayVpnService.start(this, configJson, cfg.remark)
        vm.markConnected(cfg)
    }
}

// ─── Root ─────────────────────────────────────────────────────────────────────
@Composable
fun AppRoot(vm: VpnViewModel, langCode: String, onLangChange: (String) -> Unit, onConnect: () -> Unit) {
    val s = LocalStr.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingUpdate by remember { mutableStateOf<com.seyit474.tmvpn.update.UpdateManager.UpdateInfo?>(null) }

    // Auto-check for updates once on start
    LaunchedEffect(Unit) {
        pendingUpdate = com.seyit474.tmvpn.update.UpdateManager.checkUpdate()
    }

    // Update banner at top when available
    if (pendingUpdate != null) {
        val upd = pendingUpdate!!
        var downloading by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf(0) }
        Box(
            Modifier
                .fillMaxWidth()
                .background(AccGreen.copy(0.12f))
                .border(BorderStroke(0.5.dp, AccGreen.copy(0.3f)))
                .clickable(enabled = !downloading) {
                    downloading = true
                    scope.launch {
                        val file = com.seyit474.tmvpn.update.UpdateManager.downloadApk(ctx, upd.downloadUrl) { progress = it }
                        if (file != null) com.seyit474.tmvpn.update.UpdateManager.installApk(ctx, file)
                        downloading = false
                        pendingUpdate = null
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (downloading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AccGreen, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${s.updateDownloading} %$progress", color = AccGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = AccGreen, trackColor = AccGreen.copy(0.2f))
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SystemUpdate, null, tint = AccGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.updateAvailable, color = AccGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(upd.releaseName, color = TxtSec, fontSize = 11.sp)
                    }
                    Text(s.updateInstall, color = AccGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Scaffold(
        containerColor = BgDeep,
        bottomBar = {
            NavigationBar(
                containerColor = BgSurface,
                tonalElevation = 0.dp,
                modifier = Modifier.border(0.5.dp, CardBorder),
            ) {
                NavBtn(Icons.Filled.Home, s.tabHome, tab == Tab.HOME) { tab = Tab.HOME }
                NavBtn(Icons.Filled.Dns, s.tabServers, tab == Tab.SERVERS) { tab = Tab.SERVERS }
                NavBtn(Icons.Filled.Settings, s.tabSettings, tab == Tab.SETTINGS) { tab = Tab.SETTINGS }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(BgDeep)) {
            when (tab) {
                Tab.HOME     -> HomeTab(vm, onConnect)
                Tab.SERVERS  -> ServersTab(vm)
                Tab.SETTINGS -> SettingsTab(langCode, onLangChange, vm)
            }
        }
    }
}

@Composable
private fun RowScope.NavBtn(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected, onClick = onClick,
        icon = { Icon(icon, null, modifier = Modifier.size(22.dp)) },
        label = { Text(label, fontSize = 11.sp) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = AccBlue, selectedTextColor = AccBlue,
            indicatorColor = AccBlue.copy(0.12f),
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
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
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
        ConnectOrb(state, s, onConnect)
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
    val anim by animateColorAsState(color, tween(400), label = "badge")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(anim.copy(0.12f))
            .border(1.dp, anim.copy(0.30f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(anim))
        Spacer(Modifier.width(5.dp))
        Text(label, color = anim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConnectOrb(state: VpnViewModel.UiState, s: Str, onClick: () -> Unit) {
    val isConnected  = state is VpnViewModel.UiState.Connected
    val isConnecting = state == VpnViewModel.UiState.Connecting
    val isReady      = state is VpnViewModel.UiState.Ready
    val isEnabled    = isConnected || isReady

    val accent by animateColorAsState(
        when { isConnected -> AccGreen; isConnecting -> AccAmber; isReady -> AccBlue; else -> Color(0xFF1A2440) },
        tween(500), label = "accent",
    )
    val inf = rememberInfiniteTransition(label = "orb")
    val pulseScale by inf.animateFloat(1f, 1.14f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "ps")
    val pulseAlpha by inf.animateFloat(0.18f, 0.40f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "pa")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing)), "spin")

    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            listOf(106.dp.toPx() to 0.04f, 92.dp.toPx() to 0.08f, 80.dp.toPx() to 0.13f)
                .forEach { (r, a) -> drawCircle(accent.copy(a), r, c) }
        }
        if (isConnecting) Canvas(Modifier.size(200.dp).rotate(spin)) {
            drawArc(Brush.sweepGradient(listOf(Color.Transparent, AccAmber.copy(0.7f), AccAmber)),
                0f, 260f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        if (isConnected) Canvas(Modifier.size(188.dp * pulseScale)) {
            drawCircle(AccGreen.copy(pulseAlpha * 0.5f), style = Stroke(1.5.dp.toPx()))
        }
        Canvas(Modifier.size(188.dp)) { drawCircle(accent.copy(0.30f), style = Stroke(1.dp.toPx())) }
        Box(
            modifier = Modifier
                .size(168.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(0.22f), accent.copy(0.06f))))
                .border(2.dp, Brush.linearGradient(listOf(accent.copy(0.80f), accent.copy(0.25f))), CircleShape)
                .clickable(enabled = isEnabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.PowerSettingsNew,
                    null, tint = if (isEnabled) accent else TxtSec, modifier = Modifier.size(46.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when { isConnected -> s.orbConnected; isConnecting -> "• • •"; isReady -> s.orbConnect; else -> s.orbWait },
                    color = if (isEnabled) accent else TxtSec,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(BgCard),
        border = BorderStroke(0.5.dp, CardBorder),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                TCell(Icons.Filled.Schedule, com.seyit474.tmvpn.util.TrafficCounter.formatDuration(stats.connectedSeconds), AccBlue)
                TCell(Icons.Filled.ArrowDownward, com.seyit474.tmvpn.util.TrafficCounter.formatSpeed(stats.downloadSpeed), AccGreen)
                TCell(Icons.Filled.ArrowUpward, com.seyit474.tmvpn.util.TrafficCounter.formatSpeed(stats.uploadSpeed), AccAmber)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("↓ ${com.seyit474.tmvpn.util.TrafficCounter.formatBytes(stats.downloadBytes)}", fontSize = 11.sp, color = TxtSec)
                Text("↑ ${com.seyit474.tmvpn.util.TrafficCounter.formatBytes(stats.uploadBytes)}", fontSize = 11.sp, color = TxtSec)
            }
        }
    }
}

@Composable
private fun TCell(icon: ImageVector, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = TxtPri, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActiveServerCard(server: com.seyit474.tmvpn.model.ServerConfig, isActive: Boolean, s: Str) {
    val (flag, displayName) = remember(server.remark) { extractFlag(server.remark) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(BgCard),
        border = BorderStroke(0.5.dp, if (isActive) AccGreen.copy(0.25f) else CardBorder),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(AccBlue.copy(0.12f)),
                contentAlignment = Alignment.Center) {
                if (flag != null) {
                    Text(flag, fontSize = 22.sp)
                } else {
                    Icon(Icons.Filled.Public, null, tint = AccBlue, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isActive) s.activeServer else s.selectedServer, color = TxtSec, fontSize = 11.sp)
                Text(displayName, color = TxtPri, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${server.protocol.name} • ${server.network.uppercase()}", color = TxtSec, fontSize = 12.sp)
            }
            if (isActive) Box(Modifier.size(8.dp).clip(CircleShape).background(AccGreen))
        }
    }
}

// ─── SERVERS TAB ──────────────────────────────────────────────────────────────
@Composable
private fun ServersTab(vm: VpnViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = LocalStr.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoSelectAfterTest by remember { mutableStateOf(false) }

    val pingType by com.seyit474.tmvpn.settings.AppSettings.get(
        ctx, com.seyit474.tmvpn.settings.AppSettings.PING_TYPE,
        com.seyit474.tmvpn.settings.AppSettings.Defaults.PING_TYPE,
    ).collectAsStateWithLifecycle(initialValue = com.seyit474.tmvpn.settings.AppSettings.Defaults.PING_TYPE)

    var proxyPingResult by remember { mutableStateOf<String?>(null) }
    var proxyPingRunning by remember { mutableStateOf(false) }

    // Auto-select fastest server when all pings finish
    LaunchedEffect(state) {
        if (autoSelectAfterTest && state is VpnViewModel.UiState.Ready) {
            val results = (state as VpnViewModel.UiState.Ready).results
            if (results.none { it.status == ServerPinger.Status.TESTING } && results.isNotEmpty()) {
                val autoEnabled = com.seyit474.tmvpn.settings.AppSettings.getSync(
                    ctx, com.seyit474.tmvpn.settings.AppSettings.AUTO_SELECT,
                    com.seyit474.tmvpn.settings.AppSettings.Defaults.AUTO_SELECT)
                if (autoEnabled) {
                    val fastest = results.firstOrNull { it.isReachable }
                    if (fastest != null) vm.selectServer(fastest.config)
                }
                autoSelectAfterTest = false
            }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(s.tabServers, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TxtPri)
        Spacer(Modifier.height(16.dp))

        val isConnected = state is VpnViewModel.UiState.Connected
        val isProxyMode = pingType == "proxy"

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (!isConnected) vm.refreshAndPickFastest() }, enabled = !isConnected,
                modifier = Modifier.weight(1f), border = BorderStroke(1.dp, CardBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSec),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp)); Text(s.btnRefresh)
            }
            OutlinedButton(
                onClick = {
                    if (isProxyMode && isConnected) {
                        proxyPingRunning = true
                        proxyPingResult = null
                        scope.launch {
                            val ms = com.seyit474.tmvpn.ping.ServerPinger().proxyPing()
                            proxyPingResult = if (ms != null) "${ms}ms" else "—"
                            proxyPingRunning = false
                        }
                    } else if (!isConnected) {
                        autoSelectAfterTest = true
                        vm.repingExisting()
                    }
                },
                enabled = if (isProxyMode) isConnected && !proxyPingRunning else !isConnected,
                modifier = Modifier.weight(1f), border = BorderStroke(1.dp, CardBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSec),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (proxyPingRunning) CircularProgressIndicator(color = AccBlue, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Speed, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp)); Text(s.btnTest)
            }
        }

        proxyPingResult?.let { result ->
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(AccGreen.copy(0.08f)),
                border = BorderStroke(1.dp, AccGreen.copy(0.25f)),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NetworkCheck, null, tint = AccGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Proxy Ping: $result", color = AccGreen, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (val st = state) {
            is VpnViewModel.UiState.Connected -> {
                if (!isProxyMode) Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(AccAmber.copy(0.08f)),
                    border = BorderStroke(1.dp, AccAmber.copy(0.25f)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val bars = signalBars(r.latencyMs, r.isReachable)
    val (flag, displayName) = remember(r.config.remark) { extractFlag(r.config.remark) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Brush.horizontalGradient(listOf(AccBlue.copy(0.14f), BgCard))
                else Brush.horizontalGradient(listOf(BgCard, BgCard))
            )
            .border(if (selected) 1.dp else 0.5.dp,
                if (selected) AccBlue.copy(0.45f) else CardBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(statusDotColor(r.status).copy(0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (flag != null) {
                    Text(flag, fontSize = 22.sp)
                } else {
                    Icon(Icons.Filled.Public, null, tint = statusDotColor(r.status), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(displayName,
                    color = TxtPri.copy(if (selected) 1f else 0.88f), fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.config.protocol.name,
                        color = AccBlue.copy(0.9f), fontSize = 10.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(AccBlue.copy(0.10f))
                            .padding(horizontal = 6.dp, vertical = 2.dp))
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
                        Box(Modifier.width(3.dp).height((i * 5 + 3).dp).clip(RoundedCornerShape(1.dp))
                            .background(if (i <= bars) latColor else TxtMuted))
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
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

// ─── SETTINGS TAB ────────────────────────────────────────────────────────────
@Composable
private fun SettingsTab(langCode: String, onLangChange: (String) -> Unit, vm: VpnViewModel) {
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()
    val s     = LocalStr.current

    Column(
        Modifier.fillMaxSize().systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(s.tabSettings, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TxtPri)
        Spacer(Modifier.height(16.dp))

        AccGroup(s.grpLanguage, Icons.Filled.Language, defaultExpanded = false) {
            LangRow(s.langTurkmen, "tk", langCode, onLangChange)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            LangRow(s.langTurkish, "tr", langCode, onLangChange)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            LangRow(s.langRussian, "ru", langCode, onLangChange)
        }

        Spacer(Modifier.height(10.dp))

        AccGroup(s.grpFragment, Icons.Filled.Shield) {
            TRow(ctx, scope, Icons.Filled.Shield, s.settingFragment,
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_ENABLED,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_ENABLED)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(ctx, scope, Icons.Filled.Code, s.settingPackets,
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_PACKETS,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_PACKETS,
                listOf("tlshello", "1-3", "1-1"))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(ctx, scope, Icons.Filled.LinearScale, s.settingLength,
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_LENGTH,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_LENGTH,
                listOf("1-3", "5-10", "10-20", "50-100", "100-200"))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(ctx, scope, Icons.Filled.Timer, s.settingInterval,
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_INTERVAL,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_INTERVAL,
                listOf("1-1", "5-10", "10-20", "20-40"))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(ctx, scope, Icons.Filled.ContentCut, "Max Split",
                com.seyit474.tmvpn.settings.AppSettings.FRAGMENT_MAX_SPLIT,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.FRAGMENT_MAX_SPLIT,
                listOf("50-100", "100-200", "200-400"))
        }

        Spacer(Modifier.height(10.dp))

        AccGroup(s.grpMux, Icons.Filled.Merge) {
            TRow(ctx, scope, Icons.Filled.Merge, s.settingMux,
                com.seyit474.tmvpn.settings.AppSettings.MUX_ENABLED,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.MUX_ENABLED)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(ctx, scope, Icons.Filled.Tag, s.settingQuicMux,
                com.seyit474.tmvpn.settings.AppSettings.MUX_XUDP_QUIC,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.MUX_XUDP_QUIC,
                listOf("reject", "allow", "skip"))
        }

        Spacer(Modifier.height(10.dp))

        AccGroup(s.grpRouting, Icons.Filled.Route) {
            TRow(ctx, scope, Icons.Filled.Block, s.settingUdp443,
                com.seyit474.tmvpn.settings.AppSettings.BLOCK_UDP_443,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.BLOCK_UDP_443)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TRow(ctx, scope, Icons.Filled.Search, s.settingGoogle,
                com.seyit474.tmvpn.settings.AppSettings.PROXY_GOOGLE,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.PROXY_GOOGLE)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TRow(ctx, scope, Icons.Filled.Wifi, s.settingLan,
                com.seyit474.tmvpn.settings.AppSettings.BYPASS_LAN,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.BYPASS_LAN)
        }

        Spacer(Modifier.height(10.dp))

        AccGroup("Sniffing", Icons.Filled.Visibility) {
            TRow(ctx, scope, Icons.Filled.Visibility, s.settingSniffing,
                com.seyit474.tmvpn.settings.AppSettings.SNIFFING_ENABLED,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.SNIFFING_ENABLED)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            TRow(ctx, scope, Icons.Filled.Route, s.settingRouteOnly,
                com.seyit474.tmvpn.settings.AppSettings.ROUTE_ONLY,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.ROUTE_ONLY)
        }

        Spacer(Modifier.height(10.dp))

        Spacer(Modifier.height(10.dp))

        // ── Noises ────────────────────────────────────────────────────────────
        AccGroup("Noises", Icons.Filled.WifiTethering) {
            TRow(ctx, scope, Icons.Filled.WifiTethering, "Noises",
                com.seyit474.tmvpn.settings.AppSettings.NOISES_ENABLED,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.NOISES_ENABLED)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(ctx, scope, Icons.Filled.Category, "Tip",
                com.seyit474.tmvpn.settings.AppSettings.NOISE_TYPE,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.NOISE_TYPE,
                listOf("rand", "str", "base64"))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(ctx, scope, Icons.Filled.DataObject, "Paket",
                com.seyit474.tmvpn.settings.AppSettings.NOISE_PACKET,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.NOISE_PACKET,
                listOf("10-50", "50-100", "100-200"))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(ctx, scope, Icons.Filled.Timer, "Gecikme",
                com.seyit474.tmvpn.settings.AppSettings.NOISE_DELAY,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.NOISE_DELAY,
                listOf("5-10", "5-20", "10-30"))
        }

        Spacer(Modifier.height(10.dp))

        AccGroup(s.grpDns, Icons.Filled.Dns) {
            DRow(ctx, scope, Icons.Filled.Public, s.settingRemoteDns,
                com.seyit474.tmvpn.settings.AppSettings.REMOTE_DNS,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.REMOTE_DNS,
                listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"))
        }

        Spacer(Modifier.height(10.dp))

        AccGroup(s.grpVpn, Icons.Filled.VpnKey) {
            DRow(ctx, scope, Icons.Filled.NetworkCheck, s.settingMtu,
                com.seyit474.tmvpn.settings.AppSettings.VPN_MTU,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.VPN_MTU,
                listOf(1500, 1420, 1400, 1380))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(ctx, scope, Icons.Filled.Lan, "IP Türü",
                com.seyit474.tmvpn.settings.AppSettings.PREFER_IP_TYPE,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.PREFER_IP_TYPE,
                listOf("auto", "ipv4", "ipv6"))
        }

        Spacer(Modifier.height(10.dp))

        AccGroup(s.grpApp, Icons.Filled.Apps) {
            TRow(ctx, scope, Icons.Filled.AutoMode, s.settingAutoSelect,
                com.seyit474.tmvpn.settings.AppSettings.AUTO_SELECT, true)
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            DRow(ctx, scope, Icons.Filled.BugReport, s.settingLogLevel,
                com.seyit474.tmvpn.settings.AppSettings.LOG_LEVEL,
                com.seyit474.tmvpn.settings.AppSettings.Defaults.LOG_LEVEL,
                listOf("warning", "info", "debug", "error"))
        }

        Spacer(Modifier.height(10.dp))

        PingSection(ctx, scope, vm)

        Spacer(Modifier.height(10.dp))

        AccGroup(s.grpAccount, Icons.Filled.AccountCircle) {
            AccountContent(ctx, s)
        }

        Spacer(Modifier.height(10.dp))

        UpdateCard(ctx, s)

        Spacer(Modifier.height(10.dp))

        AccGroup(s.grpAbout, Icons.Filled.Info) {
            IRow(Icons.Filled.Smartphone, s.version, "0.1.0 (build ${com.seyit474.tmvpn.BuildConfig.VERSION_CODE})")
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            IRow(Icons.Filled.Person, s.developer, "TM Oğuz")
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ─── Accordion group ──────────────────────────────────────────────────────────
@Composable
private fun AccGroup(
    title: String,
    icon: ImageVector,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "ch")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(BgCard),
        border   = BorderStroke(0.5.dp, if (expanded) AccBlue.copy(0.30f) else CardBorder),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (expanded) AccBlue else TxtSec, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = TxtPri, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ExpandMore, null,
                    tint = if (expanded) AccBlue else TxtSec,
                    modifier = Modifier.size(20.dp).rotate(chevron))
            }
            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit    = shrinkVertically(tween(150)) + fadeOut(tween(150)),
            ) {
                Column {
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    Column(content = content)
                }
            }
        }
    }
}

@Composable
private fun LangRow(label: String, code: String, current: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(code) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TxtPri, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (code == current) Icon(Icons.Filled.CheckCircle, null, tint = AccGreen, modifier = Modifier.size(20.dp))
        else Icon(Icons.Filled.RadioButtonUnchecked, null, tint = TxtMuted, modifier = Modifier.size(20.dp))
    }
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
        modifier = Modifier.fillMaxWidth()
            .clickable { scope.launch { com.seyit474.tmvpn.settings.AppSettings.set(context, key, !value) } }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AccBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TxtPri, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = value,
            onCheckedChange = { scope.launch { com.seyit474.tmvpn.settings.AppSettings.set(context, key, it) } },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccGreen, uncheckedTrackColor = TxtMuted),
        )
    }
}

@Composable
private inline fun <reified T> DRow(
    context: Context, scope: CoroutineScope,
    icon: ImageVector, label: String,
    key: androidx.datastore.preferences.core.Preferences.Key<T>,
    default: T, options: List<T>,
    showTextField: Boolean = true,
) {
    val value by com.seyit474.tmvpn.settings.AppSettings.get(context, key, default)
        .collectAsStateWithLifecycle(initialValue = default)
    var showEdit by remember { mutableStateOf(false) }
    var textInput by remember(value) { mutableStateOf(value.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { showEdit = !showEdit }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AccBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TxtPri, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value.toString(), color = AccBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        Icon(if (showEdit) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            null, tint = TxtSec, modifier = Modifier.size(18.dp))
    }

    AnimatedVisibility(showEdit, enter = expandVertically(tween(180)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(150)) + fadeOut(tween(150))) {
        Column(Modifier.background(BgDeep.copy(0.7f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (showTextField) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done,
                        keyboardType = if (T::class == Int::class) KeyboardType.Number else KeyboardType.Text),
                    keyboardActions = KeyboardActions(onDone = {
                        val parsed: T? = when {
                            T::class == String::class -> textInput as? T
                            T::class == Int::class    -> textInput.toIntOrNull() as? T
                            else                      -> null
                        }
                        if (parsed != null) scope.launch { com.seyit474.tmvpn.settings.AppSettings.set(context, key, parsed) }
                        showEdit = false
                    }),
                    trailingIcon = {
                        IconButton(onClick = {
                            val parsed: T? = when {
                                T::class == String::class -> textInput as? T
                                T::class == Int::class    -> textInput.toIntOrNull() as? T
                                else                      -> null
                            }
                            if (parsed != null) scope.launch { com.seyit474.tmvpn.settings.AppSettings.set(context, key, parsed) }
                            showEdit = false
                        }) { Icon(Icons.Filled.Check, null, tint = AccGreen) }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccBlue, unfocusedBorderColor = CardBorder,
                        focusedTextColor = TxtPri, unfocusedTextColor = TxtPri, cursorColor = AccBlue,
                    ),
                )
                Spacer(Modifier.height(8.dp))
            }
            // Preset chips
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { opt ->
                    val isSel = opt == value
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSel) AccBlue.copy(0.20f) else BgCard)
                            .border(1.dp, if (isSel) AccBlue.copy(0.60f) else CardBorder, RoundedCornerShape(20.dp))
                            .clickable {
                                scope.launch { com.seyit474.tmvpn.settings.AppSettings.set(context, key, opt) }
                                textInput = opt.toString()
                                showEdit = false
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(opt.toString(), color = if (isSel) AccBlue else TxtSec, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(context: Context, s: Str) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("idle") } // idle | checking | uptodate | downloading:N | done
    var updateInfo by remember { mutableStateOf<com.seyit474.tmvpn.update.UpdateManager.UpdateInfo?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(BgCard),
        border = BorderStroke(0.5.dp, if (updateInfo != null) AccGreen.copy(0.4f) else CardBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.SystemUpdate, null,
                tint = if (updateInfo != null) AccGreen else AccBlue,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.updateCheck, color = TxtPri, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                when {
                    state == "checking" -> Text("…", color = TxtSec, fontSize = 12.sp)
                    state == "uptodate" -> Text(s.updateUpToDate, color = AccGreen, fontSize = 12.sp)
                    state == "done" -> Text(s.updateInstall, color = AccGreen, fontSize = 12.sp)
                    state.startsWith("downloading") -> {
                        val pct = state.substringAfter(":").toIntOrNull() ?: 0
                        Text("${s.updateDownloading} %$pct", color = AccBlue, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = AccBlue,
                            trackColor = AccBlue.copy(0.15f),
                        )
                    }
                    updateInfo != null -> Text(
                        "${s.updateAvailable}: ${updateInfo!!.releaseName}",
                        color = AccGreen, fontSize = 12.sp,
                    )
                    else -> Text(
                        "Build ${com.seyit474.tmvpn.BuildConfig.VERSION_CODE}",
                        color = TxtSec, fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            when {
                state == "checking" ->
                    CircularProgressIndicator(color = AccBlue, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                updateInfo != null && !state.startsWith("downloading") && state != "done" ->
                    Button(
                        onClick = {
                            scope.launch {
                                val file = com.seyit474.tmvpn.update.UpdateManager.downloadApk(
                                    context, updateInfo!!.downloadUrl
                                ) { state = "downloading:$it" }
                                if (file != null) {
                                    com.seyit474.tmvpn.update.UpdateManager.installApk(context, file)
                                    state = "done"
                                } else state = "idle"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(AccGreen, Color.White),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(s.updateInstall, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                state != "checking" && !state.startsWith("downloading") && state != "done" ->
                    OutlinedButton(
                        onClick = {
                            state = "checking"
                            scope.launch {
                                val info = com.seyit474.tmvpn.update.UpdateManager.checkUpdate()
                                updateInfo = info
                                state = if (info != null) "idle" else "uptodate"
                            }
                        },
                        border = BorderStroke(1.dp, AccBlue.copy(0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(s.updateCheck, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun UpdateRow(context: Context, s: Str) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<String?>(null) } // null=idle, "checking", "uptodate", "downloading:N", "done"
    var updateInfo by remember { mutableStateOf<com.seyit474.tmvpn.update.UpdateManager.UpdateInfo?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.SystemUpdate, null, tint = AccBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.updateCheck, color = TxtPri, fontSize = 14.sp)
            when {
                state == "checking"  -> Text("…", color = TxtSec, fontSize = 12.sp)
                state == "uptodate"  -> Text(s.updateUpToDate, color = AccGreen, fontSize = 12.sp)
                state == "done"      -> Text(s.updateInstall, color = AccGreen, fontSize = 12.sp)
                state?.startsWith("downloading") == true -> {
                    val pct = state!!.substringAfter(":").toIntOrNull() ?: 0
                    Text("${s.updateDownloading} %$pct", color = AccBlue, fontSize = 12.sp)
                    LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = AccBlue, trackColor = AccBlue.copy(0.2f))
                }
                updateInfo != null   -> Text(updateInfo!!.releaseName, color = AccGreen, fontSize = 12.sp)
            }
        }
        when {
            state == "checking" -> CircularProgressIndicator(color = AccBlue, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            updateInfo != null && state != "done" -> OutlinedButton(
                onClick = {
                    scope.launch {
                        val file = com.seyit474.tmvpn.update.UpdateManager.downloadApk(context, updateInfo!!.downloadUrl) { state = "downloading:$it" }
                        if (file != null) { com.seyit474.tmvpn.update.UpdateManager.installApk(context, file); state = "done" }
                    }
                },
                enabled = state?.startsWith("downloading") != true,
                border = BorderStroke(1.dp, AccGreen.copy(0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccGreen),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(s.updateInstall, fontSize = 13.sp) }
            else -> OutlinedButton(
                onClick = {
                    state = "checking"
                    scope.launch {
                        val info = com.seyit474.tmvpn.update.UpdateManager.checkUpdate()
                        if (info != null) { updateInfo = info; state = null }
                        else state = "uptodate"
                    }
                },
                border = BorderStroke(1.dp, CardBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSec),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(s.updateCheck, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun IRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AccBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TxtPri, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TxtSec, fontSize = 13.sp)
    }
}

@Composable
private fun AccountContent(context: Context, s: Str) {
    val hesapId = remember { com.seyit474.tmvpn.hwid.HwidManager.getHwid(context) }
    Column(Modifier.padding(16.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BgDeep).padding(12.dp)) {
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
            colors = ButtonDefaults.buttonColors(AccBlue, Color.White),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.copy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── Ping section ─────────────────────────────────────────────────────────────
@Composable
private fun PingSection(context: Context, scope: CoroutineScope, vm: VpnViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val isConnected = state is VpnViewModel.UiState.Connected
    var proxyResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    AccGroup("Ping", Icons.Filled.Speed) {
        DRow(context, scope, Icons.Filled.NetworkPing, "Ping Türü",
            com.seyit474.tmvpn.settings.AppSettings.PING_TYPE,
            com.seyit474.tmvpn.settings.AppSettings.Defaults.PING_TYPE,
            listOf("tcp", "proxy"),
            showTextField = false)

        if (isConnected) {
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.NetworkCheck, null, tint = AccBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Proxy Ping", color = TxtPri, fontSize = 14.sp)
                    proxyResult?.let { Text(it, color = AccGreen, fontSize = 12.sp) }
                }
                OutlinedButton(
                    onClick = {
                        if (!testing) {
                            testing = true
                            proxyResult = null
                            scope.launch {
                                val ms = com.seyit474.tmvpn.ping.ServerPinger().proxyPing()
                                proxyResult = if (ms != null) "${ms}ms (generate_204)" else "Başarısız"
                                testing = false
                            }
                        }
                    },
                    enabled = !testing,
                    border  = BorderStroke(1.dp, AccBlue.copy(0.4f)),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = AccBlue),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    if (testing) CircularProgressIndicator(color = AccBlue, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Text("Test", fontSize = 13.sp)
                }
            }
        }
    }
}

// ─── Shared panels ────────────────────────────────────────────────────────────
@Composable
private fun LoadingPanel(msg: String) {
    Column(Modifier.padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = AccBlue, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(12.dp))
        Text(msg, color = TxtSec, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorPanel(msg: String, retryLabel: String, onRetry: () -> Unit) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Warning, null, tint = AccRed, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text(msg, color = TxtSec, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onRetry, border = BorderStroke(1.dp, AccBlue.copy(0.45f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccBlue)) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp)); Text(retryLabel)
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

// Returns (flagEmoji, nameWithoutFlag). Flag emoji = two regional indicator codepoints (each a surrogate pair = 4 chars).
private fun extractFlag(name: String): Pair<String?, String> {
    if (name.length < 4) return null to name
    val cp1 = name.codePointAt(0)
    if (cp1 !in 0x1F1E6..0x1F1FF) return null to name
    val cp2 = name.codePointAt(2)
    if (cp2 !in 0x1F1E6..0x1F1FF) return null to name
    return name.substring(0, 4) to name.substring(4).trimStart()
}
