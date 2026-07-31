# ASTZB Android Batch 1 Battlefield Compose Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改动现有抓包和 SQLite 主链路的前提下，交付以实时战场为默认首页的现代深色 Compose 应用壳。

**Architecture:** 新增 `com.local.stzb` Compose 层和战场领域 seam，由 `LegacyBattlefieldRepository` 适配旧 `LocalStzbRepository`、内存 Store 和抓包状态。`StzbAppActivity` 负责 VPN 授权等 Android 能力，页面状态由 ViewModel 通过 `StateFlow` 提供，旧 `MainActivity` 与 `DashboardActivity` 暂时作为兼容页面保留。

**Tech Stack:** Kotlin 2.0.21、AGP 8.9.1、Jetpack Compose BOM、Material 3、Navigation Compose、Lifecycle ViewModel、Coroutines/Flow、JUnit 4、Compose UI Test。

## Global Constraints

- 最低系统版本固定为 Android 13（minSdk 33），targetSdk 35。
- 默认首页固定为“实时战场动态”。
- 一级导航固定为战场、战报、同盟、更多四项。
- 视觉 token：Background `#0B1220`、Surface `#111B2E`、Surface High `#18243A`、Primary `#F59E0B`、Secondary `#818CF8`、Success `#2DD4BF`、Error `#F87171`。
- 保留现有 VPN、tun2socks、本机 SOCKS、协议解析、SQLite schema 和模拟器实现。
- 新页面不得直接访问 Cursor、表名或原始消息号。
- 新代码放入 `com.local.stzb`，通过 Adapter 调用旧包。
- 点击区域不小于 48dp；正文不小于 14sp；状态同时使用图标、文字和颜色。
- 不使用持续发光、闪烁或强制自动滚动。

---

## File Structure

```text
astzb/app/src/main/java/com/local/stzb/
├── StzbAppActivity.kt                     VPN 权限与 Compose 根 Activity
├── StzbApplication.kt                     进程级依赖装配
├── core/designsystem/
│   ├── Color.kt                           语义颜色
│   ├── Theme.kt                           Material 3 主题
│   └── Type.kt                            排版
├── core/navigation/
│   ├── AppDestination.kt                  四个一级目的地
│   └── StzbApp.kt                         Scaffold 和 NavHost
├── core/ui/
│   ├── LoadState.kt                       通用加载状态
│   ├── StatePanel.kt                      加载/空/错状态
│   └── MetricCard.kt                      指标卡
├── domain/battlefield/
│   ├── BattlefieldEvent.kt                统一事件模型
│   ├── BattlefieldModels.kt               状态、指标和摘要
│   └── BattlefieldRepository.kt           战场领域接口
├── data/battlefield/
│   ├── LegacyBattlefieldRepository.kt     旧实现适配器
│   └── BattlefieldEventMapper.kt           旧记录到领域事件映射
├── feature/battlefield/
│   ├── BattlefieldContract.kt             UiState/Intent
│   ├── BattlefieldViewModel.kt            状态编排
│   ├── BattlefieldScreen.kt               首页
│   └── BattlefieldComponents.kt           Feed、状态栏和摘要组件
├── feature/placeholder/
│   └── PlaceholderScreen.kt               未迁移领域兼容入口
└── feature/tools/
    └── LegacyToolsScreen.kt               旧控制台/Dashboard 入口
```

测试文件与生产文件同包镜像放入 `src/test` 和 `src/androidTest`。

---

### Task 1: Enable Compose and Test Infrastructure

**Files:**
- Modify: `astzb/gradle/libs.versions.toml`
- Modify: `astzb/app/build.gradle.kts`
- Test: `astzb/app/src/androidTest/java/com/local/stzb/ComposeSmokeTest.kt`

**Interfaces:**
- Consumes: existing Android application module and Kotlin 2.0.21 plugin.
- Produces: Compose compiler plugin, Material 3, Navigation, Lifecycle, Coroutines and UI-test dependencies available to later tasks.

- [ ] **Step 1: Add a failing Compose smoke test**

```kotlin
package com.local.stzb

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ComposeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun composeContentIsRendered() {
        composeRule.setContent { Text("ASTZB Compose ready") }
        composeRule.onNodeWithText("ASTZB Compose ready").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the smoke test and verify dependency resolution fails**

Run:

```bash
cd /Users/bytedance/stzb_watcher/astzb
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: FAIL because Compose test classes are unresolved.

- [ ] **Step 3: Add exact Compose versions and libraries**

Add to `libs.versions.toml`:

```toml
[versions]
activityCompose = "1.10.1"
composeBom = "2025.02.00"
lifecycle = "2.8.7"
navigationCompose = "2.8.8"
coroutines = "1.10.1"

[libraries]
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

Add the Compose plugin, build feature, and dependencies to `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 4: Verify Compose compilation and the existing app build**

Run:

```bash
cd /Users/bytedance/stzb_watcher/astzb
./gradlew :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add astzb/gradle/libs.versions.toml astzb/app/build.gradle.kts astzb/app/src/androidTest/java/com/local/stzb/ComposeSmokeTest.kt
git commit -m "build(android): enable Compose infrastructure"
```

---

### Task 2: Create the Modern Dark Design System

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/core/designsystem/Color.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/core/designsystem/Type.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/core/designsystem/Theme.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/core/ui/MetricCard.kt`
- Test: `astzb/app/src/androidTest/java/com/local/stzb/core/designsystem/ThemeTest.kt`

**Interfaces:**
- Consumes: Material 3 from Task 1.
- Produces: `AstzbTheme(content)`, `AstzbColors`, and reusable `MetricCard(label, value, supportingText, modifier)`.

- [ ] **Step 1: Write a failing semantics test for the metric card**

```kotlin
package com.local.stzb.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.local.stzb.core.ui.MetricCard
import org.junit.Rule
import org.junit.Test

class ThemeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun metricCardExposesLabelValueAndContext() {
        rule.setContent {
            AstzbTheme {
                MetricCard("正在行军", "12", "2 支即将到达")
            }
        }
        rule.onNodeWithText("正在行军").assertIsDisplayed()
        rule.onNodeWithText("12").assertIsDisplayed()
        rule.onNodeWithText("2 支即将到达").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test and verify missing design-system symbols**

Run `./gradlew :app:compileDebugAndroidTestKotlin` from `astzb`.

Expected: FAIL with unresolved `AstzbTheme` and `MetricCard`.

- [ ] **Step 3: Implement semantic colors and typography**

`Color.kt`:

```kotlin
package com.local.stzb.core.designsystem

import androidx.compose.ui.graphics.Color

object AstzbColors {
    val Background = Color(0xFF0B1220)
    val Surface = Color(0xFF111B2E)
    val SurfaceHigh = Color(0xFF18243A)
    val Outline = Color(0xFF2A3A55)
    val Primary = Color(0xFFF59E0B)
    val Secondary = Color(0xFF818CF8)
    val Success = Color(0xFF2DD4BF)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFA8B3C7)
}
```

`Type.kt`:

```kotlin
package com.local.stzb.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AstzbTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)
```

`Theme.kt`:

```kotlin
package com.local.stzb.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AstzbDarkScheme = darkColorScheme(
    primary = AstzbColors.Primary,
    onPrimary = AstzbColors.Background,
    secondary = AstzbColors.Secondary,
    background = AstzbColors.Background,
    onBackground = AstzbColors.TextPrimary,
    surface = AstzbColors.Surface,
    onSurface = AstzbColors.TextPrimary,
    surfaceContainerHigh = AstzbColors.SurfaceHigh,
    outline = AstzbColors.Outline,
    error = AstzbColors.Error,
)

@Composable
fun AstzbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AstzbDarkScheme,
        typography = AstzbTypography,
        content = content,
    )
}
```

- [ ] **Step 4: Implement the reusable metric card**

```kotlin
package com.local.stzb.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MetricCard(label: String, value: String, supportingText: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.defaultMinSize(minHeight = 112.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(supportingText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

- [ ] **Step 5: Run tests and commit**

Run `./gradlew :app:connectedDebugAndroidTest :app:assembleDebug`.

Expected: `ThemeTest` and smoke test PASS; BUILD SUCCESSFUL.

```bash
git add astzb/app/src/main/java/com/local/stzb/core astzb/app/src/androidTest/java/com/local/stzb/core
git commit -m "feat(android): add modern dark design system"
```

---

### Task 3: Define Battlefield Domain Interfaces

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/core/ui/LoadState.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/domain/battlefield/BattlefieldEvent.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/domain/battlefield/BattlefieldModels.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/domain/battlefield/BattlefieldRepository.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/domain/battlefield/BattlefieldEventTest.kt`

**Interfaces:**
- Consumes: no Android UI types.
- Produces: `BattlefieldRepository.observeSnapshot(): Flow<BattlefieldSnapshot>`, `setPaused(Boolean)`, `setFilter(Set<EventCategory>)`, and stable immutable models used by all battlefield code.

- [ ] **Step 1: Write failing domain behavior tests**

```kotlin
package com.local.stzb.domain.battlefield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BattlefieldEventTest {
    @Test fun eventIdentityIsStableAcrossPresentationChanges() {
        val event = BattlefieldEvent(
            id = "march:42:1700000000",
            occurredAt = 1_700_000_000L,
            category = EventCategory.MARCH,
            priority = EventPriority.NORMAL,
            title = "队伍出发",
            summary = "甲 100,100 → 101,101",
            target = EventTarget.Team(42),
        )
        assertEquals("march:42:1700000000", event.id)
        assertFalse(event.isUrgent)
    }
}
```

- [ ] **Step 2: Run and verify model symbols are missing**

Run `./gradlew :app:testDebugUnitTest --tests '*BattlefieldEventTest'`.

Expected: compilation FAIL because battlefield domain models do not exist.

- [ ] **Step 3: Implement load and event models**

```kotlin
package com.local.stzb.core.ui

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Content<T>(val value: T, val refreshing: Boolean = false) : LoadState<T>
    data class Empty(val message: String, val actionLabel: String? = null) : LoadState<Nothing>
    data class Error(val message: String, val retryable: Boolean = true) : LoadState<Nothing>
}
```

```kotlin
package com.local.stzb.domain.battlefield

enum class EventCategory { URGENT, BATTLE, MARCH, SIEGE, SYSTEM }
enum class EventPriority { NORMAL, IMPORTANT, CRITICAL }

sealed interface EventTarget {
    data class Battle(val battleId: Int) : EventTarget
    data class Team(val teamId: Int) : EventTarget
    data class Cell(val wid: Int) : EventTarget
    data object Diagnostics : EventTarget
    data object None : EventTarget
}

data class BattlefieldEvent(
    val id: String,
    val occurredAt: Long,
    val category: EventCategory,
    val priority: EventPriority,
    val title: String,
    val summary: String,
    val target: EventTarget = EventTarget.None,
) {
    val isUrgent: Boolean get() = priority == EventPriority.CRITICAL || category == EventCategory.URGENT
}
```

- [ ] **Step 4: Implement snapshot and Repository interface**

```kotlin
package com.local.stzb.domain.battlefield

data class CaptureStatus(
    val running: Boolean,
    val label: String,
    val lastEventAt: Long?,
    val warning: String? = null,
)

data class BattlefieldMetrics(
    val activeMarches: Int,
    val arrivingSoon: Int,
    val todayBattles: Int,
    val siegeEvents: Int,
)

data class BattlefieldSnapshot(
    val capture: CaptureStatus,
    val metrics: BattlefieldMetrics,
    val events: List<BattlefieldEvent>,
    val selectedCategories: Set<EventCategory> = EventCategory.entries.toSet(),
    val paused: Boolean = false,
    val bufferedEventCount: Int = 0,
)
```

```kotlin
package com.local.stzb.domain.battlefield

import kotlinx.coroutines.flow.Flow

interface BattlefieldRepository {
    fun observeSnapshot(): Flow<BattlefieldSnapshot>
    suspend fun refresh()
    fun setPaused(paused: Boolean)
    fun setFilter(categories: Set<EventCategory>)
}
```

- [ ] **Step 5: Run domain tests and commit**

Run `./gradlew :app:testDebugUnitTest --tests '*BattlefieldEventTest'`.

Expected: PASS.

```bash
git add astzb/app/src/main/java/com/local/stzb/core/ui/LoadState.kt astzb/app/src/main/java/com/local/stzb/domain astzb/app/src/test/java/com/local/stzb/domain
git commit -m "feat(android): define battlefield domain seam"
```

---

### Task 4: Map Legacy Data into Battlefield Events

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/data/battlefield/BattlefieldEventMapper.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/data/battlefield/LegacyBattlefieldRepository.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/data/battlefield/BattlefieldEventMapperTest.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/data/battlefield/LegacyBattlefieldRepositoryTest.kt`

**Interfaces:**
- Consumes: `LocalTeamMove`, `LocalFullBattle`, `LocalBattleField`, `LocalStzbRepository`, `LocalSocksCaptureServer`, `Preferences`, and Task 3 models.
- Produces: `BattlefieldEventMapper.fromMove(move)`, `fromBattle(battle)`, `fromSiege(event)`, and a `LegacyBattlefieldRepository` implementing the Task 3 interface.

- [ ] **Step 1: Write failing mapper tests**

```kotlin
package com.local.stzb.data.battlefield

import com.example.myapplication.LocalTeamMove
import com.local.stzb.domain.battlefield.EventCategory
import com.local.stzb.domain.battlefield.EventTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class BattlefieldEventMapperTest {
    @Test fun moveBecomesReadableMarchEvent() {
        val move = LocalTeamMove(
            teamId = 42, moveType = 1, subjectId = 7, ownerUid = 9,
            ownerName = "前锋", ownerUnion = "测试盟",
            fromWid = 100010, toWid = 100020, currentWid = 100015,
            fromXy = "10,10", toXy = "10,20", currentXy = "10,15",
            startTime = 1_700_000_000L, arriveTime = 1_700_000_600L, speed = 100,
        )
        val event = BattlefieldEventMapper.fromMove(move)
        assertEquals(EventCategory.MARCH, event.category)
        assertEquals("前锋 · 测试盟", event.title)
        assertEquals(EventTarget.Team(42), event.target)
    }
}
```

- [ ] **Step 2: Verify the mapper test fails**

Run `./gradlew :app:testDebugUnitTest --tests '*BattlefieldEventMapperTest'`.

Expected: FAIL because `BattlefieldEventMapper` is missing.

- [ ] **Step 3: Implement deterministic mapping**

```kotlin
package com.local.stzb.data.battlefield

import com.example.myapplication.LocalTeamMove
import com.local.stzb.domain.battlefield.BattlefieldEvent
import com.local.stzb.domain.battlefield.EventCategory
import com.local.stzb.domain.battlefield.EventPriority
import com.local.stzb.domain.battlefield.EventTarget

object BattlefieldEventMapper {
    fun fromMove(move: LocalTeamMove): BattlefieldEvent = BattlefieldEvent(
        id = "march:${move.teamId}:${move.arriveTime}",
        occurredAt = maxOf(move.startTime, move.arriveTime),
        category = EventCategory.MARCH,
        priority = EventPriority.NORMAL,
        title = listOf(move.ownerName.ifBlank { "未知玩家" }, move.ownerUnion)
            .filter { it.isNotBlank() }.joinToString(" · "),
        summary = "${move.fromXy.ifBlank { move.fromWid.toString() }} → ${move.toXy.ifBlank { move.toWid.toString() }}",
        target = EventTarget.Team(move.teamId),
    )

    fun fromBattle(battle: LocalFullBattle): BattlefieldEvent = BattlefieldEvent(
        id = "battle:${battle.battleId}",
        occurredAt = battle.time,
        category = EventCategory.BATTLE,
        priority = if (battle.garrison > 0 || battle.cityType > 0) EventPriority.IMPORTANT else EventPriority.NORMAL,
        title = "${battle.attackerName.ifBlank { "未知攻方" }} vs ${battle.defenderName.ifBlank { "未知守方" }}",
        summary = listOf(battle.widName.ifBlank { battle.widCode }, "战果 ${battle.result}")
            .filter { it.isNotBlank() }.joinToString(" · "),
        target = EventTarget.Battle(battle.battleId),
    )

    fun fromSiege(event: LocalBattleField): BattlefieldEvent = BattlefieldEvent(
        id = "siege:${event.wid}:${event.sourceMsgId}",
        occurredAt = 0L,
        category = EventCategory.SIEGE,
        priority = if (event.nearbyCount > 0) EventPriority.IMPORTANT else EventPriority.NORMAL,
        title = "攻城目标 ${event.wid}",
        summary = "附近 ${event.nearbyCount} 人",
        target = EventTarget.Cell(event.wid),
    )
}
```

- [ ] **Step 4: Implement the legacy source seam and repository**

Write `LegacyBattlefieldRepositoryTest` with a fake data source rather than Android globals:

```kotlin
private class FakeBattlefieldSource : LegacyBattlefieldSource {
    var moves = emptyList<LocalTeamMove>()
    override fun captureRunning() = true
    override fun lastEventAt(): Long? = moves.maxOfOrNull { it.arriveTime }
    override fun moves() = moves
    override fun battles() = emptyList<LocalFullBattle>()
    override fun sieges() = emptyList<LocalBattleField>()
}
```

Define the seam and Adapter in `LegacyBattlefieldRepository.kt`:

```kotlin
interface LegacyBattlefieldSource {
    fun captureRunning(): Boolean
    fun lastEventAt(): Long?
    fun moves(): List<LocalTeamMove>
    fun battles(): List<LocalFullBattle>
    fun sieges(): List<LocalBattleField>
}
```

Implement the repository with explicit visible/buffered collections:

```kotlin
class LegacyBattlefieldRepository(
    private val source: LegacyBattlefieldSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BattlefieldRepository {
    private val lock = Any()
    private val visible = LinkedHashMap<String, BattlefieldEvent>()
    private val buffered = LinkedHashMap<String, BattlefieldEvent>()
    private var paused = false
    private var categories = EventCategory.entries.toSet()
    private val state = MutableStateFlow(buildSnapshot())

    override fun observeSnapshot(): Flow<BattlefieldSnapshot> = state.asStateFlow()

    override suspend fun refresh() = withContext(ioDispatcher) {
        val incoming = buildList {
            addAll(source.moves().map(BattlefieldEventMapper::fromMove))
            addAll(source.battles().map(BattlefieldEventMapper::fromBattle))
            addAll(source.sieges().map(BattlefieldEventMapper::fromSiege))
        }.sortedByDescending { it.occurredAt }
        synchronized(lock) {
            val target = if (paused) buffered else visible
            incoming.forEach { event ->
                if (event.id !in visible && event.id !in buffered) target[event.id] = event
            }
            trim(visible, 200)
            trim(buffered, 200)
            state.value = buildSnapshot()
        }
    }

    override fun setPaused(paused: Boolean) = synchronized(lock) {
        if (this.paused && !paused) {
            buffered.values.forEach { visible[it.id] = it }
            buffered.clear()
            trim(visible, 200)
        }
        this.paused = paused
        state.value = buildSnapshot()
    }

    override fun setFilter(categories: Set<EventCategory>) = synchronized(lock) {
        require(categories.isNotEmpty())
        this.categories = categories
        state.value = buildSnapshot()
    }

    private fun buildSnapshot(): BattlefieldSnapshot {
        val moves = source.moves()
        val nowSeconds = System.currentTimeMillis() / 1000L
        return BattlefieldSnapshot(
            capture = CaptureStatus(source.captureRunning(), if (source.captureRunning()) "抓包运行中" else "抓包未启动", source.lastEventAt()),
            metrics = BattlefieldMetrics(moves.size, moves.count { it.arriveTime in nowSeconds..(nowSeconds + 300L) }, source.battles().size, source.sieges().size),
            events = visible.values.filter { it.category in categories }.sortedByDescending { it.occurredAt },
            selectedCategories = categories,
            paused = paused,
            bufferedEventCount = buffered.size,
        )
    }

    private fun trim(events: LinkedHashMap<String, BattlefieldEvent>, limit: Int) {
        while (events.size > limit) events.remove(events.keys.first())
    }
}
```

`AndroidLegacyBattlefieldSource` uses `LocalBattleMonitorStore.history().take(20).flatMap { it.moves }`, `LocalStzbRepository.loadFullBattles(80)`, `loadBattleFields(80)`, and `Preferences(context).getEnable() || LocalSocksCaptureServer.isRunning()`. Its `lastEventAt()` returns the maximum captured/arrival/battle time. Polling belongs to the lifecycle-aware ViewModel, not the repository.

- [ ] **Step 5: Run mapper and repository tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*BattlefieldEventMapperTest' --tests '*LegacyBattlefieldRepositoryTest'
```

Expected: PASS, including pause/resume and category filtering cases.

- [ ] **Step 6: Commit**

```bash
git add astzb/app/src/main/java/com/local/stzb/data/battlefield astzb/app/src/test/java/com/local/stzb/data/battlefield
git commit -m "feat(android): adapt local data to battlefield events"
```

---

### Task 5: Implement Battlefield ViewModel State Machine

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/feature/battlefield/BattlefieldContract.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/battlefield/BattlefieldViewModel.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/feature/battlefield/BattlefieldViewModelTest.kt`

**Interfaces:**
- Consumes: `BattlefieldRepository.observeSnapshot`, `refresh`, `setPaused`, and `setFilter`.
- Produces: `StateFlow<BattlefieldUiState>`, `onIntent(BattlefieldIntent)`, and one-shot `SharedFlow<BattlefieldEffect>`.

- [ ] **Step 1: Write failing state-machine tests**

```kotlin
package com.local.stzb.feature.battlefield

import app.cash.turbine.test
import com.local.stzb.domain.battlefield.EventCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlefieldViewModelTest {
    @Test fun pauseIntentDelegatesAndUpdatesState() = runTest {
        val repository = FakeBattlefieldRepository()
        val viewModel = BattlefieldViewModel(repository)
        viewModel.onIntent(BattlefieldIntent.TogglePaused)
        assertTrue(repository.snapshot.value.paused)
    }

    @Test fun filterIntentNeverAllowsAnEmptySelection() = runTest {
        val repository = FakeBattlefieldRepository()
        val viewModel = BattlefieldViewModel(repository)
        viewModel.effects.test {
            EventCategory.entries.forEach { viewModel.onIntent(BattlefieldIntent.ToggleCategory(it)) }
            assertTrue(awaitItem() is BattlefieldEffect.ShowMessage)
        }
    }
}
```

Add Turbine `1.2.0` as a test dependency in the version catalog and app module.

- [ ] **Step 2: Run and verify contract/ViewModel symbols are missing**

Run `./gradlew :app:testDebugUnitTest --tests '*BattlefieldViewModelTest'`.

Expected: compilation FAIL.

- [ ] **Step 3: Implement the contract**

```kotlin
package com.local.stzb.feature.battlefield

import com.local.stzb.core.ui.LoadState
import com.local.stzb.domain.battlefield.BattlefieldSnapshot
import com.local.stzb.domain.battlefield.EventCategory

data class BattlefieldUiState(
    val loadState: LoadState<BattlefieldSnapshot> = LoadState.Loading,
)

sealed interface BattlefieldIntent {
    data class SetActive(val active: Boolean) : BattlefieldIntent
    data object Refresh : BattlefieldIntent
    data object TogglePaused : BattlefieldIntent
    data class ToggleCategory(val category: EventCategory) : BattlefieldIntent
    data object ConsumeBufferedEvents : BattlefieldIntent
}

sealed interface BattlefieldEffect {
    data class ShowMessage(val text: String) : BattlefieldEffect
}
```

- [ ] **Step 4: Implement ViewModel collection and intent handling**

Implement the ViewModel with repository-owned data and a two-second refresh loop:

```kotlin
class BattlefieldViewModel(
    private val repository: BattlefieldRepository,
) : ViewModel() {
    private var refreshJob: Job? = null
    private val _effects = MutableSharedFlow<BattlefieldEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<BattlefieldEffect> = _effects.asSharedFlow()

    val state: StateFlow<BattlefieldUiState> = repository.observeSnapshot()
        .map { snapshot ->
            val loadState = if (!snapshot.capture.running && snapshot.events.isEmpty()) {
                LoadState.Empty("尚未收到战场动态", "启动抓包")
            } else {
                LoadState.Content(snapshot)
            }
            BattlefieldUiState(loadState)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BattlefieldUiState())

    fun onIntent(intent: BattlefieldIntent) {
        val snapshot = (state.value.loadState as? LoadState.Content)?.value
        when (intent) {
            is BattlefieldIntent.SetActive -> {
                refreshJob?.cancel()
                refreshJob = if (intent.active) viewModelScope.launch {
                    while (isActive) {
                        runCatching { repository.refresh() }
                            .onFailure { _effects.emit(BattlefieldEffect.ShowMessage(it.message ?: "刷新失败")) }
                        delay(2_000)
                    }
                } else null
            }
            BattlefieldIntent.Refresh -> viewModelScope.launch {
                runCatching { repository.refresh() }
                    .onFailure { _effects.emit(BattlefieldEffect.ShowMessage(it.message ?: "刷新失败")) }
            }
            BattlefieldIntent.TogglePaused -> snapshot?.let { repository.setPaused(!it.paused) }
            is BattlefieldIntent.ToggleCategory -> snapshot?.let {
                val next = if (intent.category in it.selectedCategories) {
                    it.selectedCategories - intent.category
                } else {
                    it.selectedCategories + intent.category
                }
                if (next.isEmpty()) {
                    _effects.tryEmit(BattlefieldEffect.ShowMessage("至少保留一种动态类型"))
                } else {
                    repository.setFilter(next)
                }
            }
            BattlefieldIntent.ConsumeBufferedEvents -> repository.setPaused(false)
        }
    }
}
```

- [ ] **Step 5: Run tests and commit**

Run `./gradlew :app:testDebugUnitTest --tests '*BattlefieldViewModelTest'`.

Expected: PASS.

```bash
git add astzb/gradle/libs.versions.toml astzb/app/build.gradle.kts astzb/app/src/main/java/com/local/stzb/feature/battlefield astzb/app/src/test/java/com/local/stzb/feature/battlefield
git commit -m "feat(android): add battlefield state machine"
```

---

### Task 6: Build the Real-Time Battlefield Screen

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/core/ui/StatePanel.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/battlefield/BattlefieldComponents.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/battlefield/BattlefieldScreen.kt`
- Test: `astzb/app/src/androidTest/java/com/local/stzb/feature/battlefield/BattlefieldScreenTest.kt`

**Interfaces:**
- Consumes: `BattlefieldUiState` and callback `(BattlefieldIntent) -> Unit`.
- Produces: stateless `BattlefieldScreen(state, onIntent, onEventClick)` and reusable status/feed components.

- [ ] **Step 1: Write a failing screen test**

```kotlin
package com.local.stzb.feature.battlefield

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.local.stzb.core.designsystem.AstzbTheme
import org.junit.Rule
import org.junit.Test

class BattlefieldScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun contentShowsStatusMetricsFeedAndPauseAction() {
        val snapshot = BattlefieldSnapshot(
            capture = CaptureStatus(true, "抓包运行中", 1_700_000_000L),
            metrics = BattlefieldMetrics(12, 2, 8, 1),
            events = listOf(
                BattlefieldEvent(
                    id = "march:42:1700000600",
                    occurredAt = 1_700_000_600L,
                    category = EventCategory.MARCH,
                    priority = EventPriority.NORMAL,
                    title = "前锋 · 测试盟",
                    summary = "10,10 → 10,20",
                    target = EventTarget.Team(42),
                )
            ),
        )
        rule.setContent {
            AstzbTheme {
                BattlefieldScreen(BattlefieldUiState(LoadState.Content(snapshot)), {}, {})
            }
        }
        rule.onNodeWithText("实时战场").assertIsDisplayed()
        rule.onNodeWithText("正在行军").assertIsDisplayed()
        rule.onNodeWithText("前锋 · 测试盟").assertIsDisplayed()
        rule.onNodeWithContentDescription("暂停实时刷新").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run and verify screen symbols are missing**

Run `./gradlew :app:compileDebugAndroidTestKotlin`.

Expected: FAIL because `BattlefieldScreen` is missing.

- [ ] **Step 3: Implement screen layout**

Use `LazyColumn` with stable event keys and these exact content sections:

```kotlin
item("header") { BattlefieldHeader(snapshot.capture, snapshot.paused, onIntent) }
item("metrics") { BattlefieldMetricsGrid(snapshot.metrics) }
item("filters") { EventCategoryFilters(snapshot.selectedCategories, onIntent) }
if (snapshot.bufferedEventCount > 0) {
    item("buffered") { NewEventsButton(snapshot.bufferedEventCount) { onIntent(BattlefieldIntent.ConsumeBufferedEvents) } }
}
items(snapshot.events, key = { it.id }) { event -> BattlefieldEventCard(event) { onEventClick(event) } }
```

Requirements:

- `BattlefieldHeader` shows “抓包运行中” or “抓包未启动” with both icon and text;
- pause button content description changes between “暂停实时刷新” and “继续实时刷新”;
- metrics use a two-column adaptive grid without nested vertical scrolling;
- filter chips expose selected semantics;
- event cards use category icon, formatted time, title, summary and priority label;
- `LazyColumn` does not automatically scroll when state updates;
- screen applies navigation-bar and status-bar insets.
- use `LifecycleStartEffect(Unit)` to emit `SetActive(true)` on start and `SetActive(false)` from `onStopOrDispose`; no polling continues while the page is stopped.

- [ ] **Step 4: Implement loading, empty and error panels**

`StatePanel.kt` must expose `LoadingPanel`, `EmptyPanel(message, actionLabel, onAction)`, and `ErrorPanel(message, retryable, onRetry)`. Each action is at least 48dp tall and each icon has a content description or is explicitly decorative.

- [ ] **Step 5: Run screen tests and commit**

Run `./gradlew :app:connectedDebugAndroidTest`.

Expected: `BattlefieldScreenTest`, `ThemeTest`, and `ComposeSmokeTest` PASS.

```bash
git add astzb/app/src/main/java/com/local/stzb/core/ui astzb/app/src/main/java/com/local/stzb/feature/battlefield astzb/app/src/androidTest/java/com/local/stzb/feature/battlefield
git commit -m "feat(android): build real-time battlefield screen"
```

---

### Task 7: Add Four-Destination Navigation and Legacy Fallbacks

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/core/navigation/AppDestination.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/core/navigation/StzbApp.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/placeholder/PlaceholderScreen.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/tools/LegacyToolsScreen.kt`
- Test: `astzb/app/src/androidTest/java/com/local/stzb/core/navigation/StzbNavigationTest.kt`

**Interfaces:**
- Consumes: `BattlefieldScreen`, callbacks `openLegacyDashboard(module)` and `openCaptureConsole()`.
- Produces: `StzbApp(repository, openLegacyDashboard, openCaptureConsole)` with four stable destinations.

- [ ] **Step 1: Write a failing navigation test**

```kotlin
package com.local.stzb.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class StzbNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun battlefieldIsDefaultAndAllPrimaryDestinationsAreReachable() {
        val repository = FakeBattlefieldRepository(
            BattlefieldSnapshot(
                capture = CaptureStatus(false, "抓包未启动", null),
                metrics = BattlefieldMetrics(0, 0, 0, 0),
                events = emptyList(),
            )
        )
        rule.setContent {
            AstzbTheme {
                StzbApp(repository, openLegacyDashboard = {}, openCaptureConsole = {})
            }
        }
        rule.onNodeWithText("实时战场").assertIsDisplayed()
        rule.onNodeWithText("战报").performClick()
        rule.onNodeWithText("战报迁移中").assertIsDisplayed()
        rule.onNodeWithText("同盟").performClick()
        rule.onNodeWithText("同盟迁移中").assertIsDisplayed()
        rule.onNodeWithText("更多").performClick()
        rule.onNodeWithText("经典抓包控制台").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run and verify navigation symbols are missing**

Run `./gradlew :app:compileDebugAndroidTestKotlin`.

Expected: FAIL because `StzbApp` and destinations do not exist.

- [ ] **Step 3: Define destinations**

```kotlin
enum class AppDestination(val route: String, val label: String) {
    BATTLEFIELD("battlefield", "战场"),
    BATTLES("battles", "战报"),
    ALLIANCE("alliance", "同盟"),
    MORE("more", "更多"),
}
```

- [ ] **Step 4: Implement Scaffold and NavHost**

Use `NavigationBar` with four `NavigationBarItem`s. Use Material Symbols for `Radar`, `ReceiptLong`, `Groups`, and `MoreHoriz`. The start destination must be `battlefield`. `launchSingleTop=true`, `restoreState=true`, and `popUpTo(graph.startDestinationId) { saveState = true }` must preserve each primary destination state.

`PlaceholderScreen` must show a concise migration message and a “打开经典页面” button. `LegacyToolsScreen` must show “经典抓包控制台” and “经典数据页面” as explicitly labeled compatibility actions.

- [ ] **Step 5: Run navigation tests and commit**

Run `./gradlew :app:connectedDebugAndroidTest`.

Expected: all four destinations reachable and tests PASS.

```bash
git add astzb/app/src/main/java/com/local/stzb/core/navigation astzb/app/src/main/java/com/local/stzb/feature/placeholder astzb/app/src/main/java/com/local/stzb/feature/tools astzb/app/src/androidTest/java/com/local/stzb/core/navigation
git commit -m "feat(android): add four-destination Compose shell"
```

---

### Task 8: Wire the Compose Activity to Existing Capture Services

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/StzbApplication.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/StzbAppActivity.kt`
- Modify: `astzb/app/src/main/AndroidManifest.xml`
- Modify: `astzb/app/src/main/java/com/example/myapplication/MainActivity.kt`
- Test: `astzb/app/src/androidTest/java/com/local/stzb/StzbAppActivityTest.kt`

**Interfaces:**
- Consumes: `LegacyBattlefieldRepository`, existing initialization functions, `VpnService.prepare`, `TProxyService`, `LocalSocksCaptureServer`, old activities.
- Produces: launcher `StzbAppActivity`, process dependency container, and explicit legacy navigation callbacks.

- [ ] **Step 1: Write a failing launcher test**

```kotlin
package com.local.stzb

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class StzbAppActivityTest {
    @get:Rule val rule = createAndroidComposeRule<StzbAppActivity>()

    @Test fun launchesDirectlyIntoBattlefield() {
        rule.onNodeWithText("实时战场").assertIsDisplayed()
        rule.onNodeWithText("战场").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Verify the Activity is missing**

Run `./gradlew :app:compileDebugAndroidTestKotlin`.

Expected: FAIL with unresolved `StzbAppActivity`.

- [ ] **Step 3: Implement process initialization**

`StzbApplication.onCreate()` must initialize exactly once:

```kotlin
LocalStzbCaptureWriter.init(this)
HeroNameResolver.init(this)
SkillNameResolver.init(this)
LocalStzbRepository.init(this)
LocalBattleSimulator.init(this)
```

Expose a lazy `battlefieldRepository: BattlefieldRepository` built from the production `LegacyBattlefieldSource`.

- [ ] **Step 4: Implement the launcher Activity**

`StzbAppActivity` must:

- inherit `ComponentActivity`;
- keep the existing trial check before rendering;
- register VPN permission with `ActivityResultContracts.StartActivityForResult`;
- call `enableEdgeToEdge()`;
- render `AstzbTheme { StzbApp(...) }`;
- open old `DashboardActivity` with its `EXTRA_MODULE` for compatibility;
- open old `MainActivity` for the classic capture console;
- never duplicate the `StzbApplication` initialization calls.

- [ ] **Step 5: Switch only the launcher intent filter**

In `AndroidManifest.xml`:

```xml
<application
    android:name="com.local.stzb.StzbApplication"
    ...>
    <activity
        android:name="com.local.stzb.StzbAppActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
    <activity
        android:name=".MainActivity"
        android:exported="false" />
</application>
```

Do not change the existing VPN service declarations.

- [ ] **Step 6: Run launcher tests and build**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
```

Expected: all tests PASS and Debug APK builds with both native ABIs.

- [ ] **Step 7: Commit**

```bash
git add astzb/app/src/main/java/com/local/stzb/StzbApplication.kt astzb/app/src/main/java/com/local/stzb/StzbAppActivity.kt astzb/app/src/main/AndroidManifest.xml astzb/app/src/main/java/com/example/myapplication/MainActivity.kt astzb/app/src/androidTest/java/com/local/stzb/StzbAppActivityTest.kt
git commit -m "feat(android): launch modern battlefield app shell"
```

---

### Task 9: Verify Real-Time Behavior and Document Batch 1

**Files:**
- Create: `astzb/app/src/test/java/com/local/stzb/data/battlefield/BattlefieldGoldenFixtureTest.kt`
- Create: `astzb/app/src/test/resources/battlefield/5028_move_sample.json`
- Modify: `astzb/README.md`
- Modify: `astzb/PC_TO_ANDROID_MIGRATION_BACKLOG.md`

**Interfaces:**
- Consumes: complete Batch 1 implementation.
- Produces: regression fixture, repeatable verification commands, updated user instructions and migration status.

- [ ] **Step 1: Add a sanitized 5028 fixture and failing golden test**

Store one real, sanitized payload whose expected move contains stable values for team ID, owner, source WID, target WID and arrival time. The test must parse it with the existing parser seam and assert the exact `BattlefieldEvent`:

```kotlin
assertEquals("march:42:1700000600", event.id)
assertEquals("测试玩家 · 测试同盟", event.title)
assertEquals("10,10 → 10,20", event.summary)
```

- [ ] **Step 2: Run the fixture test before adjusting the Adapter**

Run `./gradlew :app:testDebugUnitTest --tests '*BattlefieldGoldenFixtureTest'`.

Expected: FAIL if the legacy-to-domain mapping does not preserve the fixture fields.

- [ ] **Step 3: Make only the mapping correction required by the fixture**

Do not change packet decoding or database schema. Correct only `BattlefieldEventMapper` or the `LegacyBattlefieldSource` field selection until the golden assertions pass.

- [ ] **Step 4: Execute the complete automated verification**

Run:

```bash
cd /Users/bytedance/stzb_watcher/astzb
./gradlew clean :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL; unit, instrumentation and Compose tests all PASS.

- [ ] **Step 5: Complete the manual device checklist**

On Android 13, 14, or 15:

1. Launch the App and confirm “实时战场” is the first screen.
2. Open “更多 → 经典抓包控制台”.
3. Select STZB, grant VPN permission and start the bridge.
4. Return to the battlefield screen and confirm status changes to running.
5. Trigger a march and confirm a readable event appears.
6. Scroll away from the top, trigger another event, and confirm the list does not jump.
7. Pause updates, trigger an event, and confirm the buffered count increases.
8. Resume and confirm buffered events appear once.
9. Switch through all four primary destinations and confirm state is retained.
10. Open a classic page and return without stopping the VPN.

- [ ] **Step 6: Update documentation with exact current behavior**

In `astzb/README.md`, document the new default home, the four primary destinations, the compatibility capture-console path, and the fact that battles/alliance still use classic pages in Batch 1. In the migration backlog, mark only the Compose shell and battlefield feed complete; do not mark later domains complete.

- [ ] **Step 7: Commit**

```bash
git add astzb/app/src/test astzb/README.md astzb/PC_TO_ANDROID_MIGRATION_BACKLOG.md astzb/app/src/main/java/com/local/stzb/data/battlefield
git commit -m "test(android): verify battlefield Compose migration"
```

---

## Batch 1 Completion Gate

Do not start Batch 2 until all conditions are true:

- Modern Compose launcher opens to the real-time battlefield screen.
- Existing capture services and native libraries remain unchanged and functional.
- Pause, resume, filters, buffered-event indicator and stable scroll behavior pass tests.
- The four primary destinations are present and retain state.
- Legacy capture console and Dashboard remain reachable.
- Golden 5028 fixture, ViewModel tests, Compose UI tests and Debug build pass.
- Manual device checklist has a recorded Android version and result.
- Documentation explicitly distinguishes migrated pages from classic compatibility pages.
