package com.local.stzb.feature.simulator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.LocalSimHeroConfig
import com.example.myapplication.LocalSimHeroOption
import com.example.myapplication.LocalSimSkillOption
import com.example.myapplication.LocalSimTeamConfig
import com.local.stzb.core.ui.ErrorPanel
import com.local.stzb.core.ui.LoadingPanel
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.domain.battlefield.BattlefieldHero
import com.local.stzb.feature.battlefield.BattlefieldHeroPortrait

@Composable
fun BattleSimulatorScreen(
    state: BattleSimulatorUiState,
    onIntent: (BattleSimulatorIntent) -> Unit,
    heroName: (Long) -> String,
    heroIconId: (Long) -> Long,
    skillName: (Long) -> String,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    when {
        state.loading -> LoadingPanel(modifier.fillMaxSize())
        state.config == null -> ErrorPanel(state.error ?: "模拟器配置加载失败", false, {}, modifier.fillMaxSize())
        else -> LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回工具")
                            }
                        }
                        Text("战斗模拟器", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "本地武将 ${state.heroOptions.size} · 战法 ${state.skillOptions.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SimulatorTeamCard("攻方", SimulatorCamp.BLUE, state.config.blue, onIntent, heroName, heroIconId, skillName)
            }
            item {
                SimulatorTeamCard("守方", SimulatorCamp.RED, state.config.red, onIntent, heroName, heroIconId, skillName)
            }
            item { RunActions(state.running, onIntent) }
            state.error?.let { message ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onIntent(BattleSimulatorIntent.DismissError) },
                    ) {
                        Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            state.result?.let { result ->
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("模拟结果 · ${result.repeat} 次", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("攻方胜率 ${"%.1f".format(result.blueWinRate)}%")
                            Text("守方胜率 ${"%.1f".format(result.redWinRate)}%")
                            Text("平局 ${"%.1f".format(result.drawRate)}%")
                            Text("首场：攻方剩余 ${result.firstRun.blueRemain} · 守方剩余 ${result.firstRun.redRemain}")
                            Button(onClick = onOpenLog, modifier = Modifier.fillMaxWidth()) { Text("查看战斗日志") }
                        }
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 10.dp)) }
        }
    }

    state.picker?.let { picker ->
        SimulatorPickerDialog(picker, state.heroOptions, state.skillOptions, onIntent)
    }
}

@Composable
private fun SimulatorTeamCard(
    title: String,
    camp: SimulatorCamp,
    team: LocalSimTeamConfig,
    onIntent: (BattleSimulatorIntent) -> Unit,
    heroName: (Long) -> String,
    heroIconId: (Long) -> Long,
    skillName: (Long) -> String,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Stepper(
                    label = "士气 ${team.morale}",
                    onMinus = { onIntent(BattleSimulatorIntent.SetMorale(camp, team.morale - 5)) },
                    onPlus = { onIntent(BattleSimulatorIntent.SetMorale(camp, team.morale + 5)) },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
                team.heroes.take(3).forEachIndexed { index, hero ->
                    SimulatorHeroCard(
                        camp, index, hero, onIntent, heroName, heroIconId, skillName, Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SimulatorHeroCard(
    camp: SimulatorCamp,
    position: Int,
    hero: LocalSimHeroConfig,
    onIntent: (BattleSimulatorIntent) -> Unit,
    heroName: (Long) -> String,
    heroIconId: (Long) -> Long,
    skillName: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val name = heroName(hero.heroId)
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(listOf("大营", "中军", "前锋").getOrElse(position) { "${position + 1}号位" }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            BattlefieldHeroPortrait(BattlefieldHero("", hero.heroId, heroIconId(hero.heroId), name, hero.level, hero.advance, emptyList()))
            TextButton(onClick = { onIntent(BattleSimulatorIntent.OpenHeroPicker(camp, position)) }) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Stepper("等级 ${hero.level}", { onIntent(BattleSimulatorIntent.SetLevel(camp, position, hero.level - 1)) }, { onIntent(BattleSimulatorIntent.SetLevel(camp, position, hero.level + 1)) })
            Stepper("红度 ${hero.advance}", { onIntent(BattleSimulatorIntent.SetAdvance(camp, position, hero.advance - 1)) }, { onIntent(BattleSimulatorIntent.SetAdvance(camp, position, hero.advance + 1)) })
            repeat(3) { slot ->
                OutlinedButton(
                    onClick = { onIntent(BattleSimulatorIntent.OpenSkillPicker(camp, position, slot)) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(hero.equipSkillIds.getOrNull(slot)?.let(skillName) ?: "选择战法", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = onMinus, modifier = Modifier.heightIn(min = 40.dp)) { Text("−") }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        TextButton(onClick = onPlus, modifier = Modifier.heightIn(min = 40.dp)) { Text("+") }
    }
}

@Composable
private fun RunActions(running: Boolean, onIntent: (BattleSimulatorIntent) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("开始模拟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("模拟计算中…")
                }
            }
            Button({ onIntent(BattleSimulatorIntent.Run(1)) }, enabled = !running, modifier = Modifier.fillMaxWidth()) { Text("单次模拟") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ onIntent(BattleSimulatorIntent.Run(100)) }, enabled = !running, modifier = Modifier.weight(1f)) { Text("模拟 100 次") }
                OutlinedButton({ onIntent(BattleSimulatorIntent.Run(1000)) }, enabled = !running, modifier = Modifier.weight(1f)) { Text("模拟 1000 次") }
            }
        }
    }
}

@Composable
private fun SimulatorPickerDialog(
    picker: SimulatorPicker,
    heroes: List<LocalSimHeroOption>,
    skills: List<LocalSimSkillOption>,
    onIntent: (BattleSimulatorIntent) -> Unit,
) {
    val query = picker.query.trim()
    AlertDialog(
        onDismissRequest = { onIntent(BattleSimulatorIntent.ClosePicker) },
        confirmButton = { TextButton({ onIntent(BattleSimulatorIntent.ClosePicker) }) { Text("关闭") } },
        title = { Text(if (picker is SimulatorPicker.Hero) "选择武将" else "选择战法") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = picker.query,
                    onValueChange = { onIntent(BattleSimulatorIntent.PickerQuery(it)) },
                    label = { Text("搜索") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 430.dp)) {
                    when (picker) {
                        is SimulatorPicker.Hero -> items(heroes.filter { it.matches(query) }, key = { it.id }) { hero ->
                            PickerRow(hero.name, listOf(hero.country, hero.armyType).filter(String::isNotBlank).joinToString(" ")) {
                                onIntent(BattleSimulatorIntent.SelectHero(hero.id))
                            }
                        }
                        is SimulatorPicker.Skill -> {
                            item { PickerRow("清空该槽", "不装备战法") { onIntent(BattleSimulatorIntent.SelectSkill(null)) } }
                            items(skills.filter { it.matches(query) }, key = { it.id }) { skill ->
                                PickerRow(skill.name, "${skill.type} · 发动 ${"%.0f".format(skill.probability)}%") {
                                    onIntent(BattleSimulatorIntent.SelectSkill(skill.id))
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun PickerRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LocalSimHeroOption.matches(query: String): Boolean = query.isBlank() ||
    listOf(name, country, armyType).any { it.contains(query, ignoreCase = true) }

private fun LocalSimSkillOption.matches(query: String): Boolean = query.isBlank() ||
    listOf(name, type, desc).any { it.contains(query, ignoreCase = true) }
