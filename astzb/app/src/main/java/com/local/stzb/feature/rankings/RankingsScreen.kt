package com.local.stzb.feature.rankings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.*
import com.local.stzb.domain.rankings.*

@Composable
fun RankingsScreen(state: RankingsUiState, viewModel: RankingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassToolbar(title = "排行与团队报表") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onBack, Modifier.heightIn(min = 48.dp)) { Text("返回") }
                Text(
                    "排行与团队报表",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "筛选条件",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipRow(RankingPage.entries, state.page, { it.label }, viewModel::setPage)
                if (state.page == RankingPage.RANKINGS) {
                    ChipRow(RankingCategory.entries, state.category, { it.label }, viewModel::setCategory)
                } else {
                    ChipRow(ReportPeriod.entries, state.period, { it.label }, viewModel::setPeriod)
                    ChipRow(ReportDimension.entries, state.dimension, { it.label }, viewModel::setDimension)
                    if (state.dimension == ReportDimension.PLAYER) {
                        val groups = listOf("") + (state.report?.groups ?: emptyList())
                        ChipRow(groups.distinct(), state.group, { it.ifBlank { "全部分组" } }, viewModel::setGroup)
                    }
                }
            }
        }
        when {
            state.loading -> LoadingPanel(Modifier.weight(1f))
            state.error != null -> ErrorPanel(state.error, true, viewModel::refresh, Modifier.weight(1f))
            state.page == RankingPage.RANKINGS -> RankingsContent(state, Modifier.weight(1f), viewModel::refresh)
            else -> ReportContent(state.report, Modifier.weight(1f), viewModel::refresh)
        }
    }
}

@Composable private fun <T> ChipRow(values: List<T>, selected: T, label: (T) -> String, select: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(value == selected, { select(value) }, { Text(label(value)) }, Modifier.heightIn(min = 48.dp)) }
    }
}

@Composable private fun RankingsContent(state: RankingsUiState, modifier: Modifier, refresh: () -> Unit) {
    val rows = state.rankings?.rows(state.category).orEmpty()
    if (rows.isEmpty()) { EmptyPanel("本机还没有${state.category.label}数据", "刷新", refresh, modifier); return }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows, key = { "${state.category}:${it.rank}:${it.name}" }) { row ->
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), Arrangement.spacedBy(4.dp)) {
                    Text("#${row.rank}  ${row.name.ifBlank { "未知" }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (row.groupName.isNotBlank()) Text(row.groupName, color = MaterialTheme.colorScheme.primary)
                    val metric = when (state.category) {
                        RankingCategory.BATTLE -> "战功 ${row.value} · ${row.battles} 场 · 胜率 ${"%.1f".format(row.winRate)}%"
                        RankingCategory.UNION -> "势力 ${row.value} · ${row.members} 人"
                        RankingCategory.PLAYER_POWER -> "势力 ${row.value}"
                    }
                    Text(metric)
                }
            }
        }
    }
}

@Composable private fun ReportContent(snapshot: TeamReportSnapshot?, modifier: Modifier, refresh: () -> Unit) {
    val rows = snapshot?.rows.orEmpty()
    if (rows.isEmpty()) { EmptyPanel("本机还没有团队报表数据", "刷新", refresh, modifier); return }
    val totalBattles = rows.sumOf { it.battles }
    val totalGongxun = rows.sumOf { it.totalGongxun }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("共 ${rows.size} 项 · 战斗 $totalBattles · 武勋 $totalGongxun", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { "${it.rank}:${it.groupName}:${it.name}" }) { row ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), Arrangement.spacedBy(4.dp)) {
                        Text("#${row.rank}  ${row.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (row.groupName != row.name) Text(row.groupName, color = MaterialTheme.colorScheme.primary)
                        Text("战斗 ${row.battles} · 胜 ${row.wins} / 负 ${row.losses} / 平 ${row.draws} · 胜率 ${"%.1f".format(row.winRate)}%")
                        Text("攻城 ${row.siegeBattles}（胜 ${row.siegeWins}）· 武勋 ${row.totalGongxun} · 成员 ${row.members}")
                    }
                }
            }
        }
    }
}
