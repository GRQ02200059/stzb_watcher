package com.local.stzb.feature.research

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.EmptyPanel
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.MacGlassHeader
import com.local.stzb.core.ui.SectionLabel
import com.local.stzb.data.research.LineupResearchRepository
import com.local.stzb.data.research.LineupResearchRow

@Composable
fun LineupResearchScreen(
    repository: LineupResearchRepository,
    onOpenSimulator: (List<Long>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val rows = remember(query) { repository.load(query) }
    val quality = remember { repository.quality() }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MacGlassHeader("阵容战法研究", "配置事实 / 历史证据 / 模拟验证", leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回工具") } })
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("数据状态", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(
                    "已解析 ${quality.typedCommandCount} · 原始保留 ${quality.rawCommandCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                quality.warnings.forEach { warning ->
                    Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(query, { query = it }, label = { Text("搜索武将或组合") }, modifier = Modifier.fillMaxWidth().padding(12.dp), singleLine = true)
        }
        if (rows.isEmpty()) {
            EmptyPanel("本机还没有可研究的三人组合，请先采集完整战报", null, {}, Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows, key = { it.heroNames.joinToString("+") }) { ResearchCard(it, onOpenSimulator) }
            }
        }
    }
}

@Composable
private fun ResearchCard(row: LineupResearchRow, onOpenSimulator: (List<Long>) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(row.heroNames.joinToString(" / "), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel(row.configEvidence.label)
                Text(row.configEvidence.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel(row.historicalEvidence.label)
                Text(row.historicalEvidence.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel(row.simulationEvidence.label)
                Text(row.simulationEvidence.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { onOpenSimulator(row.heroIds) }, enabled = row.canOpenSimulator, modifier = Modifier.fillMaxWidth()) { Text("带入攻方模拟") }
        }
    }
}
