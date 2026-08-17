# Native Battle Simulator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fifth native “模拟” tab where users edit both three-hero teams, levels, advances, three skills per hero and morale, run 1/100/1000 simulations, and inspect the first run log.

**Architecture:** A feature-local contract and ViewModel own the immutable `LocalSimulationConfig`, resource lists, validation, asynchronous execution and latest result. Compose renders a single scrollable editor plus searchable selection dialogs and a separate log route; the existing `LocalBattleSimulator` remains the only calculation/resource engine.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, AndroidX ViewModel, StateFlow, Kotlin coroutines, JUnit 4, Compose UI tests.

## Global Constraints

- Add “模拟” before “更多” as the fifth persistent bottom navigation destination.
- Both camps contain exactly three valid, non-duplicated heroes.
- Level is clamped to 1–50, advance to 0–5, and morale to 0–100.
- Every hero exposes exactly three editable skill slots; empty slots are allowed, duplicate equipped skills are rejected.
- Support repeat counts of exactly 1, 100 and 1000.
- Simulation runs off the main thread, prevents re-entry, retains configuration on failure, and stores state only for the current application session.
- Reuse `LocalBattleSimulator` and do not change its combat algorithm.

---

## File Structure

- Create `app/src/main/java/com/local/stzb/feature/simulator/BattleSimulatorContract.kt`: UI state, camp and picker types, and intents.
- Create `app/src/main/java/com/local/stzb/feature/simulator/BattleSimulatorEngine.kt`: narrow adapter around the legacy singleton for deterministic ViewModel tests.
- Create `app/src/main/java/com/local/stzb/feature/simulator/BattleSimulatorViewModel.kt`: editing, validation, resource loading and simulation execution.
- Create `app/src/main/java/com/local/stzb/feature/simulator/BattleSimulatorScreen.kt`: editor, team/hero cards, controls, results and searchable pickers.
- Create `app/src/main/java/com/local/stzb/feature/simulator/BattleLogScreen.kt`: first-run record list and back navigation.
- Modify `app/src/main/java/com/local/stzb/core/navigation/AppDestination.kt`: add the SIMULATOR destination.
- Modify `app/src/main/java/com/local/stzb/core/navigation/StzbApp.kt`: create the ViewModel and register simulator/log routes.
- Create `app/src/test/java/com/local/stzb/feature/simulator/BattleSimulatorViewModelTest.kt`: business-rule and async execution coverage.
- Create `app/src/androidTest/java/com/local/stzb/feature/simulator/BattleSimulatorScreenTest.kt`: editor/result/log UI coverage.
- Modify `app/src/androidTest/java/com/local/stzb/core/navigation/StzbNavigationTest.kt`: fifth-tab reachability.

### Task 1: Simulator contract and engine boundary

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/simulator/BattleSimulatorContract.kt`
- Create: `app/src/main/java/com/local/stzb/feature/simulator/BattleSimulatorEngine.kt`
- Test: `app/src/test/java/com/local/stzb/feature/simulator/BattleSimulatorViewModelTest.kt`

**Interfaces:**
- Consumes: `LocalSimulationConfig`, `LocalSimulationSummary`, `LocalSimHeroOption`, `LocalSimSkillOption`.
- Produces: `SimulatorCamp`, `SimulatorPicker`, `BattleSimulatorUiState`, `BattleSimulatorIntent`, and `BattleSimulatorEngine`.

- [ ] **Step 1: Write the failing contract/engine test**

```kotlin
@Test fun loadsDefaultConfigurationAndResources() = runTest(dispatcher) {
    val engine = FakeEngine()
    val viewModel = BattleSimulatorViewModel(engine, dispatcher) { 123 }
    advanceUntilIdle()
    assertEquals(engine.defaultConfig, viewModel.state.value.config)
    assertEquals(engine.heroes, viewModel.state.value.heroOptions)
    assertEquals(engine.skills, viewModel.state.value.skillOptions)
    assertFalse(viewModel.state.value.loading)
}
```

- [ ] **Step 2: Run it and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests com.local.stzb.feature.simulator.BattleSimulatorViewModelTest.loadsDefaultConfigurationAndResources`

Expected: compilation failure because the simulator feature types do not exist.

- [ ] **Step 3: Add the contract**

```kotlin
enum class SimulatorCamp { BLUE, RED }

sealed interface SimulatorPicker {
    data class Hero(val camp: SimulatorCamp, val position: Int, val query: String = "") : SimulatorPicker
    data class Skill(val camp: SimulatorCamp, val position: Int, val slot: Int, val query: String = "") : SimulatorPicker
}

data class BattleSimulatorUiState(
    val loading: Boolean = true,
    val running: Boolean = false,
    val config: LocalSimulationConfig? = null,
    val heroOptions: List<LocalSimHeroOption> = emptyList(),
    val skillOptions: List<LocalSimSkillOption> = emptyList(),
    val result: LocalSimulationSummary? = null,
    val picker: SimulatorPicker? = null,
    val error: String? = null,
)

sealed interface BattleSimulatorIntent {
    data class SetMorale(val camp: SimulatorCamp, val value: Int) : BattleSimulatorIntent
    data class SetLevel(val camp: SimulatorCamp, val position: Int, val value: Int) : BattleSimulatorIntent
    data class SetAdvance(val camp: SimulatorCamp, val position: Int, val value: Int) : BattleSimulatorIntent
    data class OpenHeroPicker(val camp: SimulatorCamp, val position: Int) : BattleSimulatorIntent
    data class OpenSkillPicker(val camp: SimulatorCamp, val position: Int, val slot: Int) : BattleSimulatorIntent
    data class PickerQuery(val value: String) : BattleSimulatorIntent
    data class SelectHero(val heroId: Long) : BattleSimulatorIntent
    data class SelectSkill(val skillId: Long?) : BattleSimulatorIntent
    data object ClosePicker : BattleSimulatorIntent
    data class Run(val repeat: Int) : BattleSimulatorIntent
    data object DismissError : BattleSimulatorIntent
}
```

- [ ] **Step 4: Add the engine adapter**

```kotlin
interface BattleSimulatorEngine {
    fun defaultConfig(): LocalSimulationConfig
    fun heroes(): List<LocalSimHeroOption>
    fun skills(): List<LocalSimSkillOption>
    fun simulate(config: LocalSimulationConfig): LocalSimulationSummary
    fun heroName(id: Long): String
    fun heroIconId(id: Long): Long
    fun skillName(id: Long): String
}

object LocalBattleSimulatorEngine : BattleSimulatorEngine {
    override fun defaultConfig() = LocalBattleSimulator.defaultWebConfig()
    override fun heroes() = LocalBattleSimulator.selectableHeroes()
    override fun skills() = LocalBattleSimulator.selectableSkills()
    override fun simulate(config: LocalSimulationConfig) = LocalBattleSimulator.simulate(config)
    override fun heroName(id: Long) = LocalBattleSimulator.heroName(id)
    override fun heroIconId(id: Long) = LocalBattleSimulator.heroIconId(id)
    override fun skillName(id: Long) = LocalBattleSimulator.skillName(id)
}
```

- [ ] **Step 5: Run the focused test and commit**

Run: `./gradlew :app:testDebugUnitTest --tests com.local.stzb.feature.simulator.BattleSimulatorViewModelTest`

Expected: PASS.

```bash
git add app/src/main/java/com/local/stzb/feature/simulator app/src/test/java/com/local/stzb/feature/simulator
git commit -m "feat: add battle simulator contract"
```

### Task 2: ViewModel editing, validation and execution

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/simulator/BattleSimulatorViewModel.kt`
- Modify: `app/src/test/java/com/local/stzb/feature/simulator/BattleSimulatorViewModelTest.kt`

**Interfaces:**
- Consumes: the Task 1 contract and `BattleSimulatorEngine`.
- Produces: `BattleSimulatorViewModel.onIntent(BattleSimulatorIntent)` and observable `StateFlow<BattleSimulatorUiState>`.

- [ ] **Step 1: Add failing edit-rule tests**

```kotlin
@Test fun clampsNumericFieldsAndUpdatesOnlyRequestedHero() = runTest(dispatcher) {
    val vm = BattleSimulatorViewModel(FakeEngine(), dispatcher) { 123 }
    advanceUntilIdle()
    vm.onIntent(BattleSimulatorIntent.SetMorale(SimulatorCamp.BLUE, 999))
    vm.onIntent(BattleSimulatorIntent.SetLevel(SimulatorCamp.BLUE, 1, 0))
    vm.onIntent(BattleSimulatorIntent.SetAdvance(SimulatorCamp.BLUE, 1, 9))
    val state = vm.state.value
    assertEquals(100, state.config!!.blue.morale)
    assertEquals(1, state.config.blue.heroes[1].level)
    assertEquals(5, state.config.blue.heroes[1].advance)
}

@Test fun selectingHeroRejectsDuplicateAndClearsPreviousSkills() = runTest(dispatcher) {
    val vm = readyViewModel()
    vm.onIntent(BattleSimulatorIntent.OpenHeroPicker(SimulatorCamp.BLUE, 1))
    vm.onIntent(BattleSimulatorIntent.SelectHero(vm.state.value.config!!.blue.heroes[0].heroId))
    assertEquals("同一队伍不能重复选择武将", vm.state.value.error)
    vm.onIntent(BattleSimulatorIntent.SelectHero(999L))
    assertEquals(emptyList<Long>(), vm.state.value.config!!.blue.heroes[1].equipSkillIds)
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests com.local.stzb.feature.simulator.BattleSimulatorViewModelTest`

Expected: failures showing edits/intents are not implemented.

- [ ] **Step 3: Implement immutable team/hero updates and three-slot skill rules**

Use helpers with these exact signatures:

```kotlin
private fun LocalSimulationConfig.updateTeam(
    camp: SimulatorCamp,
    transform: (LocalSimTeamConfig) -> LocalSimTeamConfig,
): LocalSimulationConfig

private fun LocalSimTeamConfig.updateHero(
    position: Int,
    transform: (LocalSimHeroConfig) -> LocalSimHeroConfig,
): LocalSimTeamConfig

private fun List<Long>.withSkill(slot: Int, skillId: Long?): List<Long>
```

`withSkill` must normalize to three nullable conceptual slots during editing, remove the selected slot when `skillId == null`, reject a duplicate in another slot, then persist only positive IDs in slot order.

- [ ] **Step 4: Add failing execution tests**

```kotlin
@Test fun runsOnlySupportedRepeatCountsAndPublishesSummary() = runTest(dispatcher) {
    val engine = FakeEngine()
    val vm = BattleSimulatorViewModel(engine, dispatcher) { 456 }
    advanceUntilIdle()
    vm.onIntent(BattleSimulatorIntent.Run(100))
    advanceUntilIdle()
    assertEquals(100, engine.lastConfig!!.repeat)
    assertEquals(456, engine.lastConfig!!.seed)
    assertEquals(engine.summary, vm.state.value.result)
    assertFalse(vm.state.value.running)
}

@Test fun ignoresRunWhileAlreadyRunningAndRecoversFromFailure() = runTest(dispatcher) {
    val engine = SuspendingFakeEngine()
    val vm = BattleSimulatorViewModel(engine, dispatcher) { 1 }
    advanceUntilIdle()
    vm.onIntent(BattleSimulatorIntent.Run(1))
    vm.onIntent(BattleSimulatorIntent.Run(1000))
    assertEquals(1, engine.calls)
    engine.fail(IllegalStateException("模拟失败"))
    advanceUntilIdle()
    assertEquals("模拟失败：模拟失败", vm.state.value.error)
    assertNotNull(vm.state.value.config)
}
```

- [ ] **Step 5: Implement validation and background simulation**

Accept only repeats in `setOf(1, 100, 1000)`. Validate three positive, distinct hero IDs per camp before launching. Set `running = true`, clear the prior error, call `withContext(io) { engine.simulate(config.copy(repeat = repeat, seed = seed())) }`, and publish either result or `模拟失败：<message>` while always restoring `running = false`.

- [ ] **Step 6: Run all ViewModel tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests com.local.stzb.feature.simulator.BattleSimulatorViewModelTest`

Expected: PASS.

```bash
git add app/src/main/java/com/local/stzb/feature/simulator/BattleSimulatorViewModel.kt app/src/test/java/com/local/stzb/feature/simulator/BattleSimulatorViewModelTest.kt
git commit -m "feat: implement simulator editing and execution"
```

### Task 3: Native editor, resource pickers and results

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/simulator/BattleSimulatorScreen.kt`
- Create: `app/src/androidTest/java/com/local/stzb/feature/simulator/BattleSimulatorScreenTest.kt`

**Interfaces:**
- Consumes: `BattleSimulatorUiState`, `BattleSimulatorIntent`, and engine name/icon lookup functions.
- Produces: `BattleSimulatorScreen(state, onIntent, heroName, heroIconId, skillName, onOpenLog)`.

- [ ] **Step 1: Write a failing Compose editor test**

```kotlin
@Test fun showsBothTeamsThreeHeroesThreeSkillSlotsAndRunActions() {
    rule.setContent {
        AstzbTheme {
            BattleSimulatorScreen(sampleState, {}, ::heroName, { 0L }, ::skillName, {})
        }
    }
    listOf("战斗模拟器", "攻方", "守方", "士气 100", "单次模拟", "模拟 100 次", "模拟 1000 次")
        .forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    rule.onAllNodesWithText("选择战法").assertCountEquals(18)
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`

Expected: compilation failure because `BattleSimulatorScreen` does not exist.

- [ ] **Step 3: Implement the scrollable editor**

Build `LazyColumn` sections for title/resources, blue team, red team, actions, error and results. Each hero card must use `BattlefieldHeroPortrait`, show name, `等级 - / +`, `红度 - / +`, and exactly three full-width skill slot buttons. Use `CircularProgressIndicator` and disable all run buttons when `state.running`.

- [ ] **Step 4: Implement searchable hero and skill dialogs**

Use `AlertDialog` containing an `OutlinedTextField` and bounded `LazyColumn`. Filter hero options by name/country/army type and skill options by name/type/description, ignoring case. Skill dialog begins with a “清空该槽” action. Route all actions through contract intents.

- [ ] **Step 5: Add and pass result rendering tests**

```kotlin
@Test fun showsRatesRemainingTroopsAndLogActionAfterSimulation() {
    rule.setContent { AstzbTheme { BattleSimulatorScreen(resultState, {}, ::heroName, { 0 }, ::skillName, {}) } }
    listOf("攻方胜率 60.0%", "守方胜率 30.0%", "平局 10.0%", "剩余兵力", "查看战斗日志")
        .forEach { rule.onNodeWithText(it, substring = true).assertIsDisplayed() }
}
```

Run: `./gradlew :app:compileDebugAndroidTestKotlin`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/local/stzb/feature/simulator/BattleSimulatorScreen.kt app/src/androidTest/java/com/local/stzb/feature/simulator/BattleSimulatorScreenTest.kt
git commit -m "feat: add native simulator editor"
```

### Task 4: Battle log and fifth navigation tab

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/simulator/BattleLogScreen.kt`
- Modify: `app/src/main/java/com/local/stzb/core/navigation/AppDestination.kt`
- Modify: `app/src/main/java/com/local/stzb/core/navigation/StzbApp.kt`
- Modify: `app/src/androidTest/java/com/local/stzb/core/navigation/StzbNavigationTest.kt`

**Interfaces:**
- Consumes: `BattleSimulatorViewModel.state` and `LocalSimulationSummary.firstRun.records`.
- Produces: `AppDestination.SIMULATOR`, route `simulator-log`, and a navigable log page.

- [ ] **Step 1: Extend the navigation test first**

```kotlin
rule.onNodeWithText("模拟").performClick()
rule.onNodeWithText("战斗模拟器").assertIsDisplayed()
```

Run: `./gradlew :app:compileDebugAndroidTestKotlin`

Expected: failure because the destination does not exist.

- [ ] **Step 2: Add the destination and icon**

```kotlin
enum class AppDestination(val route: String, val label: String) {
    BATTLEFIELD("battlefield", "战场"),
    TEAMS("teams", "队伍"),
    TEAM_REPORT("team-report", "团队"),
    SIMULATOR("simulator", "模拟"),
    MORE("more", "更多"),
}
```

Map it to `Icons.Outlined.Science` in `StzbApp.kt`.

- [ ] **Step 3: Mount one session-scoped ViewModel and routes**

Create `BattleSimulatorViewModel(LocalBattleSimulatorEngine)` once beside the other top-level ViewModels. Register the main simulator destination with `onOpenLog = { navController.navigate("simulator-log") }`; register `simulator-log` using the same state so edits and results survive navigation.

- [ ] **Step 4: Implement the log screen**

```kotlin
@Composable
fun BattleLogScreen(run: LocalSimulationRun?, onBack: () -> Unit) {
    // TopAppBar with “返回模拟器”; summary row for winner/remaining troops;
    // LazyColumn of indexed records, or EmptyPanel when run is null.
}
```

Every record must remain selectable/readable and must not be truncated to one line.

- [ ] **Step 5: Compile navigation tests and commit**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`

Expected: PASS.

```bash
git add app/src/main/java/com/local/stzb/core/navigation app/src/main/java/com/local/stzb/feature/simulator/BattleLogScreen.kt app/src/androidTest/java/com/local/stzb/core/navigation/StzbNavigationTest.kt
git commit -m "feat: add simulator navigation and battle log"
```

### Task 5: Full verification and device installation

**Files:**
- Verify all files from Tasks 1–4.

**Interfaces:**
- Consumes: completed feature.
- Produces: tested and installed debug APK.

- [ ] **Step 1: Run focused tests**

Run:

```bash
./gradlew \
  :app:testDebugUnitTest --tests com.local.stzb.feature.simulator.BattleSimulatorViewModelTest \
  :app:compileDebugAndroidTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run full regression and build**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` with zero failing unit tests.

- [ ] **Step 3: Install and inspect the package**

Run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm path com.local.stzb.random
```

Expected: `Success` and a valid installed `base.apk` path.

- [ ] **Step 4: Device acceptance check**

Open the app and confirm: five bottom tabs; simulator default teams; hero, skill, level, advance and morale controls; 1/100/1000 actions; result percentages; log opens and returns without resetting configuration. Capture logcat if any action exits or crashes.

- [ ] **Step 5: Check diff and commit any verification-only adjustments**

Run: `git diff --check && git status --short`

Expected: no whitespace errors and no unrelated user files staged. If verification required changes, commit only simulator-related files with `git commit -m "fix: complete simulator device verification"`.
