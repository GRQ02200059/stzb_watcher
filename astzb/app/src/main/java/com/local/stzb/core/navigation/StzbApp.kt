package com.local.stzb.core.navigation

import android.app.Activity
import android.net.VpnService
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.local.stzb.core.designsystem.AstzbColors
import com.local.stzb.core.ui.MacGlassBackground
import androidx.compose.ui.unit.dp
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
import com.local.stzb.feature.battlefield.BattlefieldEventDetailScreen
import com.local.stzb.feature.battlefield.BattlefieldViewModel
import com.local.stzb.domain.battlefield.BattlefieldEvent
import com.local.stzb.domain.battlefield.EventTarget
import com.local.stzb.core.ui.LoadState
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
import com.local.stzb.feature.overlay.BattlefieldOverlayService
import com.local.stzb.feature.overlay.OverlayServiceState
import com.local.stzb.feature.simulator.BattleLogScreen
import com.local.stzb.feature.simulator.BattleSimulatorScreen
import com.local.stzb.feature.simulator.BattleSimulatorViewModel
import com.local.stzb.feature.simulator.LocalBattleSimulatorEngine
import com.local.stzb.feature.simulator.BattleSimulatorIntent
import com.local.stzb.feature.simulator.SimulatorCamp
import com.local.stzb.feature.profile.ProfileScreen
import com.local.stzb.profile.ProfileSnapshot
import com.local.stzb.feature.livearmy.LiveArmyScreen
import com.local.stzb.feature.livearmy.LiveArmyViewModel
import com.local.stzb.feature.attendance.AttendanceScreen
import com.local.stzb.feature.attendance.AttendanceViewModel
import com.local.stzb.feature.score.ScoreCenterScreen
import com.local.stzb.feature.score.ScoreCenterViewModel
import com.local.stzb.feature.research.LineupResearchScreen
import com.local.stzb.data.research.LineupResearchRepository
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
    profileSnapshot: ProfileSnapshot = ProfileSnapshot(emptyList(), null),
    onRegisterProfile: (String, String, String) -> Result<ProfileSnapshot> = { _, _, _ -> Result.failure(IllegalStateException("档案功能未配置")) },
    onSwitchProfile: (String) -> Result<Unit> = { Result.failure(IllegalStateException("档案功能未配置")) },
    onLogout: () -> Unit = {},
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
    val simulatorViewModel: BattleSimulatorViewModel = viewModel { BattleSimulatorViewModel() }
    val simulatorState by simulatorViewModel.state.collectAsStateWithLifecycle()
    val liveArmyViewModel: LiveArmyViewModel = viewModel { LiveArmyViewModel() }
    val liveArmyState by liveArmyViewModel.state.collectAsStateWithLifecycle()
    val attendanceViewModel: AttendanceViewModel = viewModel { AttendanceViewModel() }
    val attendanceState by attendanceViewModel.state.collectAsStateWithLifecycle()
    val scoreViewModel: ScoreCenterViewModel = viewModel { ScoreCenterViewModel() }
    val scoreState by scoreViewModel.state.collectAsStateWithLifecycle()
    val overlayRunning by OverlayServiceState.running.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<CaptureExport?>(null) }
    var selectedBattlefieldEvent by remember { mutableStateOf<BattlefieldEvent?>(null) }
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
    val overlayPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) BattlefieldOverlayService.start(context)
    }
    fun toggleOverlay() {
        if (overlayRunning) BattlefieldOverlayService.stop(context)
        else if (Settings.canDrawOverlays(context)) BattlefieldOverlayService.start(context)
        else overlayPermissionLauncher.launch(
            android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
        )
    }

    MacGlassBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (currentRoute !in setOf("simulator", "simulator-log")) {
                    MacGlassNavigationDock(
                        currentRoute = currentRoute,
                        onDestinationClick = { destination ->
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = AppDestination.BATTLEFIELD.route,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                composable(AppDestination.BATTLEFIELD.route) {
                    BattlefieldScreen(
                        state = battlefieldState,
                        onIntent = battlefieldViewModel::onIntent,
                        onEventClick = { event ->
                            selectedBattlefieldEvent = event
                            navController.navigate("battlefield-event-detail")
                        },
                        overlayRunning = overlayRunning,
                        onToggleOverlay = ::toggleOverlay,
                    )
                }
                composable(AppDestination.BATTLES.route) {
                    if (battlesState.selected == null) {
                        BattlesScreen(battlesState, battlesViewModel::onIntent, { navController.popBackStack() })
                    } else {
                        BattleDetailScreen(
                            state = battlesState,
                            onBack = { battlesViewModel.onIntent(BattlesIntent.CloseBattle) },
                        )
                    }
                }
                composable(AppDestination.ALLIANCE.route) {
                    AllianceScreen(allianceState, allianceViewModel, { navController.popBackStack() })
                }
                composable(AppDestination.TOOLS.route) {
                    LegacyToolsScreen(
                        openCaptureConsole = { navController.navigate("capture-console") },
                        openLegacyDashboard = { openLegacyDashboard("ranking") },
                        openMap = { navController.navigate("map") },
                        openAnnouncements = { navController.navigate("announcements") },
                        openRankings = { navController.navigate("rankings") },
                        openTeams = { navController.navigate("teams") },
                        openTeamReport = { navController.navigate("team-report") },
                        openSimulator = { navController.navigate("simulator") },
                        openProfiles = { navController.navigate("profiles") },
                        openLiveArmies = { navController.navigate("live-armies") },
                        openAttendance = { navController.navigate("attendance") },
                        openScores = { navController.navigate("scores") },
                        openResearch = { navController.navigate("research") },
                        onLogout = onLogout,
                        onBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(AppDestination.BATTLEFIELD.route) { launchSingleTop = true }
                            }
                        },
                    )
                }
                composable("teams") { TeamsScreen(teamsState, teamsViewModel::onIntent, onBack = { navController.popBackStack() }) }
                composable("team-report") { TeamReportScreen(teamReportState, teamReportViewModel, onBack = { navController.popBackStack() }) }
                composable("simulator") {
                    BattleSimulatorScreen(
                        state = simulatorState,
                        onIntent = simulatorViewModel::onIntent,
                        heroName = LocalBattleSimulatorEngine::heroName,
                        heroIconId = LocalBattleSimulatorEngine::heroIconId,
                        skillName = LocalBattleSimulatorEngine::skillName,
                        onOpenLog = { navController.navigate("simulator-log") },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("simulator-log") {
                    BattleLogScreen(simulatorState.result?.firstRun) { navController.popBackStack() }
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
                composable("profiles") {
                    ProfileScreen(
                        snapshot = profileSnapshot,
                        onRegister = onRegisterProfile,
                        onSwitch = onSwitchProfile,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("live-armies") {
                    LiveArmyScreen(
                        state = liveArmyState,
                        onQuery = liveArmyViewModel::setQuery,
                        onRefresh = liveArmyViewModel::refresh,
                        onLocate = { teamId ->
                            val event = (battlefieldState.loadState as? LoadState.Content)?.value?.events
                                ?.firstOrNull { (it.target as? EventTarget.Team)?.teamId == teamId }
                            if (event != null) {
                                selectedBattlefieldEvent = event
                                navController.navigate("battlefield-event-detail")
                            } else {
                                navController.navigate(AppDestination.BATTLEFIELD.route) { launchSingleTop = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("attendance") {
                    AttendanceScreen(
                        state = attendanceState,
                        viewModel = attendanceViewModel,
                        onExportCsv = { csv, name ->
                            pendingExport = CaptureExport(name, "text/csv", csv.toByteArray(Charsets.UTF_8))
                            documentLauncher.launch(name)
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("scores") { ScoreCenterScreen(scoreState, scoreViewModel, onBack = { navController.popBackStack() }) }
                composable("research") {
                    LineupResearchScreen(
                        repository = remember { LineupResearchRepository() },
                        onOpenSimulator = { heroIds ->
                            simulatorViewModel.onIntent(BattleSimulatorIntent.ApplyResearchLineup(SimulatorCamp.BLUE, heroIds))
                            navController.navigate("simulator")
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("battlefield-event-detail") {
                    BattlefieldEventDetailScreen(
                        event = selectedBattlefieldEvent,
                        onBack = { navController.popBackStack() },
                        onOpenBattle = { battleId ->
                            battlesViewModel.onIntent(BattlesIntent.OpenBattle(battleId))
                            navController.navigate(AppDestination.BATTLES.route)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MacGlassNavigationDock(
    currentRoute: String?,
    onDestinationClick: (AppDestination) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val dockShape = RoundedCornerShape(30.dp)
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 18.dp,
                    shape = dockShape,
                    ambientColor = Color(0x24325A8A),
                    spotColor = Color(0x24325A8A),
                )
                .clip(dockShape)
                .background(
                    Brush.linearGradient(
                        listOf(AstzbColors.GlassFloating, AstzbColors.GlassVeil),
                    ),
                )
                .border(1.dp, AstzbColors.Outline, dockShape)
                .padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppDestination.entries.forEach { destination ->
                MacGlassDockItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = { onDestinationClick(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MacGlassDockItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemShape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clip(itemShape)
            .then(
                if (selected) {
                    Modifier
                        .background(AstzbColors.GlassFrost, itemShape)
                        .border(1.dp, AstzbColors.OutlineLow, itemShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            destination.icon,
            contentDescription = destination.label,
            modifier = Modifier.size(21.dp),
            tint = if (selected) AstzbColors.Primary else AstzbColors.TextSecondary,
        )
        Spacer(Modifier.width(1.dp))
        Text(
            destination.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) AstzbColors.Primary else AstzbColors.TextSecondary,
        )
    }
}

private val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.BATTLEFIELD -> Icons.Outlined.Radar
        AppDestination.BATTLES -> Icons.AutoMirrored.Outlined.ReceiptLong
        AppDestination.ALLIANCE -> Icons.Outlined.Groups
        AppDestination.TOOLS -> Icons.Outlined.MoreHoriz
    }
