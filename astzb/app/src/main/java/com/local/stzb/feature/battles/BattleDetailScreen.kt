package com.local.stzb.feature.battles

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.InfoRow
import com.local.stzb.core.ui.LoadingPanel
import com.local.stzb.core.ui.MacGlassHeader
import com.local.stzb.core.ui.SectionLabel
import com.local.stzb.domain.battles.BattleSide

@Composable
fun BattleDetailScreen(state: BattlesUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val detail = state.selected
    if (detail == null) { LoadingPanel(modifier); return }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            MacGlassHeader(
                title = detail.summary.title,
                subtitle = detail.summary.locationAndType,
                leading = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回战报列表") }
                },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(detail.summary.outcomeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("武勋 ${detail.summary.attackerWuxun}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                GlassCard(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("战斗天气", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("天气 ${detail.weather}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(if (detail.nightBattle) "夜战" else "昼战", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { SideCard("攻方", detail.attacker) }
        item { SideCard("守方", detail.defender) }
    }
}

@Composable
private fun SideCard(role: String, side: BattleSide) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(role, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(side.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (side.name.isNotBlank()) {
                Text(side.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            if (side.unionName.isNotBlank()) {
                InfoRow("同盟", side.unionName)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBlockMini("势力", side.power.toString())
                StatBlockMini("武勋", side.wuxun.toString())
                StatBlockMini("兵力", side.hp.toString())
            }
            if (side.heroes.isNotEmpty()) {
                SectionLabel("出战武将")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    side.heroes.forEach { hero ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(hero.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Lv.${hero.level} · 进阶${hero.star} · 兵力 ${hero.remainHp}/${hero.maxHp}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlockMini(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
