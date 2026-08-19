package com.local.stzb.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.local.stzb.core.designsystem.AstzbColors
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.GlassToolbar

@Composable
fun LegacyToolsScreen(
    openCaptureConsole: () -> Unit,
    openLegacyDashboard: () -> Unit,
    openMap: () -> Unit,
    openAnnouncements: () -> Unit,
    openRankings: () -> Unit,
    openTeams: () -> Unit,
    openTeamReport: () -> Unit,
    openSimulator: () -> Unit,
    openProfiles: () -> Unit,
    openLiveArmies: () -> Unit,
    openAttendance: () -> Unit,
    openScores: () -> Unit,
    openResearch: () -> Unit,
    onLogout: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("tools-list"),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GlassToolbar(
                title = "工具中心",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回上一页")
                        }
                    }
                    Text("工具中心", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        item {
            ToolSection("战斗与队伍") {
                ToolActionCard(Icons.Outlined.EventAvailable, "攻城考勤", openAttendance)
                ToolActionCard(Icons.Outlined.Calculate, "自定义积分", openScores)
                ToolActionCard(Icons.Outlined.Groups, "队伍", openTeams)
                ToolActionCard(Icons.Outlined.Groups, "团队报表", openTeamReport)
                ToolActionCard(Icons.Outlined.Science, "战术演练", openSimulator)
                ToolActionCard(Icons.Outlined.AutoGraph, "阵容战法研究", openResearch)
            }
        }
        item {
            ToolSection("情报与榜单") {
                ToolActionCard(Icons.Outlined.Route, "实时部队", openLiveArmies)
                ToolActionCard(Icons.Outlined.Map, "地图与城池", openMap)
                ToolActionCard(Icons.Outlined.Campaign, "游戏公告", openAnnouncements)
                ToolActionCard(Icons.Outlined.Leaderboard, "排行榜", openRankings)
            }
        }
        item {
            ToolSection("抓包与经典") {
                ToolActionCard(Icons.Outlined.NetworkCheck, "抓包启动台", openCaptureConsole)
                ToolActionCard(Icons.Outlined.Dashboard, "经典数据页面", openLegacyDashboard)
            }
        }
        item {
            ToolSection("账号") {
                ToolActionCard(Icons.Outlined.AccountCircle, "账号与区服", openProfiles)
                ToolActionCard(Icons.AutoMirrored.Outlined.Logout, "退出登录", onLogout)
            }
        }
    }
}

@Composable
private fun ToolSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun ToolActionCard(icon: ImageVector, label: String, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(15.dp),
                color = AstzbColors.PrimaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
                }
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
