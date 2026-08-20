package com.local.stzb.feature.autoscroll

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.MacGlassHeader
import com.local.stzb.core.ui.SectionLabel

@Composable
fun AutoScrollScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val running by AutoScrollState.running.collectAsStateWithLifecycle()
    val accessibilityEnabled = isAccessibilityServiceEnabled(context)

    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MacGlassHeader(
            title = "自动滑动",
            subtitle = "在游戏界面自动从上往下循环滑动",
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
        )

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("使用步骤")
                Text("1. 开启悬浮窗权限（用于显示开始/停止按钮）", style = MaterialTheme.typography.bodyMedium)
                Text("2. 开启无障碍服务（用于在游戏界面执行滑动手势）", style = MaterialTheme.typography.bodyMedium)
                Text("3. 切到游戏界面，点击悬浮按钮开始自动滑动", style = MaterialTheme.typography.bodyMedium)
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("权限状态")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("悬浮窗权限", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (Settings.canDrawOverlays(context)) "已开启" else "未开启",
                        color = if (Settings.canDrawOverlays(context)) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!Settings.canDrawOverlays(context)) {
                    OutlinedButton(onClick = { openOverlaySettings(context) }) { Text("开启悬浮窗权限") }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("无障碍服务", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (accessibilityEnabled) "已开启" else "未开启",
                        color = if (accessibilityEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!accessibilityEnabled) {
                    OutlinedButton(onClick = { openAccessibilitySettings(context) }) { Text("开启无障碍服务") }
                }
            }
        }

        val canStart = Settings.canDrawOverlays(context) && accessibilityEnabled
        Button(
            onClick = {
                if (running) {
                    AutoScrollOverlayService.stop(context)
                } else {
                    AutoScrollOverlayService.start(context)
                }
            },
            enabled = canStart,
            colors = if (running) ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)) else ButtonDefaults.buttonColors(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(if (running) "停止悬浮按钮" else "显示悬浮按钮", modifier = Modifier.padding(start = 8.dp))
        }

        if (!canStart) {
            Text(
                "请先完成上述权限设置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openOverlaySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponent = context.packageName + "/" + AutoScrollAccessibilityService::class.java.name
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    while (splitter.hasNext()) {
        val component = splitter.next()
        if (component.equals(expectedComponent, ignoreCase = true)) return true
    }
    return false
}
