package com.example.myapplication

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class DashboardActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var contentView: TextView
    private lateinit var refreshPageButton: Button
    private lateinit var moduleTitleView: TextView
    private lateinit var moduleSubtitleView: TextView
    private lateinit var moduleKpiView: TextView
    private lateinit var contentScrollView: View
    private lateinit var battleListView: ListView
    private lateinit var filterPlayerInput: EditText
    private lateinit var filterUnionInput: EditText
    private lateinit var filterTeamSideInput: EditText
    private lateinit var filterTeamKeywordInput: EditText
    private lateinit var filterFightTypeInput: EditText
    private lateinit var filterResultInput: EditText
    private lateinit var filterWidInput: EditText
    private lateinit var filterStartTimeInput: EditText
    private lateinit var filterEndTimeInput: EditText
    private lateinit var battleFilterPanel: View
    private lateinit var taskSubTabScroll: HorizontalScrollView
    private lateinit var taskSubTabContainer: LinearLayout
    private lateinit var mainTabGroup: MaterialButtonToggleGroup
    private lateinit var sidebarScrim: View
    private lateinit var btnToggleSidebar: Button
    private lateinit var sidebarSwipeController: SidebarSwipeController
    private val filterRefreshHandler = Handler(Looper.getMainLooper())
    private val battleMonitorRefreshHandler = Handler(Looper.getMainLooper())
    private var activeFilterMode: FilterMode = FilterMode.NONE
    private var sidebarOpen: Boolean = false
    private var latestBattleIds: List<Int> = emptyList()
    private var battleCards: List<BattleCardItem> = emptyList()
    private var monitorCards: List<MonitorCardItem> = emptyList()
    private var infoCards: List<InfoCardItem> = emptyList()
    private var simulatorConfig: LocalSimulationConfig? = null
    private var selectedSiegeTaskId: Long? = null
    private var taskAttendanceSubPage: TaskAttendanceSubPage = TaskAttendanceSubPage.HOME
    private var selectedTeamUserUid: Long? = null
    private var selectedTeamUserGroupFilter: String = ""
    private val expandedTeamUserGroups = linkedSetOf<String>()
    private var teamUsersSubPage: TeamUsersSubPage = TeamUsersSubPage.HOME
    private var teamReportPeriod: String = "all"
    private var teamReportDim: String = "group"
    private var teamReportGroup: String = ""
    private var stateRegionPage: StateRegionPage = StateRegionPage.OVERVIEW
    private var stateRegionScope: String = "all"
    private var stateRegionGroup: String = ""
    private var stateRegionGroupOptions: List<String> = emptyList()
    private var stateRegionMetric: String = "player_count"
    private var battleMonitorSearchQuery: String = ""
    private var currentModule: String = MODULE_SIMULATOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LocalTrialManager.ensureAccessOrRedirect(this)) {
            return
        }
        setContentView(R.layout.activity_dashboard)
        StatusBarInsetHelper.applyTopSafeSpacing(findViewById(R.id.dashboardRoot))
        HeroNameResolver.init(applicationContext)
        SkillNameResolver.init(applicationContext)
        LocalStzbRepository.init(applicationContext)
        LocalBattleSimulator.init(applicationContext)

        statusView = findViewById(R.id.clientStatusView)
        contentView = findViewById(R.id.clientContentView)
        refreshPageButton = findViewById(R.id.btnRefreshPage)
        moduleTitleView = findViewById(R.id.moduleTitleView)
        moduleSubtitleView = findViewById(R.id.moduleSubtitleView)
        moduleKpiView = findViewById(R.id.moduleKpiView)
        contentScrollView = findViewById(R.id.contentScrollView)
        battleListView = findViewById(R.id.battleListView)
        filterPlayerInput = findViewById(R.id.inputFilterPlayer)
        filterUnionInput = findViewById(R.id.inputFilterUnion)
        filterTeamSideInput = findViewById(R.id.inputFilterTeamSide)
        filterTeamKeywordInput = findViewById(R.id.inputFilterTeamKeyword)
        filterFightTypeInput = findViewById(R.id.inputFilterFightType)
        filterResultInput = findViewById(R.id.inputFilterResult)
        filterWidInput = findViewById(R.id.inputFilterWid)
        filterStartTimeInput = findViewById(R.id.inputFilterStartTime)
        filterEndTimeInput = findViewById(R.id.inputFilterEndTime)
        battleFilterPanel = findViewById(R.id.battleFilterPanel)
        taskSubTabScroll = findViewById(R.id.taskSubTabScroll)
        taskSubTabContainer = findViewById(R.id.taskSubTabContainer)
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
        battleFilterPanel.visibility = View.GONE
        setupDashboardDomains()
        setupSidebarNavigation()
        setupBattleFilterAutoReload()
        refreshPageButton.setOnClickListener {
            reloadCurrentModule()
        }

        findViewById<Button>(R.id.btnRefreshLocal).setOnClickListener {
            loadLocalOverview()
        }
        findViewById<Button>(R.id.btnLoadBattles).setOnClickListener {
            loadBattleCards()
        }
        findViewById<Button>(R.id.btnLoadMonitor).setOnClickListener {
            loadMonitor()
        }
        findViewById<Button>(R.id.btnLoadPackets).setOnClickListener {
            loadRecentPackets()
        }
        findViewById<Button>(R.id.btnLoadChats).setOnClickListener {
            loadMoreRecords()
        }
        findViewById<Button>(R.id.btnLoadRanking).setOnClickListener {
            loadRanking()
        }
        findViewById<Button>(R.id.btnLoadTeamUsers).setOnClickListener {
            loadTeamUsers()
        }
        findViewById<Button>(R.id.btnLoadMapCells).setOnClickListener {
            loadMapCells()
        }
        findViewById<Button>(R.id.btnLoadPlayerProfile).setOnClickListener {
            loadPlayerProfile()
        }
        findViewById<Button>(R.id.btnLoadAnnouncements).setOnClickListener {
            loadAnnouncementsAndHeroes()
        }
        findViewById<Button>(R.id.btnLoadZonePlayers).setOnClickListener {
            loadZonePlayers()
        }
        findViewById<Button>(R.id.btnLoadDbSync).setOnClickListener {
            loadDbSync()
        }
        findViewById<Button>(R.id.btnLoadHeroStats).setOnClickListener {
            loadHeroStats()
        }
        findViewById<Button>(R.id.btnLoadTeamReport).setOnClickListener {
            loadTeamReport()
        }
        findViewById<Button>(R.id.btnLoadSimulatorResources).setOnClickListener {
            loadSimulatorResources()
        }
        findViewById<Button>(R.id.btnLoadTaskAttendance).setOnClickListener {
            loadTaskAttendance()
        }
        findViewById<Button>(R.id.btnLoadStateRegions).setOnClickListener {
            loadStateRegions()
        }
        battleListView.setOnItemClickListener { _, _, position, _ ->
            when (battleListView.adapter) {
                is BattleCardAdapter -> {
                    val battleId = battleCards.getOrNull(position)?.battleId ?: latestBattleIds.getOrNull(position) ?: return@setOnItemClickListener
                    val intent = Intent(this, BattleDetailActivity::class.java).apply {
                        putExtra(BattleDetailActivity.EXTRA_BATTLE_ID, battleId)
                    }
                    startActivity(intent)
                }
                is InfoCardAdapter -> {
                    infoCards.getOrNull(position)?.let { item ->
                        when (item.actionKey) {
                            ACTION_CREATE_SIEGE_TASK -> showCreateSiegeTaskDialog()
                            ACTION_OPEN_SIEGE_TASK -> openSiegeTaskDetail(item.actionId)
                            ACTION_REFRESH_SIEGE_TASKS -> loadTaskAttendance()
                            ACTION_CLOSE_SIEGE_TASK_DETAIL -> closeSiegeTaskDetail()
                            ACTION_RUN_SIEGE_TASK_STATISTICS -> confirmRunSiegeTaskStatistics(item.actionId)
                            ACTION_EXPORT_SIEGE_TASK_CSV -> exportSiegeTaskCsv(item.actionId)
                            ACTION_DELETE_SIEGE_TASK -> confirmDeleteSiegeTask(item.actionId)
                            ACTION_TOGGLE_TEAM_USER_GROUP -> toggleTeamUserGroup(item.actionArg)
                            ACTION_OPEN_TEAM_USER -> openTeamUserDetail(item.actionId)
                            ACTION_SWITCH_TEAM_REPORT_PERIOD -> switchTeamReportPeriod(item.actionArg)
                            ACTION_SWITCH_TEAM_REPORT_DIM -> switchTeamReportDim(item.actionArg)
                            ACTION_SWITCH_TEAM_REPORT_GROUP -> switchTeamReportGroup(item.actionArg)
                            ACTION_EXPORT_TEAM_REPORT_CSV -> exportTeamReportCsv()
                            ACTION_SWITCH_STATE_REGION_PAGE -> switchStateRegionPage(item.actionArg)
                            ACTION_SWITCH_STATE_REGION_SCOPE -> switchStateRegionScope(item.actionArg)
                            ACTION_SWITCH_STATE_REGION_GROUP -> switchStateRegionGroup(item.actionArg)
                            ACTION_PICK_STATE_REGION_GROUP -> showStateRegionGroupPicker()
                            ACTION_SWITCH_STATE_REGION_METRIC -> switchStateRegionMetric(item.actionArg)
                            ACTION_RUN_SIM_SINGLE -> runSimulationAndRender(1)
                            ACTION_RUN_SIM_100 -> runSimulationAndRender(100)
                            ACTION_RUN_SIM_1000 -> runSimulationAndRender(1000)
                            ACTION_EDIT_SIM_BLUE -> showSimulatorHeroPicker("blue")
                            ACTION_EDIT_SIM_RED -> showSimulatorHeroPicker("red")
                            else -> showInfoCardDialog(item)
                        }
                    }
                }
                is MonitorCardAdapter -> {
                    monitorCards.getOrNull(position)?.toInfoCard()?.let { showInfoCardDialog(it) }
                }
            }
        }

        setStatus("客户端就绪")
        if (!openRequestedModule()) {
            openModule(MODULE_SIMULATOR)
        }
    }

    private fun openRequestedModule(): Boolean {
        val module = intent.getStringExtra(EXTRA_MODULE).orEmpty()
        if (module.isBlank()) return false
        openModule(module)
        return true
    }

    private fun openModule(module: String) {
        currentModule = module
        if (module != MODULE_TASK_ATTENDANCE && module != MODULE_TEAM_USERS && module != MODULE_TEAM_REPORT && module != MODULE_STATE_REGIONS) {
            selectedSiegeTaskId = null
            taskAttendanceSubPage = TaskAttendanceSubPage.HOME
            selectedTeamUserUid = null
            teamUsersSubPage = TeamUsersSubPage.HOME
            taskSubTabScroll.visibility = View.GONE
            taskSubTabContainer.removeAllViews()
        }
        val domainGroup = findViewById<MaterialButtonToggleGroup>(R.id.dashboardDomainGroup)
        val tabGroup = findViewById<MaterialButtonToggleGroup>(R.id.dashboardTabGroup)

        fun select(domainId: Int, tabId: Int, load: () -> Unit) {
            domainGroup.check(domainId)
            tabGroup.check(tabId)
            load()
        }

        when (module) {
            MODULE_ALL_PLAYER_TEAMS -> select(R.id.dashDomainAnalysis, R.id.btnLoadHeroStats) { loadAllPlayerTeams() }
            MODULE_BATTLES -> select(R.id.dashDomainReport, R.id.btnLoadBattles) { loadBattleCards() }
            MODULE_MONITOR -> select(R.id.dashDomainBattle, R.id.btnLoadMonitor) { loadMonitor() }
            MODULE_BATTLE_MONITOR -> select(R.id.dashDomainBattle, R.id.btnLoadMonitor) { loadBattlefieldMonitor() }
            MODULE_MESSAGES -> select(R.id.dashDomainBattle, R.id.btnLoadChats) { loadMoreRecords() }
            MODULE_MAP -> select(R.id.dashDomainBattle, R.id.btnLoadMapCells) { loadMapCells() }
            MODULE_STATE_REGIONS -> select(R.id.dashDomainBattle, R.id.btnLoadStateRegions) { loadStateRegions() }
            MODULE_ZONE_PLAYERS -> select(R.id.dashDomainBattle, R.id.btnLoadZonePlayers) { loadZonePlayers() }
            MODULE_RECENT_PACKETS -> select(R.id.dashDomainTools, R.id.btnLoadPackets) { loadRecentPackets() }
            MODULE_RANKING -> select(R.id.dashDomainAnalysis, R.id.btnLoadRanking) { loadRanking() }
            MODULE_HERO_STATS -> select(R.id.dashDomainAnalysis, R.id.btnLoadHeroStats) { loadHeroStats() }
            MODULE_TEAM_USERS -> select(R.id.dashDomainOrg, R.id.btnLoadTeamUsers) { loadTeamUsers() }
            MODULE_GROUPED_WUXUN -> select(R.id.dashDomainAnalysis, R.id.btnLoadTeamReport) { loadGroupedWuxun() }
            MODULE_TASK_ATTENDANCE -> select(R.id.dashDomainOrg, R.id.btnLoadTaskAttendance) { loadTaskAttendance() }
            MODULE_ALLIANCE_MEMBER_TEAMS -> select(R.id.dashDomainOrg, R.id.btnLoadTeamUsers) { loadAllianceMemberTeams() }
            MODULE_TEAM_REPORT -> select(R.id.dashDomainAnalysis, R.id.btnLoadTeamReport) { loadTeamReport() }
            MODULE_PLAYER_PROFILE -> select(R.id.dashDomainReport, R.id.btnLoadPlayerProfile) { loadPlayerProfile() }
            MODULE_OVERVIEW -> select(R.id.dashDomainHome, R.id.btnRefreshLocal) { loadLocalOverview() }
            MODULE_ANNOUNCEMENTS -> select(R.id.dashDomainTools, R.id.btnLoadAnnouncements) { loadAnnouncementsAndHeroes() }
            MODULE_DB_SYNC -> select(R.id.dashDomainTools, R.id.btnLoadDbSync) { loadDbSync() }
            MODULE_SIMULATOR -> select(R.id.dashDomainExt, R.id.btnLoadSimulatorResources) { loadSimulatorResources() }
            MODULE_TEAM_INDEX_13A2 -> select(R.id.dashDomainBattle, R.id.btnLoadMapCells) { loadTeamIndex13a2() }
            else -> loadLocalOverview()
        }
    }

    private fun reloadCurrentModule() {
        setStatus("刷新中...")
        openModule(currentModule)
    }

    private fun setupBattleFilterAutoReload() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (battleFilterPanel.visibility != View.VISIBLE) return
                filterRefreshHandler.removeCallbacksAndMessages(null)
                filterRefreshHandler.postDelayed({
                    when (activeFilterMode) {
                        FilterMode.BATTLES -> loadBattleCards()
                        FilterMode.PLAYER_TEAMS -> loadAllPlayerTeams()
                        FilterMode.BATTLE_MONITOR -> {
                            battleMonitorSearchQuery = buildBattleMonitorSearchQuery()
                            loadBattlefieldMonitor()
                        }
                        FilterMode.NONE -> Unit
                    }
                }, 350)
            }
        }
        listOf(
            filterPlayerInput,
            filterUnionInput,
            filterTeamSideInput,
            filterTeamKeywordInput,
            filterFightTypeInput,
            filterResultInput,
            filterWidInput,
            filterStartTimeInput,
            filterEndTimeInput,
        ).forEach { it.addTextChangedListener(watcher) }
    }

    override fun onResume() {
        super.onResume()
        ensureBattleMonitorAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        battleMonitorRefreshHandler.removeCallbacksAndMessages(null)
    }

    private fun showBattleFilters() {
        activeFilterMode = FilterMode.BATTLES
        battleFilterPanel.visibility = View.VISIBLE
        filterPlayerInput.visibility = View.VISIBLE
        filterUnionInput.visibility = View.VISIBLE
        filterTeamSideInput.visibility = View.GONE
        filterTeamKeywordInput.visibility = View.GONE
        filterFightTypeInput.visibility = View.VISIBLE
        filterResultInput.visibility = View.VISIBLE
        filterWidInput.visibility = View.VISIBLE
        filterStartTimeInput.visibility = View.VISIBLE
        filterEndTimeInput.visibility = View.VISIBLE
    }

    private fun showPlayerTeamFilters() {
        activeFilterMode = FilterMode.PLAYER_TEAMS
        battleFilterPanel.visibility = View.VISIBLE
        filterPlayerInput.visibility = View.VISIBLE
        filterUnionInput.visibility = View.VISIBLE
        filterTeamSideInput.visibility = View.VISIBLE
        filterTeamKeywordInput.visibility = View.VISIBLE
        filterFightTypeInput.visibility = View.GONE
        filterResultInput.visibility = View.GONE
        filterWidInput.visibility = View.GONE
        filterStartTimeInput.visibility = View.GONE
        filterEndTimeInput.visibility = View.GONE
    }

    private fun showBattleMonitorFilters() {
        activeFilterMode = FilterMode.BATTLE_MONITOR
        battleFilterPanel.visibility = View.VISIBLE
        filterPlayerInput.visibility = View.VISIBLE
        filterUnionInput.visibility = View.VISIBLE
        filterTeamSideInput.visibility = View.GONE
        filterTeamKeywordInput.visibility = View.VISIBLE
        filterFightTypeInput.visibility = View.GONE
        filterResultInput.visibility = View.GONE
        filterWidInput.visibility = View.VISIBLE
        filterStartTimeInput.visibility = View.GONE
        filterEndTimeInput.visibility = View.GONE
        filterPlayerInput.hint = "玩家"
        filterUnionInput.hint = "同盟"
        filterTeamKeywordInput.hint = "队伍ID/坐标"
        filterWidInput.hint = "目标wid"
    }

    private fun hideFilters() {
        activeFilterMode = FilterMode.NONE
        battleFilterPanel.visibility = View.GONE
    }

    private fun setupSidebarNavigation() {
        val moduleByTab = mapOf(
            R.id.tabAllPlayerTeams to MODULE_ALL_PLAYER_TEAMS,
            R.id.tabAllBattles to MODULE_BATTLES,
            R.id.tabAllianceMembers to MODULE_TEAM_USERS,
            R.id.tabGroupedWuxun to MODULE_GROUPED_WUXUN,
            R.id.tabSiegeAttendance to MODULE_TASK_ATTENDANCE,
            R.id.tabAllianceMemberTeams to MODULE_ALLIANCE_MEMBER_TEAMS,
            R.id.tabBattleMessages to MODULE_MESSAGES,
            R.id.tabTeamReport to MODULE_TEAM_REPORT,
            R.id.tabSimulator to MODULE_SIMULATOR,
            R.id.tabStateRegions to MODULE_STATE_REGIONS,
            R.id.tabBattleMonitor to MODULE_BATTLE_MONITOR,
            R.id.tabTeamIndex13a2 to MODULE_TEAM_INDEX_13A2,
        )
        mainTabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                moduleByTab[checkedId]?.let { openModule(it) }
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

    private fun setupDashboardDomains() {
        val domainGroup = findViewById<MaterialButtonToggleGroup>(R.id.dashboardDomainGroup)
        val homeButtons = listOf(
            R.id.btnRefreshLocal,
        ).map { findViewById<View>(it) }
        val battleButtons = listOf(
            R.id.btnLoadMonitor,
            R.id.btnLoadChats,
            R.id.btnLoadMapCells,
            R.id.btnLoadStateRegions,
            R.id.btnLoadZonePlayers,
        ).map { findViewById<View>(it) }
        val reportButtons = listOf(
            R.id.btnLoadBattles,
            R.id.btnLoadPlayerProfile,
        ).map { findViewById<View>(it) }
        val analysisButtons = listOf(
            R.id.btnLoadRanking,
            R.id.btnLoadHeroStats,
            R.id.btnLoadTeamReport,
        ).map { findViewById<View>(it) }
        val orgButtons = listOf(
            R.id.btnLoadTeamUsers,
            R.id.btnLoadTaskAttendance,
        ).map { findViewById<View>(it) }
        val toolButtons = listOf(
            R.id.btnLoadAnnouncements,
            R.id.btnLoadDbSync,
            R.id.btnLoadPackets,
        ).map { findViewById<View>(it) }
        val extButtons = listOf(
            R.id.btnLoadSimulatorResources,
        ).map { findViewById<View>(it) }

        fun showDomain(checkedId: Int) {
            homeButtons.forEach { it.visibility = if (checkedId == R.id.dashDomainHome) View.VISIBLE else View.GONE }
            battleButtons.forEach { it.visibility = if (checkedId == R.id.dashDomainBattle) View.VISIBLE else View.GONE }
            reportButtons.forEach { it.visibility = if (checkedId == R.id.dashDomainReport) View.VISIBLE else View.GONE }
            analysisButtons.forEach { it.visibility = if (checkedId == R.id.dashDomainAnalysis) View.VISIBLE else View.GONE }
            orgButtons.forEach { it.visibility = if (checkedId == R.id.dashDomainOrg) View.VISIBLE else View.GONE }
            toolButtons.forEach { it.visibility = if (checkedId == R.id.dashDomainTools) View.VISIBLE else View.GONE }
            extButtons.forEach { it.visibility = if (checkedId == R.id.dashDomainExt) View.VISIBLE else View.GONE }
        }

        showDomain(R.id.dashDomainHome)
        domainGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                battleFilterPanel.visibility = View.GONE
                showDomain(checkedId)
            }
        }
    }

    private fun runLocalTask(title: String, block: () -> String) {
        setStatus("$title 中...")
        setModuleHeader(title, moduleDescription(title))
        battleFilterPanel.visibility = View.GONE
        battleListView.visibility = View.GONE
        contentScrollView.visibility = View.VISIBLE
        contentView.visibility = View.VISIBLE
        thread(name = "stzb-api-client") {
            val result = runCatching {
                block()
            }
            runOnUiThread {
                result.onSuccess {
                    setStatus("$title 完成")
                    contentView.text = it
                }.onFailure {
                    setStatus("$title 失败")
                    contentView.text = "读取本机数据失败：${it.message}"
                }
            }
        }
    }

    private fun loadLocalOverview() {
        runLocalTask("刷新本机数据") {
            val counts = LocalStzbRepository.counts()
            """
                本机数据概览

                原始 STZB 包：${counts.packets}
                10/92 完整战报：${counts.fullBattles}
                2100 战报通知：${counts.battleNotices}
                2100 聊天消息：${counts.chats}
                5028 行军队伍：${counts.monitorMoves}
                103 同盟成员：${counts.teamUsers}
                5026 地图格子：${counts.mapCells}
                510 玩家统计：${counts.playerStats}
                780 公告：${counts.announcements}
                671 武将解锁：${counts.heroUnlocks}
                21 当前角色：${counts.playerSelf}
                6243 战区玩家：${counts.zonePlayers}
                90005 同步表事件：${counts.dbSync}
                通用业务记录：${counts.localRecords}

                通用记录类型：
                ${renderRecordTypeCounts()}

                使用方式：
                1. 回到首页，选择 STZB 包名
                2. 启动本机 STZB 抓包桥接
                3. 打开游戏触发联网
                4. 回到这里刷新本机数据
            """.trimIndent()
        }
    }

    private fun renderRecordTypeCounts(): String {
        val typeCounts = LocalStzbRepository.countRecordsByType()
        if (typeCounts.isEmpty()) return "  暂无"
        return typeCounts.entries
            .sortedBy { it.key }
            .joinToString("\n") { "  ${it.key}: ${it.value}" }
    }

    private fun loadBattleCards() {
        setStatus("加载本机战报中...")
        setModuleHeader("全部战报", "筛选、分页和详情入口，优先读取 10/92 完整战报，缺失时使用 2100 通知兜底。")
        showBattleFilters()
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        contentView.text = ""

        thread(name = "stzb-battle-list") {
            val result = runCatching {
                val filter = LocalBattleFilter(
                    player = filterPlayerInput.text?.toString().orEmpty(),
                    unionName = filterUnionInput.text?.toString().orEmpty(),
                    fightType = filterFightTypeInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.toIntOrNull(),
                    result = filterResultInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.toIntOrNull(),
                    wid = filterWidInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.toIntOrNull(),
                    startTime = filterStartTimeInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.toLongOrNull(),
                    endTime = filterEndTimeInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.toLongOrNull(),
                    limit = 80,
                )
                val fullBattles = LocalStzbRepository.loadFullBattles(filter)
                if (fullBattles.isNotEmpty()) {
                    BattleRows(
                        ids = fullBattles.map { it.battleId },
                        titles = fullBattles.mapIndexed { idx, battle -> battle.toListTitle(idx + 1) },
                        cards = fullBattles.map { it.toBattleCardItem("完整战报") },
                        source = "10/92 完整战报${filter.describe()}",
                    )
                } else {
                    val notices = LocalStzbRepository.loadBattleNotices(50)
                    BattleRows(
                        ids = notices.map { it.battleId },
                        titles = notices.mapIndexed { idx, battle -> battle.toListTitle(idx + 1) },
                        cards = notices.map { it.toBattleCardItem("通知兜底") },
                        source = "2100 战报通知",
                    )
                }
            }
            runOnUiThread {
                result.onSuccess { rows ->
                    latestBattleIds = rows.ids
                    battleCards = rows.cards
                    setStatus("本机战报：${rows.ids.size} 条")
                    moduleKpiView.text = buildBattleKpi(rows.cards, rows.source)
                    battleListView.adapter = BattleCardAdapter(rows.cards)
                    battleListView.visibility = View.VISIBLE
                }.onFailure {
                    latestBattleIds = emptyList()
                    battleCards = emptyList()
                    battleListView.adapter = null
                    battleListView.visibility = View.GONE
                    contentScrollView.visibility = View.VISIBLE
                    setStatus("加载本机战报失败")
                    contentView.text = "读取失败：${it.message}"
                }
            }
        }
    }

    private fun LocalBattleNotice.toListTitle(index: Int): String {
        return "$index. ${formatTime(time)}  ${localResultText(result)}  ${localFightTypeText(fightType)}\n" +
            "${attackerName.ifBlank { "未知" }} -> ${defenderUnion.ifBlank { "未知守方" }}  wid:$wid"
    }

    private fun LocalFullBattle.toListTitle(index: Int): String {
        return "$index. ${formatTime(time)}  ${localResultText(result)}  ${localFightTypeText(fightType)}\n" +
            "${attackerName.ifBlank { "未知" }} -> ${defenderName.ifBlank { defenderUnion.ifBlank { "未知守方" } }}  武勋:$attackerGongxun"
    }

    private fun LocalBattleFilter.describe(): String {
        val parts = buildList {
            if (player.isNotBlank()) add("玩家=$player")
            if (unionName.isNotBlank()) add("同盟=$unionName")
            fightType?.let { add("类型=$it") }
            result?.let { add("结果=$it") }
            wid?.let { add("wid=$it") }
            startTime?.let { add("开始=$it") }
            endTime?.let { add("结束=$it") }
        }
        return if (parts.isEmpty()) "" else "，筛选：${parts.joinToString(" / ")}"
    }

    private fun LocalFullBattle.toBattleCardItem(sourceLabel: String): BattleCardItem {
        val defender = defenderName.ifBlank { defenderUnion.ifBlank { "未知守方" } }
        return BattleCardItem(
            battleId = battleId,
            timeText = formatTime(time),
            typeText = localFightTypeText(fightType),
            resultText = localResultText(result),
            attackerText = attackerName.ifBlank { "未知攻方" },
            defenderText = defender,
            metaText = "武勋 ${attackerGongxun}  ·  wid ${wid}  ·  ${widName.ifBlank { widCode.ifBlank { "未命名地块" } }}",
            sourceText = sourceLabel,
            resultStyle = resultStyle(result),
        )
    }

    private fun LocalBattleNotice.toBattleCardItem(sourceLabel: String): BattleCardItem {
        return BattleCardItem(
            battleId = battleId,
            timeText = formatTime(time),
            typeText = localFightTypeText(fightType),
            resultText = localResultText(result),
            attackerText = attackerName.ifBlank { "未知攻方" },
            defenderText = defenderName.ifBlank { defenderUnion.ifBlank { "未知守方" } },
            metaText = "武勋 ${attackerGongxun}  ·  wid ${wid}  ·  ${widCode.ifBlank { "通知战报" }}",
            sourceText = sourceLabel,
            resultStyle = resultStyle(result),
        )
    }

    private fun buildBattleKpi(cards: List<BattleCardItem>, source: String): String {
        if (cards.isEmpty()) return "暂无战报  /  等待抓包或导入"
        val wins = cards.count { it.resultStyle == ResultStyle.WIN }
        val losses = cards.count { it.resultStyle == ResultStyle.LOSE }
        val draws = cards.count { it.resultStyle == ResultStyle.DRAW }
        return "$source  ·  共 ${cards.size} 条  ·  胜 $wins  /  负 $losses  /  平 $draws"
    }

    private fun resultStyle(result: Int): ResultStyle {
        return when (result) {
            1, 7, 11 -> ResultStyle.WIN
            0, 10 -> ResultStyle.DRAW
            else -> ResultStyle.LOSE
        }
    }

    private fun resultBadgeColor(style: ResultStyle): Int {
        return when (style) {
            ResultStyle.WIN -> 0xFF16A34A.toInt()
            ResultStyle.LOSE -> 0xFFDC2626.toInt()
            ResultStyle.DRAW -> 0xFFF59E0B.toInt()
        }
    }

    private inner class BattleCardAdapter(
        private val items: List<BattleCardItem>,
    ) : BaseAdapter() {
        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = items[position].battleId.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_battle_card, parent, false)
            val item = items[position]

            view.findViewById<TextView>(R.id.battleTimeView).text = item.timeText
            view.findViewById<TextView>(R.id.battleTypeBadgeView).text = item.typeText
            view.findViewById<TextView>(R.id.battleActorsView).text = "${item.attackerText}  →  ${item.defenderText}"
            view.findViewById<TextView>(R.id.battleMetaView).text = item.metaText
            view.findViewById<TextView>(R.id.battleSourceView).text = item.sourceText

            val resultView = view.findViewById<TextView>(R.id.battleResultBadgeView)
            resultView.text = item.resultText
            when (item.resultStyle) {
                ResultStyle.WIN -> {
                    resultView.setBackgroundColor(0xFF16A34A.toInt())
                }
                ResultStyle.LOSE -> {
                    resultView.setBackgroundColor(0xFFDC2626.toInt())
                }
                ResultStyle.DRAW -> {
                    resultView.setBackgroundColor(0xFFF59E0B.toInt())
                }
            }
            return view
        }
    }

    private fun loadMonitor() {
        runLocalTask("加载本机行军监控") {
            val moves = LocalStzbRepository.loadMonitorMoves(80)
            if (moves.isEmpty()) return@runLocalTask "暂无 5028 行军数据"
            buildString {
                appendLine("5028 本机行军监控")
                appendLine()
                moves.forEachIndexed { idx, move ->
                    appendLine(
                        "${idx + 1}. team=${move.teamId} ${move.ownerName.ifBlank { "-" }} " +
                            "${move.fromXy.ifBlank { "-" }} -> ${move.toXy.ifBlank { "-" }}"
                    )
                    appendLine("   当前=${move.currentXy.ifBlank { "-" }} 到达=${formatTime(move.arriveTime)} 同盟=${move.ownerUnion.ifBlank { "-" }}")
                }
            }
        }
    }

    private fun loadRecentPackets() {
        runLocalTask("加载最近 STZB 包") {
            val packets = LocalStzbRepository.loadRecentPackets(80)
            if (packets.isEmpty()) return@runLocalTask "暂无本机 STZB 包"
            packets.joinToString("\n\n") { packet ->
                "${packet.msgId} type=${packet.dataType}/${packet.decodeKind}\n${packet.streamName}\n${packet.preview}"
            }
        }
    }

    private fun loadMoreRecords() {
        setStatus("加载战场消息中...")
        setModuleHeader("战场消息", "展示通用业务记录与消息类数据，按类型分组输出消息卡。")
        battleFilterPanel.visibility = View.GONE
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-records") {
            val result = runCatching {
                val types = LocalStzbRepository.countRecordsByType().keys.sorted()
                buildList<InfoCardItem> {
                    types.forEach { type ->
                        addAll(LocalStzbRepository.loadRecords(type, 8).map { it.toInfoCard(type) })
                    }
                }
            }
            runOnUiThread {
                result.onSuccess { cards ->
                    infoCards = cards
                    moduleKpiView.text = "消息卡 ${cards.size}"
                    contentView.text = "战场消息\n当前按本机业务记录类型分组展示。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("战场消息：${cards.size} 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载战场消息失败")
                }
            }
        }
    }

    private fun loadRanking() {
        setStatus("加载本机排行统计中...")
        setModuleHeader("排行榜", "按网页端排行中心思路，将玩家武勋、同盟武勋、势力榜和 700 排行统一成结构化榜单。")
        battleFilterPanel.visibility = View.GONE
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-ranking") {
            val result = runCatching {
                val rankings = LocalStzbRepository.loadBattleRankings(12)
                val unions = LocalStzbRepository.loadUnionRanks(8)
                val players = LocalStzbRepository.loadPlayerPowerRanks(8)
                buildList {
                    addAll(rankings.players.mapIndexed { idx, row -> row.toInfoCard(idx + 1, "玩家武勋", 0xFF2563EB.toInt()) })
                    addAll(rankings.unions.mapIndexed { idx, row -> row.toInfoCard(idx + 1, "同盟武勋", 0xFFF59E0B.toInt()) })
                    addAll(rankings.powers.mapIndexed { idx, row -> row.toInfoCard(idx + 1, "势力峰值", 0xFF7C3AED.toInt()) })
                    addAll(unions.map { it.toInfoCard() })
                    addAll(players.map { it.toInfoCard() })
                }
            }
            runOnUiThread {
                result.onSuccess { cards ->
                    infoCards = cards
                    moduleKpiView.text = "榜单 ${cards.size} 条  /  玩家武勋、同盟武勋、势力峰值、700 排行"
                    contentView.text = "排行榜\n支持后续继续细分为周期切换、维度切换和条形图。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("本机排行：${cards.size} 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载本机排行失败")
                }
            }
        }
    }

    private fun loadTeamUsers() {
        setStatus("加载同盟成员中...")
        setModuleHeader("同盟成员", "按网页端成员页结构，展示分组筛选、分组折叠和成员详情子页。")
        battleFilterPanel.visibility = View.GONE
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-team-users") {
            val result = runCatching {
                val stats = LocalStzbRepository.loadTeamStats()
                val groupNames = stats.groups.map { it.name.ifBlank { "未分组" } }
                if (expandedTeamUserGroups.isEmpty()) {
                    expandedTeamUserGroups += groupNames.take(1)
                }
                val filteredStats = if (selectedTeamUserGroupFilter.isBlank()) stats.groups else stats.groups.filter { it.name.ifBlank { "未分组" } == selectedTeamUserGroupFilter }
                val users = LocalStzbRepository.loadTeamUsers(group = selectedTeamUserGroupFilter, limit = 0)
                val selectedMember = selectedTeamUserUid?.let { uid -> users.firstOrNull { it.uid == uid } }
                    ?: selectedTeamUserUid?.let { uid ->
                        LocalStzbRepository.loadTeamUsers(limit = 0).firstOrNull { it.uid == uid }
                    }
                val memberTeams = selectedMember?.let { member ->
                    LocalStzbRepository.loadPlayerBattleTeams(0)
                        .filter { it.player == member.name }
                        .sortedWith(compareByDescending<LocalPlayerBattleTeam> { it.battles }.thenByDescending { it.winRate })
                }.orEmpty()
                val maxPower = users.maxOfOrNull { it.power }?.coerceAtLeast(1) ?: 1
                val maxWuxun = users.maxOfOrNull { it.wuxun }?.coerceAtLeast(1) ?: 1
                val maxWeekContribute = users.maxOfOrNull { it.contributeWeek }?.coerceAtLeast(1) ?: 1
                val totalPower = users.sumOf { it.power.toLong() }
                val totalWuxun = users.sumOf { it.wuxun.toLong() }
                val totalWeekContribute = users.sumOf { it.contributeWeek.toLong() }
                val cards = buildTeamUsersCards(
                    stats = stats,
                    filteredStats = filteredStats,
                    users = users,
                    selectedMember = selectedMember,
                    memberTeams = memberTeams,
                    maxPower = maxPower,
                    maxWuxun = maxWuxun,
                    maxWeekContribute = maxWeekContribute,
                    totalPower = totalPower,
                    totalWuxun = totalWuxun,
                    totalWeekContribute = totalWeekContribute,
                )
                TeamUsersUi(
                    cards = cards,
                    summary = TeamUsersUiSummary(stats.total, totalPower, totalWuxun, totalWeekContribute),
                    groupNames = groupNames,
                    selectedMember = selectedMember,
                    memberTeams = memberTeams,
                    filteredUsers = users,
                )
            }
            runOnUiThread {
                result.onSuccess { ui ->
                    if (ui.selectedMember == null) {
                        selectedTeamUserUid = null
                        teamUsersSubPage = TeamUsersSubPage.HOME
                    }
                    syncTeamUsersSubTabs(ui.groupNames, ui.selectedMember)
                    infoCards = ui.cards
                    moduleKpiView.text = if (ui.selectedMember == null) {
                        "成员 ${ui.summary.total}  /  总势力 ${ui.summary.totalPower}  /  总武勋 ${ui.summary.totalWuxun}  /  周贡献 ${ui.summary.totalWeekContribute}"
                    } else {
                        when (teamUsersSubPage) {
                            TeamUsersSubPage.OVERVIEW -> "成员概览  /  ${ui.selectedMember.name}  /  分组 ${ui.selectedMember.groupName.ifBlank { "未分组" }}"
                            TeamUsersSubPage.TEAMS -> "成员队伍 ${ui.memberTeams.size}  /  ${ui.selectedMember.name}"
                            TeamUsersSubPage.HOME -> "成员 ${ui.summary.total}"
                        }
                    }
                    contentView.text = if (ui.selectedMember == null) {
                        "同盟成员\n可按分组筛选，并通过分组标题展开/收起成员列表；点击成员进入成员子页。"
                    } else {
                        "${ui.selectedMember.name}\n当前位于成员子页，可切换概览和队伍信息。"
                    }
                    battleListView.adapter = InfoCardAdapter(ui.cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("同盟成员：${ui.filteredUsers.size} 人")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载同盟成员失败")
                }
            }
        }
    }

    private fun loadMapCells() {
        setStatus("加载地图格子中...")
        setModuleHeader("城池地图", "展示地图统计、类型分布摘要和地图格子主列表。")
        battleFilterPanel.visibility = View.GONE
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-map-cells") {
            val result = runCatching {
                val stats = LocalStzbRepository.loadMapStats()
                val cells = LocalStzbRepository.loadMapCells(limit = 60)
                buildList {
                    addAll(stats.typeStats.map { it.toInfoCard() })
                    addAll(cells.mapIndexed { idx, row -> row.toInfoCard(idx + 1) })
                } to (stats.totalCells to stats.namedCities)
            }
            runOnUiThread {
                result.onSuccess { (cards, summary) ->
                    infoCards = cards
                    moduleKpiView.text = "总格子 ${summary.first}  /  命名地块 ${summary.second}  /  卡片 ${cards.size}"
                    contentView.text = "城池地图\n当前先做统计与主列表，后续继续补更强的态势展示。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("地图格子：${cards.size} 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载地图格子失败")
                }
            }
        }
    }

    private fun loadPlayerProfile() {
        setStatus("加载玩家资料中...")
        setModuleHeader("玩家战绩", "展示当前角色摘要和 510 玩家统计主列表。")
        battleFilterPanel.visibility = View.GONE
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-player-profile") {
            val result = runCatching {
                val self = LocalStzbRepository.loadPlayerSelf()
                val stats = LocalStzbRepository.loadPlayerStats(50)
                buildList {
                    self?.let { add(it.toInfoCard()) }
                    addAll(stats.mapIndexed { idx, row -> row.toInfoCard(idx + 1) })
                }
            }
            runOnUiThread {
                result.onSuccess { cards ->
                    infoCards = cards
                    moduleKpiView.text = "玩家战绩卡 ${cards.size}"
                    contentView.text = "玩家战绩\n先显示当前角色，再显示玩家统计。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("玩家战绩：${cards.size} 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载玩家资料失败")
                }
            }
        }
    }

    private fun loadAnnouncementsAndHeroes() {
        setStatus("加载公告和武将解锁中...")
        setModuleHeader("游戏公告", "展示 780 公告和 671 武将解锁记录，按信息流卡片输出。")
        battleFilterPanel.visibility = View.GONE
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-announcements") {
            val result = runCatching {
                val announcements = LocalStzbRepository.loadAnnouncements(24)
                val unlocks = LocalStzbRepository.loadHeroUnlocks(24)
                buildList {
                    addAll(announcements.map { it.toInfoCard() })
                    addAll(unlocks.map { it.toInfoCard() })
                }
            }
            runOnUiThread {
                result.onSuccess { cards ->
                    infoCards = cards
                    moduleKpiView.text = "公告/解锁卡 ${cards.size}"
                    contentView.text = "游戏公告\n按公告与解锁事件混排显示。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("公告和解锁：${cards.size} 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载公告失败")
                }
            }
        }
    }

    private fun loadZonePlayers() {
        setStatus("加载战区玩家中...")
        setModuleHeader("战区玩家", "展示 6243 战区玩家统计、联盟势力分布摘要和玩家主列表。")
        battleFilterPanel.visibility = View.GONE
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-zone-players") {
            val result = runCatching {
                val stats = LocalStzbRepository.loadZonePlayerStats()
                buildList {
                    addAll(stats.topUnions.map { it.toInfoCard() })
                    addAll(stats.topPlayers.mapIndexed { idx, row -> row.toInfoCard(idx + 1) })
                } to stats.total
            }
            runOnUiThread {
                result.onSuccess { (cards, total) ->
                    infoCards = cards
                    moduleKpiView.text = "战区玩家 $total  /  卡片 ${cards.size}"
                    contentView.text = "战区玩家\n先显示联盟分布，再显示玩家列表。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("战区玩家：${cards.size} 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载战区玩家失败")
                }
            }
        }
    }

    private fun loadAllPlayerTeams() {
        setStatus("加载全服玩家队伍中...")
        setModuleHeader("全服玩家队伍", "基于完整战报聚合玩家、同盟、攻守方、队伍武将和队伍胜率；支持玩家、同盟、攻守方、武将/战法筛选。")
        showPlayerTeamFilters()
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-all-player-teams") {
            val result = runCatching {
                val teams = LocalStzbRepository.loadPlayerBattleTeams(0).filterByPlayerTeamInputs()
                teams.mapIndexed { idx, row -> row.toInfoCard(idx + 1, "玩家队伍") } to teams.size
            }
            runOnUiThread {
                result.onSuccess { (cards, total) ->
                    infoCards = cards
                    val avgWinRate = cards.mapNotNull { it.metricValue }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
                    moduleKpiView.text = "匹配队伍 $total  /  展示 ${cards.size}  /  平均胜率 ${"%.1f".format(avgWinRate)}%"
                    contentView.text = "全服玩家队伍\n可按玩家、同盟、攻守方、武将/战法筛选；点击卡片查看队伍详情。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("全服玩家队伍：匹配 $total 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载全服玩家队伍失败")
                }
            }
        }
    }

    private fun List<LocalPlayerBattleTeam>.filterByPlayerTeamInputs(): List<LocalPlayerBattleTeam> {
        val player = filterPlayerInput.text?.toString().orEmpty().trim()
        val union = filterUnionInput.text?.toString().orEmpty().trim()
        val side = filterTeamSideInput.text?.toString().orEmpty().trim().lowercase(Locale.CHINA)
        val keyword = filterTeamKeywordInput.text?.toString().orEmpty().trim().lowercase(Locale.CHINA)
        return filter { row ->
            val sideText = when (row.side) {
                "atk" -> "攻方 主攻 攻 atk"
                "def" -> "守方 防守 守 def"
                else -> row.side
            }
            val keywordText = listOf(row.heroes, row.heroIds, row.skills).joinToString(" ").lowercase(Locale.CHINA)
            (player.isBlank() || row.player.contains(player, ignoreCase = true)) &&
                (union.isBlank() || row.unionName.contains(union, ignoreCase = true)) &&
                (side.isBlank() || sideText.lowercase(Locale.CHINA).contains(side)) &&
                (keyword.isBlank() || keywordText.contains(keyword))
        }
    }

    private fun loadHeroStats() {
        setStatus("加载阵容统计中...")
        setModuleHeader("武将阵容", "展示武将频率、武将使用胜率、组合胜率和玩家队伍。")
        battleFilterPanel.visibility = View.GONE
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-hero-stats") {
            val result = runCatching {
                val freq = LocalStzbRepository.loadHeroFrequencies(16)
                val usage = LocalStzbRepository.loadHeroUsage("atk", 16)
                val combos = LocalStzbRepository.loadHeroComboWinRates(minCount = 2, limit = 16)
                val teams = LocalStzbRepository.loadPlayerBattleTeams(16)
                buildList {
                    addAll(freq.map { it.toInfoCard() })
                    addAll(usage.map { it.toInfoCard() })
                    addAll(combos.map { it.toInfoCard() })
                    addAll(teams.mapIndexed { idx, row -> row.toInfoCard(idx + 1, "阵容队伍") })
                }
            }
            runOnUiThread {
                result.onSuccess { cards ->
                    infoCards = cards
                    moduleKpiView.text = "阵容卡 ${cards.size}"
                    contentView.text = "武将阵容\n频率、胜率、组合和队伍按统一卡片流展示。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("武将阵容：${cards.size} 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载阵容统计失败")
                }
            }
        }
    }

    private fun loadGroupedWuxun() {
        setStatus("加载分组武勋中...")
        setModuleHeader("分组武勋", "展示分组团报和成员团报，承接网页端分组武勋页。")
        battleFilterPanel.visibility = View.GONE
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-grouped-wuxun") {
            val result = runCatching {
                val groups = LocalStzbRepository.loadTeamReport(dim = "group", limit = 0)
                val members = LocalStzbRepository.loadTeamReport(dim = "player", limit = 0)
                buildList {
                    addAll(groups.mapIndexed { index, row -> row.toGroupReportCard(index + 1) })
                    addAll(members.mapIndexed { index, row -> row.toPlayerReportCard(index + 1) })
                }
            }
            runOnUiThread {
                result.onSuccess { cards ->
                    infoCards = cards
                    moduleKpiView.text = "分组武勋卡 ${cards.size}"
                    contentView.text = "分组武勋\n先显示分组，再显示成员。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("分组武勋：${cards.size} 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载分组武勋失败")
                }
            }
        }
    }

    private fun loadAllianceMemberTeams() {
        setStatus("加载同盟成员队伍中...")
        setModuleHeader("同盟成员队伍", "以同盟成员为基准过滤队伍组合，展示成员常用队伍。")
        battleFilterPanel.visibility = View.GONE
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-alliance-member-teams") {
            val result = runCatching {
                val memberNames = LocalStzbRepository.loadTeamUsers(limit = 0)
                    .map { it.name }
                    .filter { it.isNotBlank() }
                    .toSet()
                LocalStzbRepository.loadPlayerBattleTeams(0)
                    .filter { memberNames.isEmpty() || it.player in memberNames }
                    .mapIndexed { idx, row -> row.toInfoCard(idx + 1, "成员队伍") }
            }
            runOnUiThread {
                result.onSuccess { cards ->
                    infoCards = cards
                    moduleKpiView.text = "成员队伍卡 ${cards.size}"
                    contentView.text = "同盟成员队伍\n当前展示成员过滤后的主队伍列表。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("同盟成员队伍：${cards.size} 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载同盟成员队伍失败")
                }
            }
        }
    }

    private fun loadTeamReport() {
        setStatus("加载团数据中...")
        setModuleHeader("团数据", "对齐网页端 /api/team_report：支持统计周期、按分组/按成员维度切换、按分组筛选和导出。")
        battleFilterPanel.visibility = View.GONE
        syncTeamReportSubTabs(emptyList())
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-team-report") {
            val result = runCatching {
                val groupRows = LocalStzbRepository.loadTeamReport(dim = "group", period = teamReportPeriod, limit = 0)
                val rows = LocalStzbRepository.loadTeamReport(dim = teamReportDim, period = teamReportPeriod, group = teamReportGroup, limit = 0)
                val totalPlayers = groupRows.sumOf { it.members }
                val totalBattles = groupRows.sumOf { it.battles }
                val totalWins = groupRows.sumOf { it.wins }
                val totalDraws = groupRows.sumOf { it.draws }
                val totalCity = groupRows.sumOf { it.cityBattles }
                val totalGongxun = groupRows.sumOf { it.totalGongxun }
                val winRate = if (totalBattles > 0) (totalWins + totalDraws * 0.5) * 100.0 / totalBattles else 0.0
                val cards = buildTeamReportCards(rows, totalBattles, winRate, totalPlayers, totalDraws, totalCity, totalGongxun)
                TeamReportUi(
                    cards = cards,
                    groupCount = groupRows.size,
                    playerCount = rows.size,
                    totalPlayers = totalPlayers,
                    totalBattles = totalBattles,
                    totalCity = totalCity,
                    winRate = winRate,
                    groupOptions = groupRows.map { it.name.ifBlank { "未分组" } },
                    dim = teamReportDim,
                    period = teamReportPeriod,
                    selectedGroup = teamReportGroup,
                )
            }
            runOnUiThread {
                result.onSuccess { ui ->
                    syncTeamReportSubTabs(ui.groupOptions)
                    infoCards = ui.cards
                    moduleKpiView.text = "总战报 ${ui.totalBattles}  /  胜率 ${"%.1f".format(ui.winRate)}%  /  参战人数 ${ui.totalPlayers}  /  攻城 ${ui.totalCity}"
                    contentView.text = "团数据\n周期 ${teamReportPeriodLabel(ui.period)}，当前为${if (ui.dim == "group") "按分组" else "按成员"}${if (ui.selectedGroup.isBlank()) "" else " · ${ui.selectedGroup}"}。"
                    battleListView.adapter = InfoCardAdapter(ui.cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("团数据：${ui.cards.size} 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载团报告失败")
                }
            }
        }
    }

    private fun loadBattlefieldMonitor() {
        setStatus("实时队伍监控加载中...")
        setModuleHeader("实时队伍监控", "复刻网页端 5028 / 000013a4 实时监控流：队伍、主体、marker、状态、阵容战绩。")
        showBattleMonitorFilters()
        battleMonitorSearchQuery = buildBattleMonitorSearchQuery()
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        contentView.text = ""
        thread(name = "stzb-battle-monitor") {
            val result = runCatching {
                val snapshots = LocalBattleMonitorStore.history().sortedByDescending { it.capturedAt }
                val latestSnapshot = snapshots.firstOrNull() ?: LocalBattleMonitorStore.latest()
                val fallbackMoves = if (snapshots.isEmpty()) LocalStzbRepository.loadMonitorMoves(0) else emptyList()
                val moves = latestSnapshot?.moves ?: fallbackMoves
                val filteredSnapshots = filterBattleMonitorSnapshots(snapshots)
                val filteredFallback = if (snapshots.isEmpty()) filterBattleMonitorMoves(fallbackMoves) else emptyList()
                BattleMonitorUi(
                    cards = buildList {
                        add(
                            InfoCardItem(
                                title = "5028 实时监控总览",
                                badgeText = "5028",
                                badgeColor = 0xFF2563EB.toInt(),
                                metaText = "最新队伍 ${moves.size}  ·  历史快照 ${snapshots.size.coerceAtLeast(if (latestSnapshot == null) 0 else 1)}  ·  marker ${latestSnapshot?.marker ?: 0}",
                                extraText = "主体 ${latestSnapshot?.subjects?.size ?: 0}  ·  状态 ${battleMonitorStateText(latestSnapshot)}  ·  raw ${latestSnapshot?.rawLength ?: 0}",
                                detailText = """
                                    最新快照队伍：${moves.size}
                                    历史快照：${snapshots.size}
                                    报文队伍数：${latestSnapshot?.teamIds?.size ?: 0}
                                    报文标记：${latestSnapshot?.marker ?: 0}
                                    主体数：${latestSnapshot?.subjects?.size ?: 0}
                                    状态块：${latestSnapshot?.mapStates?.size ?: 0}
                                    状态：${battleMonitorStateText(latestSnapshot)}
                                    更新时间：${latestSnapshot?.let { formatTime(it.capturedAt / 1000) } ?: "-"}
                                    来源：${latestSnapshot?.sourceLabel?.ifBlank { "5028" } ?: "5028"}
                                    plain：${latestSnapshot?.plainText?.take(180)?.ifBlank { "-" } ?: "-"}
                                    raw 长度：${latestSnapshot?.rawLength ?: 0}
                                    当前页面只展示 5028 / 000013a4 实时队伍流；5026 / 13A2 已拆到独立页面。
                                """.trimIndent(),
                                skillTags = listOf("5028", "13a4", "实时队伍", "marker ${latestSnapshot?.marker ?: 0}"),
                            )
                        )
                        if (filteredSnapshots.isNotEmpty()) {
                            filteredSnapshots.forEachIndexed { snapshotIndex, snapshot ->
                                add(snapshot.toBattleMonitorSnapshotCard(snapshotIndex + 1))
                                addAll(
                                    snapshot.moves
                                        .sortedWith(compareByDescending<LocalTeamMove> { it.arriveTime }.thenByDescending { it.startTime }.thenByDescending { it.teamId })
                                        .map {
                                        it.toBattleMonitorInfoCard(
                                            LocalStzbRepository.load13A2TeamInsight(
                                                teamId = it.teamId,
                                                ownerName = it.ownerName,
                                                relatedWids = listOf(it.fromWid, it.currentWid, it.toWid),
                                            )
                                        )
                                    }
                                )
                            }
                        } else {
                            addAll(
                                filteredFallback
                                    .sortedWith(compareByDescending<LocalTeamMove> { it.arriveTime }.thenByDescending { it.startTime }.thenByDescending { it.teamId })
                                    .map {
                                    it.toBattleMonitorInfoCard(
                                        LocalStzbRepository.load13A2TeamInsight(
                                            teamId = it.teamId,
                                            ownerName = it.ownerName,
                                            relatedWids = listOf(it.fromWid, it.currentWid, it.toWid),
                                        )
                                    )
                                }
                            )
                            if (filteredFallback.isEmpty()) {
                                add(
                                    InfoCardItem(
                                        title = if (battleMonitorSearchQuery.isBlank()) "暂无监控历史" else "没有匹配到搜索结果",
                                        badgeText = "空状态",
                                        badgeColor = 0xFF64748B.toInt(),
                                        metaText = if (battleMonitorSearchQuery.isBlank()) "当前还没有 5028 实时快照。" else "当前搜索词：$battleMonitorSearchQuery",
                                        extraText = "网页端这里会显示流式历史；继续抓包或清空搜索后再试。",
                                        skillTags = listOf("5028", "空状态"),
                                    )
                                )
                            }
                        }
                    },
                    summary = "最新队伍 ${moves.size}  /  快照 ${filteredSnapshots.size.coerceAtLeast(if (latestSnapshot == null && filteredFallback.isEmpty()) 0 else 1)}  /  marker ${latestSnapshot?.marker ?: 0}  /  主体 ${latestSnapshot?.subjects?.size ?: 0}",
                )
            }
            runOnUiThread {
                result.onSuccess { ui ->
                    infoCards = ui.cards
                    moduleKpiView.text = ui.summary
                    contentView.text = "实时队伍监控\n自动每 5 秒刷新一次；支持按队伍ID、玩家、同盟、坐标和 wid 搜索过滤。"
                    battleListView.adapter = InfoCardAdapter(ui.cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("实时队伍监控：${ui.cards.size} 条")
                    ensureBattleMonitorAutoRefresh()
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("实时队伍监控加载失败")
                }
            }
        }
    }

    private fun LocalBattleMonitorSnapshot.toBattleMonitorSnapshotCard(index: Int): InfoCardItem {
        return InfoCardItem(
            title = "$index. 5028 快照 ${formatTime(capturedAt)}",
            badgeText = if (index == 1) "最新" else "历史",
            badgeColor = if (index == 1) 0xFF2563EB.toInt() else 0xFF64748B.toInt(),
            metaText = "队伍 ${moves.size}  ·  主体 ${subjects.size}  ·  marker $marker",
            extraText = "${battleMonitorStateText(this)}  ·  来源 ${sourceLabel.ifBlank { "5028" }}  ·  raw $rawLength",
            detailText = """
                快照时间：${formatTime(capturedAt)}
                队伍数：${moves.size}
                主体数：${subjects.size}
                状态块：${mapStates.size}
                marker：$marker
                状态：${battleMonitorStateText(this)}
                来源：${sourceLabel.ifBlank { "5028" }}
                plain 文本：${plainText.ifBlank { "-" }}
                raw 长度：$rawLength
            """.trimIndent(),
            skillTags = listOf(if (index == 1) "最新快照" else "历史快照", "队伍 ${moves.size}", "marker $marker", battleMonitorStateText(this)),
        )
    }

    private fun LocalTeamMove.toBattleMonitorInfoCard(insight: Local13A2TeamInsight): InfoCardItem {
        val targetText = toXy.ifBlank { formatCityId(toWid) }
        val stats = insight.stats
        val lineup = insight.lineup
        val heroNames = lineup.heroes.map { it.heroName.ifBlank { HeroNameResolver.nameOf(it.heroId) } }
        val heroIconIds = lineup.heroes.map { HeroNameResolver.iconIdOf(it.heroId) }
        val heroText = lineup.heroes.joinToString("\n") { hero ->
            val skills = hero.skills.joinToString(" / ") { skill -> "${skill.skillName} Lv.${skill.level}" }.ifBlank { "无战法" }
            "${hero.pos}. ${hero.heroName} Lv.${hero.level} 进阶${hero.star} · $skills"
        }.ifBlank { "未匹配到武将战法" }
        val recentText = insight.recentBattles.joinToString("\n") {
            "${it.resultText} ${it.opponentName.ifBlank { "未知对手" }} · ${it.opponentHeroNames.joinToString("/")}"
        }.ifBlank { "暂无最近战绩" }
        val favoredText = insight.favored.joinToString("\n") {
            "${it.opponentHeroNames.joinToString("/")} · ${it.wins}胜/${it.total}战 · ${"%.1f".format(it.winRate)}%"
        }.ifBlank { "暂无明显克制阵容" }
        val counteredText = insight.countered.joinToString("\n") {
            "${it.opponentHeroNames.joinToString("/")} · ${it.loses}负/${it.total}战 · ${"%.1f".format(it.winRate)}%"
        }.ifBlank { "暂无明显被克制阵容" }
        val progress = if (startTime > 0L && arriveTime > startTime) {
            val now = System.currentTimeMillis() / 1000
            (((now - startTime) * 100) / (arriveTime - startTime)).toInt().coerceIn(0, 100)
        } else {
            null
        }
        return InfoCardItem(
            title = ownerName.ifBlank { "队伍 $teamId" },
            badgeText = "5028 行军",
            badgeColor = 0xFF2563EB.toInt(),
            metaText = "${fromXy.ifBlank { formatCityId(fromWid) }}  →  $targetText",
            extraText = "当前 ${currentXy.ifBlank { formatCityId(currentWid) }}  ·  到达 ${formatTime(arriveTime)}  ·  ${stats.battles}战 ${"%.1f".format(stats.winRate)}%",
            detailText = """
                队伍ID：$teamId
                行军类型：$moveType
                玩家：${ownerName.ifBlank { "-" }}
                UID：$ownerUid
                同盟：${ownerUnion.ifBlank { "-" }}
                出发：${fromXy.ifBlank { formatCityId(fromWid) }} / wid $fromWid
                当前：${currentXy.ifBlank { formatCityId(currentWid) }} / wid $currentWid
                目标：${toXy.ifBlank { formatCityId(toWid) }} / wid $toWid
                开始：${formatTime(startTime)}
                到达：${formatTime(arriveTime)}
                速度：$speed

                队伍战绩：
                ${stats.battles}战 · ${stats.wins}胜 / ${stats.draws}平 / ${stats.loses}负 · 胜率 ${"%.1f".format(stats.winRate)}%

                武将与战法：
                $heroText

                最近几场战绩：
                $recentText

                更克制的阵容：
                $favoredText

                更被克制的阵容：
                $counteredText
            """.trimIndent(),
            heroNames = heroNames,
            heroIconIds = heroIconIds,
            skillTags = buildList {
                add("team $teamId")
                add("目标 ${toXy.ifBlank { "-" }}")
                add("速度 $speed")
                add("${stats.wins}胜/${stats.draws}平/${stats.loses}负")
                addAll(lineup.heroes.flatMap { hero -> hero.skills.map { skill -> skill.skillName.ifBlank { "战法${skill.skillId}" } } }.take(6))
            },
            progressValue = progress,
            progressColor = 0xFF2563EB.toInt(),
        )
    }

    private fun LocalMapCell.toBattleMapInfoCard(rank: Int, activeTarget: Boolean): InfoCardItem {
        val displayName = cityName.ifBlank { ownerName.ifBlank { typeName.ifBlank { "地块 $wid" } } }
        return InfoCardItem(
            title = "$rank. $displayName",
            badgeText = if (activeTarget) "5026 目标" else "5026 地图",
            badgeColor = if (activeTarget) 0xFFDC2626.toInt() else 0xFF059669.toInt(),
            metaText = "坐标 (${x},${y})  ·  wid $wid  ·  ${typeName.ifBlank { "type$cellType" }}",
            extraText = "building $buildingId  ·  parent $parentWid  ·  owner ${ownerName.ifBlank { "-" }}",
            detailText = """
                名称：$displayName
                坐标：($x,$y)
                wid：$wid
                类型：${typeName.ifBlank { "type$cellType" }}($cellType)
                建筑/配置：$buildingId
                父级地块：$parentWid
                归属：${ownerName.ifBlank { "-" }}
                来源消息：${sourceMsgId.ifBlank { "5026" }}
            """.trimIndent(),
            skillTags = listOf(if (activeTarget) "行军目标" else "地图格子", "type $cellType", "wid $wid"),
            progressValue = if (activeTarget) 100 else null,
            progressColor = if (activeTarget) 0xFFDC2626.toInt() else 0xFF059669.toInt(),
        )
    }

    private fun LocalTeamMove.toMonitorCard(): MonitorCardItem {
        return MonitorCardItem(
            title = ownerName.ifBlank { "未命名行军队伍" },
            badgeText = "5028 监控",
            badgeColor = 0xFF2563EB.toInt(),
            metaText = "${fromXy.ifBlank { "-" }}  →  ${toXy.ifBlank { "-" }}  ·  team ${teamId}",
            extraText = "当前 ${currentXy.ifBlank { "-" }}  ·  到达 ${formatTime(arriveTime)}  ·  同盟 ${ownerUnion.ifBlank { "-" }}",
            sourceText = "来源：battle_monitor_moves",
        )
    }

    private inner class MonitorCardAdapter(
        private val items: List<MonitorCardItem>,
    ) : BaseAdapter() {
        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_monitor_card, parent, false)
            val item = items[position]
            view.findViewById<TextView>(R.id.monitorTitleView).text = item.title
            view.findViewById<TextView>(R.id.monitorMetaView).text = item.metaText
            view.findViewById<TextView>(R.id.monitorExtraView).text = item.extraText
            view.findViewById<TextView>(R.id.monitorSourceView).text = item.sourceText
            val badge = view.findViewById<TextView>(R.id.monitorTypeBadgeView)
            badge.text = item.badgeText
            badge.setBackgroundColor(item.badgeColor)
            return view
        }
    }

    private fun LocalRankingRow.toInfoCard(rank: Int, badgeText: String, badgeColor: Int): InfoCardItem {
        return InfoCardItem(
            title = "$rank. ${name.ifBlank { "-" }}",
            badgeText = badgeText,
            badgeColor = badgeColor,
            metaText = if (groupName.isBlank()) "数值 ${value}" else "$groupName  ·  数值 ${value}",
            extraText = "${battles} 战  ·  胜率 ${"%.1f".format(winRate)}%",
        )
    }

    private fun LocalUnionRank.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = "${rank}. ${name.ifBlank { "未命名同盟" }}",
            badgeText = "700 同盟",
            badgeColor = 0xFFDC2626.toInt(),
            metaText = "Lv.${level}  ·  势力 ${power}",
            extraText = "成员 ${totalMember}  ·  城池 ${totalNpcCity}  ·  region ${region}",
        )
    }

    private fun LocalPlayerPowerRank.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = "${rank}. ${name.ifBlank { roleId }}",
            badgeText = "700 玩家",
            badgeColor = 0xFF059669.toInt(),
            metaText = "势力 ${power}  ·  土地 ${landCount}",
            extraText = "要塞 ${fortCount}  ·  分城 ${branchCityCount}  ·  region ${region}",
        )
    }

    private fun LocalTeamGroupStat.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = name.ifBlank { "未分组" },
            badgeText = "分组",
            badgeColor = 0xFF1D4ED8.toInt(),
            metaText = "成员 ${members}  ·  势力 ${totalPower}",
            extraText = "武勋 ${totalWuxun}  ·  周贡献 ${totalWeekContribute}",
        )
    }

    private fun LocalTeamGroupStat.toExpandableGroupCard(expanded: Boolean, visibleMembers: Int): InfoCardItem {
        val groupLabel = name.ifBlank { "未分组" }
        return InfoCardItem(
            title = if (expanded) "▼ $groupLabel" else "▶ $groupLabel",
            badgeText = if (expanded) "已展开" else "已折叠",
            badgeColor = 0xFF1D4ED8.toInt(),
            metaText = "成员 $members  ·  当前展示 $visibleMembers  ·  势力 $totalPower",
            extraText = "武勋 $totalWuxun  ·  周贡献 $totalWeekContribute  ·  点击标题展开/收起",
            skillTags = listOf(groupLabel, "成员 $members"),
            actionKey = ACTION_TOGGLE_TEAM_USER_GROUP,
            actionArg = groupLabel,
        )
    }

    private fun LocalTeamUser.toInfoCard(rank: Int): InfoCardItem {
        return InfoCardItem(
            title = "$rank. ${name.ifBlank { uid.toString() }}",
            badgeText = groupName.ifBlank { "未分组" },
            badgeColor = 0xFFF59E0B.toInt(),
            metaText = "势力 ${power}  ·  武勋 ${wuxun}",
            extraText = "周贡献 ${contributeWeek}  ·  总贡献 ${contributeTotal}  ·  wid ${wid}",
        )
    }

    private fun LocalTeamUser.toMemberCard(rank: Int, maxPower: Int, maxWuxun: Int, maxWeekContribute: Int, clickable: Boolean = false): InfoCardItem {
        val posText = positionName(pos)
        val groupText = groupName.ifBlank { "未分组" }
        val progress = when {
            power > 0 -> power * 100 / maxPower
            wuxun > 0 -> wuxun * 100 / maxWuxun
            else -> contributeWeek * 100 / maxWeekContribute
        }.coerceIn(0, 100)
        return InfoCardItem(
            title = "$rank. ${name.ifBlank { uid.toString() }}",
            badgeText = groupText,
            badgeColor = positionColor(pos),
            metaText = "$posText  ·  势力 $power  ·  武勋 $wuxun",
            extraText = "周贡献 $contributeWeek  ·  总贡献 $contributeTotal  ·  wid $wid",
            detailText = """
                玩家：${name.ifBlank { "-" }}
                UID：$uid
                职位：$posText
                分组：$groupText
                势力值：$power
                武勋：$wuxun
                本周贡献：$contributeWeek
                总贡献：$contributeTotal
                wid：$wid
                武将配置：$heroConfigId
                队伍ID：$teamId
                战法字段：${formatSkillIds(heroSkills).ifBlank { "-" }}
                加入时间：${formatTime(joinTime)}
                来源消息：${sourceMsgId.ifBlank { "-" }}
            """.trimIndent(),
            progressValue = progress,
            progressColor = positionColor(pos),
            skillTags = listOf(posText, groupText, "势力 $power", "武勋 $wuxun"),
            actionKey = if (clickable) ACTION_OPEN_TEAM_USER else "",
            actionId = if (clickable) uid else 0L,
        )
    }

    private fun positionName(pos: Int): String {
        return when (pos) {
            1 -> "盟主"
            2 -> "副盟主"
            3 -> "长老"
            4 -> "成员"
            5 -> "见习"
            else -> "职位$pos"
        }
    }

    private fun positionColor(pos: Int): Int {
        return when (pos) {
            1 -> 0xFFDC2626.toInt()
            2 -> 0xFFF59E0B.toInt()
            3 -> 0xFF2563EB.toInt()
            else -> 0xFF64748B.toInt()
        }
    }

    private fun formatSkillIds(raw: String): String {
        return raw.split(',', ';', '+', '|', '/', '，', '；')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0L }
            .distinct()
            .joinToString(" / ") { SkillNameResolver.nameOf(it) }
    }

    private fun LocalZoneUnionStat.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = unionName.ifBlank { unionId.toString() },
            badgeText = "战区同盟",
            badgeColor = 0xFF1D4ED8.toInt(),
            metaText = "人数 ${memberCount}  ·  总势力 ${totalPower}",
            extraText = "均势力 ${"%.0f".format(avgPower)}  ·  最高 ${maxPower}",
        )
    }

    private fun LocalZonePlayer.toInfoCard(rank: Int): InfoCardItem {
        return InfoCardItem(
            title = "$rank. ${name.ifBlank { uid.toString() }}",
            badgeText = "战区玩家",
            badgeColor = 0xFF059669.toInt(),
            metaText = "势力 ${power}  ·  wid ${wid}  ·  union ${unionId}",
            extraText = "role ${roleId.ifBlank { "-" }}  ·  pos ${posType}  ·  活跃 ${formatTime(lastActive)}",
        )
    }

    private fun LocalTeamReportRow.toGroupReportCard(rank: Int): InfoCardItem {
        return InfoCardItem(
            title = "$rank. ${name.ifBlank { "未命名分组" }}",
            badgeText = "分组",
            badgeColor = 0xFF7C3AED.toInt(),
            metaText = "人数 ${members}  ·  战报 ${battles}  ·  胜 ${wins}  ·  败 ${loses}  ·  平 ${draws}",
            extraText = "胜率 ${"%.1f".format(winRate)}%  ·  攻城 ${cityBattles}  ·  总功勋 ${totalGongxun}",
            detailText = """
                分组：${name.ifBlank { "未分组" }}
                成员数：$members
                战报：$battles
                胜：$wins
                负：$loses
                平：$draws
                胜率：${"%.1f".format(winRate)}%
                攻城场次：$cityBattles
                攻城胜场：$cityWins
                总功勋：$totalGongxun
                平均武勋：${"%.1f".format(avgGongxun)}
                平均势力值：${"%.1f".format(avgPower)}
            """.trimIndent(),
            skillTags = listOf("胜率 ${"%.1f".format(winRate)}%", "平均武勋 ${"%.0f".format(avgGongxun)}", "平均势力 ${"%.0f".format(avgPower)}", "攻城 $cityBattles"),
            progressValue = winRate.toInt().coerceIn(0, 100),
            progressColor = if (winRate >= 60.0) 0xFF41664D.toInt() else if (winRate >= 40.0) 0xFF8F6A2A.toInt() else 0xFF9A4D41.toInt(),
        )
    }

    private fun LocalTeamReportRow.toPlayerReportCard(rank: Int): InfoCardItem {
        return InfoCardItem(
            title = "$rank. ${name.ifBlank { "未知成员" }}",
            badgeText = groupName.ifBlank { "未分组" },
            badgeColor = 0xFFF59E0B.toInt(),
            metaText = "战报 ${battles}  ·  胜 ${wins}  ·  败 ${loses}  ·  平 ${draws}",
            extraText = "胜率 ${"%.1f".format(winRate)}%  ·  攻城 ${cityBattles}  ·  功勋 ${totalGongxun}  ·  势力 ${power}",
            detailText = """
                成员：${name.ifBlank { "未知成员" }}
                分组：${groupName.ifBlank { "未分组" }}
                战报：$battles
                胜：$wins
                负：$loses
                平：$draws
                胜率：${"%.1f".format(winRate)}%
                攻城场次：$cityBattles
                攻城胜场：$cityWins
                功勋：$totalGongxun
                势力值：$power
            """.trimIndent(),
            skillTags = listOf(groupName.ifBlank { "未分组" }, "胜率 ${"%.1f".format(winRate)}%", "功勋 $totalGongxun", "攻城 $cityBattles"),
            progressValue = winRate.toInt().coerceIn(0, 100),
            progressColor = if (winRate >= 60.0) 0xFF41664D.toInt() else if (winRate >= 40.0) 0xFF8F6A2A.toInt() else 0xFF9A4D41.toInt(),
        )
    }

    private fun LocalMapTypeStat.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = typeName.ifBlank { "type$cellType" },
            badgeText = "地图类型",
            badgeColor = 0xFF2563EB.toInt(),
            metaText = "cellType $cellType",
            extraText = "数量 $count",
        )
    }

    private fun LocalMapCell.toInfoCard(rank: Int): InfoCardItem {
        return InfoCardItem(
            title = "$rank. ${cityName.ifBlank { ownerName.ifBlank { typeName } }}",
            badgeText = "地图格子",
            badgeColor = 0xFF059669.toInt(),
            metaText = "wid ${wid}  ·  (${x},${y})  ·  type ${cellType}",
            extraText = "building ${buildingId}  ·  owner ${ownerName.ifBlank { "-" }}",
        )
    }

    private fun LocalMapCell.to13a2Card(rank: Int): InfoCardItem {
        val displayName = cityName.ifBlank { ownerName.ifBlank { typeName.ifBlank { "地块 $wid" } } }
        return InfoCardItem(
            title = "$rank. $displayName",
            badgeText = "13a2索引",
            badgeColor = 0xFF2563EB.toInt(),
            metaText = "wid $wid  ·  坐标 ($x,$y)  ·  ${typeName.ifBlank { "type$cellType" }}",
            extraText = "building $buildingId  ·  parent $parentWid  ·  owner ${ownerName.ifBlank { "-" }}",
            detailText = """
                名称：$displayName
                wid：$wid
                坐标：($x,$y)
                类型：${typeName.ifBlank { "type$cellType" }}($cellType)
                建筑/配置：$buildingId
                父级地块：$parentWid
                归属：${ownerName.ifBlank { "-" }}
                来源消息：${sourceMsgId.ifBlank { "5026" }}
            """.trimIndent(),
        )
    }

    private fun LocalPlayerSelf.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = name.ifBlank { "当前角色" },
            badgeText = "当前角色",
            badgeColor = 0xFF1D4ED8.toInt(),
            metaText = "兵力 ${forceCurrent}/${force}  ·  速度 $speed",
            extraText = "粮 $food  ·  木 $wood  ·  行军上限 $marchMax",
        )
    }

    private fun LocalPlayerStats.toInfoCard(rank: Int): InfoCardItem {
        return InfoCardItem(
            title = "$rank. ${userName.ifBlank { userId.toString() }}",
            badgeText = "510 统计",
            badgeColor = 0xFFF59E0B.toInt(),
            metaText = "城 $cityCount  ·  地 $landCount  ·  武勋 $wuxunTotal",
            extraText = "势力峰值 $powerMax  ·  击杀 $killEnemyCount  ·  翻地 $grabLandCount",
        )
    }

    private fun LocalAnnouncement.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = title.ifBlank { "公告$annId" },
            badgeText = "公告",
            badgeColor = 0xFFDC2626.toInt(),
            metaText = "类型 $annType  ·  ${formatTime(pubTime)}",
            extraText = content.take(80).ifBlank { "暂无正文" },
        )
    }

    private fun LocalHeroUnlock.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = heroName.ifBlank { "武将$heroId" },
            badgeText = "武将解锁",
            badgeColor = 0xFF7C3AED.toInt(),
            metaText = "heroId $heroId",
            extraText = "解锁时间 ${formatTime(unlockTime)}",
        )
    }

    private fun LocalStateRegionStat.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = "region ${region} / area ${area}",
            badgeText = "州区统计",
            badgeColor = 0xFF2563EB.toInt(),
            metaText = "玩家 ${playerCount}  ·  总势力 ${totalPower}",
            extraText = "均势力 ${"%.0f".format(avgPower)}  ·  最高 ${maxPower}  ·  土地 ${totalLands}",
        )
    }

    private fun StateRegionStateRow.toStateRegionCard(rank: Int, metric: String): InfoCardItem {
        val focus = if (metric == "total_power") "势力 ${totalPower}" else "人数 ${playerCount}"
        val progressBase = if (metric == "total_power") (totalPower / 100000L).toInt() else playerCount
        return InfoCardItem(
            title = "$rank. $state",
            badgeText = "州",
            badgeColor = 0xFF2563EB.toInt(),
            metaText = "人数 $playerCount  ·  总势力 $totalPower",
            extraText = "平均势力 ${"%.0f".format(avgPower)}  ·  最高势力 $maxPower  ·  当前重点 $focus",
            detailText = """
                州：$state
                人数：$playerCount
                总势力值：$totalPower
                平均势力值：${"%.1f".format(avgPower)}
                最高势力值：$maxPower
            """.trimIndent(),
            skillTags = listOf("人数 $playerCount", "总势力 $totalPower"),
            progressValue = progressBase.coerceIn(0, 100),
            progressColor = if (metric == "total_power") 0xFF8F6A2A.toInt() else 0xFF2563EB.toInt(),
        )
    }

    private fun StateRegionAllianceRow.toAllianceRegionCard(rank: Int): InfoCardItem {
        return InfoCardItem(
            title = "$rank. $allianceName",
            badgeText = "同盟",
            badgeColor = 0xFF1D4ED8.toInt(),
            metaText = "人数 $playerCount  ·  总势力 $totalPower  ·  平均势力 ${"%.0f".format(avgPower)}",
            extraText = "州分布 $stateSummary",
            detailText = """
                同盟：$allianceName
                人数：$playerCount
                总势力值：$totalPower
                平均势力值：${"%.1f".format(avgPower)}
                最高势力值：$maxPower
                州分布：${stateSummary.ifBlank { "-" }}
                团分布：${groupSummary.ifBlank { "-" }}
            """.trimIndent(),
            skillTags = listOf("人数 $playerCount", "总势力 $totalPower"),
            progressValue = playerCount.coerceIn(0, 100),
            progressColor = 0xFF1D4ED8.toInt(),
        )
    }

    private fun StateRegionGroupRow.toGroupRegionCard(rank: Int): InfoCardItem {
        return InfoCardItem(
            title = "$rank. $groupName",
            badgeText = allianceName,
            badgeColor = 0xFF7C3AED.toInt(),
            metaText = "人数 $playerCount  ·  势力 $totalPower",
            extraText = "州分布 ${stateSummary.ifBlank { "-" }}",
            detailText = """
                同盟：$allianceName
                分组：$groupName
                人数：$playerCount
                总势力值：$totalPower
                平均势力值：${"%.1f".format(avgPower)}
                最高势力值：$maxPower
                州分布：${stateSummary.ifBlank { "-" }}
            """.trimIndent(),
            skillTags = listOf(allianceName, "人数 $playerCount", "势力 $totalPower"),
            progressValue = playerCount.coerceIn(0, 100),
            progressColor = 0xFF7C3AED.toInt(),
        )
    }

    private fun LocalRecord.toInfoCard(type: String): InfoCardItem {
        return InfoCardItem(
            title = title.ifBlank { key.ifBlank { "未命名记录" } },
            badgeText = type,
            badgeColor = 0xFF475569.toInt(),
            metaText = subtitle.take(72).ifBlank { "无摘要" },
            extraText = "来源 ${sourceMsgId.ifBlank { "local_records" }}",
        )
    }

    private fun LocalRecord.to13a2RecordCard(rank: Int): InfoCardItem {
        return InfoCardItem(
            title = "$rank. ${title.ifBlank { key.ifBlank { "未命名地块" } }}",
            badgeText = "5026记录",
            badgeColor = 0xFF475569.toInt(),
            metaText = subtitle.take(96).ifBlank { "无摘要" },
            extraText = "key=${key.ifBlank { "-" }}  ·  来源 ${sourceMsgId.ifBlank { "local_records" }}",
            detailText = """
                标题：${title.ifBlank { "-" }}
                key：${key.ifBlank { "-" }}
                摘要：${subtitle.ifBlank { "-" }}
                来源消息：${sourceMsgId.ifBlank { "-" }}

                raw:
                ${rawJson.take(1800)}
            """.trimIndent(),
        )
    }

    private fun LocalStzbPacket.to13a2PacketCard(rank: Int): InfoCardItem {
        return InfoCardItem(
            title = "$rank. 最近 5026 包",
            badgeText = "待解析",
            badgeColor = 0xFFF59E0B.toInt(),
            metaText = "${streamName.ifBlank { "stream" }}  ·  type=$dataType/$decodeKind",
            extraText = preview.take(120).ifBlank { "暂无预览" },
            detailText = """
                消息 ID：$msgId
                流：${streamName.ifBlank { "-" }}
                类型：$dataType/$decodeKind

                preview:
                ${preview.take(1000)}

                decoded:
                ${decodedText.take(1800)}
            """.trimIndent(),
        )
    }

    private fun Local13A2Item.to13a2TeamCard(index: Int, insight: Local13A2TeamInsight): InfoCardItem {
        val stats = insight.stats
        val lineup = insight.lineup
        val heroNames = lineup.heroes.map { it.heroName.ifBlank { HeroNameResolver.nameOf(it.heroId) } }
        val heroIconIds = lineup.heroes.map { HeroNameResolver.iconIdOf(it.heroId) }
        val skillTags = buildList {
            add(moveTypeText)
            if (groupName.isNotBlank()) add(groupName)
            add("战绩 ${stats.wins}胜/${stats.draws}平/${stats.loses}负")
            add("胜率 ${"%.1f".format(stats.winRate)}%")
            addAll(lineup.heroes.flatMap { hero -> hero.skills.map { skill -> skill.skillName.ifBlank { "战法${skill.skillId}" } } }.take(8))
        }.filter { it.isNotBlank() }
        val recentText = insight.recentBattles.joinToString("\n") {
            "${it.resultText} ${it.opponentName.ifBlank { "未知对手" }} · ${it.opponentHeroNames.joinToString("/")}"
        }.ifBlank { "暂无最近战绩" }
        val favoredText = insight.favored.joinToString("\n") {
            "${it.opponentHeroNames.joinToString("/")} · ${it.wins}胜/${it.total}战 · ${"%.1f".format(it.winRate)}%"
        }.ifBlank { "暂无明显克制阵容" }
        val counteredText = insight.countered.joinToString("\n") {
            "${it.opponentHeroNames.joinToString("/")} · ${it.loses}负/${it.total}战 · ${"%.1f".format(it.winRate)}%"
        }.ifBlank { "暂无明显被克制阵容" }
        val heroText = lineup.heroes.joinToString("\n") { hero ->
            val skills = hero.skills.joinToString(" / ") { skill -> "${skill.skillName} Lv.${skill.level}" }.ifBlank { "无战法" }
            "${hero.pos}. ${hero.heroName} Lv.${hero.level} 进阶${hero.star} · $skills"
        }.ifBlank { "未匹配到武将战法" }
        return InfoCardItem(
            title = "$index. ${ownerName.ifBlank { "未知队伍" }} · 队伍 $teamId",
            badgeText = "13A2 队伍",
            badgeColor = 0xFF2563EB.toInt(),
            metaText = "${moveTypeText}  ·  主体 $subjectId  ·  主城 ${homeXy.ifBlank { "-" }}  ·  势力 $power",
            extraText = "出发 ${fromXy.ifBlank { "-" }}  →  目标 ${toXy.ifBlank { "-" }}  ·  到达 ${formatTime(arriveTime)}  ·  ${stats.battles}战 ${"%.1f".format(stats.winRate)}%",
            detailText = """
                队伍ID：$teamId
                主体ID：$subjectId
                玩家：${ownerName.ifBlank { "-"}}
                UID：$ownerUid
                同盟ID：$unionId
                分组：${groupName.ifBlank { "-"}}
                类型：$moveTypeText($moveType)
                主城：${homeXy.ifBlank { "-" }} / wid $homeWid
                出发：${fromXy.ifBlank { "-" }} / wid $fromWid
                目标：${toXy.ifBlank { "-" }} / wid $toWid
                当前：${currentXy.ifBlank { "-" }} / wid $currentWid
                要塞：${fortressXy.ifBlank { "-" }} / wid $fortressWid
                到达：${formatTime(arriveTime)}
                关联格子：${cells.joinToString(",").ifBlank { "-" }}

                队伍战绩：
                ${stats.battles}战 · ${stats.wins}胜 / ${stats.draws}平 / ${stats.loses}负 · 胜率 ${"%.1f".format(stats.winRate)}%

                武将与战法：
                $heroText

                最近几场战绩：
                $recentText

                更克制的阵容：
                $favoredText

                更被克制的阵容：
                $counteredText

                subject raw:
                ${subjectRawText.take(1200).ifBlank { "-" }}

                cell raw:
                ${cellRawText.take(1200).ifBlank { "-" }}
            """.trimIndent(),
            heroNames = heroNames,
            heroIconIds = heroIconIds,
            skillTags = skillTags,
            progressValue = stats.winRate.toInt().coerceIn(0, 100),
            progressColor = when {
                stats.winRate >= 60.0 -> 0xFF059669.toInt()
                stats.winRate >= 40.0 -> 0xFFF59E0B.toInt()
                else -> 0xFFDC2626.toInt()
            },
        )
    }

    private fun Local13A2Cell.to13a2CellCard(index: Int): InfoCardItem {
        return InfoCardItem(
            title = "$index. 格子 $cellId",
            badgeText = "13A2 格子",
            badgeColor = 0xFF059669.toInt(),
            metaText = "坐标 ${cellXy.ifBlank { "-" }}  ·  队伍 $teamCount",
            extraText = "teamIds=${teamIds.joinToString(",").ifBlank { "-" }}",
            detailText = """
                格子ID：$cellId
                坐标：${cellXy.ifBlank { "-" }}
                队伍数：$teamCount
                队伍ID：${teamIds.joinToString(",").ifBlank { "-" }}
            """.trimIndent(),
            skillTags = listOf("格子", "队伍 $teamCount"),
        )
    }

    private fun LocalPlayerBattleTeam.toInfoCard(rank: Int, badge: String): InfoCardItem {
        val sideText = if (side == "atk") "攻方" else "守方"
        val heroNames = heroes.split('+', '/', '、', ',', '，')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val heroIconIds = heroIds.split('+', '/', '、', ',', '，')
            .mapNotNull { it.trim().toLongOrNull() }
        val skillNames = skills.split('+', '/', '、', ',', '，')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return InfoCardItem(
            title = "$rank. ${player.ifBlank { "未知玩家" }}",
            badgeText = badge,
            badgeColor = if (side == "atk") 0xFFDC2626.toInt() else 0xFF2563EB.toInt(),
            metaText = "${unionName.ifBlank { "-" }}  ·  $sideText",
            extraText = "${heroes}  ·  ${battles}战 ${wins}胜  ·  胜率 ${"%.1f".format(winRate)}%",
            detailText = """
                玩家：${player.ifBlank { "未知玩家" }}
                同盟：${unionName.ifBlank { "-" }}
                侧位：$sideText
                队伍武将：${heroes.ifBlank { "-" }}
                队伍战法：${skillNames.joinToString(" / ").ifBlank { "-" }}
                出战：${battles} 战
                胜场：${wins}
                胜率：${"%.1f".format(winRate)}%
            """.trimIndent(),
            metricValue = winRate,
            heroNames = heroNames,
            heroIconIds = heroIconIds,
            skillTags = (skillNames.take(4).ifEmpty { listOf(sideText, "${battles}战", "${wins}胜", "胜率 ${"%.1f".format(winRate)}%") }),
        )
    }

    private fun LocalHeroFrequency.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = heroName.ifBlank { "武将$heroId" },
            badgeText = "武将频率",
            badgeColor = 0xFF7C3AED.toInt(),
            metaText = "总 ${total}  ·  攻 ${attackCount}  ·  守 ${defendCount}",
            extraText = "均承伤 ${"%.0f".format(averageDamageTaken)}",
        )
    }

    private fun LocalHeroUsage.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = heroName,
            badgeText = "武将胜率",
            badgeColor = 0xFF059669.toInt(),
            metaText = "${count}战  ·  胜 ${wins}  ·  平 ${draws}",
            extraText = "胜率 ${"%.1f".format(winRate)}%  ·  Lv.$maxLevel",
        )
    }

    private fun LocalHeroComboWinRate.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = combo,
            badgeText = "组合胜率",
            badgeColor = 0xFFF59E0B.toInt(),
            metaText = "${total}战  ·  胜 ${wins}  ·  负 ${losses}  ·  平 ${draws}",
            extraText = "胜率 ${"%.1f".format(winRate)}%",
        )
    }

    private fun MonitorCardItem.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = title,
            badgeText = badgeText,
            badgeColor = badgeColor,
            metaText = metaText,
            extraText = extraText,
        )
    }

    private fun LocalTaskAttendanceRow.toInfoCard(): InfoCardItem {
        val attended = atkNum > 0 || disNum > 0
        return InfoCardItem(
            title = name.ifBlank { uid.toString() },
            badgeText = status,
            badgeColor = if (attended) 0xFF059669.toInt() else 0xFFDC2626.toInt(),
            metaText = "${groupName.ifBlank { "未分组" }}  ·  主力 $atkNum  ·  拆迁 $disNum",
            extraText = "主力队 $atkTeamNum  ·  拆迁队 $disTeamNum  ·  最近 ${formatTime(lastBattleTime)}",
            detailText = """
                名字：${name.ifBlank { "-" }}
                UID：$uid
                分组：${groupName.ifBlank { "未分组" }}
                主力次数：$atkNum
                拆迁次数：$disNum
                主力队伍：$atkTeamNum
                拆迁队伍：$disTeamNum
                总战报：$battles
                攻城武勋：$gongxun
                原武勋：$wuxun
                势力：$power
                最近战报：${formatTime(lastBattleTime)}
                状态：$status
            """.trimIndent(),
            skillTags = listOf("主力 $atkNum", "拆迁 $disNum", "主力队 $atkTeamNum", "拆迁队 $disTeamNum"),
            progressValue = if (attended) 100 else 0,
            progressColor = if (attended) 0xFF059669.toInt() else 0xFFDC2626.toInt(),
        )
    }

    private fun LocalDbSyncTableStat.toInfoCard(): InfoCardItem {
        return InfoCardItem(
            title = tableName.ifBlank { "未知表" },
            badgeText = "db_sync",
            badgeColor = 0xFF2563EB.toInt(),
            metaText = "总 ${eventCount}  ·  upsert ${upserts}  ·  update ${updates}",
            extraText = "delete ${deletes}  ·  最近 ${formatTime(lastSeen)}",
        )
    }

    private fun showInfoCardDialog(item: InfoCardItem) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(8))
            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(18).toFloat()
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = item.title
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF1E40AF.toInt())
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(makeBadge(item.badgeText, item.badgeColor))
        container.addView(header)

        container.addView(makeInfoBlock("摘要", listOf(item.metaText, item.extraText).filter { it.isNotBlank() }))

        if (item.heroNames.isNotEmpty()) {
            val heroRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, 0)
            }
            renderHeroRow(heroRow, item.heroNames, item.heroIconIds, item.heroLarge)
            container.addView(makeSectionTitle("武将"))
            container.addView(heroRow)
        }

        if (item.skillTags.isNotEmpty()) {
            val tagRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            renderTagRow(tagRow, item.skillTags)
            container.addView(makeSectionTitle("战法 / 标签"))
            container.addView(tagRow)
        }

        item.progressValue?.let {
            val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = it.coerceIn(0, 100)
                progressTintList = android.content.res.ColorStateList.valueOf(item.progressColor)
            }
            container.addView(makeSectionTitle("指标进度"))
            container.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply {
                topMargin = dp(8)
            })
        }

        if (item.detailText.isNotBlank()) {
            container.addView(makeInfoBlock("详情", item.detailText.lines().filter { it.isNotBlank() }))
        }

        val scrollView = ScrollView(this).apply {
            addView(container)
        }
        AlertDialog.Builder(this)
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun makeSectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF1E3A8A.toInt())
            setPadding(0, dp(14), 0, 0)
        }
    }

    private fun makeInfoBlock(title: String, lines: List<String>): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(0xFFEFF6FF.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(1, 0xFFBFDBFE.toInt())
            }
        }
        box.addView(
            TextView(this).apply {
                text = title
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF1E40AF.toInt())
            }
        )
        lines.forEach { line ->
            box.addView(
                TextView(this).apply {
                    text = line
                    textSize = 12f
                    setTextColor(0xFF334155.toInt())
                    setPadding(0, dp(4), 0, 0)
                }
            )
        }
        return box.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
        }
    }

    private fun makeBadge(text: String, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(9), dp(4), dp(9), dp(4))
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(12).toFloat()
            }
        }
    }

    private inner class InfoCardAdapter(
        private val items: List<InfoCardItem>,
    ) : BaseAdapter() {
        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_info_card, parent, false)
            val item = items[position]
            view.findViewById<TextView>(R.id.infoCardTitleView).text = item.title
            view.findViewById<TextView>(R.id.infoCardMetaView).text = item.metaText
            view.findViewById<TextView>(R.id.infoCardExtraView).text = item.extraText
            renderHeroRow(view.findViewById(R.id.infoCardHeroRow), item.heroNames, item.heroIconIds, item.heroLarge)
            renderTagRow(view.findViewById(R.id.infoCardSkillTagRow), item.skillTags)
            val progressBar = view.findViewById<ProgressBar>(R.id.infoCardProgressBar)
            if (item.progressValue == null) {
                progressBar.visibility = View.GONE
            } else {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = item.progressValue.coerceIn(0, 100)
                progressBar.progressTintList = android.content.res.ColorStateList.valueOf(item.progressColor)
            }
            val badge = view.findViewById<TextView>(R.id.infoCardBadgeView)
            badge.text = item.badgeText
            badge.setBackgroundColor(item.badgeColor)
            return view
        }
    }

    private fun renderHeroRow(row: LinearLayout, heroNames: List<String>, heroIconIds: List<Long>, large: Boolean = false) {
        row.removeAllViews()
        row.visibility = if (heroNames.isEmpty()) View.GONE else View.VISIBLE
        row.gravity = if (large) Gravity.TOP else Gravity.CENTER_VERTICAL
        heroNames.take(if (large) 3 else 5).forEachIndexed { index, name ->
            val iconId = heroIconIds.getOrNull(index) ?: 0L
            val heroBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                if (large) {
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                    background = GradientDrawable().apply {
                        setColor(0xFF111827.toInt())
                        cornerRadius = dp(8).toFloat()
                        setStroke(2, 0xFF7C3AED.toInt())
                    }
                }
            }
            val posText = listOf("大营", "中军", "前锋").getOrElse(index) { "P${index + 1}" }
            if (large) {
                heroBox.addView(
                    TextView(this).apply {
                        text = posText
                        textSize = 11f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(0xFFEAB308.toInt())
                        gravity = Gravity.CENTER
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                )
            }
            val avatar = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(0xFFEFF6FF.toInt())
                    setStroke(1, 0xFFBFDBFE.toInt())
                }
            }
            if (iconId > 0L) {
                val cardType = "card_medium"
                val url = "https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/${cardType}_${iconId}.jpg?gameid=g10"
                avatar.tag = url
                HeroImageLoader.load(url) { bitmap ->
                    if (avatar.tag == url) avatar.setImageBitmap(bitmap)
                }
            }
            val label = TextView(this).apply {
                text = name
                textSize = if (large) 12f else 11f
                typeface = if (large) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (large) 0xFFEAB308.toInt() else 0xFF334155.toInt())
                maxLines = 1
                gravity = Gravity.CENTER
            }
            heroBox.addView(avatar, LinearLayout.LayoutParams(dp(if (large) 92 else 54), dp(if (large) 126 else 54)).apply {
                topMargin = if (large) dp(4) else 0
            })
            heroBox.addView(label, LinearLayout.LayoutParams(dp(if (large) 98 else 64), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            })
            row.addView(heroBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(if (large) 8 else 10)
            })
        }
    }

    private fun renderTagRow(row: LinearLayout, tags: List<String>) {
        row.removeAllViews()
        row.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
        tags.take(4).forEach { tag ->
            val tagView = TextView(this).apply {
                text = tag
                textSize = 10f
                setTextColor(0xFF1D4ED8.toInt())
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = GradientDrawable().apply {
                    setColor(0xFFEFF6FF.toInt())
                    cornerRadius = dp(12).toFloat()
                    setStroke(1, 0xFFBFDBFE.toInt())
                }
            }
            row.addView(
                tagView,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(6)
                }
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun loadSimulatorResources() {
        setStatus("加载战斗模拟器中...")
        setModuleHeader("战斗模拟", "迁移网页端模拟器：默认阵容、士气、武将等级/进阶、战法、多次模拟和详细日志。")
        hideFilters()
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-simulator") {
            val result = runCatching { buildSimulatorCards(null) }
            runOnUiThread {
                result.onSuccess { cards ->
                    infoCards = cards
                    moduleKpiView.text = "可操作模拟器  /  单次、100次、1000次  /  默认网页端阵容"
                    contentView.text = "战斗模拟\n点击运行卡片开始模拟；点击阵容卡查看当前攻守方配置。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("战斗模拟器就绪")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "加载失败：${it.message}"
                    setStatus("战斗模拟器加载失败")
                }
            }
        }
    }

    private fun runSimulationAndRender(repeat: Int) {
        setStatus("战斗模拟运行中：$repeat 次...")
        thread(name = "stzb-simulator-run") {
            val result = runCatching {
                val config = (simulatorConfig ?: LocalBattleSimulator.defaultWebConfig()).copy(repeat = repeat, seed = System.currentTimeMillis().toInt())
                val summary = LocalBattleSimulator.simulate(config)
                buildSimulatorCards(summary)
            }
            runOnUiThread {
                result.onSuccess { cards ->
                    infoCards = cards
                    moduleKpiView.text = "完成 $repeat 次模拟"
                    contentView.text = "战斗模拟\n结果已刷新，点击结果卡查看详细战报日志。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("战斗模拟完成：$repeat 次")
                }.onFailure {
                    setStatus("战斗模拟失败")
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "模拟失败：${it.message}"
                }
            }
        }
    }

    private fun buildSimulatorCards(summary: LocalSimulationSummary?): List<InfoCardItem> {
        val resources = LocalBattleSimulator.resourceSummary()
        val config = simulatorConfig ?: LocalBattleSimulator.defaultWebConfig().also { simulatorConfig = it }
        return buildList {
            add(
                InfoCardItem(
                    title = "开始战斗",
                    badgeText = "单次",
                    badgeColor = 0xFFF59E0B.toInt(),
                    metaText = "按网页端默认阵容执行 1 次详细模拟",
                    extraText = "输出胜负、剩余兵力和完整战斗日志",
                    skillTags = listOf("开始战斗", "详细日志", "默认阵容"),
                    actionKey = ACTION_RUN_SIM_SINGLE,
                )
            )
            add(
                InfoCardItem(
                    title = "批量模拟 x100",
                    badgeText = "x100",
                    badgeColor = 0xFF2563EB.toInt(),
                    metaText = "重复模拟 100 次",
                    extraText = "统计攻方胜率、守方胜率和平局率",
                    actionKey = ACTION_RUN_SIM_100,
                )
            )
            add(
                InfoCardItem(
                    title = "批量模拟 x1000",
                    badgeText = "x1000",
                    badgeColor = 0xFF7C3AED.toInt(),
                    metaText = "重复模拟 1000 次",
                    extraText = "用于稳定评估阵容胜率",
                    actionKey = ACTION_RUN_SIM_1000,
                )
            )
            add(config.blue.toSimulatorTeamCard("攻方队伍", 0xFF2563EB.toInt(), ACTION_EDIT_SIM_BLUE))
            add(config.red.toSimulatorTeamCard("守方队伍", 0xFFDC2626.toInt(), ACTION_EDIT_SIM_RED))
            summary?.let {
                add(it.toSimulatorResultCard())
            }
            add(
                InfoCardItem(
                    title = "模拟器资源",
                    badgeText = "资源",
                    badgeColor = 0xFF059669.toInt(),
                    metaText = "武将 ${resources.simulatorHeroCount}  ·  战法 ${resources.simulatorSkillCount}",
                    extraText = "已加载网页端模拟器资源；支持等级、进阶、士气和装备战法参数。",
                    detailText = """
                        武将资源：${resources.simulatorHeroCount}
                        战法资源：${resources.simulatorSkillCount}
                        默认阵容：张辽 / 刘备 / 太史慈  vs  马超 / 魏延 / 曹操
                        已支持：单次详细日志、多次胜率统计、被动/指挥属性加成、主动/追击/恢复/策略/攻击通用计算。
                        后续可继续细化：每个特殊战法的专属状态机。
                    """.trimIndent(),
                )
            )
        }
    }

    private fun LocalSimTeamConfig.toSimulatorTeamCard(title: String, color: Int, action: String): InfoCardItem {
        val names = heroes.map { LocalBattleSimulator.heroName(it.heroId) }
        val tags = heroes.flatMap { hero ->
            listOf("Lv.${hero.level}", "进阶${hero.advance}") + hero.equipSkillIds.filter { it > 0L }.map { LocalBattleSimulator.skillName(it) }
        }
        return InfoCardItem(
            title = title,
            badgeText = "士气 $morale",
            badgeColor = color,
            metaText = names.joinToString(" / "),
            extraText = heroes.joinToString("  ·  ") { "${LocalBattleSimulator.heroName(it.heroId)} Lv.${it.level} 进阶${it.advance}" } + "  ·  点击编辑武将/战法",
            detailText = heroes.joinToString("\n\n") { hero ->
                val skills = hero.equipSkillIds.filter { it > 0L }.joinToString(" / ") { LocalBattleSimulator.skillName(it) }.ifBlank { "未装备额外战法" }
                "${LocalBattleSimulator.heroName(hero.heroId)}\n等级：${hero.level}\n进阶：${hero.advance}\n额外属性：攻${hero.extraAttack} 防${hero.extraDefense} 谋${hero.extraStrategy} 速${hero.extraSpeed}\n装备战法：$skills"
            },
            heroNames = names,
            heroIconIds = heroes.map { LocalBattleSimulator.heroIconId(it.heroId) },
            skillTags = tags.take(8),
            heroLarge = true,
            actionKey = action,
        )
    }

    private fun showSimulatorHeroPicker(camp: String) {
        showSimulatorTeamEditor(camp)
    }

    private fun showSimulatorTeamEditor(camp: String) {
        val config = simulatorConfig ?: LocalBattleSimulator.defaultWebConfig().also { simulatorConfig = it }
        val team = if (camp == "blue") config.blue else config.red
        val heroOptions = LocalBattleSimulator.selectableHeroes()
        val skillOptions = LocalBattleSimulator.selectableSkills()
        val heroLabels = heroOptions.map { "${it.name} · ${it.country}${it.armyType}" }
        val skillLabels = listOf("不装备") + skillOptions.map {
            "${it.name} · ${it.type} · ${if (it.probability > 0.0) "${"%.0f".format(it.probability)}%" else "--"}"
        }
        val rows = mutableListOf<SimulatorHeroEditRow>()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(4))
        }
        listOf("大营", "中军", "前锋").forEachIndexed { idx, posName ->
            val hero = team.heroes.getOrNull(idx) ?: LocalSimHeroConfig(heroId = heroOptions.firstOrNull()?.id ?: 0L)
            val title = TextView(this).apply {
                text = posName
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF1E40AF.toInt())
                setPadding(0, dp(10), 0, dp(4))
            }
            val heroSpinner = Spinner(this).apply {
                adapter = ArrayAdapter(this@DashboardActivity, android.R.layout.simple_spinner_dropdown_item, heroLabels)
                val selected = heroOptions.indexOfFirst { it.id == hero.heroId }.coerceAtLeast(0)
                setSelection(selected)
            }
            val statRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val levelInput = EditText(this).apply {
                hint = "Lv"
                inputType = InputType.TYPE_CLASS_NUMBER
                setSingleLine(true)
                setText(hero.level.toString())
            }
            val advanceInput = EditText(this).apply {
                hint = "进阶"
                inputType = InputType.TYPE_CLASS_NUMBER
                setSingleLine(true)
                setText(hero.advance.toString())
            }
            statRow.addView(levelInput, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
            statRow.addView(advanceInput, LinearLayout.LayoutParams(0, dp(44), 1f))
            val skillRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val currentSkills = hero.equipSkillIds + listOf(0L, 0L)
            val skillOne = Spinner(this).apply {
                adapter = ArrayAdapter(this@DashboardActivity, android.R.layout.simple_spinner_dropdown_item, skillLabels)
                setSelection(skillOptions.indexOfFirst { it.id == currentSkills[0] }.let { if (it >= 0) it + 1 else 0 })
            }
            val skillTwo = Spinner(this).apply {
                adapter = ArrayAdapter(this@DashboardActivity, android.R.layout.simple_spinner_dropdown_item, skillLabels)
                setSelection(skillOptions.indexOfFirst { it.id == currentSkills[1] }.let { if (it >= 0) it + 1 else 0 })
            }
            skillRow.addView(skillOne, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
            skillRow.addView(skillTwo, LinearLayout.LayoutParams(0, dp(44), 1f))
            root.addView(title)
            root.addView(heroSpinner)
            root.addView(statRow)
            root.addView(skillRow)
            rows += SimulatorHeroEditRow(heroSpinner, levelInput, advanceInput, skillOne, skillTwo)
        }
        val scroll = ScrollView(this).apply { addView(root) }
        AlertDialog.Builder(this)
            .setTitle(if (camp == "blue") "编辑攻方队伍" else "编辑守方队伍")
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                val updatedHeroes = rows.map { row ->
                    val hero = heroOptions.getOrNull(row.heroSpinner.selectedItemPosition) ?: heroOptions.first()
                    val skillIds = listOf(row.skillOne.selectedItemPosition, row.skillTwo.selectedItemPosition).map { selected ->
                        if (selected <= 0) 0L else skillOptions.getOrNull(selected - 1)?.id ?: 0L
                    }
                    LocalSimHeroConfig(
                        heroId = hero.id,
                        level = row.levelInput.text?.toString()?.toIntOrNull()?.coerceIn(1, 50) ?: 40,
                        advance = row.advanceInput.text?.toString()?.toIntOrNull()?.coerceIn(0, 9) ?: 0,
                        equipSkillIds = skillIds,
                    )
                }
                val updatedTeam = team.copy(heroes = updatedHeroes)
                simulatorConfig = if (camp == "blue") config.copy(blue = updatedTeam) else config.copy(red = updatedTeam)
                loadSimulatorResources()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSimulatorHeroReplacePicker(camp: String) {
        val config = simulatorConfig ?: LocalBattleSimulator.defaultWebConfig().also { simulatorConfig = it }
        val positions = arrayOf("大营", "中军", "前锋")
        var selectedPos = 0
        val options = LocalBattleSimulator.selectableHeroes()
        val labels = options.map { "${it.name} · ${it.country}${it.armyType} · ${it.id}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(if (camp == "blue") "选择攻方武将" else "选择守方武将")
            .setSingleChoiceItems(positions, selectedPos) { _, which ->
                selectedPos = which
            }
            .setPositiveButton("下一步") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("替换${positions[selectedPos]}武将")
                    .setItems(labels) { _, heroIndex ->
                        val option = options.getOrNull(heroIndex) ?: return@setItems
                        simulatorConfig = replaceSimulatorHero(config, camp, selectedPos, option.id)
                        loadSimulatorResources()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSimulatorSkillPicker(camp: String) {
        val config = simulatorConfig ?: LocalBattleSimulator.defaultWebConfig().also { simulatorConfig = it }
        val positions = arrayOf("大营", "中军", "前锋")
        var selectedPos = 0
        AlertDialog.Builder(this)
            .setTitle(if (camp == "blue") "选择攻方武将位置" else "选择守方武将位置")
            .setSingleChoiceItems(positions, selectedPos) { _, which ->
                selectedPos = which
            }
            .setPositiveButton("下一步") { _, _ ->
                showSimulatorSkillSlotPicker(camp, selectedPos)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSimulatorSkillSlotPicker(camp: String, pos: Int) {
        AlertDialog.Builder(this)
            .setTitle("${if (camp == "blue") "攻方" else "守方"}${listOf("大营", "中军", "前锋")[pos]}装备战法")
            .setItems(arrayOf("第1战法槽", "第2战法槽")) { _, slot ->
                showSimulatorSkillListPicker(camp, pos, slot)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSimulatorSkillListPicker(camp: String, pos: Int, slot: Int) {
        val skills = LocalBattleSimulator.selectableSkills()
        val labels = buildList {
            add("不装备")
            addAll(skills.map { "${it.name} · ${it.type} · ${if (it.probability > 0.0) "${"%.0f".format(it.probability)}%" else "--"} · 距离${it.distance}" })
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择战法")
            .setItems(labels) { _, index ->
                val skillId = if (index == 0) 0L else skills.getOrNull(index - 1)?.id ?: 0L
                simulatorConfig = updateSimulatorSkill(
                    simulatorConfig ?: LocalBattleSimulator.defaultWebConfig(),
                    camp,
                    pos,
                    slot,
                    skillId,
                )
                loadSimulatorResources()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun replaceSimulatorHero(config: LocalSimulationConfig, camp: String, pos: Int, heroId: Long): LocalSimulationConfig {
        fun replace(team: LocalSimTeamConfig): LocalSimTeamConfig {
            val updated = team.heroes.mapIndexed { idx, hero ->
                if (idx == pos) hero.copy(heroId = heroId, equipSkillIds = emptyList()) else hero
            }
            return team.copy(heroes = updated)
        }
        return if (camp == "blue") config.copy(blue = replace(config.blue)) else config.copy(red = replace(config.red))
    }

    private fun updateSimulatorSkill(config: LocalSimulationConfig, camp: String, pos: Int, slot: Int, skillId: Long): LocalSimulationConfig {
        fun update(team: LocalSimTeamConfig): LocalSimTeamConfig {
            val updated = team.heroes.mapIndexed { idx, hero ->
                if (idx != pos) {
                    hero
                } else {
                    val skills = hero.equipSkillIds.toMutableList()
                    while (skills.size < 2) skills += 0L
                    skills[slot.coerceIn(0, 1)] = skillId
                    hero.copy(equipSkillIds = skills.take(2))
                }
            }
            return team.copy(heroes = updated)
        }
        return if (camp == "blue") config.copy(blue = update(config.blue)) else config.copy(red = update(config.red))
    }

    private fun LocalSimulationSummary.toSimulatorResultCard(): InfoCardItem {
        return InfoCardItem(
            title = "模拟结果：${firstRun.winner}",
            badgeText = "${repeat}次",
            badgeColor = when (firstRun.winner) {
                "攻方" -> 0xFF2563EB.toInt()
                "守方" -> 0xFFDC2626.toInt()
                else -> 0xFFF59E0B.toInt()
            },
            metaText = "攻方胜 ${blueWins}  ·  守方胜 ${redWins}  ·  平 ${draws}",
            extraText = "攻方 ${"%.1f".format(blueWinRate)}%  ·  守方 ${"%.1f".format(redWinRate)}%  ·  平局 ${"%.1f".format(drawRate)}%",
            detailText = """
                模拟次数：$repeat
                攻方胜：$blueWins（${"%.1f".format(blueWinRate)}%）
                守方胜：$redWins（${"%.1f".format(redWinRate)}%）
                平局：$draws（${"%.1f".format(drawRate)}%）
                首场结果：${firstRun.winner}
                攻方剩余：${firstRun.blueRemain}
                守方剩余：${firstRun.redRemain}

                首场战报：
                ${firstRun.records.joinToString("\n")}
            """.trimIndent(),
            skillTags = listOf("攻 ${"%.1f".format(blueWinRate)}%", "守 ${"%.1f".format(redWinRate)}%", "平 ${"%.1f".format(drawRate)}%"),
            progressValue = blueWinRate.toInt().coerceIn(0, 100),
        )
    }

    private fun loadTeamIndex13a2() {
        setStatus("加载辅助战场监控中...")
        setModuleHeader("辅助战场监控", "复刻网页端 13A2：队伍、主体、格子、范围、战绩、武将战法和阵容克制。")
        battleFilterPanel.visibility = View.GONE
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-team-index-13a2") {
            val result = runCatching {
                val recent5026 = LocalStzbRepository.loadRecentPackets(200).filter { it.msgId == "5026" }.take(20)
                val parsed = recent5026.firstNotNullOfOrNull { packet ->
                    Local13A2Parser.parse(packet.decodedText)?.let { packet to it }
                }
                if (parsed != null) {
                    val (packet, payload) = parsed
                    val cards = buildList {
                        add(
                            InfoCardItem(
                                title = "13A2 全局监控",
                                badgeText = "13A2",
                                badgeColor = 0xFF2563EB.toInt(),
                                metaText = "队伍 ${payload.teamsCount}  ·  主体 ${payload.subjectsCount}  ·  格子 ${payload.cellsCount}  ·  marker ${payload.marker}",
                                extraText = if (payload.areaRange.size >= 4) "范围 ${payload.areaRange.joinToString(" , ")}" else "来源 ${packet.streamName.ifBlank { "5026" }}",
                                detailText = """
                                    队伍数：${payload.teamsCount}
                                    主体数：${payload.subjectsCount}
                                    格子数：${payload.cellsCount}
                                    报文标记：${payload.marker}
                                    范围：${payload.areaRange.joinToString(" , ").ifBlank { "-" }}
                                    来源流：${packet.streamName.ifBlank { "-" }}
                                """.trimIndent(),
                                skillTags = listOf("队伍 ${payload.teamsCount}", "主体 ${payload.subjectsCount}", "格子 ${payload.cellsCount}", "marker ${payload.marker}"),
                            )
                        )
                        payload.items.forEachIndexed { idx, item ->
                            add(
                                item.to13a2TeamCard(
                                    idx + 1,
                                    LocalStzbRepository.load13A2TeamInsight(
                                        teamId = item.teamId,
                                        ownerName = item.ownerName,
                                        relatedWids = listOf(item.homeWid, item.fromWid, item.currentWid, item.toWid, item.fortressWid) + item.cells,
                                    )
                                )
                            )
                        }
                        payload.cells.take(80).forEachIndexed { idx, cell ->
                            add(cell.to13a2CellCard(idx + 1))
                        }
                    }
                    TeamIndex13A2Ui(
                        cards = cards,
                        summary = "队伍 ${payload.teamsCount}  /  主体 ${payload.subjectsCount}  /  格子 ${payload.cellsCount}  /  marker ${payload.marker}",
                        status = "辅助战场监控：${payload.items.size} 支队伍",
                        parsed = true,
                    )
                } else {
                    val stats = LocalStzbRepository.loadMapStats()
                    val cells = LocalStzbRepository.loadMapCells(limit = 120)
                    val records = LocalStzbRepository.loadRecords("map_cell", 80)
                    val cards = buildList {
                        addAll(cells.mapIndexed { idx, cell -> cell.to13a2Card(idx + 1) })
                        addAll(records.mapIndexed { idx, record -> record.to13a2RecordCard(idx + 1) })
                        if (isEmpty()) addAll(recent5026.mapIndexed { idx, packet -> packet.to13a2PacketCard(idx + 1) })
                    }
                    TeamIndex13A2Ui(
                        cards = cards,
                        summary = "格子 ${stats.totalCells}  /  命名 ${stats.namedCities}  /  展示 ${cards.size}  /  最近5026包 ${recent5026.size}",
                        status = "5026兜底索引：${cards.size} 条",
                        parsed = false,
                    )
                }
            }
            runOnUiThread {
                result.onSuccess { ui ->
                    infoCards = ui.cards
                    moduleKpiView.text = ui.summary
                    contentView.text = if (ui.parsed) {
                        "辅助战场监控\n已按网页端 13A2 逻辑解析队伍、主体、格子并叠加战绩/阵容。"
                    } else {
                        "辅助战场监控\n未解析到完整 13A2 队伍结构，当前显示 5026 地块兜底数据。"
                    }
                    battleListView.adapter = InfoCardAdapter(ui.cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus(ui.status)
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载辅助战场监控失败")
                }
            }
        }
    }

    private fun loadTaskAttendance() {
        setStatus("加载工程考勤中...")
        setModuleHeader("工程考勤", "对齐网页端任务流：任务列表、新建任务、开始统计、考勤详情、该城战报、导出 CSV 和删除任务。")
        battleFilterPanel.visibility = View.GONE
        syncTaskAttendanceSubTabs(null)
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-task-attendance") {
            val result = runCatching {
                val tasks = LocalStzbRepository.loadSiegeTasks(0)
                val selectedTask = selectedSiegeTaskId?.let { selectedId ->
                    tasks.firstOrNull { it.id == selectedId } ?: LocalStzbRepository.loadSiegeTask(selectedId)
                }
                val detailRows = selectedTask?.let { LocalStzbRepository.loadTaskAttendanceForTask(it.id, 0) }.orEmpty()
                val battleRows = selectedTask?.let { LocalStzbRepository.loadTaskBattles(it.id, 0) }.orEmpty()
                val attendedUsers = tasks.sumOf { it.completeUserNum }
                val targetUsers = tasks.sumOf { it.targetUserNum }
                val actionCards = buildList {
                    add(createSiegeTaskEntryCard())
                    add(createSiegeTaskRefreshCard())
                    add(
                        InfoCardItem(
                            title = "任务总览",
                            badgeText = "任务 ${tasks.size}",
                            badgeColor = 0xFF2563EB.toInt(),
                            metaText = "目标人次 $targetUsers  ·  已出战 $attendedUsers",
                            extraText = if (selectedTask == null) "点击任务卡可展开详情、统计和导出动作。" else "当前已展开：${selectedTask.name}",
                            skillTags = listOf("列表", "统计", "详情", "导出"),
                            progressValue = if (targetUsers > 0) (attendedUsers * 100 / targetUsers).coerceIn(0, 100) else 0,
                            progressColor = 0xFF0EA5E9.toInt(),
                        )
                    )
                }
                val cards = buildTaskAttendanceCards(tasks, selectedTask, detailRows, battleRows, actionCards)
                TaskAttendanceUi(
                    cards = cards,
                    taskCount = tasks.size,
                    targetUsers = targetUsers,
                    attendedUsers = attendedUsers,
                    selectedTask = selectedTask,
                    detailRows = detailRows,
                    battleRows = battleRows,
                )
            }
            runOnUiThread {
                result.onSuccess { ui ->
                    if (ui.selectedTask == null) {
                        selectedSiegeTaskId = null
                        taskAttendanceSubPage = TaskAttendanceSubPage.HOME
                    }
                    syncTaskAttendanceSubTabs(ui.selectedTask)
                    infoCards = ui.cards
                    moduleKpiView.text = if (ui.selectedTask == null) {
                        "任务 ${ui.taskCount}  /  目标人次 ${ui.targetUsers}  /  已出战 ${ui.attendedUsers}"
                    } else {
                        when (taskAttendanceSubPage) {
                            TaskAttendanceSubPage.OVERVIEW -> "概览  /  目标 ${ui.selectedTask.targetUserNum}  /  已出战 ${ui.selectedTask.completeUserNum}"
                            TaskAttendanceSubPage.MEMBERS -> "成员 ${ui.detailRows.size}  /  实到 ${ui.detailRows.count { it.atkNum > 0 || it.disNum > 0 }}"
                            TaskAttendanceSubPage.BATTLES -> "战报 ${ui.battleRows.size}  /  坐标 ${formatCityId(ui.selectedTask.cityId)}"
                            TaskAttendanceSubPage.ACTIONS -> "操作  /  统计、导出、删除"
                            TaskAttendanceSubPage.HOME -> "任务 ${ui.taskCount}"
                        }
                    }
                    contentView.text = if (ui.selectedTask == null) {
                        "工程考勤\n主页面只保留任务工作台；点击任一任务后，会进入该任务的子标签页。"
                    } else {
                        "${ui.selectedTask.name}\n你现在位于任务子页，可在上方切换“概览 / 成员 / 战报 / 操作”，也可返回任务主页。"
                    }
                    battleListView.adapter = InfoCardAdapter(ui.cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("工程考勤：${ui.taskCount} 个任务")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载工程考勤失败")
                }
            }
        }
    }

    private fun createSiegeTaskEntryCard(): InfoCardItem {
        return InfoCardItem(
            title = "新建工程任务",
            badgeText = "任务录入",
            badgeColor = 0xFF2563EB.toInt(),
            metaText = "填写任务名、WID/X,Y、任务时间和目标成员范围",
            extraText = "支持按分组建任务，也支持按距离预览成员后勾选创建",
            detailText = "点击此卡片创建任务。逻辑与网页端一致：支持 X,Y 转 WID、分组选人、按距离智能分配成员。",
            skillTags = listOf("WID", "X,Y", "分组", "距离预览"),
            actionKey = ACTION_CREATE_SIEGE_TASK,
        )
    }

    private fun createSiegeTaskRefreshCard(): InfoCardItem {
        return InfoCardItem(
            title = "刷新工程考勤",
            badgeText = "同步视图",
            badgeColor = 0xFF0F766E.toInt(),
            metaText = "重新加载任务列表、任务详情与该城战报",
            extraText = "适合抓包后立即刷新本地统计结果",
            detailText = "点击此卡片会重刷当前工程考勤页。",
            skillTags = listOf("刷新", "任务列表", "详情"),
            actionKey = ACTION_REFRESH_SIEGE_TASKS,
        )
    }

    private fun buildTaskAttendanceCards(
        tasks: List<LocalSiegeTask>,
        selectedTask: LocalSiegeTask?,
        detailRows: List<LocalTaskAttendanceRow>,
        battleRows: List<LocalTaskBattleRow>,
        actionCards: List<InfoCardItem>,
    ): List<InfoCardItem> {
        if (selectedTask == null || taskAttendanceSubPage == TaskAttendanceSubPage.HOME) {
            return buildList {
                addAll(actionCards)
                addAll(tasks.map { it.toTaskInfoCard(it.id == selectedTask?.id) })
            }
        }
        return when (taskAttendanceSubPage) {
            TaskAttendanceSubPage.OVERVIEW -> buildList {
                add(createSiegeTaskDetailCard(selectedTask, detailRows, battleRows))
                add(
                    InfoCardItem(
                        title = "任务摘要",
                        badgeText = "概览",
                        badgeColor = 0xFF1D4ED8.toInt(),
                        metaText = "目标 ${selectedTask.targetUserNum}  ·  已出战 ${selectedTask.completeUserNum}  ·  战报 ${battleRows.size}",
                        extraText = "这里保留任务的核心摘要，适合作为任务详情首页。",
                        detailText = """
                            任务：${selectedTask.name}
                            坐标：${formatCityId(selectedTask.cityId)}
                            时间：${if (selectedTask.taskTime > 0) formatTime(selectedTask.taskTime) else "未设时间"}
                            分组：${selectedTask.targetGroups.ifBlank { "全员" }}
                            指定成员：${if (selectedTask.targetUids.isBlank()) "未指定" else selectedTask.targetUids}
                        """.trimIndent(),
                        skillTags = listOf("概览", "任务摘要", "任务首页"),
                    )
                )
            }
            TaskAttendanceSubPage.MEMBERS -> buildList {
                add(
                    InfoCardItem(
                        title = "成员考勤明细",
                        badgeText = "${detailRows.size} 人",
                        badgeColor = 0xFF059669.toInt(),
                        metaText = "按成员展示主力次数、拆迁次数、主力队与拆迁队。",
                        extraText = "与网页端成员维度一致，便于快速看缺勤和主力覆盖。",
                        skillTags = listOf("成员", "明细", "考勤"),
                    )
                )
                addAll(detailRows.mapIndexed { index, row -> row.toTaskDetailInfoCard(index + 1) })
            }
            TaskAttendanceSubPage.BATTLES -> buildList {
                add(
                    InfoCardItem(
                        title = "该城战报",
                        badgeText = "${battleRows.size} 条",
                        badgeColor = 0xFF7C3AED.toInt(),
                        metaText = "只显示当前任务成员在 ${formatCityId(selectedTask.cityId)} 的战报",
                        extraText = "与网页端“该城战报（任务成员）”口径一致，按时间倒序。",
                        skillTags = listOf("战报", "主力", "拆迁", "结果"),
                    )
                )
                addAll(battleRows.mapIndexed { index, row -> row.toTaskBattleInfoCard(index + 1) })
            }
            TaskAttendanceSubPage.ACTIONS -> buildList {
                add(
                    InfoCardItem(
                        title = "任务操作台",
                        badgeText = "动作",
                        badgeColor = 0xFF0F766E.toInt(),
                        metaText = "这里集中放当前任务的所有动作，避免和明细列表混在一起。",
                        extraText = "你可以在这里重新统计、导出 CSV、删除任务或返回列表。",
                        skillTags = listOf("统计", "导出", "删除", "返回"),
                    )
                )
                add(createSiegeTaskActionCard("开始统计", "重新计算该任务所有成员的主力/拆迁次数与队伍数。", ACTION_RUN_SIEGE_TASK_STATISTICS, selectedTask.id, 0xFFF59E0B.toInt(), "统计"))
                add(createSiegeTaskActionCard("导出 CSV", "导出当前任务详情为 CSV 文件，字段与网页端保持一致。", ACTION_EXPORT_SIEGE_TASK_CSV, selectedTask.id, 0xFF0F766E.toInt(), "导出"))
                add(createSiegeTaskActionCard("删除任务", "删除当前任务卡片及其明细入口。", ACTION_DELETE_SIEGE_TASK, selectedTask.id, 0xFFDC2626.toInt(), "删除"))
                add(createSiegeTaskActionCard("返回任务主页", "退出当前任务子页，回到工程考勤主页。", ACTION_CLOSE_SIEGE_TASK_DETAIL, selectedTask.id, 0xFF475569.toInt(), "返回"))
            }
            TaskAttendanceSubPage.HOME -> emptyList()
        }
    }

    private fun syncTaskAttendanceSubTabs(selectedTask: LocalSiegeTask?) {
        if (currentModule != MODULE_TASK_ATTENDANCE || selectedTask == null || taskAttendanceSubPage == TaskAttendanceSubPage.HOME) {
            taskSubTabScroll.visibility = View.GONE
            taskSubTabContainer.removeAllViews()
            return
        }
        taskSubTabScroll.visibility = View.VISIBLE
        taskSubTabContainer.removeAllViews()
        val tabs = listOf(
            "返回任务页" to TaskAttendanceSubPage.HOME,
            "概览" to TaskAttendanceSubPage.OVERVIEW,
            "成员" to TaskAttendanceSubPage.MEMBERS,
            "战报" to TaskAttendanceSubPage.BATTLES,
            "操作" to TaskAttendanceSubPage.ACTIONS,
        )
        tabs.forEachIndexed { index, (label, page) ->
            val button = Button(this).apply {
                text = if (page == TaskAttendanceSubPage.HOME) "← $label" else "${selectedTask.name} · $label"
                textSize = 12f
                setPadding(dp(12), 0, dp(12), 0)
                minHeight = dp(38)
                minimumHeight = dp(38)
                alpha = if (page == taskAttendanceSubPage) 1f else 0.76f
                setOnClickListener {
                    if (page == TaskAttendanceSubPage.HOME) {
                        closeSiegeTaskDetail()
                    } else {
                        taskAttendanceSubPage = page
                        loadTaskAttendance()
                    }
                }
            }
            taskSubTabContainer.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
    }

    private fun buildTeamUsersCards(
        stats: LocalTeamStats,
        filteredStats: List<LocalTeamGroupStat>,
        users: List<LocalTeamUser>,
        selectedMember: LocalTeamUser?,
        memberTeams: List<LocalPlayerBattleTeam>,
        maxPower: Int,
        maxWuxun: Int,
        maxWeekContribute: Int,
        totalPower: Long,
        totalWuxun: Long,
        totalWeekContribute: Long,
    ): List<InfoCardItem> {
        if (selectedMember != null && teamUsersSubPage != TeamUsersSubPage.HOME) {
            return when (teamUsersSubPage) {
                TeamUsersSubPage.OVERVIEW -> buildList {
                    add(
                        InfoCardItem(
                            title = "成员总览",
                            badgeText = "主页",
                            badgeColor = 0xFF2563EB.toInt(),
                            metaText = "当前成员 ${selectedMember.name}  ·  分组 ${selectedMember.groupName.ifBlank { "未分组" }}",
                            extraText = "点击上方子标签可切换到该成员的全部队伍。",
                            skillTags = listOf("成员详情", "概览", "子页"),
                        )
                    )
                    add(selectedMember.toMemberCard(1, maxPower, maxWuxun, maxWeekContribute))
                }
                TeamUsersSubPage.TEAMS -> buildList {
                    add(
                        InfoCardItem(
                            title = "成员队伍",
                            badgeText = "${memberTeams.size} 队",
                            badgeColor = 0xFF0F766E.toInt(),
                            metaText = "${selectedMember.name} 的全部队伍信息",
                            extraText = if (memberTeams.isEmpty()) "当前本地还没有该成员的战报队伍数据。" else "按战报出现频次和胜率排序。",
                            skillTags = listOf("全部队伍", "成员队伍", "战报聚合"),
                        )
                    )
                    if (memberTeams.isEmpty()) {
                        add(
                            InfoCardItem(
                                title = "暂无队伍数据",
                                badgeText = "待抓取",
                                badgeColor = 0xFFF59E0B.toInt(),
                                metaText = "当前未从战报中聚合到 ${selectedMember.name} 的队伍信息。",
                                extraText = "继续抓包并刷新后，这里会显示该成员所有队伍。",
                                skillTags = listOf("空状态"),
                            )
                        )
                    } else {
                        addAll(memberTeams.mapIndexed { index, row -> row.toInfoCard(index + 1, "成员队伍") })
                    }
                }
                TeamUsersSubPage.HOME -> emptyList()
            }
        }
        return buildList {
            add(
                InfoCardItem(
                    title = "同盟总览",
                    badgeText = if (selectedTeamUserGroupFilter.isBlank()) "全部分组" else selectedTeamUserGroupFilter,
                    badgeColor = 0xFF2563EB.toInt(),
                    metaText = "成员 ${stats.total}  ·  总势力 $totalPower",
                    extraText = "总武勋 $totalWuxun  ·  周贡献 $totalWeekContribute",
                    skillTags = listOf("成员 ${stats.total}", "势力 $totalPower", "武勋 $totalWuxun"),
                )
            )
            filteredStats.forEach { group ->
                val groupName = group.name.ifBlank { "未分组" }
                val groupUsers = users.filter { it.groupName.ifBlank { "未分组" } == groupName }
                val expanded = groupName in expandedTeamUserGroups
                add(group.toExpandableGroupCard(expanded, groupUsers.size))
                if (expanded) {
                    addAll(groupUsers.mapIndexed { index, user -> user.toMemberCard(index + 1, maxPower, maxWuxun, maxWeekContribute, clickable = true) })
                }
            }
        }
    }

    private fun syncTeamUsersSubTabs(groupNames: List<String>, selectedMember: LocalTeamUser?) {
        if (currentModule != MODULE_TEAM_USERS) return
        taskSubTabContainer.removeAllViews()
        taskSubTabScroll.visibility = View.VISIBLE
        if (selectedMember == null || teamUsersSubPage == TeamUsersSubPage.HOME) {
            val tabs = listOf("全部" to "").plus(groupNames.map { it.ifBlank { "未分组" } to it.ifBlank { "未分组" } })
            tabs.forEachIndexed { index, pair ->
                val button = Button(this).apply {
                    val groupName = pair.second
                    text = pair.first
                    textSize = 12f
                    alpha = if ((groupName.isBlank() && selectedTeamUserGroupFilter.isBlank()) || groupName == selectedTeamUserGroupFilter) 1f else 0.76f
                    setPadding(dp(12), 0, dp(12), 0)
                    minHeight = dp(38)
                    minimumHeight = dp(38)
                    setOnClickListener {
                        selectedTeamUserGroupFilter = groupName
                        if (groupName.isNotBlank()) expandedTeamUserGroups += groupName
                        loadTeamUsers()
                    }
                }
                taskSubTabContainer.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                    if (index > 0) marginStart = dp(8)
                })
            }
            return
        }
        val tabs = listOf(
            "返回成员页" to TeamUsersSubPage.HOME,
            "概览" to TeamUsersSubPage.OVERVIEW,
            "队伍" to TeamUsersSubPage.TEAMS,
        )
        tabs.forEachIndexed { index, (label, page) ->
            val button = Button(this).apply {
                text = if (page == TeamUsersSubPage.HOME) "← $label" else "${selectedMember.name} · $label"
                textSize = 12f
                alpha = if (page == teamUsersSubPage) 1f else 0.76f
                setPadding(dp(12), 0, dp(12), 0)
                minHeight = dp(38)
                minimumHeight = dp(38)
                setOnClickListener {
                    if (page == TeamUsersSubPage.HOME) {
                        closeTeamUserDetail()
                    } else {
                        teamUsersSubPage = page
                        loadTeamUsers()
                    }
                }
            }
            taskSubTabContainer.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
    }

    private fun toggleTeamUserGroup(groupName: String) {
        val normalized = groupName.ifBlank { "未分组" }
        if (!expandedTeamUserGroups.add(normalized)) {
            expandedTeamUserGroups.remove(normalized)
        }
        loadTeamUsers()
    }

    private fun openTeamUserDetail(uid: Long) {
        selectedTeamUserUid = uid
        teamUsersSubPage = TeamUsersSubPage.OVERVIEW
        loadTeamUsers()
    }

    private fun closeTeamUserDetail() {
        selectedTeamUserUid = null
        teamUsersSubPage = TeamUsersSubPage.HOME
        loadTeamUsers()
    }

    private fun buildTeamReportCards(
        rows: List<LocalTeamReportRow>,
        totalBattles: Int,
        winRate: Double,
        totalPlayers: Int,
        totalDraws: Int,
        totalCity: Int,
        totalGongxun: Long,
    ): List<InfoCardItem> {
        val isGroup = teamReportDim == "group"
        return buildList {
            add(
                InfoCardItem(
                    title = "总战报",
                    badgeText = "统计卡",
                    badgeColor = 0xFF8F6A2A.toInt(),
                    metaText = totalBattles.toString(),
                    extraText = "网页端同口径汇总",
                    skillTags = listOf("总战报"),
                )
            )
            add(
                InfoCardItem(
                    title = "胜率",
                    badgeText = "统计卡",
                    badgeColor = 0xFF41664D.toInt(),
                    metaText = "${"%.1f".format(winRate)}%",
                    extraText = "胜 + 平/2 统计",
                    progressValue = winRate.toInt().coerceIn(0, 100),
                    progressColor = 0xFF41664D.toInt(),
                    skillTags = listOf("胜率"),
                )
            )
            add(
                InfoCardItem(
                    title = "参战人数",
                    badgeText = "统计卡",
                    badgeColor = 0xFF0EA5E9.toInt(),
                    metaText = totalPlayers.toString(),
                    extraText = "包含无战报成员",
                    skillTags = listOf("参战人数"),
                )
            )
            add(
                InfoCardItem(
                    title = "平局",
                    badgeText = "统计卡",
                    badgeColor = 0xFF64748B.toInt(),
                    metaText = totalDraws.toString(),
                    extraText = "结果口径与网页端一致",
                    skillTags = listOf("平局"),
                )
            )
            add(
                InfoCardItem(
                    title = "攻城场次",
                    badgeText = "统计卡",
                    badgeColor = 0xFFDC2626.toInt(),
                    metaText = totalCity.toString(),
                    extraText = "fight_type ∈ 2/80/33",
                    skillTags = listOf("攻城"),
                )
            )
            add(
                InfoCardItem(
                    title = "总功勋",
                    badgeText = "统计卡",
                    badgeColor = 0xFF705C8D.toInt(),
                    metaText = totalGongxun.toString(),
                    extraText = "本地同盟成员功勋总和",
                    skillTags = listOf("总功勋"),
                )
            )
            add(
                InfoCardItem(
                    title = if (isGroup) "分组战斗数据" else "成员战斗数据",
                    badgeText = if (isGroup) "按分组" else "按成员",
                    badgeColor = if (isGroup) 0xFF7C3AED.toInt() else 0xFFF59E0B.toInt(),
                    metaText = "周期 ${teamReportPeriodLabel(teamReportPeriod)}  ·  ${if (teamReportGroup.isBlank()) "全部分组" else teamReportGroup}",
                    extraText = "顶部子标签可切换周期、维度与分组筛选；下方字段顺序对齐网页端。",
                    skillTags = listOf(teamReportPeriodLabel(teamReportPeriod), if (isGroup) "分组表" else "成员表"),
                )
            )
            add(
                InfoCardItem(
                    title = "导出 CSV",
                    badgeText = "导出",
                    badgeColor = 0xFF0F766E.toInt(),
                    metaText = "导出当前团数据结果为 CSV 文件",
                    extraText = "先提供结构化 CSV，便于继续核对网页端统计结果。",
                    skillTags = listOf("CSV", "导出"),
                    actionKey = ACTION_EXPORT_TEAM_REPORT_CSV,
                )
            )
            addAll(
                rows.mapIndexed { index, row ->
                    if (isGroup) row.toGroupReportCard(index + 1) else row.toPlayerReportCard(index + 1)
                }
            )
        }
    }

    private fun syncTeamReportSubTabs(groupOptions: List<String>) {
        if (currentModule != MODULE_TEAM_REPORT) return
        taskSubTabContainer.removeAllViews()
        taskSubTabScroll.visibility = View.VISIBLE
        val periodTabs = listOf(
            "今日" to "today",
            "昨日" to "yesterday",
            "本周" to "week",
            "上周" to "lastweek",
            "全部" to "all",
        )
        val dimTabs = listOf("按分组" to "group", "按成员" to "player")
        val groupTabs = listOf("全部分组" to "").plus(groupOptions.distinct().map { it to it })
        fun addTab(label: String, active: Boolean, actionKey: String, arg: String) {
            val button = Button(this).apply {
                text = label
                textSize = 12f
                alpha = if (active) 1f else 0.76f
                setPadding(dp(12), 0, dp(12), 0)
                minHeight = dp(38)
                minimumHeight = dp(38)
                setOnClickListener {
                    when (actionKey) {
                        ACTION_SWITCH_TEAM_REPORT_PERIOD -> switchTeamReportPeriod(arg)
                        ACTION_SWITCH_TEAM_REPORT_DIM -> switchTeamReportDim(arg)
                        ACTION_SWITCH_TEAM_REPORT_GROUP -> switchTeamReportGroup(arg)
                    }
                }
            }
            taskSubTabContainer.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                if (taskSubTabContainer.childCount > 0) marginStart = dp(8)
            })
        }
        periodTabs.forEach { (label, value) -> addTab(label, value == teamReportPeriod, ACTION_SWITCH_TEAM_REPORT_PERIOD, value) }
        dimTabs.forEach { (label, value) -> addTab(label, value == teamReportDim, ACTION_SWITCH_TEAM_REPORT_DIM, value) }
        groupTabs.take(10).forEach { (label, value) -> addTab(label, value == teamReportGroup, ACTION_SWITCH_TEAM_REPORT_GROUP, value) }
    }

    private fun switchTeamReportPeriod(period: String) {
        if (period == teamReportPeriod) return
        teamReportPeriod = period
        loadTeamReport()
    }

    private fun switchTeamReportDim(dim: String) {
        if (dim == teamReportDim) return
        teamReportDim = dim
        if (teamReportDim == "group") {
            teamReportGroup = ""
        }
        loadTeamReport()
    }

    private fun switchTeamReportGroup(group: String) {
        if (group == teamReportGroup) return
        teamReportGroup = group
        loadTeamReport()
    }

    private fun exportTeamReportCsv() {
        setStatus("导出团数据中...")
        thread(name = "stzb-team-report-export") {
            val result = runCatching {
                val rows = LocalStzbRepository.loadTeamReport(dim = teamReportDim, period = teamReportPeriod, group = teamReportGroup, limit = 0)
                val dir = File(getExternalFilesDir(null) ?: filesDir, "exports").apply { mkdirs() }
                val outFile = File(dir, "团数据_${teamReportPeriodLabel(teamReportPeriod)}_${if (teamReportDim == "group") "分组" else "成员"}.csv")
                val csv = buildString {
                    append('\uFEFF')
                    if (teamReportDim == "group") {
                        append("分组,人数,战报,胜,败,平,胜率,攻城,总功勋,平均武勋,平均势力值\n")
                        rows.forEach { row ->
                            append(row.name).append(',')
                            append(row.members).append(',')
                            append(row.battles).append(',')
                            append(row.wins).append(',')
                            append(row.loses).append(',')
                            append(row.draws).append(',')
                            append(row.winRate).append(',')
                            append(row.cityBattles).append(',')
                            append(row.totalGongxun).append(',')
                            append(row.avgGongxun).append(',')
                            append(row.avgPower).append('\n')
                        }
                    } else {
                        append("成员,分组,战报,胜,败,平,胜率,攻城,功勋,势力值\n")
                        rows.forEach { row ->
                            append(row.name).append(',')
                            append(row.groupName).append(',')
                            append(row.battles).append(',')
                            append(row.wins).append(',')
                            append(row.loses).append(',')
                            append(row.draws).append(',')
                            append(row.winRate).append(',')
                            append(row.cityBattles).append(',')
                            append(row.totalGongxun).append(',')
                            append(row.power).append('\n')
                        }
                    }
                }
                outFile.writeText(csv, Charsets.UTF_8)
                outFile
            }
            runOnUiThread {
                result.onSuccess { file ->
                    setStatus("已导出团数据：${file.name}")
                    AlertDialog.Builder(this)
                        .setTitle("导出成功")
                        .setMessage("CSV 已导出到：\n${file.absolutePath}")
                        .setPositiveButton("知道了", null)
                        .show()
                }.onFailure {
                    setStatus("导出团数据失败：${it.message}")
                }
            }
        }
    }

    private fun LocalSiegeTask.toTaskInfoCard(selected: Boolean): InfoCardItem {
        val groups = targetGroups.ifBlank { "全员" }
        val rate = if (targetUserNum > 0) completeUserNum * 100 / targetUserNum else 0
        val timeText = if (taskTime > 0) formatTime(taskTime) else "未设时间"
        return InfoCardItem(
            title = name.ifBlank { "工程任务 #$id" },
            badgeText = when {
                selected -> "详情展开中"
                completeUserNum > 0 -> "已统计"
                else -> "待考勤"
            },
            badgeColor = when {
                selected -> 0xFF1D4ED8.toInt()
                completeUserNum > 0 -> 0xFF059669.toInt()
                else -> 0xFFF59E0B.toInt()
            },
            metaText = "坐标 ${formatCityId(cityId)}  ·  WID $cityId  ·  $timeText",
            extraText = "目标 $targetUserNum  ·  实到 $completeUserNum  ·  分组 $groups",
            detailText = """
                任务名：${name.ifBlank { "工程任务 #$id" }}
                任务时间：$timeText
                坐标：${formatCityId(cityId)}
                WID：$cityId
                目标分组：$groups
                指定成员：${if (targetUids.isBlank()) "未指定" else targetUids}
                目标人数：$targetUserNum
                已出战：$completeUserNum
                创建时间：${formatTime(createdAt)}
                更新时间：${formatTime(updatedAt)}
            """.trimIndent(),
            skillTags = listOf("目标 $targetUserNum", "实到 $completeUserNum", groups, if (taskTime > 0) "定时" else "即时"),
            progressValue = rate.coerceIn(0, 100),
            progressColor = if (selected) 0xFF1D4ED8.toInt() else if (completeUserNum > 0) 0xFF059669.toInt() else 0xFFF59E0B.toInt(),
            actionKey = ACTION_OPEN_SIEGE_TASK,
            actionId = id,
        )
    }

    private fun showCreateSiegeTaskDialog() {
        val groupTags = LocalStzbRepository.loadTaskGroups()
        val nameInput = EditText(this).apply {
            hint = "任务名，例如 7点内黄"
            setSingleLine(true)
        }
        val posInput = EditText(this).apply {
            hint = "坐标/WID，例如 123,4567 或 12304567"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val timeInput = EditText(this).apply {
            hint = "任务时间，可空，格式 2026-07-10 19:00"
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val groupsInput = EditText(this).apply {
            hint = "目标分组，可空；多个用逗号分隔"
            setSingleLine(true)
        }
        val modeButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnGroupMode = Button(this).apply { text = "按分组" }
        val btnNearbyMode = Button(this).apply { text = "按距离选人" }
        modeButtons.addView(btnGroupMode, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) })
        modeButtons.addView(btnNearbyMode, LinearLayout.LayoutParams(0, dp(42), 1f))
        val groupTagRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val groupTagScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(groupTagRow)
        }
        val nearbyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val nearbyHeaderRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val nearbyLimitInput = EditText(this).apply {
            hint = "人数"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText("20")
        }
        val nearbyPreviewButton = Button(this).apply { text = "查询附近成员" }
        nearbyHeaderRow.addView(nearbyLimitInput, LinearLayout.LayoutParams(dp(86), dp(42)))
        nearbyHeaderRow.addView(nearbyPreviewButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(8) })
        val nearbyStatusView = TextView(this).apply {
            setTextColor(0xFF64748B.toInt())
            textSize = 12f
            text = "按距离模式会根据城池坐标列出最近成员。"
        }
        val nearbyList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val selectedNearbyUids = linkedSetOf<Long>()

        fun syncModeUi(nearbyMode: Boolean) {
            nearbyPanel.visibility = if (nearbyMode) View.VISIBLE else View.GONE
            btnGroupMode.alpha = if (nearbyMode) 0.6f else 1f
            btnNearbyMode.alpha = if (nearbyMode) 1f else 0.6f
        }

        fun refreshGroupTags() {
            groupTagRow.removeAllViews()
            listOf("全员").plus(groupTags).forEach { group ->
                val button = Button(this).apply {
                    text = group
                    textSize = 12f
                    setOnClickListener {
                        if (group == "全员") {
                            groupsInput.setText("")
                            return@setOnClickListener
                        }
                        val values = groupsInput.text?.toString().orEmpty()
                            .split(',', '，', ';', '；', '、')
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .toMutableList()
                        if (values.contains(group)) {
                            values.remove(group)
                        } else {
                            values.add(group)
                        }
                        groupsInput.setText(values.distinct().joinToString(","))
                    }
                }
                groupTagRow.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                    marginEnd = dp(8)
                })
            }
        }

        fun renderNearbyPlayers(players: List<LocalNearbyTaskPlayer>) {
            nearbyList.removeAllViews()
            selectedNearbyUids.clear()
            if (players.isEmpty()) {
                nearbyList.addView(TextView(this).apply {
                    text = "当前没有可用于分配的成员。"
                    setTextColor(0xFF64748B.toInt())
                    textSize = 12f
                })
                return
            }
            players.forEachIndexed { index, player ->
                val checkBox = CheckBox(this).apply {
                    text = "${player.name} · ${player.groupName} · ${formatCityId(player.wid)} · 距离 ${"%.1f".format(player.distance)} · 势力 ${player.power}"
                    isChecked = index < 20
                    if (isChecked) selectedNearbyUids += player.uid
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedNearbyUids += player.uid else selectedNearbyUids -= player.uid
                    }
                }
                nearbyList.addView(checkBox)
            }
        }

        nearbyPreviewButton.setOnClickListener {
            runCatching {
                val group = groupsInput.text?.toString().orEmpty()
                    .split(',', '，', ';', '；', '、')
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                val limit = nearbyLimitInput.text?.toString()?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                LocalStzbRepository.loadTaskNearbyPlayers(posInput.text?.toString().orEmpty(), limit, group)
            }.onSuccess { players ->
                nearbyStatusView.text = "已找到 ${players.size} 名附近成员，默认勾选前 20 名。"
                renderNearbyPlayers(players)
            }.onFailure {
                nearbyStatusView.text = it.message ?: "查询失败"
            }
        }

        btnGroupMode.setOnClickListener { syncModeUi(false) }
        btnNearbyMode.setOnClickListener { syncModeUi(true) }
        refreshGroupTags()
        syncModeUi(false)
        nearbyPanel.addView(nearbyHeaderRow)
        nearbyPanel.addView(nearbyStatusView)
        nearbyPanel.addView(ScrollView(this).apply {
            addView(nearbyList)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)).apply {
                topMargin = dp(8)
            }
        })
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
            addView(nameInput)
            addView(posInput)
            addView(timeInput)
            addView(groupsInput)
            addView(groupTagScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
            addView(modeButtons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })
            addView(nearbyPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })
        }
        AlertDialog.Builder(this)
            .setTitle("新建工程任务")
            .setMessage("网页端同款任务流：录入坐标后，可按分组创建，也可先查询附近成员再勾选生成。")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("取消", null)
            .setPositiveButton("创建", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val title = nameInput.text?.toString().orEmpty()
                        val pos = posInput.text?.toString().orEmpty()
                        val taskTime = parseTaskTimeInput(timeInput.text?.toString().orEmpty())
                        val groups = groupsInput.text?.toString().orEmpty()
                            .split(',', '，', ';', '；', '、')
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        runCatching {
                            LocalStzbRepository.createSiegeTask(
                                name = title,
                                posRaw = pos,
                                groups = groups,
                                taskTime = taskTime,
                                targetUids = if (nearbyPanel.visibility == View.VISIBLE) selectedNearbyUids.toList() else emptyList(),
                            )
                        }.onSuccess {
                            dismiss()
                            loadTaskAttendance()
                        }.onFailure { err ->
                            posInput.error = err.message ?: "创建失败"
                        }
                    }
                }
            }
            .show()
    }

    private fun openSiegeTaskDetail(taskId: Long) {
        selectedSiegeTaskId = taskId
        taskAttendanceSubPage = TaskAttendanceSubPage.OVERVIEW
        loadTaskAttendance()
    }

    private fun closeSiegeTaskDetail() {
        selectedSiegeTaskId = null
        taskAttendanceSubPage = TaskAttendanceSubPage.HOME
        loadTaskAttendance()
    }

    private fun createSiegeTaskDetailCard(
        task: LocalSiegeTask,
        rows: List<LocalTaskAttendanceRow>,
        battles: List<LocalTaskBattleRow>,
    ): InfoCardItem {
        val attended = rows.count { it.atkNum > 0 || it.disNum > 0 }
        val atk = rows.sumOf { it.atkNum }
        val dis = rows.sumOf { it.disNum }
        val timeText = if (task.taskTime > 0) formatTime(task.taskTime) else "未设时间"
        return InfoCardItem(
            title = "考勤详情 - ${task.name}",
            badgeText = "已展开",
            badgeColor = 0xFF1D4ED8.toInt(),
            metaText = "坐标 ${formatCityId(task.cityId)}  ·  时间 $timeText  ·  战报 ${battles.size}",
            extraText = "目标 ${rows.size}  ·  实到 $attended  ·  主力 $atk  ·  拆迁 $dis",
            detailText = """
                任务名：${task.name}
                任务时间：$timeText
                坐标：${formatCityId(task.cityId)}
                WID：${task.cityId}
                目标分组：${task.targetGroups.ifBlank { "全员" }}
                指定成员：${if (task.targetUids.isBlank()) "未指定" else task.targetUids}
                目标人数：${task.targetUserNum}
                已出战：${task.completeUserNum}
                当前成员明细：${rows.size}
                该城战报：${battles.size}
            """.trimIndent(),
            skillTags = listOf("详情", "统计", "战报", "导出"),
            progressValue = if (task.targetUserNum > 0) (task.completeUserNum * 100 / task.targetUserNum).coerceIn(0, 100) else 0,
            progressColor = 0xFF1D4ED8.toInt(),
        )
    }

    private fun createSiegeTaskActionCard(
        title: String,
        detail: String,
        actionKey: String,
        actionId: Long,
        color: Int,
        badge: String,
    ): InfoCardItem {
        return InfoCardItem(
            title = title,
            badgeText = badge,
            badgeColor = color,
            metaText = detail,
            extraText = "点击执行当前工程任务动作",
            detailText = detail,
            actionKey = actionKey,
            actionId = actionId,
        )
    }

    private fun LocalTaskAttendanceRow.toTaskDetailInfoCard(rank: Int): InfoCardItem {
        val attended = atkNum > 0 || disNum > 0
        return InfoCardItem(
            title = "$rank. ${name.ifBlank { uid.toString() }}",
            badgeText = if (attended) "出战" else "缺勤",
            badgeColor = if (attended) 0xFF059669.toInt() else 0xFFDC2626.toInt(),
            metaText = "${groupName.ifBlank { "未分组" }}  ·  主力 $atkNum  ·  拆迁 $disNum",
            extraText = "主力队 $atkTeamNum  ·  拆迁队 $disTeamNum  ·  最近 ${formatTime(lastBattleTime)}",
            detailText = """
                名字：${name.ifBlank { "-" }}
                UID：$uid
                分组：${groupName.ifBlank { "未分组" }}
                主力次数：$atkNum
                拆迁次数：$disNum
                主力队伍：$atkTeamNum
                拆迁队伍：$disTeamNum
                总战报：$battles
                攻城武勋：$gongxun
                原武勋：$wuxun
                势力：$power
                最近战报：${formatTime(lastBattleTime)}
            """.trimIndent(),
            skillTags = listOf("主力 $atkNum", "拆迁 $disNum", "主力队 $atkTeamNum", "拆迁队 $disTeamNum"),
            progressValue = if (attended) 100 else 0,
            progressColor = if (attended) 0xFF059669.toInt() else 0xFFDC2626.toInt(),
        )
    }

    private fun LocalTaskBattleRow.toTaskBattleInfoCard(rank: Int): InfoCardItem {
        val garrisonText = if (garrison == 1) "拆迁" else "主力"
        val resultStyle = when (result) {
            1, 7, 11 -> ResultStyle.WIN
            2, 6, 12 -> ResultStyle.LOSE
            else -> ResultStyle.DRAW
        }
        return InfoCardItem(
            title = "$rank. ${attackerName.ifBlank { "未知攻方" }}",
            badgeText = localResultText(result),
            badgeColor = resultBadgeColor(resultStyle),
            metaText = "${formatTime(time)}  ·  $garrisonText  ·  ${attackerUnion.ifBlank { "无同盟" }}",
            extraText = heroes.ifBlank { "未解析到武将" },
            detailText = """
                战报ID：$battleId
                时间：${formatTime(time)}
                攻方：${attackerName.ifBlank { "-" }}
                同盟：${attackerUnion.ifBlank { "-" }}
                类型：$garrisonText
                结果：${localResultText(result)}
                武将：${heroes.ifBlank { "-" }}
            """.trimIndent(),
            skillTags = listOf(garrisonText, localResultText(result)),
        )
    }

    private fun confirmRunSiegeTaskStatistics(taskId: Long) {
        val task = LocalStzbRepository.loadSiegeTask(taskId) ?: return
        val message = """
            任务：${task.name}
            坐标：${formatCityId(task.cityId)}
            时间：${if (task.taskTime > 0) formatTime(task.taskTime) else "未设时间"}
            分组：${task.targetGroups.ifBlank { "全员" }}
            目标人数：${task.targetUserNum}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("开始统计考勤")
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认统计") { _, _ ->
                setStatus("工程考勤统计中...")
                thread(name = "stzb-task-statistics") {
                    val result = runCatching { LocalStzbRepository.refreshSiegeTaskStatistics(taskId) }
                    runOnUiThread {
                        result.onSuccess { summary ->
                            setStatus("统计完成：实到 ${summary.completeUsers} 人")
                            loadTaskAttendance()
                        }.onFailure {
                            setStatus("统计失败：${it.message}")
                        }
                    }
                }
            }
            .show()
    }

    private fun confirmDeleteSiegeTask(taskId: Long) {
        val task = LocalStzbRepository.loadSiegeTask(taskId) ?: return
        AlertDialog.Builder(this)
            .setTitle("删除工程任务")
            .setMessage("确认删除「${task.name}」吗？删除后该任务将不再出现在考勤列表。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                LocalStzbRepository.deleteSiegeTask(taskId)
                if (selectedSiegeTaskId == taskId) selectedSiegeTaskId = null
                loadTaskAttendance()
            }
            .show()
    }

    private fun exportSiegeTaskCsv(taskId: Long) {
        setStatus("导出工程考勤中...")
        thread(name = "stzb-task-export") {
            val result = runCatching {
                val task = LocalStzbRepository.loadSiegeTask(taskId) ?: error("任务不存在")
                val rows = LocalStzbRepository.loadTaskAttendanceForTask(taskId, 0)
                val dir = File(getExternalFilesDir(null) ?: filesDir, "exports").apply { mkdirs() }
                val safeName = task.name.replace(Regex("""[\\/:*?"<>|]"""), "_")
                val outFile = File(dir, "${safeName}_考勤表.csv")
                val csv = buildString {
                    append('\uFEFF')
                    append("名字,分组,主力队,拆迁队,主力次数,拆迁次数,状态\n")
                    rows.sortedByDescending { it.atkNum + it.disNum }.forEach { row ->
                        append(row.name).append(',')
                        append(row.groupName).append(',')
                        append(row.atkTeamNum).append(',')
                        append(row.disTeamNum).append(',')
                        append(row.atkNum).append(',')
                        append(row.disNum).append(',')
                        append(if (row.atkNum > 0 || row.disNum > 0) "出战" else "缺勤")
                        append('\n')
                    }
                }
                outFile.writeText(csv, Charsets.UTF_8)
                outFile
            }
            runOnUiThread {
                result.onSuccess { file ->
                    setStatus("已导出工程考勤：${file.name}")
                    AlertDialog.Builder(this)
                        .setTitle("导出成功")
                        .setMessage("CSV 已导出到：\n${file.absolutePath}")
                        .setPositiveButton("知道了", null)
                        .show()
                }.onFailure {
                    setStatus("导出失败：${it.message}")
                }
            }
        }
    }

    private fun parseTaskTimeInput(raw: String): Long {
        val text = raw.trim()
        if (text.isBlank()) return 0L
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA),
            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA),
        )
        formats.forEach { format ->
            runCatching {
                format.isLenient = false
                val parsed = format.parse(text)
                if (parsed != null) return parsed.time / 1000
            }
        }
        return text.toLongOrNull() ?: throw IllegalArgumentException("任务时间格式错误，请输入 yyyy-MM-dd HH:mm")
    }

    private fun loadStateRegions() {
        setStatus("加载州郡分布中...")
        setModuleHeader("州郡分布", "对齐网页端州郡页：支持全部成员/仅指定团切换、团筛选、人数/势力维度切换，以及州/同盟/分组统计。")
        battleFilterPanel.visibility = View.GONE
        syncStateRegionSubTabs(emptyList())
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-state-regions") {
            val result = runCatching {
                val powerRanks = LocalStzbRepository.loadPlayerPowerRanks(0)
                val zonePlayers = LocalStzbRepository.loadZonePlayers(0)
                val unionRanks = LocalStzbRepository.loadUnionRanks(0)
                val zoneByUid = zonePlayers.associateBy { it.uid }
                val unionNameById = unionRanks.associate { it.unionId.toLong() to it.name }
                val rawMembers = powerRanks.map { row ->
                    val zone = zoneByUid[row.userId]
                    val unionName = unionNameById[zone?.unionId ?: 0L].orEmpty().ifBlank { "同盟未知" }
                    val groupName = namePrefixGroup(row.name)
                    StateRegionMember(
                        uid = row.userId,
                        name = row.name,
                        power = row.power,
                        region = row.region,
                        state = stateRegionName(row.region),
                        unionName = unionName,
                        groupName = groupName,
                    )
                }
                val allGroups = rawMembers.map { it.groupName }.filter { it.isNotBlank() && it != "未分组" }.distinct().sorted()
                val members = rawMembers.filter { stateRegionScope != "group" || stateRegionGroup.isBlank() || it.groupName == stateRegionGroup }
                val summary = StateRegionSummary(
                    totalPlayers = members.size,
                    totalPower = members.sumOf { it.power },
                    stateCount = members.map { it.state }.filter { it != "未知" }.distinct().size,
                    allianceCount = members.map { it.unionName }.filter { it != "同盟未知" }.distinct().size,
                    groupCount = rawMembers.map { it.groupName }.distinct().size,
                    groupedPlayers = members.count { it.groupName != "未分组" },
                )
                val stateRows = members.groupBy { it.state }.map { (state, rows) ->
                    StateRegionStateRow(
                        state = state,
                        region = rows.firstOrNull()?.region ?: 0,
                        playerCount = rows.size,
                        totalPower = rows.sumOf { it.power },
                        avgPower = if (rows.isEmpty()) 0.0 else rows.sumOf { it.power }.toDouble() / rows.size,
                        maxPower = rows.maxOfOrNull { it.power } ?: 0L,
                    )
                }.sortedWith(
                    if (stateRegionMetric == "total_power") {
                        compareByDescending<StateRegionStateRow> { it.totalPower }.thenByDescending { it.playerCount }.thenBy { it.region }
                    } else {
                        compareByDescending<StateRegionStateRow> { it.playerCount }.thenByDescending { it.totalPower }.thenBy { it.region }
                    }
                )
                val groupRows = members.groupBy { "${it.unionName}__${it.groupName}" }.map { (_, rows) ->
                    val states = rows.groupBy { it.state }.mapValues { it.value.size }
                    StateRegionGroupRow(
                        allianceName = rows.firstOrNull()?.unionName ?: "同盟未知",
                        groupName = rows.firstOrNull()?.groupName ?: "未分组",
                        playerCount = rows.size,
                        totalPower = rows.sumOf { it.power },
                        avgPower = if (rows.isEmpty()) 0.0 else rows.sumOf { it.power }.toDouble() / rows.size,
                        maxPower = rows.maxOfOrNull { it.power } ?: 0L,
                        stateSummary = states.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(4).joinToString(" / ") { "${it.key}${it.value}人" },
                    )
                }.sortedWith(compareByDescending<StateRegionGroupRow> { it.playerCount }.thenByDescending { it.totalPower }.thenBy { it.allianceName }.thenBy { it.groupName })
                val allianceRows = members.groupBy { it.unionName }.map { (_, rows) ->
                    val states = rows.groupBy { it.state }.mapValues { it.value.size }
                    val groups = rows.groupBy { it.groupName }.mapValues { it.value.size }
                    StateRegionAllianceRow(
                        allianceName = rows.firstOrNull()?.unionName ?: "同盟未知",
                        playerCount = rows.size,
                        totalPower = rows.sumOf { it.power },
                        avgPower = if (rows.isEmpty()) 0.0 else rows.sumOf { it.power }.toDouble() / rows.size,
                        maxPower = rows.maxOfOrNull { it.power } ?: 0L,
                        stateSummary = states.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(4).joinToString(" / ") { "${it.key}${it.value}人" },
                        groupSummary = groups.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(4).joinToString(" / ") { "${it.key}${it.value}人" },
                    )
                }.sortedWith(compareByDescending<StateRegionAllianceRow> { it.playerCount }.thenByDescending { it.totalPower }.thenBy { it.allianceName })
                StateRegionUi(
                    cards = buildStateRegionCards(summary, stateRows, groupRows, allianceRows),
                    summary = summary,
                    groupOptions = allGroups,
                    stateRows = stateRows,
                    groupRows = groupRows,
                    allianceRows = allianceRows,
                )
            }
            runOnUiThread {
                result.onSuccess { ui ->
                    stateRegionGroupOptions = ui.groupOptions
                    syncStateRegionSubTabs(ui.groupOptions)
                    infoCards = ui.cards
                    moduleKpiView.text = "人数 ${ui.summary.totalPlayers}  /  总势力 ${ui.summary.totalPower}  /  州 ${ui.summary.stateCount}  /  同盟 ${ui.summary.allianceCount}"
                    contentView.text = "州郡分布\n${if (stateRegionScope == "group") "仅指定团 · ${stateRegionGroup.ifBlank { "全部团" }}" else "全部成员"}，当前位于${stateRegionPage.label}。"
                    battleListView.adapter = InfoCardAdapter(ui.cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("州郡分布：州 ${ui.stateRows.size} 条 / 同盟 ${ui.allianceRows.size} 条 / 分组 ${ui.groupRows.size} 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载州郡分布失败")
                }
            }
        }
    }

    private fun buildStateRegionCards(
        summary: StateRegionSummary,
        stateRows: List<StateRegionStateRow>,
        groupRows: List<StateRegionGroupRow>,
        allianceRows: List<StateRegionAllianceRow>,
    ): List<InfoCardItem> {
        val cards = mutableListOf<InfoCardItem>()
        cards += listOf(
            stateRegionStatCard("统计人数", summary.totalPlayers.toString(), "全部范围内纳入统计的人数", 0xFF2563EB.toInt()),
            stateRegionStatCard("总势力值", summary.totalPower.toString(), "网页端同口径汇总", 0xFF8F6A2A.toInt()),
            stateRegionStatCard("覆盖州数", summary.stateCount.toString(), "当前命中州数量", 0xFF0EA5E9.toInt()),
            stateRegionStatCard("同盟数量", summary.allianceCount.toString(), "按战区/排行合并后的同盟数", 0xFF1D4ED8.toInt()),
            stateRegionStatCard("分组数量", summary.groupCount.toString(), "成员分组统计", 0xFF7C3AED.toInt()),
            stateRegionStatCard("已分组成员", summary.groupedPlayers.toString(), "已有团归属的成员数量", 0xFF059669.toInt()),
        )
        cards += InfoCardItem(
            title = "州郡页说明",
            badgeText = if (stateRegionScope == "group") "仅指定团" else "全部成员",
            badgeColor = 0xFF475569.toInt(),
            metaText = "顶部子标签支持切换范围、团筛选、展示页签和排序维度。",
            extraText = "当前州排序按${if (stateRegionMetric == "total_power") "总势力值" else "人数"}，与网页端“州地图着色维度”含义保持一致。",
            skillTags = listOf(stateRegionPage.label, if (stateRegionMetric == "total_power") "势力值" else "人数"),
        )
        when (stateRegionPage) {
            StateRegionPage.OVERVIEW -> {
                cards += InfoCardItem(
                    title = "州势力 Top",
                    badgeText = if (stateRegionMetric == "total_power") "按势力" else "按人数",
                    badgeColor = 0xFF2563EB.toInt(),
                    metaText = "承接网页端州地图和右侧柱状排行。",
                    extraText = "这里用高密度州卡承接地图信息，保留人数、总势力、平均势力和最高势力。",
                )
                cards += stateRows.take(8).mapIndexed { index, row -> row.toStateRegionCard(index + 1, stateRegionMetric) }
                cards += InfoCardItem(
                    title = "同盟 / 分组 Top",
                    badgeText = "概览",
                    badgeColor = 0xFF7C3AED.toInt(),
                    metaText = "承接网页端下半区“同盟 / 分组统计”和人数 Top。",
                    extraText = "先展示前几条重点数据，详细列表在其他子页查看。",
                )
                cards += allianceRows.take(6).mapIndexed { index, row -> row.toAllianceRegionCard(index + 1) }
                cards += groupRows.take(6).mapIndexed { index, row -> row.toGroupRegionCard(index + 1) }
            }
            StateRegionPage.STATES -> {
                cards += InfoCardItem(
                    title = "各州人数 / 势力值",
                    badgeText = "州表",
                    badgeColor = 0xFF2563EB.toInt(),
                    metaText = "字段顺序对齐网页端：州、人数、总势力、平均势力、最高势力。",
                    extraText = "通过顶部切换人数/势力维度，改变排序和重点展示口径。",
                )
                cards += stateRows.mapIndexed { index, row -> row.toStateRegionCard(index + 1, stateRegionMetric) }
            }
            StateRegionPage.ALLIANCES -> {
                cards += InfoCardItem(
                    title = "同盟统计",
                    badgeText = "同盟",
                    badgeColor = 0xFF1D4ED8.toInt(),
                    metaText = "承接网页端同盟聚合结果，展示州分布和团分布。",
                    extraText = "同盟列表按人数、总势力排序。",
                )
                cards += allianceRows.mapIndexed { index, row -> row.toAllianceRegionCard(index + 1) }
            }
            StateRegionPage.GROUPS -> {
                cards += InfoCardItem(
                    title = "同盟 / 分组统计",
                    badgeText = "分组",
                    badgeColor = 0xFF7C3AED.toInt(),
                    metaText = "字段顺序对齐网页端：同盟、分组、人数、势力、州分布。",
                    extraText = "当范围切到“仅指定团”时，这里只展示当前团数据。",
                )
                cards += groupRows.mapIndexed { index, row -> row.toGroupRegionCard(index + 1) }
            }
        }
        return cards
    }

    private fun stateRegionStatCard(title: String, value: String, hint: String, color: Int): InfoCardItem {
        return InfoCardItem(
            title = title,
            badgeText = "统计卡",
            badgeColor = color,
            metaText = value,
            extraText = hint,
            skillTags = listOf(title),
        )
    }

    private fun syncStateRegionSubTabs(groupOptions: List<String>) {
        if (currentModule != MODULE_STATE_REGIONS) return
        taskSubTabContainer.removeAllViews()
        taskSubTabScroll.visibility = View.VISIBLE
        fun addTab(label: String, active: Boolean, actionKey: String, arg: String) {
            val button = Button(this).apply {
                text = label
                textSize = 12f
                alpha = if (active) 1f else 0.76f
                setPadding(dp(12), 0, dp(12), 0)
                minHeight = dp(38)
                minimumHeight = dp(38)
                setOnClickListener {
                    when (actionKey) {
                        ACTION_SWITCH_STATE_REGION_PAGE -> switchStateRegionPage(arg)
                        ACTION_SWITCH_STATE_REGION_SCOPE -> switchStateRegionScope(arg)
                        ACTION_SWITCH_STATE_REGION_GROUP -> switchStateRegionGroup(arg)
                        ACTION_SWITCH_STATE_REGION_METRIC -> switchStateRegionMetric(arg)
                    }
                }
            }
            taskSubTabContainer.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                if (taskSubTabContainer.childCount > 0) marginStart = dp(8)
            })
        }
        StateRegionPage.entries.forEach { page -> addTab(page.label, page == stateRegionPage, ACTION_SWITCH_STATE_REGION_PAGE, page.key) }
        addTab("全部成员", stateRegionScope == "all", ACTION_SWITCH_STATE_REGION_SCOPE, "all")
        addTab("仅指定团", stateRegionScope == "group", ACTION_SWITCH_STATE_REGION_SCOPE, "group")
        if (stateRegionScope == "group") {
            addTab("全部团", stateRegionGroup.isBlank(), ACTION_SWITCH_STATE_REGION_GROUP, "")
            addTab(
                if (stateRegionGroup.isBlank()) "选择团" else "选择团：$stateRegionGroup",
                false,
                ACTION_PICK_STATE_REGION_GROUP,
                "",
            )
        }
        addTab("人数", stateRegionMetric == "player_count", ACTION_SWITCH_STATE_REGION_METRIC, "player_count")
        addTab("势力值", stateRegionMetric == "total_power", ACTION_SWITCH_STATE_REGION_METRIC, "total_power")
    }

    private fun switchStateRegionPage(pageKey: String) {
        val page = StateRegionPage.entries.firstOrNull { it.key == pageKey } ?: return
        if (page == stateRegionPage) return
        stateRegionPage = page
        loadStateRegions()
    }

    private fun switchStateRegionScope(scope: String) {
        if (scope == stateRegionScope) return
        stateRegionScope = scope
        if (scope != "group") {
            stateRegionGroup = ""
        }
        loadStateRegions()
    }

    private fun switchStateRegionGroup(group: String) {
        if (group == stateRegionGroup) return
        stateRegionGroup = group
        loadStateRegions()
    }

    private fun showStateRegionGroupPicker() {
        if (stateRegionGroupOptions.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("选择团")
                .setMessage("当前没有可筛选的团数据。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        val input = EditText(this).apply {
            hint = "输入团名前缀筛选"
            setSingleLine(true)
        }
        val listView = ListView(this)
        val filtered = stateRegionGroupOptions.toMutableList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filtered)
        listView.adapter = adapter
        root.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)).apply { topMargin = dp(10) })
        val dialog = AlertDialog.Builder(this)
            .setTitle("选择团")
            .setView(root)
            .setNegativeButton("取消", null)
            .create()
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val keyword = s?.toString().orEmpty().trim()
                filtered.clear()
                filtered += stateRegionGroupOptions.filter { keyword.isBlank() || it.contains(keyword, ignoreCase = true) }
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        listView.setOnItemClickListener { _, _, position, _ ->
            val group = filtered.getOrNull(position) ?: return@setOnItemClickListener
            dialog.dismiss()
            switchStateRegionGroup(group)
        }
        dialog.show()
    }

    private fun switchStateRegionMetric(metric: String) {
        if (metric == stateRegionMetric) return
        stateRegionMetric = metric
        loadStateRegions()
    }

    private fun buildBattleMonitorSearchQuery(): String {
        return listOf(
            filterPlayerInput.text?.toString().orEmpty().trim(),
            filterUnionInput.text?.toString().orEmpty().trim(),
            filterTeamKeywordInput.text?.toString().orEmpty().trim(),
            filterWidInput.text?.toString().orEmpty().trim(),
        ).filter { it.isNotBlank() }.joinToString(" ").lowercase(Locale.CHINA)
    }

    private fun filterBattleMonitorSnapshots(snapshots: List<LocalBattleMonitorSnapshot>): List<LocalBattleMonitorSnapshot> {
        val query = battleMonitorSearchQuery.trim().lowercase(Locale.CHINA)
        if (query.isBlank()) return snapshots
        return snapshots.mapNotNull { snapshot ->
            val filteredMoves = filterBattleMonitorMoves(snapshot.moves)
            val haystack = buildString {
                append(snapshot.plainText).append(' ')
                append(snapshot.sourceLabel).append(' ')
                append(snapshot.marker).append(' ')
            }.lowercase(Locale.CHINA)
            if (filteredMoves.isNotEmpty() || haystack.contains(query)) snapshot.copy(moves = filteredMoves) else null
        }
    }

    private fun filterBattleMonitorMoves(moves: List<LocalTeamMove>): List<LocalTeamMove> {
        val query = battleMonitorSearchQuery.trim().lowercase(Locale.CHINA)
        if (query.isBlank()) return moves
        return moves.filter { move ->
            listOf(
                move.teamId.toString(),
                move.ownerName,
                move.ownerUnion,
                move.ownerUid.toString(),
                move.fromXy,
                move.toXy,
                move.currentXy,
                move.toWid.toString(),
                move.fromWid.toString(),
                move.currentWid.toString(),
            ).joinToString(" ").lowercase(Locale.CHINA).contains(query)
        }
    }

    private fun battleMonitorStateText(snapshot: LocalBattleMonitorSnapshot?): String {
        if (snapshot == null) return "-"
        return if (snapshot.mapStates.isNotEmpty()) "状态 ${snapshot.mapStates.size}" else "实时事件"
    }

    private fun ensureBattleMonitorAutoRefresh() {
        battleMonitorRefreshHandler.removeCallbacksAndMessages(null)
        if (currentModule != MODULE_BATTLE_MONITOR) return
        battleMonitorRefreshHandler.postDelayed(object : Runnable {
            override fun run() {
                if (currentModule == MODULE_BATTLE_MONITOR && !isFinishing && !isDestroyed) {
                    loadBattlefieldMonitor()
                    battleMonitorRefreshHandler.postDelayed(this, 5000)
                }
            }
        }, 5000)
    }

    private fun normalizeStateRegionName(value: String): String {
        return value.replace("\\s+".toRegex(), "")
    }

    private fun namePrefixGroup(name: String): String {
        val text = name.trim()
        if (text.isBlank()) return "未分组"
        val prefixRegex = Regex("""^\s*([\u4e00-\u9fffA-Za-z0-9]{1,6})\s*[丨|｜、/／\\丶·•･・:：\-_ —\s灬乄の〆メ~～]+\s*.+$""")
        prefixRegex.matchEntire(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val seps = listOf('丨', '|', '｜', '、', '/', '／', '\\', '丶', '·', '•', '･', '・', ':', '：', '-', '—', '_', ' ', '灬', '乄', 'の', '〆', 'メ', '~', '～')
        var splitPos = -1
        for (sep in seps) {
            val pos = text.indexOf(sep)
            if (pos > 0 && (splitPos < 0 || pos < splitPos)) {
                splitPos = pos
            }
        }
        if (splitPos > 0) {
            val prefix = text.substring(0, splitPos).trim()
            if (prefix.isNotBlank()) return prefix
        }
        return "未分组"
    }

    private fun stateRegionName(region: Int): String {
        return when (region) {
            1 -> "凉州"
            2 -> "并州"
            3 -> "冀州"
            4 -> "幽州"
            5 -> "青州"
            6 -> "徐州"
            7 -> "兖州"
            8 -> "司隶"
            9 -> "豫州"
            10 -> "雍州"
            11 -> "荆州"
            12 -> "扬州"
            13 -> "益州"
            else -> "未知"
        }
    }

    private fun loadDbSync() {
        setStatus("加载同步表统计中...")
        setModuleHeader("数据同步", "展示 90005 db_sync 各表事件统计。")
        battleFilterPanel.visibility = View.GONE
        contentScrollView.visibility = View.GONE
        battleListView.visibility = View.GONE
        thread(name = "stzb-db-sync") {
            val result = runCatching {
                LocalStzbRepository.loadDbSyncTableStats(80).map { it.toInfoCard() }
            }
            runOnUiThread {
                result.onSuccess { cards ->
                    infoCards = cards
                    moduleKpiView.text = "同步卡 ${cards.size}"
                    contentView.text = "数据同步\n当前按数据表输出同步事件统计卡。"
                    battleListView.adapter = InfoCardAdapter(cards)
                    battleListView.visibility = View.VISIBLE
                    setStatus("数据同步：${cards.size} 条")
                }.onFailure {
                    infoCards = emptyList()
                    battleListView.adapter = null
                    contentScrollView.visibility = View.VISIBLE
                    contentView.text = "读取失败：${it.message}"
                    setStatus("加载同步表统计失败")
                }
            }
        }
    }

    private fun StringBuilder.appendRankingSection(title: String, rows: List<LocalRankingRow>) {
        appendLine(title)
        if (rows.isEmpty()) {
            appendLine("  暂无数据")
            return
        }
        rows.forEachIndexed { idx, row ->
            val group = if (row.groupName.isBlank()) "" else " ${row.groupName}"
            appendLine(
                "${idx + 1}. ${row.name.ifBlank { "-" }}$group  ${row.value}  " +
                    "${row.battles}战 胜率${"%.1f".format(row.winRate)}%"
            )
        }
    }

    private fun setStatus(text: String) {
        statusView.text = "状态：$text"
    }

    private fun setModuleHeader(title: String, subtitle: String) {
        moduleTitleView.text = title
        moduleSubtitleView.text = subtitle
        val counts = runCatching { LocalStzbRepository.counts() }.getOrNull()
        moduleKpiView.text = if (counts == null) {
            "完整战报 --  /  行军 --  /  成员 --  /  地图 --"
        } else {
            "完整战报 ${counts.fullBattles}  /  通知 ${counts.battleNotices}  /  行军 ${counts.monitorMoves}  /  成员 ${counts.teamUsers}  /  地图 ${counts.mapCells}"
        }
    }

    private fun moduleDescription(title: String): String {
        return when {
            title.contains("战报") -> "筛选、分页和详情入口，优先读取 10/92 完整战报，缺失时使用 2100 通知兜底。"
            title.contains("行军") -> "展示 5028 行军相关数据，用于快速判断队伍方向、坐标和到达信息。"
            title.contains("排行") -> "聚合玩家武勋、同盟武勋、势力排行与 700 排行专表。"
            title.contains("同盟") -> "成员总览、分组统计与贡献/势力等管理视角。"
            title.contains("阵容") || title.contains("武将") -> "武将频率、组合胜率、玩家队伍等分析型数据。"
            title.contains("团报告") -> "面向组织管理的分组与成员战斗贡献统计。"
            title.contains("地图") || title.contains("州") -> "地图格子、州郡分布与区域态势统计。"
            title.contains("公告") || title.contains("消息") || title.contains("同步") -> "事件流、公告、聊天、同步记录和通用业务记录历史。"
            title.contains("模拟") -> "本机战斗模拟资源与可运行模拟结果。"
            else -> "本机 SQLite 已迁移数据的摘要、列表与诊断视图。"
        }
    }

    private fun teamReportPeriodLabel(period: String): String {
        return when (period) {
            "today" -> "今日"
            "yesterday" -> "昨日"
            "week" -> "本周"
            "lastweek" -> "上周"
            else -> "全部"
        }
    }

    private fun formatTime(ts: Long): String {
        if (ts <= 0L) return "--:--"
        val millis = if (ts < 10_000_000_000L) ts * 1000 else ts
        return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))
    }

    private fun formatCityId(cityId: Int): String {
        if (cityId <= 0) return "-"
        return "${cityId / 10000},${cityId % 10000}"
    }

    companion object {
        private const val ACTION_CREATE_SIEGE_TASK = "create_siege_task"
        private const val ACTION_OPEN_SIEGE_TASK = "open_siege_task"
        private const val ACTION_REFRESH_SIEGE_TASKS = "refresh_siege_tasks"
        private const val ACTION_CLOSE_SIEGE_TASK_DETAIL = "close_siege_task_detail"
        private const val ACTION_RUN_SIEGE_TASK_STATISTICS = "run_siege_task_statistics"
        private const val ACTION_EXPORT_SIEGE_TASK_CSV = "export_siege_task_csv"
        private const val ACTION_DELETE_SIEGE_TASK = "delete_siege_task"
        private const val ACTION_TOGGLE_TEAM_USER_GROUP = "toggle_team_user_group"
        private const val ACTION_OPEN_TEAM_USER = "open_team_user"
        private const val ACTION_SWITCH_TEAM_REPORT_PERIOD = "switch_team_report_period"
        private const val ACTION_SWITCH_TEAM_REPORT_DIM = "switch_team_report_dim"
        private const val ACTION_SWITCH_TEAM_REPORT_GROUP = "switch_team_report_group"
        private const val ACTION_EXPORT_TEAM_REPORT_CSV = "export_team_report_csv"
        private const val ACTION_SWITCH_STATE_REGION_PAGE = "switch_state_region_page"
        private const val ACTION_SWITCH_STATE_REGION_SCOPE = "switch_state_region_scope"
        private const val ACTION_SWITCH_STATE_REGION_GROUP = "switch_state_region_group"
        private const val ACTION_PICK_STATE_REGION_GROUP = "pick_state_region_group"
        private const val ACTION_SWITCH_STATE_REGION_METRIC = "switch_state_region_metric"
        private const val ACTION_RUN_SIM_SINGLE = "run_sim_single"
        private const val ACTION_RUN_SIM_100 = "run_sim_100"
        private const val ACTION_RUN_SIM_1000 = "run_sim_1000"
        private const val ACTION_EDIT_SIM_BLUE = "edit_sim_blue"
        private const val ACTION_EDIT_SIM_RED = "edit_sim_red"
        const val EXTRA_MODULE = "dashboard_module"
        const val MODULE_OVERVIEW = "overview"
        const val MODULE_ALL_PLAYER_TEAMS = "all_player_teams"
        const val MODULE_BATTLES = "battles"
        const val MODULE_MONITOR = "monitor"
        const val MODULE_BATTLE_MONITOR = "battle_monitor"
        const val MODULE_MESSAGES = "messages"
        const val MODULE_MAP = "map"
        const val MODULE_STATE_REGIONS = "state_regions"
        const val MODULE_ZONE_PLAYERS = "zone_players"
        const val MODULE_RECENT_PACKETS = "recent_packets"
        const val MODULE_RANKING = "ranking"
        const val MODULE_HERO_STATS = "hero_stats"
        const val MODULE_TEAM_USERS = "team_users"
        const val MODULE_GROUPED_WUXUN = "grouped_wuxun"
        const val MODULE_TASK_ATTENDANCE = "task_attendance"
        const val MODULE_ALLIANCE_MEMBER_TEAMS = "alliance_member_teams"
        const val MODULE_TEAM_REPORT = "team_report"
        const val MODULE_PLAYER_PROFILE = "player_profile"
        const val MODULE_ANNOUNCEMENTS = "announcements"
        const val MODULE_DB_SYNC = "db_sync"
        const val MODULE_SIMULATOR = "simulator"
        const val MODULE_TEAM_INDEX_13A2 = "team_index_13a2"
    }

    private data class BattleRows(
        val ids: List<Int>,
        val titles: List<String>,
        val cards: List<BattleCardItem>,
        val source: String,
    )

    private data class MonitorRows(
        val cards: List<MonitorCardItem>,
        val summary: String,
    )

    private data class BattleMonitorUi(
        val cards: List<InfoCardItem>,
        val summary: String,
    )

    private data class TeamIndex13A2Ui(
        val cards: List<InfoCardItem>,
        val summary: String,
        val status: String,
        val parsed: Boolean,
    )

    private data class TaskAttendanceUi(
        val cards: List<InfoCardItem>,
        val taskCount: Int,
        val targetUsers: Int,
        val attendedUsers: Int,
        val selectedTask: LocalSiegeTask?,
        val detailRows: List<LocalTaskAttendanceRow>,
        val battleRows: List<LocalTaskBattleRow>,
    )

    private data class TeamUsersUi(
        val cards: List<InfoCardItem>,
        val summary: TeamUsersUiSummary,
        val groupNames: List<String>,
        val selectedMember: LocalTeamUser?,
        val memberTeams: List<LocalPlayerBattleTeam>,
        val filteredUsers: List<LocalTeamUser>,
    )

    private data class StateRegionUi(
        val cards: List<InfoCardItem>,
        val summary: StateRegionSummary,
        val groupOptions: List<String>,
        val stateRows: List<StateRegionStateRow>,
        val groupRows: List<StateRegionGroupRow>,
        val allianceRows: List<StateRegionAllianceRow>,
    )

    private enum class TaskAttendanceSubPage {
        HOME,
        OVERVIEW,
        MEMBERS,
        BATTLES,
        ACTIONS,
    }

    private enum class TeamUsersSubPage {
        HOME,
        OVERVIEW,
        TEAMS,
    }

    private enum class StateRegionPage(val key: String, val label: String) {
        OVERVIEW("overview", "总览"),
        STATES("states", "州郡"),
        ALLIANCES("alliances", "同盟"),
        GROUPS("groups", "分组"),
    }

    private data class SimulatorHeroEditRow(
        val heroSpinner: Spinner,
        val levelInput: EditText,
        val advanceInput: EditText,
        val skillOne: Spinner,
        val skillTwo: Spinner,
    )

    private enum class FilterMode {
        NONE,
        BATTLES,
        PLAYER_TEAMS,
        BATTLE_MONITOR,
    }

    private data class TeamUsersUiSummary(
        val total: Int,
        val totalPower: Long,
        val totalWuxun: Long,
        val totalWeekContribute: Long,
    )

    private data class StateRegionMember(
        val uid: Long,
        val name: String,
        val power: Long,
        val region: Int,
        val state: String,
        val unionName: String,
        val groupName: String,
    )

    private data class StateRegionSummary(
        val totalPlayers: Int,
        val totalPower: Long,
        val stateCount: Int,
        val allianceCount: Int,
        val groupCount: Int,
        val groupedPlayers: Int,
    )

    private data class StateRegionStateRow(
        val state: String,
        val region: Int,
        val playerCount: Int,
        val totalPower: Long,
        val avgPower: Double,
        val maxPower: Long,
    )

    private data class StateRegionAllianceRow(
        val allianceName: String,
        val playerCount: Int,
        val totalPower: Long,
        val avgPower: Double,
        val maxPower: Long,
        val stateSummary: String,
        val groupSummary: String,
    )

    private data class StateRegionGroupRow(
        val allianceName: String,
        val groupName: String,
        val playerCount: Int,
        val totalPower: Long,
        val avgPower: Double,
        val maxPower: Long,
        val stateSummary: String,
    )

    private data class TeamReportUi(
        val cards: List<InfoCardItem>,
        val groupCount: Int,
        val playerCount: Int,
        val totalPlayers: Int,
        val totalBattles: Int,
        val totalCity: Int,
        val winRate: Double,
        val groupOptions: List<String>,
        val dim: String,
        val period: String,
        val selectedGroup: String,
    )

    private data class BattleCardItem(
        val battleId: Int,
        val timeText: String,
        val typeText: String,
        val resultText: String,
        val attackerText: String,
        val defenderText: String,
        val metaText: String,
        val sourceText: String,
        val resultStyle: ResultStyle,
    )

    private data class MonitorCardItem(
        val title: String,
        val badgeText: String,
        val badgeColor: Int,
        val metaText: String,
        val extraText: String,
        val sourceText: String,
    )

    private data class InfoCardItem(
        val title: String,
        val badgeText: String,
        val badgeColor: Int,
        val metaText: String,
        val extraText: String,
        val detailText: String = "",
        val metricValue: Double? = null,
        val heroNames: List<String> = emptyList(),
        val heroIconIds: List<Long> = emptyList(),
        val heroLarge: Boolean = false,
        val skillTags: List<String> = emptyList(),
        val progressValue: Int? = null,
        val progressColor: Int = 0xFF2563EB.toInt(),
        val actionKey: String = "",
        val actionId: Long = 0L,
        val actionArg: String = "",
    )

    private enum class ResultStyle {
        WIN,
        LOSE,
        DRAW,
    }
}

private object HeroImageLoader {
    private val cache = ConcurrentHashMap<String, android.graphics.Bitmap>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(url: String, onLoaded: (android.graphics.Bitmap) -> Unit) {
        cache[url]?.let {
            onLoaded(it)
            return
        }
        thread(name = "stzb-hero-image-loader") {
            val bitmap = runCatching {
                URL(url).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull() ?: return@thread
            cache[url] = bitmap
            mainHandler.post { onLoaded(bitmap) }
        }
    }
}
