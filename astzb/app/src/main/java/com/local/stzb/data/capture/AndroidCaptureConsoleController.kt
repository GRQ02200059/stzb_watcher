package com.local.stzb.data.capture

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidCaptureConsoleController(private val context: Context) : CaptureConsoleController {
    private val preferences get() = Preferences(context)
    private var sessionStarted = false
    private var sessionVpnEstablished = false
    private var sessionStopped = false
    private var sessionNetworkRestored = false
    private var sessionTargetPackage = ""
    private var protocolBaseline = emptyMap<String, Int>()
    private var databaseBaseline = 0
    private var socksBaseline = 0L

    override fun observe(): Flow<CaptureRuntime> = callbackFlow {
        fun publish(logs: List<String> = PacketLogStore.snapshot()) {
            val running = preferences.enable || LocalSocksCaptureServer.isRunning() || CaptureVpnService.isRunning
            if (sessionStarted && preferences.enable) sessionVpnEstablished = true
            trySend(CaptureRuntime(
                running = running,
                nativeReady = TProxyService.isNativeReady(),
                socksHost = preferences.socksAddress,
                socksPort = preferences.socksPort,
                packetCount = LocalStzbPacketStore.snapshot().size,
                targetPackage = preferences.apps.firstOrNull().orEmpty(),
                logs = logs,
                evidence = currentEvidence(),
            ))
        }
        val listener: (List<String>) -> Unit = ::publish
        PacketLogStore.addListener(listener)
        publish()
        val poller = launch {
            while (isActive) {
                delay(1_000)
                publish()
            }
        }
        awaitClose {
            poller.cancel()
            PacketLogStore.removeListener(listener)
        }
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
        sessionStarted = true
        sessionVpnEstablished = false
        sessionStopped = false
        sessionNetworkRestored = false
        sessionTargetPackage = targetPackage
        protocolBaseline = protocolDistribution()
        databaseBaseline = businessRowCount()
        socksBaseline = LocalSocksCaptureServer.acceptedConnectionCount()
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
        context.startForegroundService(Intent(context, TProxyService::class.java).setAction(TProxyService.ACTION_CONNECT))
        PacketLogStore.add("启动本机 STZB 抓包桥接：target=$targetPackage, tun2socks -> 127.0.0.1:$port")
    }

    override suspend fun stop() {
        context.stopService(Intent(context, CaptureVpnService::class.java))
        context.startService(Intent(context, TProxyService::class.java).setAction(TProxyService.ACTION_DISCONNECT))
        LocalSocksCaptureServer.stop()
        repeat(20) {
            if (!preferences.enable) return@repeat
            delay(100)
        }
        sessionStopped = !preferences.enable && !LocalSocksCaptureServer.isRunning()
        sessionNetworkRestored = sessionStopped && hasInternetNetwork()
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
            CaptureExportKind.EVIDENCE -> writeEvidenceExport()
        }
        return captureExport(kind, file)
    }

    private fun currentEvidence(): CaptureEvidence {
        if (sessionStopped && !sessionNetworkRestored) {
            sessionNetworkRestored = hasInternetNetwork()
        }
        val protocols = protocolDistribution().mapValues { (id, count) ->
            (count - (protocolBaseline[id] ?: 0)).coerceAtLeast(0)
        }.filterValues { it > 0 }
        return CaptureEvidence.from(
            nativeReady = TProxyService.isNativeReady(),
            vpnEstablished = sessionVpnEstablished,
            socksConnections = (LocalSocksCaptureServer.acceptedConnectionCount() - socksBaseline).coerceAtLeast(0),
            protocolCounts = protocols,
            databaseRowDelta = (businessRowCount() - databaseBaseline).coerceAtLeast(0),
            stopped = sessionStopped,
            networkRestored = sessionNetworkRestored,
            targetPackage = sessionTargetPackage,
        )
    }

    private fun protocolDistribution(): Map<String, Int> = LocalStzbPacketStore.snapshot()
        .groupingBy { it.msgId }
        .eachCount()

    private fun businessRowCount(): Int = LocalStzbRepository.counts().let { counts ->
        counts.fullBattles + counts.battleNotices + counts.chats + counts.monitorMoves +
            counts.teamUsers + counts.mapCells + counts.unionRanks + counts.playerPowerRanks +
            counts.playerStats + counts.announcements + counts.heroUnlocks + counts.playerSelf +
            counts.zonePlayers + counts.dbSync + counts.battleFields + counts.marchEvents + counts.localRecords
    }

    private fun hasInternetNetwork(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun writeEvidenceExport(): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        return File(dir, "capture_evidence_${System.currentTimeMillis()}.txt").apply {
            writeText(currentEvidence().toRedactedText(), Charsets.UTF_8)
        }
    }
}

fun captureExport(kind: CaptureExportKind, file: File, now: Long = System.currentTimeMillis()): CaptureExport {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(now))
    val (prefix, extension, mime) = when (kind) {
        CaptureExportKind.STZB -> Triple("STZB解析包", "txt", "text/plain")
        CaptureExportKind.DATABASE -> Triple("STZB数据库", "db", "application/octet-stream")
        CaptureExportKind.DIAGNOSTICS -> Triple("STZB诊断", "txt", "text/plain")
        CaptureExportKind.EVIDENCE -> Triple("STZB抓包证据", "txt", "text/plain")
    }
    return CaptureExport("${prefix}_${timestamp}.$extension", mime, file.readBytes())
}
