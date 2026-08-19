package com.local.stzb.feature.teams

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.EmptyPanel
import com.local.stzb.core.ui.ErrorPanel
import com.local.stzb.core.ui.LoadingPanel
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.MacGlassHeader
import com.local.stzb.domain.battlefield.BattlefieldHero
import com.local.stzb.domain.teams.PlayerTeam
import com.local.stzb.feature.battlefield.BattlefieldHeroPortrait

@Composable
fun TeamsScreen(state: TeamsUiState, onIntent: (TeamsIntent) -> Unit, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val players = state.allTeams.map { it.player }.distinct().size
        MacGlassHeader(
            title = "全服玩家队伍",
            subtitle = "队伍 ${state.allTeams.size} · 玩家 $players",
            leading = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回工具")
                    }
                }
            },
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onIntent(TeamsIntent.QueryChanged(it)) },
                    label = { Text("搜索玩家、同盟、武将或战法") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeamSide.entries.forEach { side ->
                        FilterChip(
                            selected = state.side == side,
                            onClick = { onIntent(TeamsIntent.SideChanged(side)) },
                            label = { Text(side.label) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
        when {
            state.loading -> LoadingPanel(Modifier.weight(1f))
            state.error != null -> ErrorPanel(state.error, true, { onIntent(TeamsIntent.Refresh) }, Modifier.weight(1f))
            state.visibleTeams.isEmpty() -> EmptyPanel("本机还没有全服玩家队伍数据，请先采集完整玩家战报", "刷新", { onIntent(TeamsIntent.Refresh) }, Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.visibleTeams, key = { "${it.player}:${it.side}:${it.heroes.joinToString { hero -> hero.heroId.toString() }}" }) {
                    PlayerTeamCard(it)
                }
            }
        }
    }
}

@Composable
private fun PlayerTeamCard(team: PlayerTeam) {
    GlassCard {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(team.player, team.unionName).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(team.sideLabel, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                team.heroes.take(3).forEachIndexed { index, hero ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val position = listOf("大营", "中军", "前锋").getOrElse(index) { "${index + 1}号位" }
                        Text(position, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        BattlefieldHeroPortrait(BattlefieldHero(position, hero.heroId, hero.iconId, hero.name, 0, 0, emptyList()))
                        Text(hero.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        hero.skillNames.take(3).forEach { skill ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    skill,
                                    Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (hero.skillNames.isEmpty()) {
                            Text("战法未记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text("${team.battles} 战 · ${team.wins} 胜 · 胜率 ${"%.1f".format(team.winRate)}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
