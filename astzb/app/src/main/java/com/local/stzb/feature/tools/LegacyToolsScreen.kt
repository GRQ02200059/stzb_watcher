package com.local.stzb.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LegacyToolsScreen(
    openCaptureConsole: () -> Unit,
    openLegacyDashboard: () -> Unit,
    openMap: () -> Unit,
    openAnnouncements: () -> Unit,
    openRankings: () -> Unit,
    openBattles: () -> Unit,
    openAlliance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("更多工具")
        Button(onClick = openBattles, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null)
            Text("战报", modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = openAlliance, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Groups, contentDescription = null)
            Text("同盟成员", modifier = Modifier.padding(start = 8.dp))
        }
        Button(onClick = openMap, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Map, contentDescription = null)
            Text("地图与城池", modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = openAnnouncements, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Campaign, contentDescription = null)
            Text("游戏公告", modifier = Modifier.padding(start = 8.dp))
        }
        Button(onClick = openRankings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Leaderboard, contentDescription = null)
            Text("排行榜", modifier = Modifier.padding(start = 8.dp))
        }
        Button(
            onClick = openCaptureConsole,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.NetworkCheck, contentDescription = null)
            Text("抓包启动台", modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(
            onClick = openLegacyDashboard,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.Dashboard, contentDescription = null)
            Text("经典数据页面", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
