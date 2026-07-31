package com.local.stzb.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.local.stzb.core.ui.MetricCard
import org.junit.Rule
import org.junit.Test

class ThemeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun metricCardExposesLabelValueAndContext() {
        rule.setContent {
            AstzbTheme {
                MetricCard("正在行军", "12", "2 支即将到达")
            }
        }
        rule.onNodeWithText("正在行军").assertIsDisplayed()
        rule.onNodeWithText("12").assertIsDisplayed()
        rule.onNodeWithText("2 支即将到达").assertIsDisplayed()
    }
}
