package com.local.stzb.feature.battlefield

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.local.stzb.core.designsystem.AstzbTheme
import com.local.stzb.core.ui.LoadState
import com.local.stzb.domain.battlefield.BattlefieldEvent
import com.local.stzb.domain.battlefield.BattlefieldHero
import com.local.stzb.domain.battlefield.BattlefieldSkill
import com.local.stzb.domain.battlefield.BattlefieldTeamPresentation
import com.local.stzb.domain.battlefield.BattlefieldMetrics
import com.local.stzb.domain.battlefield.BattlefieldSnapshot
import com.local.stzb.domain.battlefield.CaptureStatus
import com.local.stzb.domain.battlefield.EventCategory
import com.local.stzb.domain.battlefield.EventPriority
import com.local.stzb.domain.battlefield.EventTarget
import org.junit.Rule
import org.junit.Test

class BattlefieldScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contentShowsStatusMetricsFeedAndPauseAction() {
        val snapshot = contentSnapshot()

        rule.setContent {
            AstzbTheme {
                BattlefieldScreen(BattlefieldUiState(LoadState.Content(snapshot)), {}, {})
            }
        }

        rule.onNodeWithText("实时战场").assertIsDisplayed()
        rule.onNodeWithText("正在行军").assertIsDisplayed()
        rule.onNodeWithText("前锋 · 测试盟").assertIsDisplayed()
        rule.onNodeWithContentDescription("暂停实时刷新").assertIsDisplayed()
    }

    @Test
    fun controlsExposeStateAndDispatchIntents() {
        val intents = mutableListOf<BattlefieldIntent>()
        val snapshot = contentSnapshot().copy(
            paused = true,
            bufferedEventCount = 3,
            selectedCategories = setOf(EventCategory.MARCH),
        )

        rule.setContent {
            AstzbTheme {
                BattlefieldScreen(BattlefieldUiState(LoadState.Content(snapshot)), intents::add, {})
            }
        }

        rule.onNodeWithContentDescription("继续实时刷新").performClick()
        rule.onNodeWithText("行军").assertIsSelected().performClick()
        rule.onNodeWithText("查看 3 条新动态").performClick()
        rule.runOnIdle {
            check(BattlefieldIntent.TogglePaused in intents)
            check(BattlefieldIntent.ToggleCategory(EventCategory.MARCH) in intents)
            check(BattlefieldIntent.ConsumeBufferedEvents in intents)
        }
    }

    @Test
    fun statePanelsAndRefreshIndicatorExposeActionsAndSemantics() {
        var state = androidx.compose.runtime.mutableStateOf<BattlefieldUiState>(BattlefieldUiState())
        val intents = mutableListOf<BattlefieldIntent>()
        rule.setContent {
            AstzbTheme {
                BattlefieldScreen(state.value, intents::add, {})
            }
        }

        rule.onNodeWithContentDescription("正在加载").assertIsDisplayed()
        rule.runOnIdle {
            state.value = BattlefieldUiState(LoadState.Empty("尚未收到战场动态", "启动抓包"))
        }
        rule.onNodeWithText("启动抓包").performClick()
        rule.runOnIdle {
            state.value = BattlefieldUiState(LoadState.Error("网络错误", retryable = true))
        }
        rule.onNodeWithText("重试").performClick()
        rule.runOnIdle {
            state.value = BattlefieldUiState(LoadState.Content(contentSnapshot(), refreshing = true))
        }
        rule.onNodeWithContentDescription("正在刷新").assertIsDisplayed()
        rule.runOnIdle {
            check(intents.count { it == BattlefieldIntent.Refresh } == 2)
        }
    }

    @Test
    fun lifecycleDispatchesActiveOnlyWhileStarted() {
        val intents = mutableListOf<BattlefieldIntent>()
        rule.setContent {
            AstzbTheme {
                BattlefieldScreen(BattlefieldUiState(), intents::add, {})
            }
        }
        rule.runOnIdle {
            check(BattlefieldIntent.SetActive(true) in intents)
        }

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.runOnIdle {
            check(BattlefieldIntent.SetActive(false) in intents)
        }
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @Test
    fun eventCardDispatchesSelectedEvent() {
        val snapshot = contentSnapshot()
        var selected: BattlefieldEvent? = null
        rule.setContent {
            AstzbTheme {
                BattlefieldScreen(
                    BattlefieldUiState(LoadState.Content(snapshot)),
                    onIntent = {},
                    onEventClick = { selected = it },
                )
            }
        }

        rule.onNodeWithText("前锋 · 测试盟").performClick()
        rule.runOnIdle { check(selected == snapshot.events.single()) }
    }

    @Test
    fun heroPortraitUsesAccessibleInitialFallbackWhenIconIsUnavailable() {
        val hero = BattlefieldHero("大营", 0, 0, "陆逊", 50, 5, emptyList())
        rule.setContent { AstzbTheme { BattlefieldHeroPortrait(hero) } }

        rule.onNodeWithContentDescription("大营 陆逊").assertIsDisplayed()
        rule.onNodeWithText("陆").assertIsDisplayed()
    }

    @Test
    fun recordedMarchUsesCompactThreeHeroCard() {
        val event = contentSnapshot().events.single().copy(
            summary = "地图队伍 · 10,10 → 10,20 · 士气 88",
            teamPresentation = BattlefieldTeamPresentation(
                teamId = 42,
                heroes = listOf(
                    BattlefieldHero("大营", 1, 0, "陆逊", 50, 5, listOf(BattlefieldSkill("深谋远虑", 10))),
                    BattlefieldHero("中军", 2, 0, "周瑜", 49, 4, listOf(BattlefieldSkill("神兵天降", 10))),
                    BattlefieldHero("前锋", 3, 0, "吕蒙", 48, 3, listOf(BattlefieldSkill("反计之策", 10))),
                ),
                routeText = "10,10 → 10,20",
                destinationText = "10,20",
                moraleText = "士气 88",
                stateText = "行军",
                recordText = "12战 8胜1平3负 · 胜率 70.8%",
                arrivalText = "到达 16:30:00",
                arrivalAt = 1_700_000_600L,
                winRate = 70.8,
            ),
        )
        rule.setContent { AstzbTheme { BattlefieldEventCard(event, {}) } }

        listOf("大营", "中军", "前锋", "陆逊", "周瑜", "吕蒙", "Lv.50 · 进阶5", "深谋远虑", "10,10 → 10,20", "士气 88", "12战 8胜1平3负 · 胜率 70.8%").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun unmatchedMarchKeepsGenericEventCard() {
        val event = contentSnapshot().events.single()
        rule.setContent { AstzbTheme { BattlefieldEventCard(event, {}) } }

        rule.onNodeWithText("10,10 → 10,20").assertIsDisplayed()
        rule.onNodeWithText("普通").assertIsDisplayed()
    }

    private fun contentSnapshot() = BattlefieldSnapshot(
        capture = CaptureStatus(true, "抓包运行中", 1_700_000_000L),
        metrics = BattlefieldMetrics(12, 2, 8, 1),
        events = listOf(
            BattlefieldEvent(
                id = "march:42:1700000600",
                occurredAt = 1_700_000_600L,
                category = EventCategory.MARCH,
                priority = EventPriority.NORMAL,
                title = "前锋 · 测试盟",
                summary = "10,10 → 10,20",
                target = EventTarget.Team(42),
            ),
        ),
    )
}
