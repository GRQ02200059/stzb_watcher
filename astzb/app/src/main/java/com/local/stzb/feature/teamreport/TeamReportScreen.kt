package com.local.stzb.feature.teamreport

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.EmptyPanel
import com.local.stzb.core.ui.ErrorPanel
import com.local.stzb.core.ui.LoadingPanel
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.domain.rankings.*

@Composable
fun TeamReportScreen(state: TeamReportUiState, viewModel: TeamReportViewModel, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val report = state.report
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null && report != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(TeamReportCsv.encode(report, state.dimension, state.period, state.group))
            }
        }
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回工具")
                    }
                }
                Text("团队报表", style = MaterialTheme.typography.headlineMedium)
            }
            OutlinedButton(
                onClick = { exporter.launch("团队报表_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}.csv") },
                enabled = !report?.rows.isNullOrEmpty(),
            ) { Text("导出 CSV") }
        }
        ChipRow(ReportPeriod.entries, state.period, { it.label }, viewModel::setPeriod)
        ChipRow(ReportDimension.entries, state.dimension, { it.label }, viewModel::setDimension)
        if (state.dimension == ReportDimension.PLAYER) {
            ChipRow(listOf("") + state.report?.groups.orEmpty(), state.group, { it.ifBlank { "全部分组" } }, viewModel::setGroup)
        }
        when {
            state.loading -> LoadingPanel(Modifier.weight(1f))
            state.error != null -> ErrorPanel(state.error, true, viewModel::refresh, Modifier.weight(1f))
            state.report?.rows.isNullOrEmpty() -> EmptyPanel("本机还没有团队报表数据", "刷新", viewModel::refresh, Modifier.weight(1f))
            else -> ReportRows(state.report!!, Modifier.weight(1f))
        }
    }
}

@Composable private fun <T> ChipRow(values: List<T>, selected: T, label: (T) -> String, select: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.distinct().forEach { value -> FilterChip(value == selected, { select(value) }, { Text(label(value)) }, Modifier.heightIn(min = 48.dp)) }
    }
}

@Composable private fun ReportRows(snapshot: TeamReportSnapshot, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("共 ${snapshot.rows.size} 项 · 战斗 ${snapshot.rows.sumOf { it.battles }} · 武勋 ${snapshot.rows.sumOf { it.totalGongxun }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(snapshot.rows, key = { "${it.rank}:${it.groupName}:${it.name}" }) { row ->
                GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), Arrangement.spacedBy(4.dp)) {
                    Text("#${row.rank}  ${row.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (row.groupName != row.name) Text(row.groupName, color = MaterialTheme.colorScheme.primary)
                    Text("战斗 ${row.battles} · 胜 ${row.wins} / 负 ${row.losses} / 平 ${row.draws} · 胜率 ${"%.1f".format(row.winRate)}%")
                    Text("攻城 ${row.siegeBattles}（胜 ${row.siegeWins}）· 武勋 ${row.totalGongxun} · 成员 ${row.members}")
                } }
            }
        }
    }
}
