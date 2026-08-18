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
        rule.onNodeWithText("本软件完全免费，禁止任何形式的倒卖、付费代装或捆绑销售。")
            .assertIsDisplayed()
        rule.onNodeWithText("去 https://github.com/GRQ02200059/stzb_watcher 点 Star 和 Fork 后再注册。")
            .assertIsDisplayed()
    }
}
