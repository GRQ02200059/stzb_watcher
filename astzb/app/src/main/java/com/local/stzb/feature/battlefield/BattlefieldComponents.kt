package com.local.stzb.feature.battlefield

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SportsMma
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.designsystem.AstzbColors
import com.local.stzb.core.ui.MetricCard
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.GlassStatus
import com.local.stzb.core.ui.GlassStatusPill
import com.local.stzb.domain.battlefield.BattlefieldEvent
import com.local.stzb.domain.battlefield.BattlefieldMetrics
import com.local.stzb.domain.battlefield.CaptureStatus
import com.local.stzb.domain.battlefield.EventCategory
import com.local.stzb.domain.battlefield.EventPriority
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BattlefieldHeader(
    capture: CaptureStatus,
    paused: Boolean,
    onIntent: (BattlefieldIntent) -> Unit,
    overlayRunning: Boolean = false,
    onToggleOverlay: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val statusText = if (capture.running) "抓包运行中" else "抓包未启动"
    val statusColor = if (capture.running) AstzbColors.Success else AstzbColors.Error
    val actionDescription = if (paused) "继续实时刷新" else "暂停实时刷新"

    GlassCard(modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("实时战场", style = MaterialTheme.typography.headlineMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (capture.running) Icons.Outlined.GppGood else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = statusColor,
                    )
                    GlassStatusPill(statusText, if (capture.running) GlassStatus.SUCCESS else GlassStatus.ERROR)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onToggleOverlay, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (overlayRunning) "关闭悬浮" else "开启悬浮")
                }
                IconButton(
                    onClick = { onIntent(BattlefieldIntent.TogglePaused) },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Icon(
                        if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = actionDescription,
                    )
                }
            }
        }
        capture.warning?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
}

@Composable
fun BattlefieldMetricsGrid(metrics: BattlefieldMetrics, modifier: Modifier = Modifier) {
    val cards = listOf(
        Triple("正在行军", metrics.activeMarches.toString(), "${metrics.arrivingSoon} 支即将到达"),
        Triple("即将到达", metrics.arrivingSoon.toString(), "请留意行军变化"),
        Triple("今日战斗", metrics.todayBattles.toString(), "今日已捕获战报"),
        Triple("攻城动态", metrics.siegeEvents.toString(), "城池相关事件"),
    )
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 300.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.chunked(columns).forEach { rowCards ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowCards.forEach { (label, value, supporting) ->
                        MetricCard(label, value, supporting, Modifier.weight(1f))
                    }
                    repeat(columns - rowCards.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun EventCategoryFilters(
    selectedCategories: Set<EventCategory>,
    onIntent: (BattlefieldIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EventCategory.entries.forEach { category ->
            FilterChip(
                selected = category in selectedCategories,
                onClick = { onIntent(BattlefieldIntent.ToggleCategory(category)) },
                label = { Text(category.label) },
                leadingIcon = {
                    Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
fun NewEventsButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.Refresh, contentDescription = null)
        Text("查看 $count 条新动态", modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun BattlefieldEventCard(
    event: BattlefieldEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = event.priority.color
    if (event.category == EventCategory.MARCH && event.teamPresentation?.heroes?.isNotEmpty() == true) {
        BattlefieldTeamCard(event, accent, onClick, modifier)
        return
    }
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = accent.copy(alpha = 0.14f)) {
                Icon(
                    event.category.icon,
                    contentDescription = event.category.label,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                    tint = accent,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(event.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(formatEventTime(event.occurredAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(event.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                event.details.forEach { detail ->
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = onClick,
                    label = { Text(event.priority.label, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

private val EventCategory.label: String
    get() = when (this) {
        EventCategory.URGENT -> "紧急"
        EventCategory.BATTLE -> "战斗"
        EventCategory.MARCH -> "行军"
        EventCategory.SIEGE -> "攻城"
        EventCategory.SYSTEM -> "系统"
    }

private val EventCategory.icon: ImageVector
    get() = when (this) {
        EventCategory.URGENT -> Icons.Outlined.Campaign
        EventCategory.BATTLE -> Icons.Outlined.SportsMma
        EventCategory.MARCH -> Icons.AutoMirrored.Outlined.TrendingFlat
        EventCategory.SIEGE -> Icons.Outlined.Flag
        EventCategory.SYSTEM -> Icons.Outlined.Shield
    }

private val EventPriority.label: String
    get() = when (this) {
        EventPriority.NORMAL -> "普通"
        EventPriority.IMPORTANT -> "重要"
        EventPriority.CRITICAL -> "紧急"
    }

private val EventPriority.color: Color
    get() = when (this) {
        EventPriority.NORMAL -> AstzbColors.Secondary
        EventPriority.IMPORTANT -> AstzbColors.Warning
        EventPriority.CRITICAL -> AstzbColors.Error
    }

private val eventTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.CHINA)

private fun formatEventTime(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(eventTimeFormatter)
