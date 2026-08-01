package com.local.stzb.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.local.stzb.core.designsystem.AstzbTheme
import com.local.stzb.domain.battlefield.BattlefieldMetrics
import com.local.stzb.domain.battlefield.BattlefieldRepository
import com.local.stzb.domain.battlefield.BattlefieldSnapshot
import com.local.stzb.domain.battlefield.CaptureStatus
import com.local.stzb.domain.battlefield.EventCategory
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
                StzbApp(repository, EmptyTeamsRepository, EmptyBattleRepository, EmptyAllianceRepository, EmptyIntelRepository, EmptyRankingRepository, openLegacyDashboard = {}, openCaptureConsole = {})
            }
        }

        rule.onNodeWithText("实时战场").assertIsDisplayed()
        rule.onNodeWithText("队伍").performClick()
        rule.onNodeWithText("全服玩家队伍").assertIsDisplayed()
        rule.onNodeWithText("团队").performClick()
        rule.onNodeWithText("团队报表").assertIsDisplayed()
        rule.onNodeWithText("更多").performClick()
        rule.onNodeWithText("经典抓包控制台").assertIsDisplayed()

        rule.onNodeWithText("战报").performClick()
        rule.onNodeWithText("本机战报").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回更多").performClick()

        rule.onNodeWithText("同盟成员").performClick()
        rule.onNodeWithText("同盟中心").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回更多").performClick()

        rule.onNodeWithText("地图与城池").performClick()
        rule.onNodeWithText("本机还没有地图格子数据").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回更多").performClick()

        rule.onNodeWithText("游戏公告").performClick()
        rule.onNodeWithText("本机还没有游戏公告").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回更多").performClick()

        rule.onNodeWithText("排行榜").performClick()
        rule.onNodeWithText("本机还没有战功榜数据").assertIsDisplayed()
        rule.onNodeWithText("返回").performClick()
        rule.onNodeWithText("经典抓包控制台").assertIsDisplayed()
    }

    private object EmptyTeamsRepository : TeamsRepository { override fun loadTeams() = emptyList<com.local.stzb.domain.teams.PlayerTeam>() }

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
