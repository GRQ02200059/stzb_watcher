package com.local.stzb.data.capture

import android.content.Context
import android.content.Intent
import com.example.myapplication.CaptureVpnService
import com.example.myapplication.LocalBattleMonitorStore
import com.example.myapplication.LocalMigrationDiagnostics
import com.example.myapplication.LocalSocksCaptureServer
import com.example.myapplication.LocalStzbCaptureWriter
import com.example.myapplication.LocalStzbPacketStore
import com.example.myapplication.LocalStzbRepository
import com.example.myapplication.PacketCaptureStore
import com.example.myapplication.PacketLogStore
import com.local.stzb.feature.capture.*
import hev.sockstun.Preferences
import hev.sockstun.TProxyService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidCaptureConsoleController(private val context: Context) : CaptureConsoleController {
    private val preferences get() = Preferences(context)

    override fun observe(): Flow<CaptureRuntime> = callbackFlow {
        fun publish(logs: List<String> = PacketLogStore.snapshot()) {
            trySend(CaptureRuntime(
                running = preferences.enable || LocalSocksCaptureServer.isRunning() || CaptureVpnService.isRunning,
                nativeReady = TProxyService.isNativeReady(),
                socksHost = preferences.socksAddress,
                socksPort = preferences.socksPort,
                packetCount = LocalStzbPacketStore.snapshot().size,
                targetPackage = preferences.apps.firstOrNull().orEmpty(),
                logs = logs,
            ))
        }
        val listener: (List<String>) -> Unit = ::publish
        PacketLogStore.addListener(listener)
        publish()
        awaitClose { PacketLogStore.removeListener(listener) }
    }.conflate()

    override suspend fun installedApps(): List<InstalledApp> = context.packageManager
        .getInstalledApplications(android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
        .map { InstalledApp(it.loadLabel(context.packageManager).toString().ifBlank { it.packageName }, it.packageName) }
        .filter { it.packageName.isNotBlank() && it.packageName != context.packageName }
        .distinctBy(InstalledApp::packageName)
        .sortedWith(compareBy<InstalledApp> { it.label.lowercase() }.thenBy(InstalledApp::packageName))

    override suspend fun start(targetPackage: String) {
        require(targetPackage.isNotBlank()) { "请先选择目标 App" }
        check(TProxyService.isNativeReady()) { "抓包 native 组件不可用" }
        context.packageManager.getApplicationInfo(targetPackage, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
        val port = LocalSocksCaptureServer.start()
        preferences.apply {
            socksAddress = "127.0.0.1"
            socksPort = port
            socksUdpAddress = ""
            socksUsername = ""
            socksPassword = ""
            ipv4 = true
            ipv6 = true
            global = false
            udpInTcp = false
            remoteDns = true
            apps = setOf(targetPackage)
        }
        context.startService(Intent(context, TProxyService::class.java).setAction(TProxyService.ACTION_CONNECT))
        PacketLogStore.add("启动本机 STZB 抓包桥接：target=$targetPackage, tun2socks -> 127.0.0.1:$port")
    }

    override suspend fun stop() {
        context.stopService(Intent(context, CaptureVpnService::class.java))
        context.startService(Intent(context, TProxyService::class.java).setAction(TProxyService.ACTION_DISCONNECT))
        LocalSocksCaptureServer.stop()
        PacketLogStore.add("已请求停止抓取服务")
    }

    override suspend fun clear() {
        PacketLogStore.clear()
        PacketCaptureStore.clear()
        LocalStzbPacketStore.clear()
        LocalBattleMonitorStore.clear()
    }

    override suspend fun prepareExport(kind: CaptureExportKind): CaptureExport? {
        val file = when (kind) {
            CaptureExportKind.STZB -> LocalStzbCaptureWriter.exportSummary(context) ?: return null
            CaptureExportKind.DATABASE -> LocalStzbRepository.exportDatabase(context)
            CaptureExportKind.DIAGNOSTICS -> LocalMigrationDiagnostics.export(context)
        }
        return captureExport(kind, file)
    }
}

fun captureExport(kind: CaptureExportKind, file: File, now: Long = System.currentTimeMillis()): CaptureExport {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(now))
    val (prefix, extension, mime) = when (kind) {
        CaptureExportKind.STZB -> Triple("STZB解析包", "txt", "text/plain")
        CaptureExportKind.DATABASE -> Triple("STZB数据库", "db", "application/octet-stream")
        CaptureExportKind.DIAGNOSTICS -> Triple("STZB诊断", "txt", "text/plain")
    }
    return CaptureExport("${prefix}_${timestamp}.$extension", mime, file.readBytes())
}
