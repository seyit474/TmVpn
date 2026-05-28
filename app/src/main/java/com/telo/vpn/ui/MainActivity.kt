package com.telo.vpn.ui

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.telo.vpn.data.AppPreferences
import com.telo.vpn.service.XrayConfigBuilder
import com.telo.vpn.service.XrayVpnService
import com.telo.vpn.ui.screens.HomeScreen
import com.telo.vpn.ui.screens.ServerListScreen
import com.telo.vpn.ui.screens.SettingsScreen
import com.telo.vpn.ui.theme.TeloVpnTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) launchVpn()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TeloVpnTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        vm = vm,
                        prefs = AppPreferences(this@MainActivity),
                        onConnectClick = { requestVpnPermission() },
                        onDisconnect = { disconnectVpn() }
                    )
                }
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermissionLauncher.launch(intent) else launchVpn()
    }

    private fun launchVpn() {
        val selected = vm.getSelectedConfig() ?: return
        val configJson = XrayConfigBuilder.build(selected)
        vm.markConnected(selected)
        XrayVpnService.start(this, configJson, selected.remark, vm.killSwitch)
    }

    private fun disconnectVpn() {
        XrayVpnService.stop(this)
        vm.onDisconnect()
    }
}

@Composable
private fun MainNavigation(
    vm: MainViewModel,
    prefs: AppPreferences,
    onConnectClick: () -> Unit,
    onDisconnect: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavItem.entries.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.telo.vpn.ui.theme.TeloGreen,
                            selectedTextColor = com.telo.vpn.ui.theme.TeloGreen,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = NavItem.HOME.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(NavItem.HOME.route) {
                HomeScreen(vm = vm, onConnectClick = onConnectClick, onDisconnect = onDisconnect)
            }
            composable(NavItem.SERVERS.route) {
                ServerListScreen(vm = vm)
            }
            composable(NavItem.SETTINGS.route) {
                SettingsScreen(vm = vm, prefs = prefs)
            }
        }
    }
}

private enum class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    HOME("home", "Baş sahypa", Icons.Default.Home),
    SERVERS("servers", "Serverler", Icons.Default.List),
    SETTINGS("settings", "Sazlamalar", Icons.Default.Settings)
}
