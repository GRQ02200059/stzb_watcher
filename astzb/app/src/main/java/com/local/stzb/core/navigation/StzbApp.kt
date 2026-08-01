package com.local.stzb.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.local.stzb.domain.battlefield.BattlefieldRepository
import com.local.stzb.domain.battles.BattleRepository
import com.local.stzb.feature.battlefield.BattlefieldScreen
import com.local.stzb.feature.battlefield.BattlefieldViewModel
import com.local.stzb.feature.placeholder.PlaceholderScreen
import com.local.stzb.feature.battles.BattleDetailScreen
import com.local.stzb.feature.battles.BattlesIntent
import com.local.stzb.feature.battles.BattlesScreen
import com.local.stzb.feature.battles.BattlesViewModel
import com.local.stzb.feature.tools.LegacyToolsScreen

@Composable
fun StzbApp(
    repository: BattlefieldRepository,
    battleRepository: BattleRepository,
    openLegacyDashboard: (String) -> Unit,
    openCaptureConsole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val battlefieldViewModel: BattlefieldViewModel = viewModel {
        BattlefieldViewModel(repository)
    }
    val battlefieldState by battlefieldViewModel.state.collectAsStateWithLifecycle()
    val battlesViewModel: BattlesViewModel = viewModel { BattlesViewModel(battleRepository) }
    val battlesState by battlesViewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.BATTLEFIELD.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(AppDestination.BATTLEFIELD.route) {
                BattlefieldScreen(
                    state = battlefieldState,
                    onIntent = battlefieldViewModel::onIntent,
                    onEventClick = {},
                )
            }
            composable(AppDestination.BATTLES.route) {
                if (battlesState.selected == null) {
                    BattlesScreen(battlesState, battlesViewModel::onIntent)
                } else {
                    BattleDetailScreen(
                        state = battlesState,
                        onBack = { battlesViewModel.onIntent(BattlesIntent.CloseBattle) },
                    )
                }
            }
            composable(AppDestination.ALLIANCE.route) {
                PlaceholderScreen(
                    title = "同盟迁移中",
                    message = "同盟与团数据暂时保留经典页面。",
                    onOpenLegacy = { openLegacyDashboard("team_users") },
                )
            }
            composable(AppDestination.MORE.route) {
                LegacyToolsScreen(
                    openCaptureConsole = openCaptureConsole,
                    openLegacyDashboard = { openLegacyDashboard("ranking") },
                )
            }
        }
    }
}

private val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.BATTLEFIELD -> Icons.Outlined.Radar
        AppDestination.BATTLES -> Icons.AutoMirrored.Outlined.ReceiptLong
        AppDestination.ALLIANCE -> Icons.Outlined.Groups
        AppDestination.MORE -> Icons.Outlined.MoreHoriz
    }
