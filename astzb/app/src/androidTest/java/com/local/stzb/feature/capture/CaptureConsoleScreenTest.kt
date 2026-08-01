package com.local.stzb.feature.capture

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.local.stzb.core.designsystem.AstzbTheme
import org.junit.Rule
import org.junit.Test

class CaptureConsoleScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun showsNativeCaptureControlsAndCompatibilityActions() {
        rule.setContent {
            AstzbTheme {
                CaptureConsoleScreen(
                    state = CaptureConsoleUiState(packetCount = 12, selectedApp = InstalledApp("率土之滨", "com.netease.stzb.netease")),
                    onIntent = {}, onRequestVpnPermission = {}, onExport = {}, onOpenLegacy = {}, onBack = {},
                )
            }
        }
        rule.onNodeWithText("抓包启动台").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回更多").assertIsDisplayed()
        listOf("启动抓包", "停止抓包", "清空内存日志", "导出解析包", "导出数据库", "导出诊断", "打开旧控制台")
            .forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("已解析 12 包").assertIsDisplayed()
    }
}
