package com.local.stzb.feature.battles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.LoadingPanel
import com.local.stzb.domain.battles.BattleSide

@Composable
fun BattleDetailScreen(state: BattlesUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val detail = state.selected
    if (detail == null) { LoadingPanel(modifier); return }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回战报列表") }
                Column { Text(detail.summary.outcomeLabel, color = MaterialTheme.colorScheme.primary); Text(detail.summary.title, style = MaterialTheme.typography.headlineSmall) }
            }
        }
        item { Text(detail.summary.locationAndType) }
        item { SideCard(detail.attacker) }
        item { SideCard(detail.defender) }
        item { Text("天气 ${detail.weather} · 夜战 ${if (detail.nightBattle) "是" else "否"}") }
    }
}

@Composable
private fun SideCard(side: BattleSide) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(side.label, fontWeight = FontWeight.Bold)
            Text(side.name.ifBlank { "未知" }, style = MaterialTheme.typography.titleLarge)
            Text("同盟 ${side.unionName.ifBlank { "-" }} · 势力 ${side.power} · 武勋 ${side.wuxun} · 兵力 ${side.hp}")
            side.heroes.forEach { hero -> Text("${hero.name} Lv.${hero.level} 进阶${hero.star} · ${hero.remainHp}/${hero.maxHp}") }
        }
    }
}
