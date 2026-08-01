package com.local.stzb.feature.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BattlefieldOverlayContent(
    state: OverlayMonitorState,
    collapsed: Boolean,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    dragHandleModifier: Modifier,
) {
    if (collapsed) {
        Surface(
            modifier = dragHandleModifier.size(72.dp).clickable(onClick = onExpand),
            shape = RoundedCornerShape(24.dp), color = Color(0xDC111827), shadowElevation = 12.dp,
        ) { Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("战场", color = Color.White, fontWeight = FontWeight.Bold)
            Text("${state.teams.size}", color = Color(0xFFC4B5FD), style = MaterialTheme.typography.titleLarge)
            Box(Modifier.fillMaxSize().clickable(onClickLabel = "展开悬浮窗", onClick = onExpand))
        } }
        return
    }
    Surface(
        modifier = Modifier.width(370.dp), shape = RoundedCornerShape(22.dp),
        color = Color(0xD9141D2F), shadowElevation = 14.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(dragHandleModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("战场队伍", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${state.teams.size} 支 · ${if (state.captureRunning) "实时更新" else "数据暂停"}", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onCollapse) { Icon(Icons.Outlined.Remove, "折叠悬浮窗", tint = Color.White) }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "关闭悬浮窗", tint = Color.White) }
            }
            if (state.teams.isEmpty()) Text("等待战场队伍数据", color = Color(0xFFCBD5E1), modifier = Modifier.padding(12.dp))
            else LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(state.teams, key = OverlayTeam::teamId) { team -> OverlayTeamRow(team) }
            }
            state.error?.let { Text(it, color = Color(0xFFFCA5A5), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable private fun OverlayTeamRow(team: OverlayTeam) {
    Column(
        Modifier.fillMaxWidth().background(Color(0x18FFFFFF), RoundedCornerShape(13.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
            Text(team.playerName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(team.stateText, color = Color(0xFFC4B5FD), style = MaterialTheme.typography.labelMedium)
            Text(team.winRate?.let { "胜率 ${"%.1f".format(Locale.US, it)}%" } ?: "胜率 --", color = Color(0xFFFBBF24), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Text(team.heroes.joinToString(" · ") { "${it.name} ${it.advance}红" }.ifBlank { "武将未记录" }, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        val time = team.arrivalAt?.let { "到达 ${formatOverlayTime(it)}" } ?: "更新 ${formatOverlayTime(team.updatedAt)}"
        Text("目的地 ${team.destination.ifBlank { "--" }}　$time", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

private val overlayTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.CHINA)
private fun formatOverlayTime(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(overlayTimeFormatter)
