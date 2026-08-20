package com.local.stzb.feature.livearmy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.EmptyPanel
import com.local.stzb.core.ui.ErrorPanel
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.LoadingPanel
import com.local.stzb.core.ui.MacGlassHeader
import com.local.stzb.core.ui.StatBlock
import com.local.stzb.data.livearmy.LineupEvidence
import com.local.stzb.data.livearmy.LiveArmy
import com.local.stzb.data.livearmy.LiveArmyFreshness

@Composable
fun LiveArmyScreen(
    state: LiveArmyUiState,
    onQuery: (String) -> Unit,
    onRefresh: () -> Unit,
    onLocate: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MacGlassHeader(
            title = "实时部队",
            subtitle = snapshot?.let { "当前 ${it.current.size} · 行军 ${it.moving} · 精确阵容 ${it.exactLineups}" } ?: "5028 与战报证据",
            leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回工具") } },
        )
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().padding(12.dp),
                label = { Text("搜索队伍 ID、玩家、武将或 WID") }, singleLine = true,
            )
        }
        when {
            state.loading -> LoadingPanel(Modifier.weight(1f))
            state.error != null -> ErrorPanel(state.error, true, onRefresh, Modifier.weight(1f))
            snapshot == null || snapshot.current.isEmpty() -> EmptyPanel("没有匹配的实时部队，请先完成 5028 抓包", "刷新", onRefresh, Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(snapshot.current, key = LiveArmy::teamId) { army -> LiveArmyCard(army, onLocate) }
            }
        }
    }
}

@Composable
private fun LiveArmyCard(army: LiveArmy, onLocate: (Int) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("#${army.teamId} ${army.ownerName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(listOf(army.ownerUnion, army.freshness.label).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(999.dp), color = if (army.isMoving) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        army.stateLabel,
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = if (army.isMoving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text("${army.fromLocation} → ${army.currentLocation} → ${army.targetLocation}", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBlock("士气", army.morale.toString(), emphasis = true)
                StatBlock("阵容证据", army.lineupEvidence.label)
                if (army.battles > 0) StatBlock("战绩", "${army.battles}战·${"%.1f".format(army.winRate)}%")
            }
            if (army.heroes.isNotEmpty()) {
                Text(army.heroes.joinToString(" / ") { it.name }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = { onLocate(army.teamId) }, modifier = Modifier.fillMaxWidth()) { Text("回到战场定位") }
        }
    }
}

private val LiveArmyFreshness.label: String get() = when (this) {
    LiveArmyFreshness.FRESH -> "新鲜"
    LiveArmyFreshness.AGING -> "老化"
    LiveArmyFreshness.STALE -> "过期"
    LiveArmyFreshness.UNKNOWN -> "时间未知"
}
private val LineupEvidence.label: String get() = when (this) {
    LineupEvidence.EXACT_BATTLE -> "精确战报阵容"
    LineupEvidence.OBSERVED_TYPE -> "观测武将类型"
    LineupEvidence.UNKNOWN -> "阵容未知"
}
