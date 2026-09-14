package com.local.stzb.feature.research

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.*
import com.local.stzb.data.research.*

@Composable
fun SharedLineupRankingsScreen(state: SharedLineupUiState, heroName: (Long) -> String, onTabChange: (SharedRankingTab) -> Unit, onQueryChange: (String) -> Unit, onRefresh: () -> Unit, onOpenSimulator: (List<Long>) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MacGlassHeader("全服阵容榜", "共享实战样本 · 保守胜率 · 克制关系", leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回工具") } })
        val summary = state.snapshot?.summary
        val summaryText = if (summary == null) "正在收集共享样本" else summary.eligibleBattles.toString() + " 场有效战报 · " + summary.uniqueLineups + " 套阵容 · " + summary.uniqueMatchups + " 组对阵"
        GlassCard(Modifier.fillMaxWidth()) { Text(summaryText, Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SharedRankingTab.entries.forEach { tab -> FilterChip(state.tab == tab, { onTabChange(tab) }, { Text(tab.label) }) } }
        OutlinedTextField(state.query, onQueryChange, label = { Text("搜索武将") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        when {
            state.loading -> LoadingPanel(Modifier.weight(1f))
            state.error != null -> ErrorPanel(state.error, true, onRefresh, Modifier.weight(1f))
            summary == null || summary.eligibleBattles == 0 -> EmptyPanel("共享库正在收集样本", "刷新", onRefresh, Modifier.weight(1f))
            else -> RankingList(state, heroName, onOpenSimulator, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RankingList(state: SharedLineupUiState, heroName: (Long) -> String, open: (List<Long>) -> Unit, modifier: Modifier) {
    val query = state.query.trim(); val snapshot = state.snapshot ?: return
    val counters = snapshot.counters.filter { row -> query.isBlank() || (row.lineup.heroIds + row.opponentHeroIds).any { heroName(it).contains(query, true) } }
    val rows = when (state.tab) { SharedRankingTab.POPULAR -> snapshot.popular; SharedRankingTab.WIN_RATE -> snapshot.winRate; SharedRankingTab.COUNTERS -> emptyList() }.filter { row -> query.isBlank() || row.heroIds.any { heroName(it).contains(query, true) } }
    if (state.tab == SharedRankingTab.COUNTERS) {
        if (counters.isEmpty()) { EmptyPanel("暂无达到门槛的克制样本", null, {}, modifier); return }
        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) { items(counters, key = { it.lineup.lineupKey + ">" + it.opponentKey }) { SharedCard(it.lineup, heroName, open, it.opponentHeroIds) } }
    } else {
        if (rows.isEmpty()) { EmptyPanel("没有匹配的阵容", null, {}, modifier); return }
        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) { items(rows, key = { it.lineupKey }) { SharedCard(it, heroName, open) } }
    }
}

@Composable
private fun SharedCard(row: SharedLineupRow, heroName: (Long) -> String, open: (List<Long>) -> Unit, opponent: List<Long>? = null) {
    GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(row.heroIds.joinToString(" / ") { heroName(it) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (opponent != null) Text("优势对阵：" + opponent.joinToString(" / ") { heroName(it) }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Text(row.samples.toString() + " 场 · " + row.wins + "胜 " + row.draws + "平 " + row.losses + "负 · " + row.confidence + "可信", style = MaterialTheme.typography.bodySmall)
        Text("实战胜率 " + "%.1f".format(row.rawWinRate) + "% · 保守胜率 " + "%.1f".format(row.conservativeWinRate) + "%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { open(row.heroIds) }, modifier = Modifier.fillMaxWidth()) { Text("带入模拟器") }
    } }
}
