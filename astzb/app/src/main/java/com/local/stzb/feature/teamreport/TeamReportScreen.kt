package com.local.stzb.feature.teamreport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.EmptyPanel
import com.local.stzb.core.ui.ErrorPanel
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.LoadingPanel
import com.local.stzb.core.ui.MacGlassHeader
import com.local.stzb.domain.rankings.ReportDimension
import com.local.stzb.domain.rankings.ReportPeriod
import com.local.stzb.domain.rankings.TeamReportSnapshot
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MacGlassHeader(
            title = "团队报表",
            subtitle = "团队战绩、武勋与成员统计",
            leading = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回工具")
                    }
                }
            },
            trailing = {
                OutlinedButton(
                    onClick = { exporter.launch("团队报表_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}.csv") },
                    enabled = !report?.rows.isNullOrEmpty(),
                ) { Text("导出") }
            },
        )
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(ReportPeriod.entries, state.period, { it.label }, viewModel::setPeriod)
                ChipRow(ReportDimension.entries, state.dimension, { it.label }, viewModel::setDimension)
                if (state.dimension == ReportDimension.PLAYER) {
                    ChipRow(listOf("") + state.report?.groups.orEmpty(), state.group, { it.ifBlank { "全部" } }, viewModel::setGroup)
                }
            }
        }
        when {
            state.loading -> LoadingPanel(Modifier.weight(1f))
            state.error != null -> ErrorPanel(state.error, true, viewModel::refresh, Modifier.weight(1f))
            state.report?.rows.isNullOrEmpty() -> EmptyPanel("本机还没有团队报表数据", "刷新", viewModel::refresh, Modifier.weight(1f))
            else -> ReportRows(state.report!!, Modifier.weight(1f))
        }
    }
}

@Composable
private fun <T> ChipRow(values: List<T>, selected: T, label: (T) -> String, select: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.distinct().forEach { value ->
            FilterChip(value == selected, { select(value) }, { Text(label(value)) }, Modifier.heightIn(min = 44.dp))
        }
    }
}

@Composable
private fun ReportRows(snapshot: TeamReportSnapshot, modifier: Modifier) {
    val totalBattles = snapshot.rows.sumOf { it.battles }
    val totalGongxun = snapshot.rows.sumOf { it.totalGongxun }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatPill("共 ${snapshot.rows.size}", "成员")
                StatPill("战斗 $totalBattles", "总场次")
                StatPill("武勋 $totalGongxun", "累计")
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(snapshot.rows, key = { "${it.rank}:${it.groupName}:${it.name}" }) { row ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("#${row.rank}  ${row.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("武勋 ${row.totalGongxun}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        if (row.groupName != row.name) Text(row.groupName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "战斗 ${row.battles} · 胜${row.wins}/负${row.losses}/平${row.draws} · 胜率 ${"%.1f".format(row.winRate)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
