package com.local.stzb.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.MacGlassHeader
import com.local.stzb.core.ui.SectionLabel

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
    openAutoScroll: () -> Unit,
    onLogout: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("tools-list"),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MacGlassHeader(
                title = "工具中心",
                subtitle = "监控、分析与账号管理",
                leading = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
        item {
            ToolSection("战斗与队伍") {
                ToolRow {
                    ToolTile(Icons.Outlined.EventAvailable, "攻城考勤", openAttendance, Modifier.weight(1f))
                    ToolTile(Icons.Outlined.Calculate, "自定义积分", openScores, Modifier.weight(1f))
                    ToolTile(Icons.Outlined.Groups, "队伍", openTeams, Modifier.weight(1f))
                }
                ToolRow {
                    ToolTile(Icons.Outlined.AutoGraph, "团队报表", openTeamReport, Modifier.weight(1f))
                    ToolTile(Icons.Outlined.Science, "战术演练", openSimulator, Modifier.weight(1f))
                    ToolTile(Icons.Outlined.AutoGraph, "全服阵容榜", openResearch, Modifier.weight(1f))
                }
                ToolRow {
                    ToolTile(Icons.Outlined.SwapVert, "自动滑动", openAutoScroll, Modifier.weight(1f))
                    SpacerTile(Modifier.weight(1f))
                    SpacerTile(Modifier.weight(1f))
                }
            }
        }
        item {
            ToolSection("情报与榜单") {
                ToolRow {
                    ToolTile(Icons.Outlined.Route, "实时部队", openLiveArmies, Modifier.weight(1f))
                    ToolTile(Icons.Outlined.Map, "地图城池", openMap, Modifier.weight(1f))
                    ToolTile(Icons.Outlined.Campaign, "游戏公告", openAnnouncements, Modifier.weight(1f))
                }
                ToolRow {
                    ToolTile(Icons.Outlined.Leaderboard, "排行榜", openRankings, Modifier.weight(1f))
                    SpacerTile(Modifier.weight(1f))
                    SpacerTile(Modifier.weight(1f))
                }
            }
        }
        item {
            ToolSection("抓包与经典") {
                ToolRow {
                    ToolTile(Icons.Outlined.NetworkCheck, "抓包启动台", openCaptureConsole, Modifier.weight(1f))
                    ToolTile(Icons.Outlined.Dashboard, "经典数据页面", openLegacyDashboard, Modifier.weight(1f))
                    SpacerTile(Modifier.weight(1f))
                }
            }
        }
        item {
            ToolSection("账号") {
                ToolRow {
                    ToolTile(Icons.Outlined.AccountCircle, "账号与区服", openProfiles, Modifier.weight(1f))
                    ToolTile(Icons.AutoMirrored.Outlined.Logout, "退出登录", onLogout, Modifier.weight(1f))
                    SpacerTile(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(title)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun ToolRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun ToolTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.heightIn(min = 92.dp),
        onClick = onClick,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SpacerTile(modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 92.dp))
}

