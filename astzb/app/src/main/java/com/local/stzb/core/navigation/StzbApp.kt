package com.local.stzb.core.navigation

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import com.local.stzb.domain.alliance.AllianceRepository
import com.local.stzb.domain.intel.IntelRepository
import com.local.stzb.domain.rankings.RankingRepository
import com.local.stzb.domain.teams.TeamsRepository
import com.local.stzb.feature.battlefield.BattlefieldScreen
import com.local.stzb.feature.battlefield.BattlefieldViewModel
import com.local.stzb.feature.placeholder.PlaceholderScreen
import com.local.stzb.feature.battles.BattleDetailScreen
import com.local.stzb.feature.battles.BattlesIntent
import com.local.stzb.feature.battles.BattlesScreen
import com.local.stzb.feature.battles.BattlesViewModel
import com.local.stzb.feature.alliance.AllianceScreen
import com.local.stzb.feature.alliance.AllianceViewModel
import com.local.stzb.feature.intel.IntelPage
import com.local.stzb.feature.intel.IntelScreen
import com.local.stzb.feature.tools.LegacyToolsScreen
import com.local.stzb.feature.rankings.RankingsScreen
import com.local.stzb.feature.rankings.RankingsViewModel
import com.local.stzb.feature.teams.TeamsScreen
import com.local.stzb.feature.teams.TeamsViewModel
import com.local.stzb.feature.teamreport.TeamReportScreen
import com.local.stzb.feature.teamreport.TeamReportViewModel
import com.local.stzb.feature.capture.*
import kotlinx.coroutines.launch

@Composable
fun StzbApp(
    repository: BattlefieldRepository,
    teamsRepository: TeamsRepository,
    battleRepository: BattleRepository,
    allianceRepository: AllianceRepository,
    intelRepository: IntelRepository,
    rankingRepository: RankingRepository,
    captureController: CaptureConsoleController,
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
    val allianceViewModel: AllianceViewModel = viewModel { AllianceViewModel(allianceRepository) }
    val allianceState by allianceViewModel.state.collectAsStateWithLifecycle()
    val rankingsViewModel: RankingsViewModel = viewModel { RankingsViewModel(rankingRepository) }
    val rankingsState by rankingsViewModel.state.collectAsStateWithLifecycle()
    val teamsViewModel: TeamsViewModel = viewModel { TeamsViewModel(teamsRepository) }
    val teamsState by teamsViewModel.state.collectAsStateWithLifecycle()
    val teamReportViewModel: TeamReportViewModel = viewModel { TeamReportViewModel(rankingRepository) }
    val teamReportState by teamReportViewModel.state.collectAsStateWithLifecycle()
    val captureViewModel: CaptureConsoleViewModel = viewModel { CaptureConsoleViewModel(captureController) }
    val captureState by captureViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<CaptureExport?>(null) }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri != null && export != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(export.bytes) }
                ?: error("无法打开导出位置")
        }.onSuccess {
            captureViewModel.onIntent(CaptureConsoleIntent.Message("已导出 ${export.name}"))
        }.onFailure {
            captureViewModel.onIntent(CaptureConsoleIntent.Message("导出失败：${it.message}"))
        }
    }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) captureViewModel.onIntent(CaptureConsoleIntent.StartApproved)
        else captureViewModel.onIntent(CaptureConsoleIntent.Message("已取消 VPN 授权"))
    }

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
            composable(AppDestination.TEAMS.route) { TeamsScreen(teamsState, teamsViewModel::onIntent) }
            composable(AppDestination.TEAM_REPORT.route) { TeamReportScreen(teamReportState, teamReportViewModel) }
            composable("battles") {
                if (battlesState.selected == null) {
                    BattlesScreen(battlesState, battlesViewModel::onIntent, { navController.popBackStack() })
                } else {
                    BattleDetailScreen(
                        state = battlesState,
                        onBack = { battlesViewModel.onIntent(BattlesIntent.CloseBattle) },
                    )
                }
            }
            composable("alliance") {
                AllianceScreen(allianceState, allianceViewModel, { navController.popBackStack() })
            }
            composable(AppDestination.MORE.route) {
                LegacyToolsScreen(
                    openCaptureConsole = { navController.navigate("capture-console") },
                    openLegacyDashboard = { openLegacyDashboard("ranking") },
                    openMap = { navController.navigate("map") },
                    openAnnouncements = { navController.navigate("announcements") },
                    openRankings = { navController.navigate("rankings") },
                    openBattles = { navController.navigate("battles") },
                    openAlliance = { navController.navigate("alliance") },
                )
            }
            composable("map") {
                IntelScreen(IntelPage.MAP, intelRepository.load(), { navController.popBackStack() }, intelRepository::load)
            }
            composable("announcements") {
                IntelScreen(IntelPage.ANNOUNCEMENTS, intelRepository.load(), { navController.popBackStack() }, intelRepository::load)
            }
            composable("rankings") {
                RankingsScreen(rankingsState, rankingsViewModel, { navController.popBackStack() })
            }
            composable("capture-console") {
                CaptureConsoleScreen(
                    state = captureState,
                    onIntent = captureViewModel::onIntent,
                    onRequestVpnPermission = {
                        val permission = VpnService.prepare(context)
                        if (permission == null) captureViewModel.onIntent(CaptureConsoleIntent.StartApproved)
                        else vpnLauncher.launch(permission)
                    },
                    onExport = { kind -> scope.launch {
                        runCatching { captureViewModel.prepareExport(kind) }
                            .onSuccess { export ->
                                if (export == null) captureViewModel.onIntent(CaptureConsoleIntent.Message("当前没有可导出的解析包"))
                                else { pendingExport = export; documentLauncher.launch(export.name) }
                            }
                            .onFailure { captureViewModel.onIntent(CaptureConsoleIntent.Message("导出准备失败：${it.message}")) }
                    } },
                    onOpenLegacy = openCaptureConsole,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.BATTLEFIELD -> Icons.Outlined.Radar
        AppDestination.TEAMS -> Icons.AutoMirrored.Outlined.ReceiptLong
        AppDestination.TEAM_REPORT -> Icons.Outlined.Groups
        AppDestination.MORE -> Icons.Outlined.MoreHoriz
    }
