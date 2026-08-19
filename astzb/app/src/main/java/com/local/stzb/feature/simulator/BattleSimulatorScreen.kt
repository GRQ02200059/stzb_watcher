package com.local.stzb.feature.simulator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.LocalSimHeroConfig
import com.example.myapplication.LocalSimHeroOption
import com.example.myapplication.LocalSimSkillOption
import com.example.myapplication.LocalSimTeamConfig
import com.example.myapplication.R
import com.local.stzb.core.ui.ErrorPanel
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.LoadingPanel
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
        state.tacticalView == TacticalSimulatorView.REPORTS -> TacticalReportsLibrary(
            reports = state.reports,
            onOpenReport = { onIntent(BattleSimulatorIntent.SelectReport(it)) },
            onBackToDuel = { onIntent(BattleSimulatorIntent.SelectTacticalView(TacticalSimulatorView.DUEL)) },
            modifier = modifier,
        )
        state.tacticalView == TacticalSimulatorView.DETAIL -> TacticalReportDetail(
            run = state.reports.firstOrNull { it.id == state.selectedReportId }?.run ?: state.result?.firstRun,
            reportTab = state.reportTab,
            selectedEventIndex = state.selectedEventIndex,
            heroIconId = heroIconId,
            onSelectTab = { onIntent(BattleSimulatorIntent.SelectReportTab(it)) },
            onSelectEvent = { onIntent(BattleSimulatorIntent.SelectEvent(it)) },
            onBackToReports = { onIntent(BattleSimulatorIntent.SelectTacticalView(TacticalSimulatorView.REPORTS)) },
            modifier = modifier,
        )
        else -> TacticalDuelScreen(
            state = state,
            onIntent = onIntent,
            heroName = heroName,
            heroIconId = heroIconId,
            skillName = skillName,
            onBack = onBack,
            modifier = modifier,
        )
    }

    state.picker?.let { picker ->
        SimulatorPickerDialog(picker, state.heroOptions, state.skillOptions, onIntent)
    }
}

@Composable
private fun TacticalDuelScreen(
    state: BattleSimulatorUiState,
    onIntent: (BattleSimulatorIntent) -> Unit,
    heroName: (Long) -> String,
    heroIconId: (Long) -> Long,
    skillName: (Long) -> String,
    onBack: (() -> Unit)?,
    modifier: Modifier,
) {
    TacticalBackdrop(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TacticalDuelHeader(
                reports = state.reports.size,
                onOpenReports = { onIntent(BattleSimulatorIntent.SelectTacticalView(TacticalSimulatorView.REPORTS)) },
                onBack = onBack,
            )
            TacticalTeamBand("我的队伍", TacticalBlue, requireNotNull(state.config).blue.morale, SimulatorCamp.BLUE, onIntent)
            requireNotNull(state.config).blue.heroes.take(3).forEachIndexed { index, hero ->
                TacticalEditableHeroRow(SimulatorCamp.BLUE, index, hero, onIntent, heroName, heroIconId, skillName, TacticalBlue)
            }
            TacticalVersusAxis()
            TacticalTeamBand("敌方队伍", TacticalRed, requireNotNull(state.config).red.morale, SimulatorCamp.RED, onIntent)
            requireNotNull(state.config).red.heroes.take(3).forEachIndexed { index, hero ->
                TacticalEditableHeroRow(SimulatorCamp.RED, index, hero, onIntent, heroName, heroIconId, skillName, TacticalRed)
            }
            TacticalStageControls(state.running, state.reports.size, onIntent)
            state.error?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = TacticalDamage.copy(alpha = 0.84f)),
                    modifier = Modifier.fillMaxWidth().clickable { onIntent(BattleSimulatorIntent.DismissError) },
                ) {
                    Text(message, Modifier.padding(12.dp), color = Color.White)
                }
            }
            state.result?.let { result ->
                TacticalDuelResult(result, { onIntent(BattleSimulatorIntent.SelectTacticalView(TacticalSimulatorView.DETAIL)) })
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun TacticalDuelHeader(reports: Int, onOpenReports: () -> Unit, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回工具", tint = TacticalInk) }
            Column(Modifier.background(Color(0xAA151515), RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("模拟对局", color = TacticalGold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("本地推演 · 联网卡图", color = TacticalInk.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
            }
        }
        TacticalStageAction("战报 $reports", onOpenReports)
    }
}

@Composable
private fun TacticalTeamBand(
    title: String,
    accent: Color,
    morale: Int,
    camp: SimulatorCamp,
    onIntent: (BattleSimulatorIntent) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(accent.copy(alpha = 0.48f), RoundedCornerShape(2.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TacticalInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Stepper("士气 $morale", { onIntent(BattleSimulatorIntent.SetMorale(camp, morale - 5)) }, { onIntent(BattleSimulatorIntent.SetMorale(camp, morale + 5)) })
    }
}

@Composable
private fun TacticalEditableHeroRow(
    camp: SimulatorCamp,
    position: Int,
    hero: LocalSimHeroConfig,
    onIntent: (BattleSimulatorIntent) -> Unit,
    heroName: (Long) -> String,
    heroIconId: (Long) -> Long,
    skillName: (Long) -> String,
    accent: Color,
) {
    val name = heroName(hero.heroId)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xE91E2022)),
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(listOf("大营", "中军", "前锋").getOrElse(position) { "${position + 1}号" }, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("${position + 1}", color = TacticalInk.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.width(82.dp).height(94.dp)) {
                BattlefieldHeroPortrait(
                    BattlefieldHero("", hero.heroId, heroIconId(hero.heroId), name, hero.level, hero.advance, emptyList()),
                    Modifier.fillMaxSize(),
                )
                Image(
                    painter = painterResource(if (accent == TacticalRed) R.drawable.tactical_frame_red else R.drawable.tactical_frame_gold),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.86f,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TextButton(onClick = { onIntent(BattleSimulatorIntent.OpenHeroPicker(camp, position)) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(name, color = TacticalInk, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Lv.${hero.level} · 红${hero.advance}", color = TacticalInk.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(3) { slot ->
                        TacticalSkillSlot(
                            label = hero.equipSkillIds.getOrNull(slot)?.let(skillName) ?: "选择战法",
                            onClick = { onIntent(BattleSimulatorIntent.OpenSkillPicker(camp, position, slot)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TacticalMiniAction("等级", { onIntent(BattleSimulatorIntent.SetLevel(camp, position, hero.level - 1)) })
                    Text("${hero.level}", color = TacticalInk, style = MaterialTheme.typography.labelSmall)
                    TacticalMiniAction("+", { onIntent(BattleSimulatorIntent.SetLevel(camp, position, hero.level + 1)) })
                    TacticalMiniAction("红度", { onIntent(BattleSimulatorIntent.SetAdvance(camp, position, hero.advance - 1)) })
                    Text("${hero.advance}", color = TacticalInk, style = MaterialTheme.typography.labelSmall)
                    TacticalMiniAction("+", { onIntent(BattleSimulatorIntent.SetAdvance(camp, position, hero.advance + 1)) })
                }
            }
        }
    }
}

@Composable
private fun TacticalSkillSlot(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = label,
        modifier = modifier.background(Color(0xFF303234), RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 5.dp),
        color = TacticalGold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun TacticalMiniAction(label: String, onClick: () -> Unit) {
    Text(label, Modifier.clickable(onClick = onClick).padding(horizontal = 2.dp), color = TacticalGold, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun TacticalVersusAxis() {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.height(1.dp).weight(1f).background(TacticalBlue.copy(alpha = 0.7f)))
        Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.tactical_battle_emblem), null, Modifier.fillMaxSize(), alpha = 0.5f, colorFilter = ColorFilter.tint(TacticalRed))
            Text("对决", color = TacticalGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.height(1.dp).weight(1f).background(TacticalRed.copy(alpha = 0.7f)))
    }
}

@Composable
private fun TacticalStageControls(running: Boolean, reports: Int, onIntent: (BattleSimulatorIntent) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (running) { CircularProgressIndicator(color = TacticalGold); Text("战场推演中…", color = TacticalInk) }
        TacticalDiamondAction("开始推演", !running) { onIntent(BattleSimulatorIntent.Run(1)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TacticalStageAction("战报库 $reports", { onIntent(BattleSimulatorIntent.SelectTacticalView(TacticalSimulatorView.REPORTS)) }, Modifier.weight(1f))
            TacticalStageAction("阵容编辑", {}, Modifier.weight(1f))
            TacticalStageAction("批量推演", { onIntent(BattleSimulatorIntent.Run(100)) }, Modifier.weight(1f))
        }
        Text("1000 次推演", Modifier.clickable(enabled = !running) { onIntent(BattleSimulatorIntent.Run(1000)) }.padding(4.dp), color = TacticalInk.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TacticalDiamondAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(104.dp).graphicsLayer { rotationZ = 45f }.background(if (enabled) TacticalRed.copy(alpha = 0.82f) else Color(0xFF484848)).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, Modifier.graphicsLayer { rotationZ = -45f }, color = TacticalInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TacticalStageAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(label, modifier = modifier.background(Color(0xD9222325), RoundedCornerShape(2.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = TacticalInk, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun TacticalDuelResult(result: com.example.myapplication.LocalSimulationSummary, onOpenDetail: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = TacticalPanel), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(result.firstRun.winner, color = outcomeColor(result.firstRun.winner), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("攻方 ${"%.1f".format(result.blueWinRate)}% · 守方 ${"%.1f".format(result.redWinRate)}% · 平局 ${"%.1f".format(result.drawRate)}%", color = TacticalInk)
            Text("首场剩余：攻方 ${result.firstRun.blueRemain} · 守方 ${result.firstRun.redRemain}", color = TacticalInk.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) { Text("打开本场战报") }
        }
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
    GlassCard(modifier) {
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
