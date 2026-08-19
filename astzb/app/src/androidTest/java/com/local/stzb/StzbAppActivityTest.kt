package com.local.stzb

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class StzbAppActivityTest {
    @get:Rule
    val rule = createAndroidComposeRule<StzbAppActivity>()

    @Test
    fun coldStartShowsAuthenticationGate() {
        rule.onNodeWithText("率土助手").assertIsDisplayed()
        rule.onNodeWithText("独立抓包与核心分析 Beta").assertIsDisplayed()
        rule.onNodeWithText("本软件完全免费，禁止任何形式的倒卖、付费代装或捆绑销售。")
            .assertIsDisplayed()
        rule.onNodeWithText("注册前请先到 GitHub 项目页点 Star 和 Fork。")
            .assertIsDisplayed()
    }
}
