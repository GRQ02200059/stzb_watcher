package com.local.stzb.feature.battlefield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.GlassStatus
import com.local.stzb.core.ui.GlassStatusPill
import com.local.stzb.core.ui.MacGlassHeader
import com.local.stzb.domain.battlefield.BattlefieldEvent
import com.local.stzb.domain.battlefield.EventCategory
import com.local.stzb.domain.battlefield.EventPriority
import com.local.stzb.domain.battlefield.EventTarget

@Composable
fun BattlefieldEventDetailScreen(
    event: BattlefieldEvent?,
    onBack: () -> Unit,
    onOpenBattle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MacGlassHeader(
            title = "事件详情",
            subtitle = event?.category?.label ?: "事件已失效",
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回战场")
                }
            },
        )

        if (event == null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "该事件已被刷新，请返回战场重新选择。",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    GlassStatusPill(event.priority.label, event.priority.status)
                }
                Text(event.summary, style = MaterialTheme.typography.bodyLarge)
                Text(formatEventTime(event.occurredAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (event.details.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("事件信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    event.details.forEach { detail ->
                        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        val target = event.target
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("关联目标", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(target.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (target is EventTarget.Battle) {
                    Button(onClick = { onOpenBattle(target.battleId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("查看完整战报")
                    }
                }
            }
        }
    }
}

private val EventCategory.label: String
    get() = when (this) {
        EventCategory.URGENT -> "紧急事件"
        EventCategory.BATTLE -> "战斗事件"
        EventCategory.MARCH -> "行军事件"
        EventCategory.SIEGE -> "攻城事件"
        EventCategory.SYSTEM -> "系统事件"
    }

private val EventPriority.label: String
    get() = when (this) {
        EventPriority.NORMAL -> "普通"
        EventPriority.IMPORTANT -> "重要"
        EventPriority.CRITICAL -> "紧急"
    }

private val EventPriority.status: GlassStatus
    get() = when (this) {
        EventPriority.NORMAL -> GlassStatus.INFO
        EventPriority.IMPORTANT -> GlassStatus.WARNING
        EventPriority.CRITICAL -> GlassStatus.ERROR
    }

private val EventTarget.label: String
    get() = when (this) {
        is EventTarget.Battle -> "战报 #$battleId"
        is EventTarget.Team -> "队伍 #$teamId"
        is EventTarget.Cell -> "地块 ${wid / 10_000},${wid % 10_000}"
        EventTarget.Diagnostics -> "抓包诊断"
        EventTarget.None -> "无关联目标"
    }

private fun formatEventTime(epochSeconds: Long): String = if (epochSeconds <= 0L) {
    "时间未记录"
} else {
    java.time.Instant.ofEpochSecond(epochSeconds)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA))
}
