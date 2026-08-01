package com.local.stzb.feature.battles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.EmptyPanel
import com.local.stzb.core.ui.ErrorPanel
import com.local.stzb.core.ui.LoadingPanel
import com.local.stzb.domain.battles.BattleOutcome
import com.local.stzb.domain.battles.BattleSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BattlesScreen(state: BattlesUiState, onIntent: (BattlesIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("本机战报", style = MaterialTheme.typography.headlineMedium); Text("来自 battles_v2") }
            IconButton(onClick = { onIntent(BattlesIntent.Refresh) }) { Icon(Icons.Outlined.Refresh, "刷新战报") }
        }
        OutlinedTextField(
            value = state.filters.query,
            onValueChange = { onIntent(BattlesIntent.SetQuery(it)) },
            label = { Text("搜索玩家") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickBattleFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter.matches(state),
                    onClick = { onIntent(BattlesIntent.SetQuickFilter(filter)) },
                    label = { Text(filter.label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        when {
            state.loading -> LoadingPanel(Modifier.weight(1f))
            state.error != null -> ErrorPanel(state.error, true, { onIntent(BattlesIntent.Refresh) }, Modifier.weight(1f))
            state.battles.isEmpty() -> EmptyPanel("本机还没有完整战报", "刷新", { onIntent(BattlesIntent.Refresh) }, Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.battles, key = BattleSummary::id) { battle -> BattleCard(battle) { onIntent(BattlesIntent.OpenBattle(battle.id)) } }
            }
        }
    }
}

@Composable
private fun BattleCard(battle: BattleSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, battle.outcome.color.copy(alpha = .65f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(battle.outcomeLabel, color = battle.outcome.color, fontWeight = FontWeight.Bold)
                Text(formatTime(battle.occurredAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(battle.title, style = MaterialTheme.typography.titleLarge)
            Text(battle.locationAndType, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("武勋 ${battle.attackerWuxun} · 兵力 ${battle.attackerHp} / ${battle.defenderHp}")
            if (battle.heroNames.isNotEmpty()) Text(battle.heroNames.joinToString(" · "))
        }
    }
}

private fun QuickBattleFilter.matches(state: BattlesUiState) = when (this) {
    QuickBattleFilter.ALL -> state.filters.outcome == null && !state.filters.siegeOnly
    QuickBattleFilter.VICTORY -> state.filters.outcome == BattleOutcome.VICTORY
    QuickBattleFilter.DEFEAT -> state.filters.outcome == BattleOutcome.DEFEAT
    QuickBattleFilter.SIEGE -> state.filters.siegeOnly
}

private val BattleOutcome.color: Color
    @Composable get() = when (this) {
        BattleOutcome.VICTORY -> MaterialTheme.colorScheme.tertiary
        BattleOutcome.DEFEAT -> MaterialTheme.colorScheme.error
        BattleOutcome.DRAW -> MaterialTheme.colorScheme.primary
        BattleOutcome.OTHER -> MaterialTheme.colorScheme.secondary
    }

private fun formatTime(seconds: Long): String = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    .format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()))
