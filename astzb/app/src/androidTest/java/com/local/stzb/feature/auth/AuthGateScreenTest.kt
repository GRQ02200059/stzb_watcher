package com.local.stzb.feature.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.stzb.auth.AuthGateState
import com.local.stzb.auth.AuthGateUiState
import com.local.stzb.core.designsystem.AstzbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthGateScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loginShowsRequiredNoticesGithubGuidanceAndMasksPassword() {
        rule.setContent {
            AstzbTheme {
                AuthGateScreen(
                    uiState = AuthGateUiState(
                        state = AuthGateState.LoginRequired(),
                        username = "player",
                        password = "secret",
                    ),
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onModeChange = {},
                    onSubmit = {},
                    onRetry = {},
                )
            }
        }

        rule.onNodeWithText("率土助手").assertIsDisplayed()
        rule.onNodeWithText("本软件完全免费，禁止任何形式的倒卖、付费代装或捆绑销售。").assertIsDisplayed()
        rule.onNodeWithText("密码无法找回，请自行妥善保存。").assertIsDisplayed()
        rule.onNodeWithText("去 https://github.com/GRQ02200059/stzb_watcher 点 Star 和 Fork 后再注册。")
            .assertIsDisplayed()
        rule.onNodeWithText("当前版本通过 HTTP 明文连接认证服务器，请仅在可信网络中使用。").assertIsDisplayed()
        rule.onNodeWithContentDescription("密码输入框").assertTextContains("••••••")
        rule.onNodeWithText("登录").assertIsEnabled()
    }

    @Test
    fun registrationModeDispatchesAction() {
        var mode: Boolean? = null
        rule.setContent {
            AstzbTheme {
                AuthGateScreen(
                    uiState = AuthGateUiState(
                        state = AuthGateState.LoginRequired(),
                    ),
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onModeChange = { mode = it },
                    onSubmit = {},
                    onRetry = {},
                )
            }
        }

        rule.onNodeWithText("注册新账号").performClick()
        rule.runOnIdle {
            assertEquals(true, mode)
        }
    }

    @Test
    fun unavailableStateDispatchesRetry() {
        var retries = 0
        rule.setContent {
            AstzbTheme {
                AuthGateScreen(
                    uiState = AuthGateUiState(
                        state = AuthGateState.Unavailable("认证服务器暂时无法连接"),
                    ),
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onModeChange = {},
                    onSubmit = {},
                    onRetry = { retries += 1 },
                )
            }
        }

        rule.onNodeWithText("认证服务器暂时无法连接").assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun submittingDisablesDuplicateActions() {
        rule.setContent {
            AstzbTheme {
                AuthGateScreen(
                    uiState = AuthGateUiState(
                        state = AuthGateState.SubmittingLogin,
                        username = "player",
                        password = "secret",
                    ),
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onModeChange = {},
                    onSubmit = {},
                    onRetry = {},
                )
            }
        }

        rule.onNodeWithText("正在登录…").assertIsNotEnabled()
        rule.onNodeWithText("注册新账号").assertIsNotEnabled()
    }
}
