package com.local.stzb.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import com.local.stzb.core.designsystem.AstzbTheme
import com.local.stzb.domain.battlefield.BattlefieldMetrics
import com.local.stzb.domain.battlefield.BattlefieldRepository
import com.local.stzb.domain.battlefield.BattlefieldSnapshot
import com.local.stzb.domain.battlefield.CaptureStatus
import com.local.stzb.domain.battlefield.EventCategory
import com.local.stzb.domain.battlefield.EventPriority
import com.local.stzb.domain.battlefield.EventTarget
import com.local.stzb.domain.battlefield.BattlefieldEvent
import com.local.stzb.domain.battles.BattleDetail
import com.local.stzb.domain.battles.BattleFilters
import com.local.stzb.domain.battles.BattleRepository
import com.local.stzb.domain.battles.BattleSummary
import com.local.stzb.domain.alliance.AllianceRepository
import com.local.stzb.domain.alliance.AllianceSnapshot
import com.local.stzb.domain.intel.IntelRepository
import com.local.stzb.domain.intel.IntelSnapshot
import com.local.stzb.domain.rankings.*
import com.local.stzb.domain.teams.TeamsRepository
import com.local.stzb.feature.capture.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class StzbNavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun battlefieldIsDefaultAndAllPrimaryDestinationsAreReachable() {
        val repository = FakeBattlefieldRepository(
            BattlefieldSnapshot(
                capture = CaptureStatus(false, "抓包未启动", null),
                metrics = BattlefieldMetrics(0, 0, 0, 0),
                events = emptyList(),
            ),
        )
        rule.setContent {
            AstzbTheme {
                StzbApp(repository, EmptyTeamsRepository, EmptyBattleRepository, EmptyAllianceRepository, EmptyIntelRepository, EmptyRankingRepository, EmptyCaptureController, openLegacyDashboard = {}, openCaptureConsole = {})
            }
        }

        rule.onNodeWithText("实时战场").assertIsDisplayed()

        // Primary bottom navigation: 战场 / 战报 / 同盟 / 工具
        rule.onNodeWithText("战报").performClick()
        rule.onNodeWithText("本机战报").assertIsDisplayed()

        rule.onNodeWithText("同盟").performClick()
        rule.onNodeWithText("同盟中心").assertIsDisplayed()

        rule.onNodeWithText("工具").performClick()
        rule.onNodeWithText("抓包启动台").performClick()
        rule.onNodeWithText("启动抓包").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回更多").performClick()
        rule.onNodeWithText("工具").assertIsDisplayed()

        // Secondary pages remain reachable from tools entry.
        rule.onNodeWithText("队伍").performClick()
        rule.onNodeWithText("全服玩家队伍").assertIsDisplayed()
        rule.onNodeWithText("工具").performClick()
        rule.onNodeWithText("工具").assertIsDisplayed()

        rule.onNodeWithText("团队报表").performClick()
        rule.onNodeWithText("团队报表").assertIsDisplayed()
        rule.onNodeWithText("工具").performClick()
        rule.onNodeWithText("工具").assertIsDisplayed()

        rule.onNodeWithText("模拟器").performClick()
        rule.onNodeWithText("战斗模拟器").assertIsDisplayed()
        rule.onNodeWithText("工具").performClick()
        rule.onNodeWithText("工具").assertIsDisplayed()

        rule.onNodeWithText("战报").performClick()
        rule.onNodeWithText("本机战报").assertIsDisplayed()
        rule.onNodeWithText("工具").performClick()

        rule.onNodeWithText("同盟中心").assertIsDisplayed()

        rule.onNodeWithText("地图与城池").performClick()
        rule.onNodeWithText("本机还没有地图格子数据").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回更多").performClick()

        rule.onNodeWithText("游戏公告").performClick()
        rule.onNodeWithText("本机还没有游戏公告").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回更多").performClick()

        rule.onNodeWithText("排行榜").performClick()
        rule.onNodeWithText("本机还没有战功榜数据").assertIsDisplayed()
        rule.onNodeWithText("返回").performClick()
        rule.onNodeWithText("抓包启动台").assertIsDisplayed()
    }

    @Test
    fun toolsScreenHasVisibleBackAction() {
        val repository = FakeBattlefieldRepository(
            BattlefieldSnapshot(
                capture = CaptureStatus(false, "抓包未启动", null),
                metrics = BattlefieldMetrics(0, 0, 0, 0),
                events = emptyList(),
            ),
        )
        rule.setContent {
            AstzbTheme {
                StzbApp(repository, EmptyTeamsRepository, EmptyBattleRepository, EmptyAllianceRepository, EmptyIntelRepository, EmptyRankingRepository, EmptyCaptureController, openLegacyDashboard = {}, openCaptureConsole = {})
            }
        }

        rule.onNodeWithText("工具").performClick()
        rule.onNodeWithContentDescription("返回上一页").assertIsDisplayed()

        rule.onNodeWithContentDescription("返回上一页").performClick()
        rule.onNodeWithText("实时战场").assertIsDisplayed()
    }

    @Test
    fun toolSecondaryPagesHaveVisibleBackAction() {
        val repository = FakeBattlefieldRepository(
            BattlefieldSnapshot(
                capture = CaptureStatus(false, "抓包未启动", null),
                metrics = BattlefieldMetrics(0, 0, 0, 0),
                events = emptyList(),
            ),
        )
        rule.setContent {
            AstzbTheme {
                StzbApp(repository, EmptyTeamsRepository, EmptyBattleRepository, EmptyAllianceRepository, EmptyIntelRepository, EmptyRankingRepository, EmptyCaptureController, openLegacyDashboard = {}, openCaptureConsole = {})
            }
        }

        rule.onNodeWithText("工具").performClick()
        rule.onNodeWithText("队伍").performClick()
        rule.onNodeWithContentDescription("返回工具").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回工具").performClick()
        rule.onNodeWithText("工具中心").assertIsDisplayed()

        rule.onNodeWithText("团队报表").performClick()
        rule.onNodeWithContentDescription("返回工具").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回工具").performClick()
        rule.onNodeWithText("工具中心").assertIsDisplayed()

        rule.onNodeWithText("模拟器").performClick()
        rule.onNodeWithContentDescription("返回工具").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回工具").performClick()
        rule.onNodeWithText("工具中心").assertIsDisplayed()
    }

    @Test
    fun battlefieldEventClickOpensDetailAndCanReturn() {
        val event = BattlefieldEvent(
            id = "march:42:1700000600",
            occurredAt = 1_700_000_600L,
            category = EventCategory.MARCH,
            priority = EventPriority.NORMAL,
            title = "测试行军",
            summary = "10,10 → 10,20",
            details = listOf("行动：行军"),
            target = EventTarget.Team(42),
        )
        val repository = FakeBattlefieldRepository(
            BattlefieldSnapshot(
                capture = CaptureStatus(true, "抓包运行中", event.occurredAt),
                metrics = BattlefieldMetrics(1, 0, 0, 0),
                events = listOf(event),
            ),
        )
        rule.setContent {
            AstzbTheme {
                StzbApp(repository, EmptyTeamsRepository, EmptyBattleRepository, EmptyAllianceRepository, EmptyIntelRepository, EmptyRankingRepository, EmptyCaptureController, openLegacyDashboard = {}, openCaptureConsole = {})
            }
        }

        rule.onNodeWithText("测试行军").performClick()
        rule.onNodeWithText("事件详情").assertIsDisplayed()
        rule.onNodeWithText("10,10 → 10,20").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回战场").performClick()
        rule.onNodeWithText("实时战场").assertIsDisplayed()
    }

    @Test
    fun migratedAdvancedToolsAreReachableFromTools() {
        val repository = FakeBattlefieldRepository(BattlefieldSnapshot(CaptureStatus(false, "抓包未启动", null), BattlefieldMetrics(0, 0, 0, 0), emptyList()))
        rule.setContent { AstzbTheme { StzbApp(repository, EmptyTeamsRepository, EmptyBattleRepository, EmptyAllianceRepository, EmptyIntelRepository, EmptyRankingRepository, EmptyCaptureController, openLegacyDashboard = {}, openCaptureConsole = {}) } }

        rule.onNodeWithText("工具").performClick()
        listOf("实时部队", "攻城考勤", "自定义积分", "阵容战法研究").forEach { label ->
            rule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithText("实时部队").performScrollTo().performClick()
        rule.onNodeWithText("没有匹配的实时部队，请先完成 5028 抓包").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回工具").performClick()
        rule.onNodeWithText("攻城考勤").performScrollTo().performClick()
        rule.onNodeWithText("新建任务").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("自定义积分").performScrollTo().performClick()
        rule.onNodeWithText("规则预设").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回工具").performClick()
        rule.onNodeWithText("阵容战法研究").performScrollTo().performClick()
        rule.onNodeWithText("配置事实 / 历史证据 / 模拟验证").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回工具").performClick()
        rule.onNodeWithTag("tools-list").performScrollToIndex(4)
        rule.onNodeWithText("账号与区服").performScrollTo().performClick()
        rule.onNodeWithText("新增档案").assertIsDisplayed()
    }

    private object EmptyTeamsRepository : TeamsRepository { override fun loadTeams() = emptyList<com.local.stzb.domain.teams.PlayerTeam>() }
    private object EmptyCaptureController : CaptureConsoleController {
        override fun observe() = MutableStateFlow(CaptureRuntime())
        override suspend fun installedApps() = emptyList<InstalledApp>()
        override suspend fun start(targetPackage: String) = Unit
        override suspend fun stop() = Unit
        override suspend fun clear() = Unit
        override suspend fun prepareExport(kind: CaptureExportKind): CaptureExport? = null
    }

    private object EmptyBattleRepository : BattleRepository {
        override fun loadBattles(filters: BattleFilters): List<BattleSummary> = emptyList()
        override fun loadBattle(id: Int): BattleDetail? = null
    }

    private object EmptyAllianceRepository : AllianceRepository {
        override fun load(query: String, group: String) = AllianceSnapshot(0, emptyList(), emptyList())
    }

    private object EmptyIntelRepository : IntelRepository {
        override fun load(mapQuery: String) = IntelSnapshot(0, 0, emptyList(), emptyList())
    }

    private object EmptyRankingRepository : RankingRepository {
        override fun loadRankings() = RankingSnapshot(emptyList(), emptyList(), emptyList())
        override fun loadTeamReport(dimension: ReportDimension, period: ReportPeriod, group: String) = TeamReportSnapshot(emptyList(), emptyList())
    }

    private class FakeBattlefieldRepository(initial: BattlefieldSnapshot) : BattlefieldRepository {
        private val snapshots = MutableStateFlow(initial)

        override fun observeSnapshot(): Flow<BattlefieldSnapshot> = snapshots
        override suspend fun refresh() = Unit
        override fun setPaused(paused: Boolean) = Unit
        override fun setFilter(categories: Set<EventCategory>) = Unit
    }
}
