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
    fun launchesDirectlyIntoBattlefield() {
        rule.onNodeWithText("实时战场").assertIsDisplayed()
        rule.onNodeWithText("战场").assertIsDisplayed()
    }
}
