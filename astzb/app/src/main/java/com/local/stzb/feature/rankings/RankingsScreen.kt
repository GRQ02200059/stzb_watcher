package com.local.stzb.feature.rankings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.EmptyPanel
import com.local.stzb.core.ui.ErrorPanel
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.LoadingPanel
import com.local.stzb.core.ui.MacGlassHeader
import com.local.stzb.domain.rankings.RankingCategory
import com.local.stzb.domain.rankings.RankingPage
import com.local.stzb.domain.rankings.ReportDimension
import com.local.stzb.domain.rankings.ReportPeriod
import com.local.stzb.domain.rankings.TeamReportSnapshot

@Composable
fun RankingsScreen(state: RankingsUiState, viewModel: RankingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MacGlassHeader(
            title = "排行与团队报表",
            subtitle = "排行榜、周期报表与团队维度",
            leading = {
                IconButton(onBack) { Text("返回", style = MaterialTheme.typography.labelLarge) }
            },
        )

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChipRow(RankingPage.entries, state.page, { it.label }, viewModel::setPage)
                if (state.page == RankingPage.RANKINGS) {
                    ChipRow(RankingCategory.entries, state.category, { it.label }, viewModel::setCategory)
                } else {
                    ChipRow(ReportPeriod.entries, state.period, { it.label }, viewModel::setPeriod)
                    ChipRow(ReportDimension.entries, state.dimension, { it.label }, viewModel::setDimension)
                    if (state.dimension == ReportDimension.PLAYER) {
                        val groups = listOf("") + (state.report?.groups ?: emptyList())
                        ChipRow(groups.distinct(), state.group, { it.ifBlank { "全部" } }, viewModel::setGroup)
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

@Composable
private fun <T> ChipRow(values: List<T>, selected: T, label: (T) -> String, select: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(value == selected, { select(value) }, { Text(label(value)) }, Modifier.heightIn(min = 44.dp))
        }
    }
}

@Composable
private fun RankRow(
    rank: Int,
    name: String,
    subtitle: String?,
    trailing: String,
    onClick: (() -> Unit)? = null,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(rankColor(rank)),
                contentAlignment = Alignment.Center,
            ) {
                Text("#$rank", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { "未知" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
            Text(
                trailing,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun rankColor(rank: Int) = when (rank) {
    1 -> androidx.compose.ui.graphics.Color(0xFFFFD700)
    2 -> androidx.compose.ui.graphics.Color(0xFFC0C0C0)
    3 -> androidx.compose.ui.graphics.Color(0xFFCD7F32)
    else -> androidx.compose.ui.graphics.Color(0xFF426AD2)
}

@Composable
private fun RankingsContent(state: RankingsUiState, modifier: Modifier, refresh: () -> Unit) {
    val rows = state.rankings?.rows(state.category).orEmpty()
    if (rows.isEmpty()) { EmptyPanel("本机还没有${state.category.label}数据", "刷新", refresh, modifier); return }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows, key = { "${state.category}:${it.rank}:${it.name}" }) { row ->
            val metric = when (state.category) {
                RankingCategory.BATTLE -> "战功 ${row.value} · ${row.battles}场·${"%.1f".format(row.winRate)}%"
                RankingCategory.UNION -> "势力 ${row.value} · ${row.members}人"
                RankingCategory.PLAYER_POWER -> "势力 ${row.value}"
            }
            RankRow(row.rank, row.name, row.groupName.takeIf { it.isNotBlank() && it != row.name }, metric)
        }
    }
}

@Composable
private fun ReportContent(snapshot: TeamReportSnapshot?, modifier: Modifier, refresh: () -> Unit) {
    val rows = snapshot?.rows.orEmpty()
    if (rows.isEmpty()) { EmptyPanel("本机还没有团队报表数据", "刷新", refresh, modifier); return }
    val totalBattles = rows.sumOf { it.battles }
    val totalGongxun = rows.sumOf { it.totalGongxun }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatPill("共 ${rows.size} 项", "团队/成员")
                StatPill("战斗 $totalBattles", "总场次")
                StatPill("武勋 $totalGongxun", "累计")
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { "${it.rank}:${it.groupName}:${it.name}" }) { row ->
                RankRow(
                    rank = row.rank,
                    name = row.name,
                    subtitle = row.groupName.takeIf { it != row.name },
                    trailing = "武勋 ${row.totalGongxun}",
                )
            }
        }
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
