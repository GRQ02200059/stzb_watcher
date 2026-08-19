package com.local.stzb.feature.attendance

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.local.stzb.core.ui.LoadingPanel
import com.local.stzb.core.ui.MacGlassHeader

@Composable
fun AttendanceScreen(
    state: AttendanceUiState,
    viewModel: AttendanceViewModel,
    onExportCsv: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var pos by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf("") }
    var taskTime by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("删除工程任务") },
        text = { Text("确认删除「${state.selectedTask?.name.orEmpty()}」吗？") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteSelected() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MacGlassHeader(
            title = state.selectedTask?.name ?: "攻城考勤",
            subtitle = state.selectedTask?.let { "${it.cityId / 10_000},${it.cityId % 10_000} · 目标 ${it.targetUserNum} · 已出战 ${it.completeUserNum}" } ?: "任务、成员、战报与统计",
            leading = { IconButton(onClick = { if (state.selectedTask != null) viewModel.closeTask() else onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
        )
        if (state.loading) { LoadingPanel(Modifier.weight(1f)); return@Column }
        if (state.selectedTask == null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("新建任务", fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, { name = it }, label = { Text("任务名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(pos, { pos = it }, label = { Text("坐标 WID 或 X,Y") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(groups, { groups = it }, label = { Text("目标分组，逗号分隔；留空为全员") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(taskTime, { taskTime = it }, label = { Text("任务时间（Unix 秒，可留空）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { viewModel.createTask(name, pos, groups.split(',', '，').map(String::trim).filter(String::isNotBlank), taskTime.toLongOrNull() ?: 0L) }, enabled = name.isNotBlank() && pos.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("创建任务") }
                }
            }
            if (state.tasks.isEmpty()) EmptyPanel("还没有攻城任务", null, {}, Modifier.weight(1f))
            else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.tasks, key = { it.id }) { task ->
                    GlassCard(Modifier.fillMaxWidth(), onClick = { viewModel.openTask(task.id) }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(task.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("坐标 ${task.cityId / 10_000},${task.cityId % 10_000} · ${task.targetGroups.ifBlank { "全员" }}")
                            Text("目标 ${task.targetUserNum} · 已出战 ${task.completeUserNum}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::calculateSelected, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("开始统计") }
                OutlinedButton(onClick = { onExportCsv(viewModel.selectedCsv(), "${state.selectedTask.name}_考勤.csv") }, modifier = Modifier.weight(1f)) { Text("导出 CSV") }
            }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("删除任务") }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("成员考勤", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(state.attendance, key = { it.uid }) { row ->
                    GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), Arrangement.spacedBy(4.dp)) {
                        Text("${row.name} · ${row.status}", fontWeight = FontWeight.Bold)
                        Text("${row.groupName} · 主力 ${row.atkNum} · 拆迁 ${row.disNum} · 武勋 ${row.gongxun}")
                    } }
                }
                item { Text("关联战报 ${state.battles.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(state.battles, key = { it.battleId }) { battle ->
                    GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), Arrangement.spacedBy(4.dp)) {
                        Text("#${battle.battleId} ${battle.attackerName}", fontWeight = FontWeight.Bold)
                        Text("${if (battle.garrison == 1) "拆迁" else "主力"} · ${battle.heroes}")
                    } }
                }
            }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
