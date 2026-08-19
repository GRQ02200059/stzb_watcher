package com.local.stzb.feature.simulator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.example.myapplication.LocalSimulationEvent
import com.example.myapplication.LocalSimulationEventKind
import com.example.myapplication.LocalSimulationHeroSnapshot
import com.example.myapplication.LocalSimulationRun
import com.example.myapplication.R
import com.local.stzb.domain.battlefield.BattlefieldHero
import com.local.stzb.feature.battlefield.BattlefieldHeroPortrait

val TacticalInk = Color(0xFFECE2C8)
val TacticalPanel = Color(0xEE252629)
val TacticalBlue = Color(0xFF3A7491)
val TacticalRed = Color(0xFF8A3337)
val TacticalGold = Color(0xFFE9C36B)
val TacticalDamage = Color(0xFFFF7474)
val TacticalRecovery = Color(0xFF75E5A5)
private val TacticalMidnight = Color(0xFF303132)
private val TacticalMistTop = Color(0xA82E2F30)
private val TacticalMistCenter = Color(0x7A242627)
private val TacticalMistBottom = Color(0xDC171819)

@Composable
fun TacticalReportsLibrary(
    reports: List<TacticalSimulationReport>,
    onOpenReport: (Long) -> Unit,
    onBackToDuel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TacticalBackdrop(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TacticalTitle("战报库", "本次进程中的单场模拟", "返回对局", onBackToDuel) }
            if (reports.isEmpty()) {
                item { TacticalPanelCard { Text("还没有战报", color = TacticalInk); Text("完成一次单次模拟后会自动保存在这里。", color = TacticalInk.copy(alpha = 0.72f)) } }
            }
            items(reports, key = { it.id }) { report ->
                TacticalPanelCard(onClick = { onOpenReport(report.id) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("第 ${report.id} 封战报", color = TacticalInk, fontWeight = FontWeight.Bold)
                            Text("${report.run.roundsPlayed} 回合 · 种子 ${report.run.seed}", color = TacticalInk.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(report.run.winner, color = outcomeColor(report.run.winner), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    TacticalTroopBar("蓝色方", TacticalBlue, report.run.blueRemain, report.run.blueRemain + report.run.redRemain)
                    TacticalTroopBar("红色方", TacticalRed, report.run.redRemain, report.run.blueRemain + report.run.redRemain)
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
fun TacticalReportDetail(
    run: LocalSimulationRun?,
    reportTab: TacticalReportTab,
    selectedEventIndex: Int?,
    heroIconId: (Long) -> Long,
    onSelectTab: (TacticalReportTab) -> Unit,
    onSelectEvent: (Int?) -> Unit,
    onBackToReports: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (run == null) {
        TacticalBackdrop(modifier) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("还没有可查看的战报", color = TacticalInk)
                Button(onClick = onBackToReports) { Text("查看战报库") }
            }
        }
        return
    }
    val selectedEvent = selectedEventIndex?.let { run.events.getOrNull(it) }
    val visibleEvents = run.events.withIndex().filter { it.value.matches(reportTab) }
    TacticalBackdrop(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TacticalTitle("战报", "第 ${run.roundsPlayed} 回合 · 固定种子 ${run.seed}", "返回战报库", onBackToReports) }
            item { TacticalSideBlock("蓝色方", TacticalBlue, run.attackerHeroes, run.blueRemain, heroIconId, "tactical-report-blue-stage") }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.tactical_battle_emblem),
                        contentDescription = null,
                        modifier = Modifier.size(104.dp),
                        alpha = 0.4f,
                        colorFilter = ColorFilter.tint(outcomeColor(run.winner)),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(run.winner, color = TacticalGold, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("第 ${run.roundsPlayed} 回合结束", color = TacticalInk.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { TacticalSideBlock("红色方", TacticalRed, run.defenderHeroes, run.redRemain, heroIconId, "tactical-report-red-stage") }
            item { TacticalTabs(reportTab, onSelectTab) }
            item {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xEA171819)).testTag("tactical-report-event-stream").padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text("战报过程", Modifier.fillMaxWidth().background(Color(0xFF222324)).padding(horizontal = 12.dp, vertical = 9.dp), color = TacticalGold, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text("点击记录查看战法、目标与兵力变化", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = TacticalInk.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
                    visibleEvents.forEach { indexed ->
                        TacticalEventRow(indexed.index, indexed.value) { onSelectEvent(indexed.index) }
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
    if (selectedEvent != null) TacticalEventDialog(selectedEvent) { onSelectEvent(null) }
}

@Composable
fun TacticalBackdrop(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TacticalMidnight)
            .testTag("tactical-battlefield-backdrop"),
    ) {
        Image(
            painter = painterResource(R.drawable.tactical_battlefield_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.42f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(TacticalMistTop, TacticalMistCenter, TacticalMistBottom),
                    ),
                )
                .testTag("tactical-battlefield-mist"),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0x143A7491),
                            Color(0x0C3A7491),
                            Color.Transparent,
                            Color(0x0C8A3337),
                        ),
                    ),
                )
                .testTag("tactical-battlefield-aurora"),
        )
        content()
    }
}

@Composable
private fun TacticalTitle(title: String, supporting: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, color = TacticalGold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(supporting, color = TacticalInk.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun TacticalSideBlock(
    label: String,
    color: Color,
    heroes: List<LocalSimulationHeroSnapshot>,
    remain: Int,
    heroIconId: (Long) -> Long,
    testTag: String,
) {
    Column(Modifier.fillMaxWidth().testTag(testTag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.fillMaxWidth().background(color.copy(alpha = 0.56f)).padding(horizontal = 10.dp, vertical = 4.dp), color = TacticalInk, fontWeight = FontWeight.Bold)
        TacticalTroopBar(label, color, remain, heroes.sumOf { it.initialTroops }, showLabel = false)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            heroes.forEach { hero -> TacticalHeroCard(hero, heroIconId, color, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TacticalTroopBar(label: String, color: Color, remain: Int, total: Int, showLabel: Boolean = true) {
    val fraction = if (total <= 0) 0f else (remain.toFloat() / total).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (showLabel) label else "", color = TacticalInk.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
            Text("$remain / $total", color = TacticalInk, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Black.copy(alpha = 0.6f))) {
            Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(color))
        }
    }
}

@Composable
private fun TacticalHeroCard(hero: LocalSimulationHeroSnapshot, heroIconId: (Long) -> Long, frameColor: Color, modifier: Modifier) {
    Box(modifier.testTag("tactical-report-hero-card-${hero.heroId}")) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xED202123)), shape = RoundedCornerShape(1.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hero.positionName, color = TacticalGold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                BattlefieldHeroPortrait(BattlefieldHero(hero.positionName, hero.heroId, heroIconId(hero.heroId), hero.name, hero.level, hero.advance, emptyList()), Modifier.aspectRatio(0.72f))
                Text(hero.name, color = TacticalInk, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(
                    "Lv.${hero.level} ${hero.remainingTroops}/${hero.initialTroops}",
                    Modifier.testTag("tactical-report-hero-troops-${hero.heroId}"),
                    color = if (hero.alive) TacticalInk.copy(alpha = 0.78f) else TacticalDamage,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
        Image(
            painter = painterResource(if (frameColor == TacticalRed) R.drawable.tactical_frame_red else R.drawable.tactical_frame_gold),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize(),
            alpha = 0.9f,
        )
    }
}

@Composable
private fun TacticalTabs(selected: TacticalReportTab, onSelect: (TacticalReportTab) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TacticalReportTab.entries.forEach { tab ->
            Card(
                modifier = Modifier.weight(1f).clickable { onSelect(tab) },
                colors = CardDefaults.cardColors(containerColor = if (tab == selected) TacticalGold else TacticalPanel),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(tab.label, Modifier.fillMaxWidth().padding(9.dp), textAlign = TextAlign.Center, color = if (tab == selected) Color(0xFF31291B) else TacticalInk)
            }
        }
    }
}

@Composable
private fun TacticalEventRow(index: Int, event: LocalSimulationEvent, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(event.stageColor()).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
            Text(if (event.round == 0) "备" else "R${event.round}", color = TacticalGold, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f)) {
                Text(event.description.ifBlank { event.kind.label() }, color = TacticalInk, style = MaterialTheme.typography.bodyMedium)
                if (event.amount != 0) Text("${if (event.kind == LocalSimulationEventKind.RECOVERY) "+" else "-"}${event.amount} · 剩余 ${event.targetRemaining}", color = event.color(), fontWeight = FontWeight.Bold)
            }
    }
}

@Composable
private fun TacticalEventDialog(event: LocalSimulationEvent, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("关闭") } },
        title = { Text(event.kind.label()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(event.description.ifBlank { "暂无补充说明" })
                HorizontalDivider()
                TacticalDetailLine("回合", if (event.round == 0) "准备阶段" else "第 ${event.round} 回合")
                TacticalDetailLine("来源", event.sourceName.ifBlank { "系统" })
                TacticalDetailLine("目标", event.targetName.ifBlank { "-" })
                TacticalDetailLine("战法/动作", event.skillName.ifBlank { "-" })
                if (event.amount != 0) TacticalDetailLine("数值", event.amount.toString())
                if (event.targetRemaining > 0) TacticalDetailLine("目标剩余兵力", event.targetRemaining.toString())
                Text("此明细来自本地模拟事件，不代表官方实战战报。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
private fun TacticalDetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TacticalPanelCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable { onClick() }),
        colors = CardDefaults.cardColors(containerColor = TacticalPanel),
        shape = RoundedCornerShape(10.dp),
    ) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }
}

private fun LocalSimulationEvent.matches(tab: TacticalReportTab): Boolean = when (tab) {
    TacticalReportTab.ROUND -> round > 0 && kind != LocalSimulationEventKind.STATUS
    TacticalReportTab.STATUS -> kind == LocalSimulationEventKind.STATUS || kind == LocalSimulationEventKind.PREPARATION
    TacticalReportTab.TRIGGER -> kind == LocalSimulationEventKind.ACTION || kind == LocalSimulationEventKind.DAMAGE || kind == LocalSimulationEventKind.RECOVERY
}

private fun LocalSimulationEventKind.label(): String = when (this) {
    LocalSimulationEventKind.PREPARATION -> "准备效果"
    LocalSimulationEventKind.ROUND_START -> "回合开始"
    LocalSimulationEventKind.ACTION -> "行动"
    LocalSimulationEventKind.DAMAGE -> "伤害"
    LocalSimulationEventKind.RECOVERY -> "恢复"
    LocalSimulationEventKind.STATUS -> "状态/效果"
    LocalSimulationEventKind.RESULT -> "战斗结算"
}

private fun LocalSimulationEvent.color(): Color = when (kind) {
    LocalSimulationEventKind.DAMAGE -> TacticalDamage
    LocalSimulationEventKind.RECOVERY -> TacticalRecovery
    LocalSimulationEventKind.STATUS, LocalSimulationEventKind.PREPARATION -> TacticalGold
    else -> TacticalInk
}

private fun LocalSimulationEvent.stageColor(): Color = when {
    kind == LocalSimulationEventKind.DAMAGE || kind == LocalSimulationEventKind.ACTION -> TacticalRed.copy(alpha = 0.32f)
    kind == LocalSimulationEventKind.RECOVERY -> TacticalBlue.copy(alpha = 0.38f)
    kind == LocalSimulationEventKind.STATUS || kind == LocalSimulationEventKind.PREPARATION -> Color(0x553A7491)
    else -> Color(0x222B2C2F)
}

fun outcomeColor(winner: String): Color = when (winner) {
    "攻方" -> TacticalBlue
    "守方" -> TacticalRed
    else -> TacticalGold
}
