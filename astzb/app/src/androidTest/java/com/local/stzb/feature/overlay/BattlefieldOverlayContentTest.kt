package com.local.stzb.feature.overlay

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.local.stzb.core.designsystem.AstzbTheme
import org.junit.Rule
import org.junit.Test

class BattlefieldOverlayContentTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun expandedContentShowsCompactTeamInformationAndControls() {
        val team = OverlayTeam(1, "青山丨晚安", "驻守", listOf(OverlayHero("太史慈", 5), OverlayHero("吕蒙", 4), OverlayHero("陆抗", 0)), "24,540", null, 1_700_000_000, 66.7)
        rule.setContent { AstzbTheme { BattlefieldOverlayContent(OverlayMonitorState(listOf(team), true), false, {}, {}, {}, Modifier) } }
        listOf("战场队伍", "青山丨晚安", "驻守", "太史慈 5红 · 吕蒙 4红 · 陆抗 0红", "目的地 24,540", "胜率 66.7%").forEach {
            rule.onNodeWithText(it, substring = true).assertIsDisplayed()
        }
        rule.onNodeWithContentDescription("折叠悬浮窗").assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭悬浮窗").assertIsDisplayed()
    }

    @Test fun collapsedContentShowsBallAndTeamCount() {
        rule.setContent { AstzbTheme { BattlefieldOverlayContent(OverlayMonitorState(listOf(OverlayTeam(1,"甲","行军", emptyList(),"",null,0,null))), true, {}, {}, {}, Modifier) } }
        rule.onNodeWithText("战场").assertIsDisplayed()
        rule.onNodeWithText("1").assertIsDisplayed()
        rule.onNodeWithContentDescription("展开悬浮窗").assertIsDisplayed()
    }
}
