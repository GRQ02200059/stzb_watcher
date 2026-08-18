package com.local.stzb.feature.alliance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.EmptyPanel
import com.local.stzb.core.ui.ErrorPanel
import com.local.stzb.core.ui.LoadingPanel
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.domain.alliance.AllianceMember

@Composable
fun AllianceScreen(state: AllianceUiState, viewModel: AllianceViewModel, onBack: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回更多") }
            Text("同盟中心", style = MaterialTheme.typography.headlineMedium)
        }
        val snapshot = state.snapshot
        if (snapshot != null) Text("成员 ${snapshot.totalMembers} · 分组 ${snapshot.groups.size}")
        OutlinedTextField(state.query, viewModel::setQuery, label = { Text("搜索成员") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (snapshot != null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("") .plus(snapshot.groups.map { it.name }).distinct().forEach { group ->
                    FilterChip(
                        selected = state.group == group,
                        onClick = { viewModel.setGroup(group) },
                        label = { Text(group.ifBlank { "全部分组" }) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        when {
            state.loading -> LoadingPanel(Modifier.weight(1f))
            state.error != null -> ErrorPanel(state.error, true, viewModel::refresh, Modifier.weight(1f))
            snapshot == null || snapshot.members.isEmpty() -> EmptyPanel("本机还没有同盟成员数据", "刷新", viewModel::refresh, Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(snapshot.members, key = AllianceMember::uid) { member -> MemberCard(member) }
            }
        }
    }
}

@Composable
private fun MemberCard(member: AllianceMember) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(member.name.ifBlank { "未知成员" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(member.groupName, color = MaterialTheme.colorScheme.primary)
            }
            Text("势力 ${member.power} · 武勋 ${member.wuxun} · 本周贡献 ${member.weeklyContribution}")
            Text("UID ${member.uid} · 职位 ${member.position}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
