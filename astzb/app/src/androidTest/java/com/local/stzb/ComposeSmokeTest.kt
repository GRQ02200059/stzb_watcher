package com.local.stzb

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ComposeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun composeContentIsRendered() {
        composeRule.setContent { Text("ASTZB Compose ready") }
        composeRule.onNodeWithText("ASTZB Compose ready").assertIsDisplayed()
    }
}
