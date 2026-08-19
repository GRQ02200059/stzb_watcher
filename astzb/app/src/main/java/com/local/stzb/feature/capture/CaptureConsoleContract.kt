package com.local.stzb.feature.capture

import kotlinx.coroutines.flow.Flow

data class InstalledApp(val label: String, val packageName: String)

data class CaptureRuntime(
    val running: Boolean = false,
    val nativeReady: Boolean = true,
    val socksHost: String = "127.0.0.1",
    val socksPort: Int = 1080,
    val packetCount: Int = 0,
    val targetPackage: String = "",
    val logs: List<String> = emptyList(),
    val evidence: CaptureEvidence = CaptureEvidence.from(
        nativeReady = true,
        vpnEstablished = false,
        socksConnections = 0,
        protocolCounts = emptyMap(),
        databaseRowDelta = 0,
        stopped = false,
        networkRestored = false,
    ),
)

enum class CaptureExportKind { STZB, DATABASE, DIAGNOSTICS, EVIDENCE }

data class CaptureExport(val name: String, val mimeType: String, val bytes: ByteArray)

interface CaptureConsoleController {
    fun observe(): Flow<CaptureRuntime>
    suspend fun installedApps(): List<InstalledApp>
    suspend fun start(targetPackage: String)
    suspend fun stop()
    suspend fun clear()
    suspend fun prepareExport(kind: CaptureExportKind): CaptureExport?
}

data class CaptureConsoleUiState(
    val running: Boolean = false,
    val nativeReady: Boolean = true,
    val socksHost: String = "127.0.0.1",
    val socksPort: Int = 1080,
    val packetCount: Int = 0,
    val selectedApp: InstalledApp? = null,
    val apps: List<InstalledApp> = emptyList(),
    val protocolFilter: String = "",
    val visibleLogs: List<String> = emptyList(),
    val requestVpnPermission: Boolean = false,
    val message: String? = null,
    val busy: Boolean = false,
    val evidence: CaptureEvidence = CaptureEvidence.from(
        nativeReady = true,
        vpnEstablished = false,
        socksConnections = 0,
        protocolCounts = emptyMap(),
        databaseRowDelta = 0,
        stopped = false,
        networkRestored = false,
    ),
)

sealed interface CaptureConsoleIntent {
    data class SetProtocolFilter(val value: String) : CaptureConsoleIntent
    data class SelectApp(val app: InstalledApp) : CaptureConsoleIntent
    data object LoadApps : CaptureConsoleIntent
    data object RequestStart : CaptureConsoleIntent
    data object StartApproved : CaptureConsoleIntent
    data object VpnPermissionHandled : CaptureConsoleIntent
    data object Stop : CaptureConsoleIntent
    data object Clear : CaptureConsoleIntent
    data class Message(val value: String?) : CaptureConsoleIntent
}

fun filterParsedLogs(logs: List<String>, query: String): List<String> {
    val ids = query.split(',', '，', ' ', '\n', '\t', ';', '；', '/')
        .map(String::trim).filter { it.matches(Regex("\\d+")) }.distinct()
    return logs.filter { line ->
        val parsed = listOf("STZB", "战报", "聊天", "行军", "专表", "db_sync", "本机业务记录入库")
            .any { line.contains(it, ignoreCase = true) } &&
            !line.contains("SOCKS 转发") && !line.contains("原始包") && !line.contains("最新包详情")
        parsed && (ids.isEmpty() || ids.any { Regex("(?<!\\d)${Regex.escape(it)}(?!\\d)").containsMatchIn(line) })
    }
}
