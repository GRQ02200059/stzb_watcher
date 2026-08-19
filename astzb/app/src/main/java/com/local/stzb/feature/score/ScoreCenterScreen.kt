package com.local.stzb.feature.score

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.LocalStzbRepository
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.MacGlassHeader
import com.local.stzb.data.score.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AndroidScoreRepository : ScoreRepository {
    override fun rules() = LocalStzbRepository.scoreRules()
    override fun createRule(name: String, presetKey: String, config: ScoreRuleConfig) = LocalStzbRepository.createScoreRule(name, presetKey, config)
    override fun activateRule(id: Long) = LocalStzbRepository.activateScoreRule(id)
    override fun activeRule() = rules().firstOrNull { it.status == RuleStatus.ACTIVE }
    override fun adjustments() = LocalStzbRepository.scoreAdjustments()
    override fun addAdjustment(playerName: String, points: Double, reason: String) = LocalStzbRepository.addScoreAdjustment(playerName, points, reason)
    override fun metrics() = LocalStzbRepository.playerScoreMetrics()
    override fun replaceScores(ruleId: Long, rows: List<ScoreRow>) = LocalStzbRepository.replaceCustomScores(ruleId, rows)
    override fun scores() = LocalStzbRepository.customScores()
}

data class ScoreCenterUiState(
    val rules: List<ScoreRuleVersion> = emptyList(), val activeRule: ScoreRuleVersion? = null,
    val adjustments: List<ScoreAdjustment> = emptyList(), val savedRows: List<ScoreRow> = emptyList(),
    val preview: ScorePreview? = null, val busy: Boolean = false, val message: String? = null, val error: String? = null,
)

class ScoreCenterViewModel(
    private val repository: ScoreRepository = AndroidScoreRepository,
) : ViewModel() {
    private val service = ScoreService(repository)
    private val mutableState = MutableStateFlow(ScoreCenterUiState())
    val state: StateFlow<ScoreCenterUiState> = mutableState.asStateFlow()
    init { refresh() }
    fun refresh() = perform { publish() }
    fun createPreset(name: String, key: String) = perform { repository.createRule(name, key, ScorePresets.byKey(key)); publish("规则已创建，请激活") }
    fun activate(id: Long) = perform { repository.activateRule(id); publish("规则已激活") }
    fun addAdjustment(player: String, points: Double, reason: String) = perform { repository.addAdjustment(player, points, reason); publish("调整已保存") }
    fun preview() = perform { mutableState.value = mutableState.value.copy(preview = service.preview(), message = "预览不会写入数据库") }
    fun confirmPreview() { val token = mutableState.value.preview?.token ?: return; perform { service.confirm(token).getOrThrow(); publish("重算已写入") } }
    fun dismissPreview() { mutableState.value = mutableState.value.copy(preview = null) }
    private fun publish(message: String? = null) { mutableState.value = mutableState.value.copy(rules = repository.rules(), activeRule = repository.activeRule(), adjustments = repository.adjustments(), savedRows = repository.scores(), preview = null, message = message) }
    private fun perform(block: suspend () -> Unit) { viewModelScope.launch { mutableState.value = mutableState.value.copy(busy = true, error = null); runCatching { withContext(Dispatchers.IO) { block() } }.onSuccess { mutableState.value = mutableState.value.copy(busy = false) }.onFailure { mutableState.value = mutableState.value.copy(busy = false, error = it.message ?: "操作失败") } } }
}

@Composable
fun ScoreCenterScreen(state: ScoreCenterUiState, viewModel: ScoreCenterViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var player by remember { mutableStateOf("") }; var points by remember { mutableStateOf("") }; var reason by remember { mutableStateOf("") }
    state.preview?.let { preview -> AlertDialog(
        onDismissRequest = viewModel::dismissPreview, title = { Text("积分重算预览") },
        text = { Text("规则 v${preview.rule.version} · ${preview.rows.size} 人\n前 3：${preview.rows.take(3).joinToString { "${it.playerName} ${it.score}" }}") },
        confirmButton = { TextButton(onClick = viewModel::confirmPreview) { Text("确认写入") } },
        dismissButton = { TextButton(onClick = viewModel::dismissPreview) { Text("取消") } },
    ) }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MacGlassHeader("自定义积分", state.activeRule?.let { "当前 v${it.version} ${it.name}" } ?: "请创建并激活规则", leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回工具") } }) }
        item { GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(14.dp), Arrangement.spacedBy(8.dp)) {
            Text("规则预设", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ viewModel.createPreset("联盟贡献", "alliance_contribution") }, Modifier.weight(1f)) { Text("联盟贡献") }
                OutlinedButton({ viewModel.createPreset("赛季奖励", "season_reward") }, Modifier.weight(1f)) { Text("赛季奖励") }
                OutlinedButton({ viewModel.createPreset("攻城优先", "siege_priority") }, Modifier.weight(1f)) { Text("攻城优先") }
            }
            state.rules.forEach { rule -> Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("v${rule.version} ${rule.name} · ${rule.status.name}"); if (rule.status != RuleStatus.ACTIVE) TextButton({ viewModel.activate(rule.id) }) { Text("激活") } } }
        } } }
        item { GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(14.dp), Arrangement.spacedBy(8.dp)) {
            Text("手工调整", fontWeight = FontWeight.Bold); OutlinedTextField(player, { player = it }, label = { Text("玩家名") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(points, { points = it }, label = { Text("分值") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(reason, { reason = it }, label = { Text("原因（必填）") }, modifier = Modifier.fillMaxWidth()); Button({ viewModel.addAdjustment(player, points.toDoubleOrNull() ?: 0.0, reason) }, enabled = player.isNotBlank() && reason.isNotBlank() && points.toDoubleOrNull() != null, modifier = Modifier.fillMaxWidth()) { Text("保存调整") }
        } } }
        item { Button(viewModel::preview, enabled = state.activeRule != null && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("预览重算") } }
        if (state.savedRows.isNotEmpty()) item { Text("已保存榜单", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(state.savedRows, key = { it.playerName }) { row -> GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), Arrangement.spacedBy(4.dp)) { Text("#${row.rank} ${row.playerName}  ${row.score}", fontWeight = FontWeight.Bold); Text("战斗 ${row.battleScore} · 攻城 ${row.siegeScore} · 调整 ${row.adjustmentScore}") } } }
        state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }; state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}
