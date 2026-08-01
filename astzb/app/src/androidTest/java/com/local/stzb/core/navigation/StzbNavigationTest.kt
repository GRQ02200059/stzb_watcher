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
                StzbApp(repository, openLegacyDashboard = {}, openCaptureConsole = {})
            }
        }

        rule.onNodeWithText("实时战场").assertIsDisplayed()
        rule.onNodeWithText("战报").performClick()
        rule.onNodeWithText("战报迁移中").assertIsDisplayed()
        rule.onNodeWithText("同盟").performClick()
        rule.onNodeWithText("同盟迁移中").assertIsDisplayed()
        rule.onNodeWithText("更多").performClick()
        rule.onNodeWithText("经典抓包控制台").assertIsDisplayed()
    }

    private class FakeBattlefieldRepository(initial: BattlefieldSnapshot) : BattlefieldRepository {
        private val snapshots = MutableStateFlow(initial)

        override fun observeSnapshot(): Flow<BattlefieldSnapshot> = snapshots
        override suspend fun refresh() = Unit
        override fun setPaused(paused: Boolean) = Unit
        override fun setFilter(categories: Set<EventCategory>) = Unit
    }
}
