package com.local.stzb.feature.profile

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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.core.ui.GlassCard
import com.local.stzb.core.ui.MacGlassHeader
import com.local.stzb.profile.ProfileSnapshot

@Composable
fun ProfileScreen(
    snapshot: ProfileSnapshot,
    onRegister: (serverAddress: String, roleId: String, displayName: String) -> Result<ProfileSnapshot>,
    onSwitch: (profileId: String) -> Result<Unit>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var serverAddress by remember { mutableStateOf("") }
    var roleId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var currentSnapshot by remember(snapshot) { mutableStateOf(snapshot) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MacGlassHeader(
                title = "账号与区服",
                subtitle = "不同档案使用独立本机数据库",
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回工具")
                    }
                },
            )
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("新增档案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(serverAddress, { serverAddress = it }, label = { Text("区服地址或标识") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(roleId, { roleId = it }, label = { Text("角色 ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(displayName, { displayName = it }, label = { Text("显示名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            onRegister(serverAddress, roleId, displayName)
                                .onSuccess { result ->
                                    currentSnapshot = result
                                    serverAddress = ""
                                    roleId = ""
                                    displayName = ""
                                    message = "档案已保存"
                                }
                                .onFailure { message = it.message ?: "保存失败" }
                        },
                        enabled = serverAddress.isNotBlank() && roleId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("保存档案") }
                }
            }
        }
        items(currentSnapshot.profiles, key = { it.profileId }) { profile ->
            val active = profile.profileId == currentSnapshot.current?.profileId
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (active) "当前" else "", color = MaterialTheme.colorScheme.primary)
                    }
                    Text("区服 ${profile.serverAddress} · 角色 ${profile.roleId}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(profile.databaseName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!active) {
                        OutlinedButton(
                            onClick = {
                                onSwitch(profile.profileId)
                                    .onSuccess { message = "正在切换档案…" }
                                    .onFailure { message = it.message ?: "切换失败" }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("切换到此档案") }
                    }
                }
            }
        }
        message?.let { value ->
            item { Text(value, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
    }
}
