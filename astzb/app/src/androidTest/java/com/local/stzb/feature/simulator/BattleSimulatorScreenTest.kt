package com.local.stzb.feature.simulator

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.myapplication.LocalSimHeroConfig
import com.example.myapplication.LocalSimTeamConfig
import com.example.myapplication.LocalSimulationConfig
import com.example.myapplication.LocalSimulationEvent
import com.example.myapplication.LocalSimulationEventKind
import com.example.myapplication.LocalSimulationHeroSnapshot
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

        listOf("模拟对局", "我的队伍").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("敌方队伍").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("对决").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("选择战法").assertCountEquals(18)
    }

    @Test fun duelUsesReferenceStyleBattleStageLandmarks() {
        rule.setContent {
            AstzbTheme {
                BattleSimulatorScreen(sampleState(), {}, { "武将$it" }, { 0L }, { "战法$it" }, {})
            }
        }

        rule.onNodeWithText("我的队伍").assertIsDisplayed()
        rule.onNodeWithText("敌方队伍").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("开始推演").performScrollTo().assertIsDisplayed()
        listOf("战报库", "阵容编辑", "批量推演").forEach {
            rule.onNodeWithText(it, substring = true).performScrollTo().assertIsDisplayed()
        }
        rule.onAllNodesWithText("大营").assertCountEquals(2)
        rule.onAllNodesWithText("中军").assertCountEquals(2)
        rule.onAllNodesWithText("前锋").assertCountEquals(2)
    }

    @Test fun tacticalDuelUsesLayeredBattlefieldBackdropInsteadOfOpaqueBlackCover() {
        rule.setContent {
            AstzbTheme {
                BattleSimulatorScreen(sampleState(), {}, { "武将$it" }, { 0L }, { "战法$it" }, {})
            }
        }

        rule.onNodeWithTag("tactical-battlefield-backdrop").assertIsDisplayed()
        rule.onNodeWithTag("tactical-battlefield-mist").assertIsDisplayed()
        rule.onNodeWithTag("tactical-battlefield-aurora").assertIsDisplayed()
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

        rule.onNodeWithText("模拟对局").assertIsDisplayed()
    }

    @Test fun reportLibraryAndDetailShowTacticalLabels() {
        val report = TacticalSimulationReport(
            1,
            LocalSimulationRun("攻方", 1_200, 800, listOf("回合1"), roundsPlayed = 1, seed = 88),
        )
        rule.setContent {
            AstzbTheme {
                BattleSimulatorScreen(
                    sampleState().copy(
                        reports = listOf(report),
                        selectedReportId = 1,
                        tacticalView = TacticalSimulatorView.DETAIL,
                    ),
                    {}, { "武将$it" }, { it }, { "战法$it" }, {},
                )
            }
        }

        listOf("战报过程", "蓝色方", "红色方", "状态", "触发").forEach {
            rule.onNodeWithText(it, substring = false).assertIsDisplayed()
        }
        rule.onNodeWithText("回合", substring = false).assertIsDisplayed()
    }

    @Test fun reportDetailUsesReferenceStyleCampStagesAndEventStream() {
        val report = TacticalSimulationReport(
            1,
            LocalSimulationRun("攻方", 1_200, 800, listOf("回合1"), roundsPlayed = 1, seed = 88),
        )
        rule.setContent {
            AstzbTheme {
                BattleSimulatorScreen(
                    sampleState().copy(reports = listOf(report), selectedReportId = 1, tacticalView = TacticalSimulatorView.DETAIL),
                    {}, { "武将$it" }, { it }, { "战法$it" }, {},
                )
            }
        }

        rule.onNodeWithTag("tactical-report-blue-stage").assertIsDisplayed()
        rule.onNodeWithTag("tactical-report-red-stage").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("tactical-report-event-stream").performScrollTo().assertIsDisplayed()
    }

    @Test fun reportHeroTroopTextKeepsSafeInsetAboveTheDecorativeFrame() {
        val report = TacticalSimulationReport(1, reportWithHeroSnapshots())
        rule.setContent {
            AstzbTheme {
                BattleSimulatorScreen(
                    sampleState().copy(reports = listOf(report), selectedReportId = 1, tacticalView = TacticalSimulatorView.DETAIL),
                    {}, { "武将$it" }, { it }, { "战法$it" }, {},
                )
            }
        }

        val card = rule.onNodeWithTag("tactical-report-hero-card-101").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("tactical-report-hero-level-101").assertIsDisplayed()
        rule.onNodeWithTag("tactical-report-hero-troop-value-101").assertIsDisplayed()
        rule.onNodeWithText("9800/10000", substring = false).assertIsDisplayed()
        val troops = rule.onNodeWithTag("tactical-report-hero-troops-101").fetchSemanticsNode().boundsInRoot
        assert(troops.bottom <= card.bottom - 24f) {
            "Troop text must stay clear of the report card's bottom frame"
        }
        assert(troops.left >= card.left + 24f && troops.right <= card.right - 24f) {
            "Troop text must stay clear of the report card's side frames"
        }
    }

    @Test fun reportRoundTabRendersPreparationThenEachRoundAsAContinuousTimeline() {
        val report = TacticalSimulationReport(1, reportWithTimelineEvents())
        rule.setContent {
            AstzbTheme {
                BattleSimulatorScreen(
                    sampleState().copy(reports = listOf(report), selectedReportId = 1, tacticalView = TacticalSimulatorView.DETAIL),
                    {}, { "武将$it" }, { it }, { "战法$it" }, {},
                )
            }
        }

        listOf("准备阶段", "第 1 回合", "第 2 回合", "战斗结算").forEach { label ->
            rule.onNodeWithText(label, substring = false).performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithText("张辽 对 马超 造成 320 伤害（普通攻击）", substring = false).performScrollTo().assertIsDisplayed()
    }

    @Test fun reportRoundTabAlwaysShowsPreparationStageEvenWithoutPreparationEffects() {
        val report = TacticalSimulationReport(1, reportWithHeroSnapshots())
        rule.setContent {
            AstzbTheme {
                BattleSimulatorScreen(
                    sampleState().copy(reports = listOf(report), selectedReportId = 1, tacticalView = TacticalSimulatorView.DETAIL),
                    {}, { "武将$it" }, { it }, { "战法$it" }, {},
                )
            }
        }

        rule.onNodeWithText("准备阶段", substring = false).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("本局没有准备阶段的可触发战法", substring = false).performScrollTo().assertIsDisplayed()
    }

    private fun reportWithHeroSnapshots() = LocalSimulationRun(
        winner = "攻方",
        blueRemain = 18_000,
        redRemain = 12_000,
        records = listOf("回合1"),
        attackerHeroes = listOf(
            LocalSimulationHeroSnapshot(101, "张辽", "大营", 10_000, 9_800, 40, 5),
            LocalSimulationHeroSnapshot(102, "刘备", "中军", 10_000, 7_800, 40, 5),
            LocalSimulationHeroSnapshot(103, "太史慈", "前锋", 10_000, 400, 40, 5),
        ),
        defenderHeroes = listOf(
            LocalSimulationHeroSnapshot(201, "马超", "大营", 10_000, 9_600, 40, 5),
            LocalSimulationHeroSnapshot(202, "魏延", "中军", 10_000, 2_000, 40, 5),
            LocalSimulationHeroSnapshot(203, "曹操", "前锋", 10_000, 400, 40, 5),
        ),
        roundsPlayed = 8,
        seed = 88,
    )

    private fun reportWithTimelineEvents() = reportWithHeroSnapshots().copy(
        events = listOf(
            LocalSimulationEvent(0, LocalSimulationEventKind.PREPARATION, "张辽", "张辽", "陷阵", targetRemaining = 10_000, description = "张辽 执行指挥战法【陷阵】"),
            LocalSimulationEvent(1, LocalSimulationEventKind.ROUND_START, description = "第1回合开始"),
            LocalSimulationEvent(1, LocalSimulationEventKind.ACTION, "张辽", "张辽", targetRemaining = 10_000, description = "张辽 行动开始，兵力=10000"),
            LocalSimulationEvent(1, LocalSimulationEventKind.DAMAGE, "张辽", "马超", "普通攻击", 320, 9_680, "张辽 对 马超 造成 320 伤害（普通攻击）"),
            LocalSimulationEvent(2, LocalSimulationEventKind.ROUND_START, description = "第2回合开始"),
            LocalSimulationEvent(2, LocalSimulationEventKind.RECOVERY, "刘备", "刘备", "仁德", 180, 9_980, "刘备 发动【仁德】恢复 180 兵力"),
            LocalSimulationEvent(2, LocalSimulationEventKind.RESULT, "攻方", amount = 500, description = "战斗结束：攻方，攻方剩余=18000，守方剩余=12000"),
        ),
    )

    private fun sampleState() = BattleSimulatorUiState(
        loading = false,
        config = LocalSimulationConfig(
            blue = LocalSimTeamConfig(100, (1L..3L).map { LocalSimHeroConfig(it, 40, 5) }),
            red = LocalSimTeamConfig(100, (4L..6L).map { LocalSimHeroConfig(it, 40, 5) }),
        ),
    )
}
