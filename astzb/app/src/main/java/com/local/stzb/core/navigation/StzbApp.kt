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

@Composable
fun StzbApp(
    repository: BattlefieldRepository,
    teamsRepository: TeamsRepository,
    battleRepository: BattleRepository,
    allianceRepository: AllianceRepository,
    intelRepository: IntelRepository,
    rankingRepository: RankingRepository,
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
                    openCaptureConsole = openCaptureConsole,
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
