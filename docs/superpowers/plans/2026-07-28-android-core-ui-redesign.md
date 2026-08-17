# ASTZB Android Core UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the ASTZB Android home, battle list, battle detail, live monitor, and ranking experiences as a coherent dark data terminal without changing packet capture, parsing, or SQLite behavior.

**Architecture:** Keep the existing XML + Material Components application and `LocalStzbRepository` interface. Add semantic Android resources, pure Kotlin UI models/mappers, and a five-item bottom navigation shell; migrate each core screen onto reusable card/list states while leaving low-frequency modules reachable through “更多”.

**Tech Stack:** Kotlin 2.0.21, Android Views/XML, Material Components 1.10.0, AppCompat 1.6.1, SQLiteOpenHelper, JUnit 4.13.2, Gradle 8/AGP 8.9.1.

## Global Constraints

- Keep XML + Material Components; do not migrate to Jetpack Compose.
- Use one dark theme in this phase with `#08111F` background, `#0F1B2D` surface, `#142238` raised surface, `#263750` border, `#F4F7FB` primary text, `#9AAAC0` secondary text, `#38BDF8` primary accent, and `#F59E0B` action accent.
- Do not change packet capture, packet parsing, SQLite schema, or the public behavior of `LocalStzbRepository`.
- Keep every Android touch target at least 48×48dp and every adjacent touch target at least 8dp apart.
- Use Material vector icons; do not use emoji as structural icons.
- Use 4/8dp spacing, 16dp page gutters, 16dp card padding, 12dp card gaps, 24dp section gaps, and 14dp card corners.
- Keep animations between 150–250ms, preserve layout bounds during press feedback, and honor reduced-motion settings.
- Preserve filters and scroll state when switching primary destinations.
- Keep raw JSON out of the primary reading flow and collapsed by default.
- Every task must leave `./gradlew :app:testDebugUnitTest` passing.

---

## Planned File Structure

### New production files

- `astzb/app/src/main/java/com/example/myapplication/ui/CoreUiModels.kt` — immutable display-only models shared by the five core screens.
- `astzb/app/src/main/java/com/example/myapplication/ui/BattleUiMapper.kt` — converts full battles and notices into battle cards/details.
- `astzb/app/src/main/java/com/example/myapplication/ui/MonitorUiMapper.kt` — converts movement records into stable monitor feed rows.
- `astzb/app/src/main/java/com/example/myapplication/ui/RankingUiMapper.kt` — adds ranks, medals, formatted values, and current-identity highlighting.
- `astzb/app/src/main/java/com/example/myapplication/ui/HomeUiMapper.kt` — converts repository counts and runtime pipeline flags into the home dashboard.
- `astzb/app/src/main/java/com/example/myapplication/ui/CoreUiState.kt` — loading/content/refreshing/empty/error states.
- `astzb/app/src/main/res/values/dimens.xml` — spacing, radius, touch, and type-size dimensions.
- `astzb/app/src/main/res/values/styles.xml` — reusable terminal card, label, button, and typography styles.
- `astzb/app/src/main/res/color/bottom_navigation_item_color.xml` — selected/unselected bottom navigation colors.
- `astzb/app/src/main/res/menu/menu_dashboard_bottom.xml` — five primary destinations.
- `astzb/app/src/main/res/drawable/` terminal surface, badge, state-strip, and skeleton shapes.
- `astzb/app/src/main/res/layout/view_core_empty_state.xml` — reusable empty/error state.
- `astzb/app/src/main/res/layout/view_home_dashboard.xml` — home KPI, pipeline, shortcuts, and recent activity.
- `astzb/app/src/main/res/layout/sheet_battle_filters.xml` — advanced battle filter bottom sheet.
- `astzb/app/src/main/res/layout/item_ranking_row.xml` — ranking row.

### New test files

- `astzb/app/src/test/java/com/example/myapplication/ui/BattleUiMapperTest.kt`
- `astzb/app/src/test/java/com/example/myapplication/ui/MonitorUiMapperTest.kt`
- `astzb/app/src/test/java/com/example/myapplication/ui/RankingUiMapperTest.kt`

### Existing files to modify

- `astzb/app/src/main/res/values/colors.xml`
- `astzb/app/src/main/res/values/themes.xml`
- `astzb/app/src/main/res/values-night/themes.xml`
- `astzb/app/src/main/res/values/strings.xml`
- `astzb/app/src/main/res/layout/activity_dashboard.xml`
- `astzb/app/src/main/res/layout/activity_battle_detail.xml`
- `astzb/app/src/main/res/layout/item_battle_card.xml`
- `astzb/app/src/main/res/layout/item_monitor_card.xml`
- `astzb/app/src/main/res/layout/item_info_card.xml`
- `astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt`
- `astzb/app/src/main/java/com/example/myapplication/BattleDetailActivity.kt`

---

### Task 1: Semantic Dark Theme Foundation

**Files:**
- Modify: `astzb/app/src/main/res/values/colors.xml`
- Create: `astzb/app/src/main/res/values/dimens.xml`
- Create: `astzb/app/src/main/res/values/styles.xml`
- Modify: `astzb/app/src/main/res/values/themes.xml`
- Modify: `astzb/app/src/main/res/values-night/themes.xml`
- Modify: `astzb/app/src/main/res/values/strings.xml`
- Create: `astzb/app/src/main/res/drawable/bg_terminal_card.xml`
- Create: `astzb/app/src/main/res/drawable/bg_terminal_badge.xml`
- Create: `astzb/app/src/main/res/drawable/bg_terminal_input.xml`

**Interfaces:**
- Consumes: existing `Theme.MaterialComponents.DayNight.NoActionBar`.
- Produces: `Theme.Astzb`, `TextAppearance.Astzb.*`, `Widget.Astzb.*`, and semantic `@color/astzb_*` resources used by every later task.

- [ ] **Step 1: Add a resource contract test**

Create `astzb/app/src/test/java/com/example/myapplication/ui/ThemeContractTest.kt`:

```kotlin
package com.example.myapplication.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeContractTest {
    @Test
    fun `core spacing follows four dp grid`() {
        val spacing = listOf(4, 8, 12, 16, 24)
        assertEquals(true, spacing.all { it % 4 == 0 })
    }

    @Test
    fun `core touch target is at least forty eight dp`() {
        assertEquals(true, CoreUiTokens.MIN_TOUCH_DP >= 48)
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
cd astzb
./gradlew :app:testDebugUnitTest --tests com.example.myapplication.ui.ThemeContractTest
```

Expected: compilation failure because `CoreUiTokens` does not exist.

- [ ] **Step 3: Add the Kotlin token contract**

Create `astzb/app/src/main/java/com/example/myapplication/ui/CoreUiState.kt`:

```kotlin
package com.example.myapplication.ui

object CoreUiTokens {
    const val MIN_TOUCH_DP = 48
    const val PRESS_DURATION_MS = 120L
    const val TRANSITION_DURATION_MS = 200L
}

sealed interface CoreUiState<out T> {
    data object InitialLoading : CoreUiState<Nothing>
    data class Content<T>(val value: T) : CoreUiState<T>
    data class Refreshing<T>(val value: T) : CoreUiState<T>
    data class Empty(val title: String, val message: String, val action: String) : CoreUiState<Nothing>
    data class Error(val title: String, val message: String, val action: String = "重试") : CoreUiState<Nothing>
}
```

- [ ] **Step 4: Add semantic resources**

Replace generic purple/teal usage with:

```xml
<color name="astzb_background">#08111F</color>
<color name="astzb_surface">#0F1B2D</color>
<color name="astzb_surface_raised">#142238</color>
<color name="astzb_border">#263750</color>
<color name="astzb_text_primary">#F4F7FB</color>
<color name="astzb_text_secondary">#9AAAC0</color>
<color name="astzb_primary">#38BDF8</color>
<color name="astzb_accent">#F59E0B</color>
<color name="astzb_success">#22C55E</color>
<color name="astzb_warning">#FBBF24</color>
<color name="astzb_danger">#F05252</color>
<color name="astzb_scrim">#99000000</color>
```

Define `space_4`, `space_8`, `space_12`, `space_16`, `space_24`, `radius_card`, `touch_min`, and typography dimensions in `dimens.xml`. Define card, badge, primary/secondary text, outlined action, and icon-button styles in `styles.xml`.

- [ ] **Step 5: Apply the dark theme**

Make both `values/themes.xml` and `values-night/themes.xml` define the same `Theme.Astzb` parented by `Theme.MaterialComponents.DayNight.NoActionBar`, set dark system bars, `windowLightStatusBar=false`, `windowLightNavigationBar=false`, and update the manifest-facing `Theme.MyApplication` to inherit `Theme.Astzb`.

- [ ] **Step 6: Verify resources and tests**

Run:

```bash
cd astzb
./gradlew :app:processDebugResources :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`; `ThemeContractTest` passes.

- [ ] **Step 7: Commit**

```bash
git add astzb/app/src/main/java/com/example/myapplication/ui/CoreUiState.kt \
  astzb/app/src/test/java/com/example/myapplication/ui/ThemeContractTest.kt \
  astzb/app/src/main/res/values \
  astzb/app/src/main/res/drawable/bg_terminal_card.xml \
  astzb/app/src/main/res/drawable/bg_terminal_badge.xml \
  astzb/app/src/main/res/drawable/bg_terminal_input.xml
git commit -m "feat(android): add terminal design system"
```

---

### Task 2: Pure Kotlin UI Models and Mappers

**Files:**
- Create: `astzb/app/src/main/java/com/example/myapplication/ui/CoreUiModels.kt`
- Create: `astzb/app/src/main/java/com/example/myapplication/ui/BattleUiMapper.kt`
- Create: `astzb/app/src/main/java/com/example/myapplication/ui/MonitorUiMapper.kt`
- Create: `astzb/app/src/main/java/com/example/myapplication/ui/RankingUiMapper.kt`
- Create: `astzb/app/src/test/java/com/example/myapplication/ui/BattleUiMapperTest.kt`
- Create: `astzb/app/src/test/java/com/example/myapplication/ui/MonitorUiMapperTest.kt`
- Create: `astzb/app/src/test/java/com/example/myapplication/ui/RankingUiMapperTest.kt`

**Interfaces:**
- Consumes: `LocalFullBattle`, `LocalBattleNotice`, `LocalBattleHero`, `LocalTeamMove`, and `LocalRankingRow`.
- Produces:
  - `BattleUiMapper.card(LocalFullBattle): BattleCardUi`
  - `BattleUiMapper.card(LocalBattleNotice): BattleCardUi`
  - `BattleUiMapper.detail(LocalFullBattle): BattleDetailUi`
  - `BattleUiMapper.detail(LocalBattleNotice): BattleDetailUi`
  - `MonitorUiMapper.row(LocalTeamMove, nowSeconds: Long): MonitorRowUi`
  - `RankingUiMapper.rows(List<LocalRankingRow>, currentName: String): List<RankingRowUi>`

- [ ] **Step 1: Define failing mapper tests**

Use concrete fixtures and assert behavior, not Android resources:

```kotlin
@Test
fun `winning battle exposes text and semantic success tone`() {
    val ui = BattleUiMapper.card(fullBattle(result = 1, attackerName = "甲", defenderName = "乙"))
    assertEquals("胜利", ui.resultLabel)
    assertEquals(UiTone.SUCCESS, ui.tone)
    assertEquals("甲", ui.attacker)
    assertEquals("乙", ui.defender)
}

@Test
fun `arriving within five minutes is urgent`() {
    val ui = MonitorUiMapper.row(move(arriveTime = 1_300L), nowSeconds = 1_000L)
    assertEquals(MonitorStatus.ARRIVING, ui.status)
    assertEquals(UiTone.WARNING, ui.tone)
}

@Test
fun `ranking rows assign medals and current identity`() {
    val rows = RankingUiMapper.rows(
        listOf(LocalRankingRow("甲", "", 99, 10, 80.0), LocalRankingRow("乙", "", 88, 9, 70.0)),
        currentName = "乙",
    )
    assertEquals("金", rows[0].medalLabel)
    assertEquals(true, rows[1].isCurrent)
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd astzb
./gradlew :app:testDebugUnitTest --tests 'com.example.myapplication.ui.*MapperTest'
```

Expected: compilation failure for missing models/mappers.

- [ ] **Step 3: Add display-only models**

Create exact types:

```kotlin
enum class UiTone { PRIMARY, SUCCESS, WARNING, DANGER, MUTED }
enum class MonitorStatus { MOVING, ARRIVING, ACTIVE, FINISHED, INCOMPLETE }

data class BattleCardUi(
    val battleId: Int,
    val timeLabel: String,
    val typeLabel: String,
    val resultLabel: String,
    val attacker: String,
    val defender: String,
    val locationLabel: String,
    val metricLabel: String,
    val heroNames: List<String>,
    val sourceLabel: String,
    val tone: UiTone,
)

data class HeroRowUi(val name: String, val levelLabel: String, val hpLabel: String)
data class BattleSideUi(val title: String, val player: String, val union: String, val hp: String, val heroes: List<HeroRowUi>)
data class MetricUi(val label: String, val value: String, val tone: UiTone = UiTone.PRIMARY)
data class BattleDetailUi(
    val battleId: Int,
    val resultLabel: String,
    val tone: UiTone,
    val metaLabel: String,
    val attacker: BattleSideUi,
    val defender: BattleSideUi,
    val metrics: List<MetricUi>,
    val extensions: List<MetricUi>,
    val rawJson: String,
    val isNoticeOnly: Boolean,
)

data class MonitorRowUi(
    val stableId: String,
    val teamId: Int,
    val owner: String,
    val union: String,
    val route: String,
    val currentPosition: String,
    val countdown: String,
    val status: MonitorStatus,
    val statusLabel: String,
    val tone: UiTone,
)

data class RankingRowUi(
    val rank: Int,
    val medalLabel: String,
    val name: String,
    val groupName: String,
    val primaryValue: String,
    val secondaryValue: String,
    val isCurrent: Boolean,
)
```

- [ ] **Step 4: Implement deterministic mappers**

Keep formatting inside mapper helpers. Result mapping must recognize existing winning values `1, 7, 11`, losing values `2, 6, 12`, and use `MUTED` for unknown/draw. `MonitorUiMapper` must normalize second/millisecond timestamps before calculating countdown. `RankingUiMapper` must not reorder repository results.

- [ ] **Step 5: Run mapper and full unit tests**

Run:

```bash
cd astzb
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`; all new mapper tests pass.

- [ ] **Step 6: Commit**

```bash
git add astzb/app/src/main/java/com/example/myapplication/ui \
  astzb/app/src/test/java/com/example/myapplication/ui
git commit -m "feat(android): add core UI mappers"
```

---

### Task 3: Five-Destination Navigation Shell

**Files:**
- Create: `astzb/app/src/main/res/menu/menu_dashboard_bottom.xml`
- Create: `astzb/app/src/main/res/color/bottom_navigation_item_color.xml`
- Create: `astzb/app/src/main/res/drawable/ic_home_24.xml`
- Create: `astzb/app/src/main/res/drawable/ic_battle_24.xml`
- Create: `astzb/app/src/main/res/drawable/ic_monitor_24.xml`
- Create: `astzb/app/src/main/res/drawable/ic_ranking_24.xml`
- Create: `astzb/app/src/main/res/drawable/ic_more_24.xml`
- Modify: `astzb/app/src/main/res/layout/activity_dashboard.xml`
- Modify: `astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt`

**Interfaces:**
- Consumes: existing `openModule(module: String)` and module constants.
- Produces: bottom navigation mappings `home -> MODULE_OVERVIEW`, `battles -> MODULE_BATTLES`, `monitor -> MODULE_BATTLE_MONITOR`, `ranking -> MODULE_RANKING`, `more -> MODULE_MORE`.

- [ ] **Step 1: Add the menu resource**

```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/navHome" android:icon="@drawable/ic_home_24" android:title="@string/nav_home" />
    <item android:id="@+id/navBattles" android:icon="@drawable/ic_battle_24" android:title="@string/nav_battles" />
    <item android:id="@+id/navMonitor" android:icon="@drawable/ic_monitor_24" android:title="@string/nav_monitor" />
    <item android:id="@+id/navRanking" android:icon="@drawable/ic_ranking_24" android:title="@string/nav_ranking" />
    <item android:id="@+id/navMore" android:icon="@drawable/ic_more_24" android:title="@string/nav_more" />
</menu>
```

- [ ] **Step 2: Add a `BottomNavigationView` to the dashboard shell**

Place it below the content container, give it `wrap_content` height, `@color/astzb_surface`, selected/unselected color list, label visibility mode `labeled`, and bottom inset handling. Remove root hardcoded light colors and add content bottom padding so the last row is visible.

- [ ] **Step 3: Wire primary navigation**

Add `MODULE_MORE = "more"` and:

```kotlin
private fun setupBottomNavigation() {
    findViewById<BottomNavigationView>(R.id.dashboardBottomNavigation)
        .setOnItemSelectedListener { item ->
            val module = when (item.itemId) {
                R.id.navHome -> MODULE_OVERVIEW
                R.id.navBattles -> MODULE_BATTLES
                R.id.navMonitor -> MODULE_BATTLE_MONITOR
                R.id.navRanking -> MODULE_RANKING
                R.id.navMore -> MODULE_MORE
                else -> return@setOnItemSelectedListener false
            }
            openModule(module)
            true
        }
}
```

Call it from `onCreate`, default to `MODULE_OVERVIEW`, and keep the existing sidebar only as compatibility navigation for low-frequency modules.

- [ ] **Step 4: Preserve scroll/filter state**

Store `battleListView.firstVisiblePosition` keyed by module before switching and restore it after the adapter is installed. Do not clear battle filter fields in `openModule`.

- [ ] **Step 5: Build and smoke test**

Run:

```bash
cd astzb
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`; five navigation items compile and the app launches to overview.

- [ ] **Step 6: Commit**

```bash
git add astzb/app/src/main/res/menu astzb/app/src/main/res/color \
  astzb/app/src/main/res/drawable/ic_*_24.xml \
  astzb/app/src/main/res/layout/activity_dashboard.xml \
  astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt
git commit -m "feat(android): add five-item core navigation"
```

---

### Task 4: Home Dashboard and More Grid

**Files:**
- Create: `astzb/app/src/main/res/layout/view_home_dashboard.xml`
- Create: `astzb/app/src/main/res/layout/view_core_empty_state.xml`
- Create: `astzb/app/src/main/res/drawable/bg_pipeline_node.xml`
- Create: `astzb/app/src/main/java/com/example/myapplication/ui/HomeUiMapper.kt`
- Create: `astzb/app/src/test/java/com/example/myapplication/ui/HomeUiMapperTest.kt`
- Modify: `astzb/app/src/main/res/layout/activity_dashboard.xml`
- Modify: `astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt`

**Interfaces:**
- Consumes: `LocalStzbRepository.counts()`, `CaptureVpnService.isRunning`, `LocalSocksCaptureServer.isRunning()`, `LocalStzbPacketStore.snapshot()`, and existing `openModule`.
- Produces: `HomeUiMapper.map(counts, pipeline)`, `HomeDashboardUi` rendered by `renderHomeDashboard`, plus `MODULE_MORE` shortcut grid.

- [ ] **Step 1: Add a pure overview model test**

Test the complete fixture below maps to KPI labels `"5"`, `"3"`, and `"40"` without database or Android dependencies:

```kotlin
private val counts = LocalDataCounts(
    packets = 20,
    fullBattles = 5,
    battleNotices = 2,
    chats = 0,
    monitorMoves = 3,
    teamUsers = 40,
    mapCells = 0,
    unionRanks = 0,
    playerPowerRanks = 0,
    playerStats = 0,
    announcements = 0,
    heroUnlocks = 0,
    playerSelf = 0,
    zonePlayers = 0,
    dbSync = 0,
    battleFields = 0,
    marchEvents = 0,
    localRecords = 0,
)
```

- [ ] **Step 2: Implement `HomeDashboardUi` mapping**

Add:

```kotlin
data class HomeKpiUi(val label: String, val value: String, val tone: UiTone)
data class PipelineNodeUi(val label: String, val stateLabel: String, val tone: UiTone)
data class ShortcutUi(val label: String, val module: String)
data class HomePipelineInput(
    val vpnRunning: Boolean,
    val socksRunning: Boolean,
    val parserHasPackets: Boolean,
    val databaseReady: Boolean,
)
data class HomeDashboardUi(
    val statusLabel: String,
    val kpis: List<HomeKpiUi>,
    val pipeline: List<PipelineNodeUi>,
    val shortcuts: List<ShortcutUi>,
)
```

The four KPI labels are `今日战报`, `活跃队伍`, `同盟成员`, `监控事件`. If no date-specific query exists, label the first card `本机战报` rather than pretending it is today’s count.

`HomeUiMapper.map(counts, pipeline)` must use exactly these pipeline rules:

- VPN is normal when `CaptureVpnService.isRunning` is true.
- SOCKS5 is normal when `LocalSocksCaptureServer.isRunning()` is true.
- Parser is normal when `LocalStzbPacketStore.snapshot().isNotEmpty()`; otherwise it is waiting, not failed.
- SQLite is normal after `LocalStzbRepository.counts()` succeeds; a thrown query maps it to failed.

- [ ] **Step 3: Build the home XML**

Use a `NestedScrollView` with:

- status header;
- two-column KPI grid;
- four pipeline nodes: VPN, SOCKS5, 解析器, SQLite;
- six shortcut buttons;
- recent activity section;
- included empty/error state.

Every shortcut must have a Material vector icon, visible text, and 48dp minimum height.

- [ ] **Step 4: Render overview and more destinations**

Replace the overview text dump with `renderHomeDashboard(ui)`. Map shortcuts to `MODULE_TEAM_REPORT`, `MODULE_TEAM_USERS`, `MODULE_MAP`, `MODULE_TASK_ATTENDANCE`, `MODULE_HERO_STATS`, and `MODULE_SIMULATOR`. Render `MODULE_MORE` as a larger grid containing those shortcuts plus existing tools.

- [ ] **Step 5: Add loading, empty, and failure behavior**

During refresh, keep the previous values visible and show a small progress indicator. If all counts are zero, show “尚未捕获到本机数据” with an action leading back to the capture console. On failure, show an inline retry card instead of only a Toast.

- [ ] **Step 6: Test and build**

Run:

```bash
cd astzb
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: all tests pass and home resources compile.

- [ ] **Step 7: Commit**

```bash
git add astzb/app/src/main/res/layout/view_home_dashboard.xml \
  astzb/app/src/main/res/layout/view_core_empty_state.xml \
  astzb/app/src/main/res/drawable/bg_pipeline_node.xml \
  astzb/app/src/main/res/layout/activity_dashboard.xml \
  astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt \
  astzb/app/src/main/java/com/example/myapplication/ui \
  astzb/app/src/test/java/com/example/myapplication/ui
git commit -m "feat(android): redesign home dashboard"
```

---

### Task 5: Battle List and Filter Bottom Sheet

**Files:**
- Modify: `astzb/app/src/main/res/layout/item_battle_card.xml`
- Create: `astzb/app/src/main/res/layout/sheet_battle_filters.xml`
- Create: `astzb/app/src/main/res/drawable/bg_result_success.xml`
- Create: `astzb/app/src/main/res/drawable/bg_result_danger.xml`
- Create: `astzb/app/src/main/res/drawable/bg_result_muted.xml`
- Modify: `astzb/app/src/main/res/layout/activity_dashboard.xml`
- Modify: `astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt`

**Interfaces:**
- Consumes: `BattleUiMapper.card`, `LocalBattleFilter`, and existing repository battle loaders.
- Produces: `BattleCardAdapter(List<BattleCardUi>)`, `showBattleFilterSheet()`, and fast-filter state.

- [ ] **Step 1: Add battle filter state tests**

Define and test:

```kotlin
enum class BattleQuickFilter { ALL, WIN, LOSS, SIEGE, TODAY }
data class BattleFilterUi(
    val player: String = "",
    val unionName: String = "",
    val fightType: Int? = null,
    val result: Int? = null,
    val wid: Int? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val quick: BattleQuickFilter = BattleQuickFilter.ALL,
)
```

Assert `WIN` maps to winning repository result values supported by the existing query, and `TODAY` produces local-day start/end epoch seconds.

- [ ] **Step 2: Replace persistent filters with fast chips**

Hide/remove the current always-visible horizontal input strip from normal battle mode. Add Material chips for `全部 / 胜利 / 失败 / 攻城 / 今日`, plus search and filter icon buttons.

- [ ] **Step 3: Implement the advanced filter bottom sheet**

Use `BottomSheetDialog` and `sheet_battle_filters.xml`. The sheet contains labeled Material inputs for player, union, battle type, result, wid, start, and end, with `重置` and `应用` actions. Applying creates `LocalBattleFilter` and reloads without clearing list position until the new result arrives.

- [ ] **Step 4: Redesign battle cards**

Bind `BattleCardUi` with:

- a 4dp semantic result strip;
- result badge and type label;
- attack/defense names as the primary row;
- time and location as secondary metadata;
- main metric and up to three hero labels;
- source label `完整战报` or `通知级数据`.

No text should be smaller than 13sp; the row must remain usable at large system font.

- [ ] **Step 5: Render loading/empty/error states**

Show a list-shaped skeleton only for initial loading. For refresh, keep old rows. For zero results, show “当前筛选没有战报” with “清除筛选”. For repository failure, show an inline retry state.

- [ ] **Step 6: Verify**

Run:

```bash
cd astzb
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: battle mapper/filter tests pass; battle list resources compile.

- [ ] **Step 7: Commit**

```bash
git add astzb/app/src/main/res/layout/item_battle_card.xml \
  astzb/app/src/main/res/layout/sheet_battle_filters.xml \
  astzb/app/src/main/res/drawable/bg_result_*.xml \
  astzb/app/src/main/res/layout/activity_dashboard.xml \
  astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt \
  astzb/app/src/main/java/com/example/myapplication/ui \
  astzb/app/src/test/java/com/example/myapplication/ui
git commit -m "feat(android): redesign battle list and filters"
```

---

### Task 6: Structured Battle Detail

**Files:**
- Modify: `astzb/app/src/main/res/layout/activity_battle_detail.xml`
- Create: `astzb/app/src/main/res/layout/item_battle_hero.xml`
- Modify: `astzb/app/src/main/java/com/example/myapplication/BattleDetailActivity.kt`
- Modify: `astzb/app/src/main/java/com/example/myapplication/ui/BattleUiMapper.kt`
- Modify: `astzb/app/src/test/java/com/example/myapplication/ui/BattleUiMapperTest.kt`

**Interfaces:**
- Consumes: `BattleUiMapper.detail(LocalFullBattle)` and a notice-only detail mapper.
- Produces: structured attacker/defender panels, hero rows, metric grid, collapsed extensions, and collapsed debug JSON.

- [ ] **Step 1: Add detail-mapper edge-case tests**

Cover:

- full battle with six heroes;
- missing hero names;
- notice-only battle;
- blank raw JSON;
- unknown result;
- very long player and union names.

Assert notice-only detail returns `isNoticeOnly=true` and no fake empty metrics.

- [ ] **Step 2: Redesign the XML hierarchy**

Use:

- dark top app bar with back and refresh;
- result/meta card;
- symmetric attacker/defender cards around a `VS` label;
- two vertical hero containers using `item_battle_hero.xml`;
- compact metric grid;
- collapsed “扩展信息” section;
- collapsed “调试信息” section.

Remove the raw text block from the initial viewport.

- [ ] **Step 3: Render `BattleDetailUi`**

Replace text template construction in `renderFullDetail` and `renderDetail` with one `renderDetail(ui: BattleDetailUi)` path. Add hero child views from `ui.attacker.heroes` and `ui.defender.heroes`. Toggle extension/debug containers without changing repository calls.

- [ ] **Step 4: Add explicit page states**

Loading shows a structured skeleton; missing ID shows a fatal inline state; repository miss shows “本机库中没有找到该战报”; notice-only data displays a visible `通知级数据` banner.

- [ ] **Step 5: Verify**

Run:

```bash
cd astzb
./gradlew :app:testDebugUnitTest --tests com.example.myapplication.ui.BattleUiMapperTest
./gradlew :app:assembleDebug
```

Expected: tests and build pass.

- [ ] **Step 6: Commit**

```bash
git add astzb/app/src/main/res/layout/activity_battle_detail.xml \
  astzb/app/src/main/res/layout/item_battle_hero.xml \
  astzb/app/src/main/java/com/example/myapplication/BattleDetailActivity.kt \
  astzb/app/src/main/java/com/example/myapplication/ui/BattleUiMapper.kt \
  astzb/app/src/test/java/com/example/myapplication/ui/BattleUiMapperTest.kt
git commit -m "feat(android): redesign battle detail"
```

---

### Task 7: Live Monitor Feed

**Files:**
- Modify: `astzb/app/src/main/res/layout/item_monitor_card.xml`
- Modify: `astzb/app/src/main/res/layout/activity_dashboard.xml`
- Modify: `astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt`
- Modify: `astzb/app/src/main/java/com/example/myapplication/ui/MonitorUiMapper.kt`
- Modify: `astzb/app/src/test/java/com/example/myapplication/ui/MonitorUiMapperTest.kt`

**Interfaces:**
- Consumes: `LocalStzbRepository.loadMonitorMoves`, `MonitorUiMapper.row`, and the current auto-refresh handler.
- Produces: monitor pause/resume state, `MonitorCardAdapter(List<MonitorRowUi>)`, copy actions, and “jump to latest”.

- [ ] **Step 1: Extend monitor tests**

Test moving, arriving within five minutes, finished, missing owner, missing coordinates, seconds timestamps, and milliseconds timestamps. Assert countdown never becomes a negative string.

- [ ] **Step 2: Build the monitor header**

Add:

- live status indicator;
- last refresh time;
- pause/resume Material icon button;
- filter button;
- “查看最新” action shown only when new events arrive while the user is scrolled away from the top.

- [ ] **Step 3: Redesign feed rows**

Each row shows owner/team, union, semantic status badge, route, current position, and countdown. Team ID and coordinates use monospace text. Long press or explicit copy icons copy team ID and destination coordinate; each icon has a content description and 48dp hit area.

- [ ] **Step 4: Preserve user scroll**

Before adapter refresh, capture the first visible position and top offset. If the user is not at the top, restore them and show “查看最新”; do not call `setSelection(0)`. Pause stops UI scheduling only and never stops `CaptureVpnService` or `TProxyService`.

- [ ] **Step 5: Add new-event highlight**

Compare row identity using `teamId + toWid + arriveTime`. New rows receive one 200ms surface-color transition. Skip this transition when system animator duration scale is zero.

- [ ] **Step 6: Verify**

Run:

```bash
cd astzb
./gradlew :app:testDebugUnitTest --tests com.example.myapplication.ui.MonitorUiMapperTest
./gradlew :app:assembleDebug
```

Expected: monitor tests pass and APK builds.

- [ ] **Step 7: Commit**

```bash
git add astzb/app/src/main/res/layout/item_monitor_card.xml \
  astzb/app/src/main/res/layout/activity_dashboard.xml \
  astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt \
  astzb/app/src/main/java/com/example/myapplication/ui/MonitorUiMapper.kt \
  astzb/app/src/test/java/com/example/myapplication/ui/MonitorUiMapperTest.kt
git commit -m "feat(android): redesign live monitor feed"
```

---

### Task 8: Ranking Center

**Files:**
- Create: `astzb/app/src/main/res/layout/item_ranking_row.xml`
- Create: `astzb/app/src/main/res/layout/view_ranking_podium.xml`
- Modify: `astzb/app/src/main/res/layout/activity_dashboard.xml`
- Modify: `astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt`
- Modify: `astzb/app/src/main/java/com/example/myapplication/ui/RankingUiMapper.kt`
- Modify: `astzb/app/src/test/java/com/example/myapplication/ui/RankingUiMapperTest.kt`

**Interfaces:**
- Consumes: `LocalStzbRepository.loadBattleRankings`, `RankingUiMapper.rows`, and current player identity if available.
- Produces: ranking mode `PLAYER_WUXUN | UNION_WUXUN | PLAYER_POWER`, podium rows, normal rows, and current-identity highlight.

- [ ] **Step 1: Extend ranking tests**

Test empty input, one/two/three rows, more than three rows, current identity, long names, zero battles, and decimal win rate formatting.

- [ ] **Step 2: Add ranking mode and segmented control**

```kotlin
private enum class RankingMode {
    PLAYER_WUXUN,
    UNION_WUXUN,
    PLAYER_POWER,
}
```

Use a single-selection Material button group labeled `玩家武勋 / 同盟武勋 / 玩家势力`. Preserve selected mode when leaving and returning.

- [ ] **Step 3: Render the podium and rows**

The podium shows the first three only when available and remains compact on small screens. Remaining rows use `item_ranking_row.xml` with rank, name, primary metric, and battles/win rate. Highlight current identity with a border plus `当前` label, not color alone.

- [ ] **Step 4: Add filters and sample explanation**

Expose existing period/alliance constraints only if the repository can honor them. Do not add fake UI controls. Always show a small “按本机已捕获战报统计” explanation and an empty state when the selected ranking has no rows.

- [ ] **Step 5: Verify**

Run:

```bash
cd astzb
./gradlew :app:testDebugUnitTest --tests com.example.myapplication.ui.RankingUiMapperTest
./gradlew :app:assembleDebug
```

Expected: ranking tests and build pass.

- [ ] **Step 6: Commit**

```bash
git add astzb/app/src/main/res/layout/item_ranking_row.xml \
  astzb/app/src/main/res/layout/view_ranking_podium.xml \
  astzb/app/src/main/res/layout/activity_dashboard.xml \
  astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt \
  astzb/app/src/main/java/com/example/myapplication/ui/RankingUiMapper.kt \
  astzb/app/src/test/java/com/example/myapplication/ui/RankingUiMapperTest.kt
git commit -m "feat(android): redesign ranking center"
```

---

### Task 9: Accessibility, Responsive Layout, and Regression Pass

**Files:**
- Modify: all core layout/resource files changed in Tasks 1–8 as findings require.
- Modify: `astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt`
- Modify: `astzb/app/src/main/java/com/example/myapplication/BattleDetailActivity.kt`
- Create: `astzb/app/src/androidTest/java/com/example/myapplication/CoreNavigationSmokeTest.kt`
- Modify: `astzb/APP_PAGE_PRODUCTIZATION_TODO.md`

**Interfaces:**
- Consumes: completed five-screen implementation.
- Produces: verified safe-area, dynamic-type, TalkBack, reduced-motion, navigation, and regression behavior.

- [ ] **Step 1: Add a navigation smoke test**

```kotlin
@RunWith(AndroidJUnit4::class)
class CoreNavigationSmokeTest {
    @Test
    fun primaryDestinationsAreReachable() {
        ActivityScenario.launch(DashboardActivity::class.java).use {
            onView(withId(R.id.navHome)).check(matches(isDisplayed()))
            onView(withId(R.id.navBattles)).perform(click())
            onView(withId(R.id.navMonitor)).perform(click())
            onView(withId(R.id.navRanking)).perform(click())
            onView(withId(R.id.navMore)).perform(click())
        }
    }
}
```

- [ ] **Step 2: Run static UI validation**

Run:

```bash
python3 /Users/bytedance/.codex/skills/ui-ux-pro-max/scripts/search.py \
  "animation accessibility z-index loading" --domain ux
```

Review the critical/high rules against the implementation. Confirm: no emoji icons, all icon buttons have content descriptions, touch targets are at least 48dp, status uses text plus color, and scrim opacity is 40–60% black.

- [ ] **Step 3: Validate Android resources and unit tests**

Run:

```bash
cd astzb
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Expected: all tasks finish successfully. Fix every new error and any accessibility warning in the touched layouts.

- [ ] **Step 4: Run instrumentation when a device is available**

Run:

```bash
cd astzb
./gradlew :app:connectedDebugAndroidTest
```

Expected: `CoreNavigationSmokeTest` passes. If no device is connected, record that instrumentation is pending but do not represent it as passing.

- [ ] **Step 5: Manual device matrix**

Verify:

- small phone portrait;
- ordinary phone portrait;
- landscape;
- large system font;
- gesture navigation;
- three-button navigation;
- animator scale disabled;
- empty local database;
- active capture with incoming monitor rows.

For each, confirm no clipped critical text, no content behind bottom navigation/system bars, preserved filters/scroll, and predictable back behavior.

- [ ] **Step 6: Update productization status**

Mark only the five completed core-page items in `astzb/APP_PAGE_PRODUCTIZATION_TODO.md`. Do not mark unrelated map, team, attendance, lineup, or simulator work complete.

- [ ] **Step 7: Final verification and commit**

Run:

```bash
cd astzb
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

```bash
git add astzb/app/src astzb/APP_PAGE_PRODUCTIZATION_TODO.md
git commit -m "test(android): verify core UI redesign"
```

---

## Final Acceptance Checklist

- [ ] App launches to the redesigned home destination.
- [ ] Bottom navigation exposes exactly five primary destinations.
- [ ] Home contains four real KPI values, four pipeline nodes, six shortcuts, and recent activity.
- [ ] Battle filters live in quick chips plus a bottom sheet, not a permanent input strip.
- [ ] Battle cards expose result, sides, time, location, metric, heroes, and source level.
- [ ] Battle detail has structured sides, heroes, metrics, collapsed extensions, and collapsed debug data.
- [ ] Monitor feed preserves scroll and can pause UI updates without stopping capture.
- [ ] Ranking center supports the three agreed ranking modes and distinguishes the current identity without color alone.
- [ ] Loading, refreshing, empty, and error states exist on all five core screens.
- [ ] All touch targets, safe areas, contrast, TalkBack labels, and reduced-motion rules pass review.
- [ ] Packet capture, protocol parsing, database schema, and repository behavior remain unchanged.
- [ ] `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug` pass.
