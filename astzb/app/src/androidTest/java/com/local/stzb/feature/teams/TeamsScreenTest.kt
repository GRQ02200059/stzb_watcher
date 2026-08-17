package com.local.stzb.feature.teams

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.local.stzb.core.designsystem.AstzbTheme
import com.local.stzb.domain.teams.PlayerTeam
import com.local.stzb.domain.teams.TeamHero
import org.junit.Rule
import org.junit.Test

class TeamsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun showsFullServerTeamWithThreePortraitsSkillsAndRecord() {
        val team = PlayerTeam(
            "玩家甲", "测试盟", "atk",
            listOf(TeamHero(1, 0, "陆逊"), TeamHero(2, 0, "周瑜"), TeamHero(3, 0, "吕蒙")),
            listOf("深谋远虑", "神兵天降"), 12, 8, 66.7,
        )
        rule.setContent { AstzbTheme { TeamsScreen(TeamsUiState(false, allTeams = listOf(team), visibleTeams = listOf(team)), {}) } }

        listOf("全服玩家队伍", "玩家甲 · 测试盟", "攻方", "陆逊", "周瑜", "吕蒙", "深谋远虑", "12 战 · 8 胜 · 胜率 66.7%").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
        rule.onNodeWithContentDescription("大营 陆逊").assertIsDisplayed()
    }
}
