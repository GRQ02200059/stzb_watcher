package com.local.stzb.feature.simulator

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.LocalSimHeroConfig
import com.example.myapplication.LocalSimTeamConfig
import com.example.myapplication.LocalSimulationConfig
import com.example.myapplication.LocalSimulationRun
import com.example.myapplication.LocalSimulationSummary
import com.local.stzb.core.designsystem.AstzbTheme
import org.junit.Rule
import org.junit.Test

class BattleSimulatorScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun showsBothTeamsHeroesSkillSlotsAndRunActions() {
        rule.setContent {
            AstzbTheme {
                BattleSimulatorScreen(sampleState(), {}, { "武将$it" }, { 0L }, { "战法$it" }, {})
            }
        }

        listOf("战斗模拟器", "攻方", "守方", "单次模拟", "模拟 100 次", "模拟 1000 次").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
        rule.onAllNodesWithText("选择战法").assertCountEquals(18)
    }

    @Test fun showsRatesRemainingTroopsAndLogActionAfterSimulation() {
        val result = LocalSimulationSummary(
            10, 6, 3, 1, 60.0, 30.0, 10.0,
            LocalSimulationRun("攻方", 1234, 567, listOf("回合1")),
        )
        rule.setContent {
            AstzbTheme {
                BattleSimulatorScreen(sampleState().copy(result = result), {}, { "武将$it" }, { 0L }, { "战法$it" }, {})
            }
        }

        listOf("攻方胜率 60.0%", "守方胜率 30.0%", "平局 10.0%", "攻方剩余 1234", "守方剩余 567", "查看战斗日志").forEach {
            rule.onNodeWithText(it, substring = true).assertIsDisplayed()
        }
    }

    private fun sampleState() = BattleSimulatorUiState(
        loading = false,
        config = LocalSimulationConfig(
            blue = LocalSimTeamConfig(100, (1L..3L).map { LocalSimHeroConfig(it, 40, 5) }),
            red = LocalSimTeamConfig(100, (4L..6L).map { LocalSimHeroConfig(it, 40, 5) }),
        ),
    )
}
