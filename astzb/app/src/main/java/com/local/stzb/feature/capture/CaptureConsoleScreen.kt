package com.local.stzb.feature.capture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun CaptureConsoleScreen(
    state: CaptureConsoleUiState,
    onIntent: (CaptureConsoleIntent) -> Unit,
    onRequestVpnPermission: () -> Unit,
    onExport: (CaptureExportKind) -> Unit,
    onOpenLegacy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var appDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.requestVpnPermission) { if (state.requestVpnPermission) onRequestVpnPermission() }
    if (appDialog) AppPicker(state.apps, { onIntent(CaptureConsoleIntent.SelectApp(it)); appDialog = false }, { appDialog = false })

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回更多") }
                Column {
                    Text("抓包启动台", style = MaterialTheme.typography.headlineMedium)
                    Text("本机 VPN → SOCKS5 → STZB 协议解析", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            GlassGroupCard(
                title = "抓包状态",
                supporting = if (state.running) "运行中" else "未启动",
            ) {
                StatusCard(state)
            }
        }
        item {
            GlassGroupCard(
                title = "抓包控制",
                supporting = "目标 App / 启停控制 / 权限触发",
            ) {
                OutlinedTextField(
                    value = state.selectedApp?.let { "${it.label}\n${it.packageName}" }.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("目标 App") },
                    placeholder = { Text("尚未选择") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { onIntent(CaptureConsoleIntent.LoadApps); appDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("搜索并选择 App") }
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onIntent(CaptureConsoleIntent.RequestStart) },
                        enabled = state.selectedApp != null && state.nativeReady && !state.busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("启动抓包") }
                    OutlinedButton(
                        onClick = { onIntent(CaptureConsoleIntent.Stop) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("停止抓包") }
                }
            }
        }
        item {
            GlassGroupCard(
                title = "抓包日志",
                supporting = "STZB 解析输出（最多展示 40 行）",
            ) {
                OutlinedTextField(
                    value = state.protocolFilter,
                    onValueChange = { onIntent(CaptureConsoleIntent.SetProtocolFilter(it)) },
                    label = { Text("协议 ID 筛选，例如 5026, 5028") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        state.visibleLogs.takeLast(40).joinToString("\n").ifBlank { "等待 STZB 业务包解析…" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 120.dp),
                    )
                }
                OutlinedButton(onClick = { onIntent(CaptureConsoleIntent.Clear) }, Modifier.fillMaxWidth()) { Text("清空内存日志") }
            }
        }
        item {
            GlassGroupCard(
                title = "导出",
                supporting = "导出解析包 / 数据库 / 诊断",
            ) {
                ExportButton("导出解析包") { onExport(CaptureExportKind.STZB) }
                ExportButton("导出数据库") { onExport(CaptureExportKind.DATABASE) }
                ExportButton("导出诊断") { onExport(CaptureExportKind.DIAGNOSTICS) }
                TextButton(onClick = onOpenLegacy, Modifier.fillMaxWidth()) { Text("打开旧控制台") }
            }
        }
        state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable private fun StatusCard(state: CaptureConsoleUiState) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = if (state.running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    )) { Column(Modifier.padding(16.dp), Arrangement.spacedBy(6.dp)) {
        Text(
            if (state.running) "抓包运行中" else "抓包未启动",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text("已解析 ${state.packetCount} 包")
        Text("SOCKS ${state.socksHost}:${state.socksPort}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.nativeReady) Text("当前安装包缺少 native 抓包组件", color = MaterialTheme.colorScheme.error)
    } }
}

@Composable private fun ExportButton(label: String, action: () -> Unit) {
    OutlinedButton(onClick = action, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

@Composable private fun AppPicker(apps: List<InstalledApp>, select: (InstalledApp) -> Unit, dismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val visible = remember(apps, query) { apps.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) } }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("选择目标 App") },
        text = { Column(Modifier.heightIn(max = 500.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("搜索 App 或包名") }, singleLine = true)
            LazyColumn { items(visible, key = InstalledApp::packageName) { app ->
                TextButton(onClick = { select(app) }, Modifier.fillMaxWidth()) { Text("${app.label}\n${app.packageName}", Modifier.fillMaxWidth()) }
            } }
        } },
        confirmButton = {}, dismissButton = { TextButton(dismiss) { Text("取消") } },
    )
}

@Composable
private fun GlassGroupCard(
    title: String,
    supporting: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!supporting.isNullOrBlank()) {
                    Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}
