package com.example.myapplication

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import hev.sockstun.Preferences
import hev.sockstun.TProxyService

class MainActivity : AppCompatActivity() {

    private lateinit var logsView: TextView
    private lateinit var statusView: TextView
    private lateinit var packageInput: EditText
    private lateinit var socksHostInput: EditText
    private lateinit var socksPortInput: EditText
    private lateinit var protocolFilterInput: EditText
    private lateinit var refreshPageButton: Button
    private lateinit var mainTabGroup: MaterialButtonToggleGroup
    private lateinit var sidebarScrim: View
    private lateinit var btnToggleSidebar: Button
    private lateinit var sidebarSwipeController: SidebarSwipeController
    private var sidebarOpen: Boolean = false
    private var pendingVpnAction: PendingVpnAction = PendingVpnAction.OPEN_SOURCE_BRIDGE

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                when (pendingVpnAction) {
                    PendingVpnAction.OPEN_SOURCE_BRIDGE -> startBridgeService()
                }
            } else {
                PacketLogStore.add("用户取消了 VPN 授权")
            }
        }

    private val logListener: (List<String>) -> Unit = { logs ->
        runOnUiThread {
            renderStzbLogs(logs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LocalTrialManager.ensureAccessOrRedirect(this)) {
            return
        }
        setContentView(R.layout.activity_capture_console)
        StatusBarInsetHelper.applyTopSafeSpacing(findViewById(R.id.captureConsoleRoot))
        logsView = findViewById(R.id.logsView)
        statusView = findViewById(R.id.statusView)
        packageInput = findViewById(R.id.packageInput)
        socksHostInput = findViewById(R.id.socksHostInput)
        socksPortInput = findViewById(R.id.socksPortInput)
        protocolFilterInput = findViewById(R.id.protocolFilterInput)
        refreshPageButton = findViewById(R.id.btnRefreshPage)
        mainTabGroup = findViewById(R.id.mainTabGroup)
        sidebarScrim = findViewById(R.id.sidebarScrim)
        btnToggleSidebar = findViewById(R.id.btnToggleSidebar)
        sidebarSwipeController = SidebarSwipeController(
            context = this,
            root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup,
            drawer = mainTabGroup,
            scrim = sidebarScrim,
            toggleButton = btnToggleSidebar,
        ) { open ->
            sidebarOpen = open
        }
        setupMainTabs()

        findViewById<Button>(R.id.btnPickApp).setOnClickListener {
            showInstalledAppsDialog()
        }
        findViewById<Button>(R.id.btnStartBridge).setOnClickListener {
            requestVpnAndStartBridge()
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, CaptureVpnService::class.java))
            val tproxyIntent = Intent(this, TProxyService::class.java).apply {
                action = TProxyService.ACTION_DISCONNECT
            }
            startService(tproxyIntent)
            LocalSocksCaptureServer.stop()
            PacketLogStore.add("已请求停止抓取服务")
            renderStatus()
        }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            PacketLogStore.clear()
            PacketCaptureStore.clear()
            LocalStzbPacketStore.clear()
            LocalBattleMonitorStore.clear()
        }
        findViewById<Button>(R.id.btnExportStzb).setOnClickListener {
            val outFile = LocalStzbCaptureWriter.exportSummary(this)
            if (outFile == null) {
                PacketLogStore.add("没有可导出的 STZB 解析包")
            } else {
                PacketLogStore.add("已导出 STZB 解析包：${outFile.absolutePath}")
                PacketLogStore.add("本机 capture_new 根目录：${LocalStzbCaptureWriter.captureRoot(this).absolutePath}")
            }
        }
        findViewById<Button>(R.id.btnExportDatabase).setOnClickListener {
            val outFile = LocalStzbRepository.exportDatabase(this)
            PacketLogStore.add("已导出本机 SQLite 数据库：${outFile.absolutePath}")
        }
        findViewById<Button>(R.id.btnExportDiagnostics).setOnClickListener {
            val outFile = LocalMigrationDiagnostics.export(this)
            PacketLogStore.add("已导出真机迁移诊断：${outFile.absolutePath}")
        }
        refreshPageButton.setOnClickListener {
            PacketLogStore.add("已刷新抓包控制台")
            renderStatus()
            renderStzbLogs(PacketLogStore.snapshot())
        }
        protocolFilterInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderStzbLogs(PacketLogStore.snapshot())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        PacketLogStore.add("STZB 抓包控制台就绪：请选择目标 App 后一键授权启动")
        PacketLogStore.add("STZB 本机解析链路：tun2socks -> SOCKS5 捕获器 -> 协议解析 -> SQLite")
        renderStatus()
    }

    private fun setupMainTabs() {
        val moduleByTab = mapOf(
            R.id.tabAllPlayerTeams to DashboardActivity.MODULE_ALL_PLAYER_TEAMS,
            R.id.tabAllBattles to DashboardActivity.MODULE_BATTLES,
            R.id.tabAllianceMembers to DashboardActivity.MODULE_TEAM_USERS,
            R.id.tabGroupedWuxun to DashboardActivity.MODULE_GROUPED_WUXUN,
            R.id.tabSiegeAttendance to DashboardActivity.MODULE_TASK_ATTENDANCE,
            R.id.tabAllianceMemberTeams to DashboardActivity.MODULE_ALLIANCE_MEMBER_TEAMS,
            R.id.tabBattleMessages to DashboardActivity.MODULE_MESSAGES,
            R.id.tabTeamReport to DashboardActivity.MODULE_TEAM_REPORT,
            R.id.tabSimulator to DashboardActivity.MODULE_SIMULATOR,
            R.id.tabStateRegions to DashboardActivity.MODULE_STATE_REGIONS,
            R.id.tabBattleMonitor to DashboardActivity.MODULE_BATTLE_MONITOR,
            R.id.tabTeamIndex13a2 to DashboardActivity.MODULE_TEAM_INDEX_13A2,
        )
        mainTabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                moduleByTab[checkedId]?.let { openDashboardModule(it) }
                setSidebarOpen(false)
            }
        }
        btnToggleSidebar.setOnClickListener {
            sidebarSwipeController.toggle()
        }
    }

    private fun setSidebarOpen(open: Boolean) {
        sidebarSwipeController.setOpen(open)
    }

    fun openDashboardFromXml(view: View) {
        openDashboardModule(DashboardActivity.MODULE_RANKING)
    }

    private fun openDashboardModule(module: String) {
        startActivity(
            Intent(this, DashboardActivity::class.java).apply {
                putExtra(DashboardActivity.EXTRA_MODULE, module)
            }
        )
    }

    override fun onStart() {
        super.onStart()
        PacketLogStore.addListener(logListener)
        logListener(PacketLogStore.snapshot())
        renderStatus()
    }

    override fun onStop() {
        PacketLogStore.removeListener(logListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
    }

    private fun requestVpnAndStartBridge() {
        pendingVpnAction = PendingVpnAction.OPEN_SOURCE_BRIDGE
        val intent = VpnService.prepare(this)
        if (intent == null) {
            PacketLogStore.add("STZB 一键启动：VPN 已授权，启动本机桥接")
            startBridgeService()
        } else {
            PacketLogStore.add("STZB 一键启动：请求系统 VPN 授权")
            vpnPermissionLauncher.launch(intent)
        }
    }

    private fun startBridgeService() {
        if (!TProxyService.isNativeReady()) {
            PacketLogStore.add("开源桥接 native 库当前不可用：本机未产出或未打包 libhev-socks5-tunnel.so")
            renderStatus()
            return
        }

        val targetPackage = packageInput.text?.toString()?.trim().orEmpty()
        val socksHost = "127.0.0.1"
        val socksPort = LocalSocksCaptureServer.start()
        socksHostInput.setText(socksHost)
        socksPortInput.setText(socksPort.toString())

        val prefs = Preferences(this)
        prefs.setSocksAddress(socksHost)
        prefs.setSocksPort(socksPort)
        prefs.setSocksUdpAddress("")
        prefs.setSocksUsername("")
        prefs.setSocksPassword("")
        prefs.setIpv4(true)
        prefs.setIpv6(true)
        prefs.setGlobal(false)
        prefs.setUdpInTcp(false)
        prefs.setRemoteDns(true)
        prefs.setApps(
            if (targetPackage.isBlank()) emptySet() else setOf(targetPackage)
        )

        val intent = Intent(this, TProxyService::class.java).apply {
            action = TProxyService.ACTION_CONNECT
        }
        startService(intent)
        PacketLogStore.add(
            "启动本机 STZB 抓包桥接：target=${targetPackage.ifBlank { "<none>" }}, tun2socks -> $socksHost:$socksPort"
        )
        renderStatus()
    }


    private fun showInstalledAppsDialog() {
        val apps = packageManager.getInstalledApplications(
            PackageManager.ApplicationInfoFlags.of(0)
        ).map { appInfo ->
            val label = appInfo.loadLabel(packageManager)?.toString().orEmpty()
            val pkg = appInfo.packageName.orEmpty()
            AppEntry(
                label = if (label.isBlank()) pkg else label,
                packageName = pkg
            )
        }.filter { it.packageName.isNotBlank() }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<AppEntry> { it.label.lowercase() }.thenBy { it.packageName })

        if (apps.isEmpty()) {
            PacketLogStore.add("未查询到可展示的已安装应用")
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        val searchInput = EditText(this).apply {
            hint = "输入 App 名称或包名搜索，例如 stzb"
            setSingleLine(true)
        }
        val listView = ListView(this)
        container.addView(searchInput)
        container.addView(
            listView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (420 * resources.displayMetrics.density).toInt()
            )
        )

        val filteredApps = apps.toMutableList()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            filteredApps.map { it.displayText() }.toMutableList()
        )
        listView.adapter = adapter

        fun refreshList(query: String) {
            val keyword = query.trim()
            filteredApps.clear()
            filteredApps += if (keyword.isBlank()) {
                apps
            } else {
                apps.filter { app ->
                    app.label.contains(keyword, ignoreCase = true) ||
                        app.packageName.contains(keyword, ignoreCase = true)
                }
            }
            adapter.clear()
            adapter.addAll(filteredApps.map { it.displayText() })
            adapter.notifyDataSetChanged()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("搜索并选择 App（共 ${apps.size} 个）")
            .setView(container)
            .setNegativeButton("取消", null)
            .create()
        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = filteredApps.getOrNull(position)
            if (selected != null) {
                packageInput.setText(selected.packageName)
                PacketLogStore.add("已选择应用：${selected.label} (${selected.packageName})")
                dialog.dismiss()
            }
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshList(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        dialog.setOnShowListener {
            searchInput.requestFocus()
            getSystemService(InputMethodManager::class.java)?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
    }
    private fun renderStatus() {
        val tunnelRunning = Preferences(this).getEnable()
        statusView.text = if (CaptureVpnService.isRunning || tunnelRunning || LocalSocksCaptureServer.isRunning()) {
            "状态：抓取中 / 本机解析包 ${LocalStzbPacketStore.snapshot().size}"
        } else {
            "状态：未启动"
        }
    }

    private fun renderStzbLogs(logs: List<String>) {
        val protocolIds = protocolFilterInput.text?.toString().orEmpty().parseProtocolIds()
        val parsedLogs = logs
            .filter { it.isStzbParsedLog() }
            .filter { line -> protocolIds.isEmpty() || protocolIds.any { line.containsProtocolId(it) } }
        logsView.text = if (parsedLogs.isEmpty()) {
            if (protocolIds.isEmpty()) {
                "等待 STZB 业务包解析...\n\n只显示 STZB 包头识别、专表入库、战报/聊天/行军等解析后的日志。"
            } else {
                "当前协议筛选：${protocolIds.joinToString(", ")}\n暂无匹配的 STZB 解析日志。"
            }
        } else {
            parsedLogs.joinToString(separator = "\n")
        }
    }

    private fun String.parseProtocolIds(): List<String> {
        return split(',', '，', ' ', '\n', '\t', ';', '；', '/')
            .map { it.trim() }
            .filter { it.matches(Regex("""\d+""")) }
            .distinct()
    }

    private fun String.containsProtocolId(protocolId: String): Boolean {
        return Regex("""(?<!\d)${Regex.escape(protocolId)}(?!\d)""").containsMatchIn(this)
    }

    private fun String.isStzbParsedLog(): Boolean {
        val markers = listOf(
            "STZB",
            "战报",
            "聊天",
            "行军",
            "同盟成员专表",
            "地图格子专表",
            "排行专表",
            "玩家统计专表",
            "公告专表",
            "武将解锁专表",
            "当前角色专表",
            "战区玩家专表",
            "db_sync",
            "攻城战场专表",
            "玩家行军专表",
            "本机业务记录入库",
        )
        return markers.any { contains(it, ignoreCase = true) } &&
            !contains("SOCKS 转发") &&
            !contains("原始包") &&
            !contains("最新包详情")
    }
}

private enum class PendingVpnAction {
    OPEN_SOURCE_BRIDGE,
}

private data class AppEntry(
    val label: String,
    val packageName: String,
) {
    fun displayText(): String = "$label\n$packageName"
}
