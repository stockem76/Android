package com.obdinsight.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.obdinsight.app.ObdInsightApp
import com.obdinsight.app.ui.ai.AiScreen
import com.obdinsight.app.ui.connect.ConnectScreen
import com.obdinsight.app.ui.log.LogScreen
import com.obdinsight.app.ui.scan.ScanScreen
import com.obdinsight.app.ui.settings.SettingsScreen

private sealed class Dest(val route: String, val label: String) {
    data object Connect : Dest("connect", "Connect")
    data object Scan : Dest("scan", "Scan")
    data object Log : Dest("log", "Log")
    data object Ai : Dest("ai", "AI")
    data object Settings : Dest("settings", "Settings")
}

private val destinations = listOf(Dest.Connect, Dest.Scan, Dest.Log, Dest.Ai, Dest.Settings)

@Composable
fun AppRoot(app: ObdInsightApp) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(iconFor(dest), contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Connect.route,
            modifier = androidx.compose.ui.Modifier.padding(padding),
        ) {
            composable(Dest.Connect.route) { ConnectScreen(app.controller) }
            composable(Dest.Scan.route) { ScanScreen(app.controller, app.repository) }
            composable(Dest.Log.route) { LogScreen(app.controller, app.repository) }
            composable(Dest.Ai.route) { AiScreen(app.repository, app.securePrefs) }
            composable(Dest.Settings.route) { SettingsScreen(app.securePrefs) }
        }
    }
}

private fun iconFor(dest: Dest) = when (dest) {
    Dest.Connect -> Icons.Filled.Bluetooth
    Dest.Scan -> Icons.Filled.Analytics
    Dest.Log -> Icons.Filled.ListAlt
    Dest.Ai -> Icons.Filled.Psychology
    Dest.Settings -> Icons.Filled.Settings
}
