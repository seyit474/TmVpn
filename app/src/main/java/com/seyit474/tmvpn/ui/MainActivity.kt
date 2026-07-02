package com.seyit474.tmvpn.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seyit474.tmvpn.service.VpnState
import com.seyit474.tmvpn.service.XrayVpnService
import com.seyit474.tmvpn.ui.theme.TmVpnTheme

class MainActivity : ComponentActivity() {

    private val vm: VpnViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpnService()
    }

    // Android 13+ bildirim izni — kalıcı VPN bildirimi görünsün diye.
    // Reddedilse bile bağlantı akışı devam eder, bildirim sadece gizli kalır.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sonuç bağlayıcı değil */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            TmVpnTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by vm.state.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = state,
                        onRefresh = vm::refresh,
                        onSelectServer = vm::selectServer,
                        onToggleConnection = ::toggleConnection,
                        onErrorConsumed = vm::consumeError,
                        onSaveSubscription = vm::saveSubscriptionUrl
                    )
                }
            }
        }
    }

    private fun toggleConnection() {
        when (vm.state.value.vpnState) {
            is VpnState.Connected, is VpnState.Connecting -> XrayVpnService.stop(this)
            else -> requestVpnPermissionAndConnect()
        }
    }

    private fun requestVpnPermissionAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val intent: Intent? = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val request = vm.buildConnectionRequest() ?: return
        XrayVpnService.start(this, request.configJson, request.serverRemark)
    }
}
