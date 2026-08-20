package com.local.stzb.feature.simulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.myapplication.LocalSimulationRun
import com.local.stzb.core.ui.EmptyPanel
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.MacGlassHeader
import com.local.stzb.core.ui.StatBlock

@Composable
fun BattleLogScreen(run: LocalSimulationRun?, onBack: () -> Unit) {
    if (run == null) {
        EmptyPanel("还没有可查看的战斗日志", null, {}, Modifier.fillMaxSize())
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MacGlassHeader(
            title = "战斗日志",
            subtitle = "共 ${run.records.size} 条",
            leading = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回模拟器") } },
        )
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("首场结果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatBlock("胜者", run.winner, emphasis = true)
                    StatBlock("攻方剩余", run.blueRemain.toString())
                    StatBlock("守方剩余", run.redRemain.toString())
                }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(run.records) { index, record ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "${index + 1}. $record",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
