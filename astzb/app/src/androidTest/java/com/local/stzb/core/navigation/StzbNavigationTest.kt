package com.local.stzb.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
                StzbApp(repository, EmptyBattleRepository, EmptyAllianceRepository, EmptyIntelRepository, openLegacyDashboard = {}, openCaptureConsole = {})
            }
        }

        rule.onNodeWithText("实时战场").assertIsDisplayed()
        rule.onNodeWithText("战报").performClick()
        rule.onNodeWithText("本机战报").assertIsDisplayed()
        rule.onNodeWithText("本机还没有完整战报").assertIsDisplayed()
        rule.onNodeWithText("同盟").performClick()
        rule.onNodeWithText("同盟中心").assertIsDisplayed()
        rule.onNodeWithText("本机还没有同盟成员数据").assertIsDisplayed()
        rule.onNodeWithText("更多").performClick()
        rule.onNodeWithText("经典抓包控制台").assertIsDisplayed()
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

    private class FakeBattlefieldRepository(initial: BattlefieldSnapshot) : BattlefieldRepository {
        private val snapshots = MutableStateFlow(initial)

        override fun observeSnapshot(): Flow<BattlefieldSnapshot> = snapshots
        override suspend fun refresh() = Unit
        override fun setPaused(paused: Boolean) = Unit
        override fun setFilter(categories: Set<EventCategory>) = Unit
    }
}
