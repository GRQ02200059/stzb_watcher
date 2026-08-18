package com.local.stzb.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.local.stzb.auth.AuthGateState
import com.local.stzb.auth.AuthGateUiState
import com.local.stzb.core.designsystem.AstzbColors
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.GlassSurface

private const val GITHUB_GUIDANCE =
    "去 https://github.com/GRQ02200059/stzb_watcher 点 Star 和 Fork 后再注册。"

@Composable
fun AuthGateScreen(
    uiState: AuthGateUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onModeChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val submitting = uiState.state is AuthGateState.SubmittingLogin ||
        uiState.state is AuthGateState.SubmittingRegistration
    val canSubmit = !submitting &&
        uiState.username.isNotBlank() &&
        uiState.password.isNotBlank()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AstzbColors.BackgroundTop, AstzbColors.BackgroundBottom))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "率土助手",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "登录后进入战场、抓包和工具中心",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    NoticeCard(
                        icon = Icons.Outlined.Star,
                        text = GITHUB_GUIDANCE,
                    )
                    NoticeCard(
                        icon = Icons.Outlined.Shield,
                        text = "本软件完全免费，禁止任何形式的倒卖、付费代装或捆绑销售。",
                    )
                    NoticeCard(
                        icon = Icons.Outlined.Lock,
                        text = "密码无法找回，请自行妥善保存。",
                    )
                    Text(
                        text = "当前版本通过 HTTP 明文连接认证服务器，请仅在可信网络中使用。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )

                    when (val state = uiState.state) {
                        AuthGateState.CheckingSession -> CheckingSession()
                        is AuthGateState.Blocked -> StatusPanel(state.message)
                        is AuthGateState.Unavailable -> {
                            StatusPanel(state.message)
                            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                                Text("重试")
                            }
                        }
                        is AuthGateState.Ready -> Unit
                        else -> {
                            val message = (state as? AuthGateState.LoginRequired)?.message
                            if (message != null) {
                                StatusPanel(message)
                            }
                            AuthFields(
                                uiState = uiState,
                                submitting = submitting,
                                canSubmit = canSubmit,
                                onUsernameChange = onUsernameChange,
                                onPasswordChange = onPasswordChange,
                                onModeChange = onModeChange,
                                onSubmit = onSubmit,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(icon: ImageVector, text: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CheckingSession() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator()
        Text("正在验证登录状态…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AuthFields(
    uiState: AuthGateUiState,
    submitting: Boolean,
    canSubmit: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onModeChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !submitting,
            singleLine = true,
            label = { Text("用户名") },
        )
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "密码输入框" },
            enabled = !submitting,
            singleLine = true,
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(2.dp))
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = canSubmit,
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text(
                when (uiState.state) {
                    AuthGateState.SubmittingLogin -> "正在登录…"
                    AuthGateState.SubmittingRegistration -> "正在注册…"
                    else -> if (uiState.registrationMode) "注册并登录" else "登录"
                },
            )
        }
        OutlinedButton(
            onClick = { onModeChange(!uiState.registrationMode) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !submitting,
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text(if (uiState.registrationMode) "返回登录" else "注册新账号")
        }
    }
}

@Composable
private fun StatusPanel(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
