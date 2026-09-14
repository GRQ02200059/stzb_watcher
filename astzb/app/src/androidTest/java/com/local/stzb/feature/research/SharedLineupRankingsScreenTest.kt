package com.local.stzb.feature.research

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.stzb.core.designsystem.AstzbTheme
import com.local.stzb.data.research.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SharedLineupRankingsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun showsSummaryTabsConfidenceAndSimulatorHandoff() {
        var selected = SharedRankingTab.POPULAR
        var opened = emptyList<Long>()
        val row = SharedLineupRow("1+2+3", listOf(1,2,3), 12,8,2,2,75.0,46.8,7.5,"中",1)
        rule.setContent { AstzbTheme {
            SharedLineupRankingsScreen(
                state = SharedLineupUiState(snapshot = SharedLineupSnapshot(
                    SharedLineupSummary(80,63,76,1,"now"), listOf(row), listOf(row),
                    listOf(SharedCounterRow(row,listOf(4,5,6),"4+5+6")),
                ), tab = selected),
                heroName = { "武将$it" }, onTabChange = { selected = it },
                onQueryChange = {}, onRefresh = {}, onOpenSimulator = { opened = it }, onBack = {},
            )
        } }
        rule.onNodeWithText("全服阵容榜").assertIsDisplayed()
        rule.onNodeWithText("80 场有效战报", substring = true).assertIsDisplayed()
        rule.onNodeWithText("中可信", substring = true).assertIsDisplayed()
        rule.onNodeWithText("带入模拟器").performClick()
        rule.runOnIdle { assertEquals(listOf(1L,2L,3L), opened) }
        rule.onNodeWithText("克制").performClick()
        rule.runOnIdle { assertEquals(SharedRankingTab.COUNTERS, selected) }
    }
}
