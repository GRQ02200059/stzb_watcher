# Android Battle Stage Reconstruction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the simulator's vertical form layout with a BorderHelper-style tactical battle stage while retaining the Android app's existing simulator data and report behavior.

**Architecture:** Keep state, intents, data mapping, pickers, and local simulation unchanged. Refactor the visual composition into focused tactical stage primitives shared by the duel, report library, and report detail. Verify the design through Compose semantics and a real Pixel_6 simulation path.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android instrumentation tests, existing BorderHelper-authorized drawable resources.

## Global Constraints

- Keep online hero cards via `BattlefieldHeroPortrait`; do not package a hero-card collection.
- Preserve exactly three editable skill slots per hero.
- Keep existing `BattleSimulatorIntent` run/picker/report behavior.
- Do not alter authentication, backend APIs, app package ID, signing configuration, or unrelated worktree changes.
- Do not commit or push.
- Test Android UI only on `emulator-5554`.

---

### Task 1: Stage-Landmark Regression Contract

**Files:**
- Modify: `astzb/app/src/androidTest/java/com/local/stzb/feature/simulator/BattleSimulatorScreenTest.kt`

**Interfaces:**
- Consumes: `BattleSimulatorScreen(sampleState(), ...)`
- Produces: semantic assertions for `我的队伍`, `敌方队伍`, `开始推演`, `战报库`, `阵容编辑`, `批量推演`, and six position labels.

- [ ] **Step 1: Write failing stage test**

```kotlin
@Test fun duelUsesReferenceStyleBattleStageLandmarks() {
    rule.setContent { AstzbTheme { BattleSimulatorScreen(sampleState(), {}, { "武将$it" }, { 0L }, { "战法$it" }, {}) } }
    listOf("我的队伍", "敌方队伍", "开始推演", "战报库", "阵容编辑", "批量推演").forEach {
        rule.onNodeWithText(it, substring = true).assertIsDisplayed()
    }
    rule.onAllNodesWithText("大营").assertCountEquals(2)
    rule.onAllNodesWithText("中军").assertCountEquals(2)
    rule.onAllNodesWithText("前锋").assertCountEquals(2)
}
```

- [ ] **Step 2: Verify RED**

Run: `GRADLE_USER_HOME=/private/tmp/stzb-watcher-gradle-cache ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.stzb.feature.simulator.BattleSimulatorScreenTest`

Expected: FAIL because the new stage actions are not present.

- [ ] **Step 3: Keep the test as the visual composition contract**

Do not weaken assertions to fit the old vertical-card layout.

### Task 2: Duel Battle Stage

**Files:**
- Modify: `astzb/app/src/main/java/com/local/stzb/feature/simulator/BattleSimulatorScreen.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/feature/simulator/TacticalReportScreen.kt`

**Interfaces:**
- Consumes: `BattleSimulatorUiState`, `BattleSimulatorIntent`, hero/skill resolvers, `TacticalBackdrop`.
- Produces: `TacticalDuelScreen` with team bands, three horizontal hero rows per side, a diamond run action, and stage action strip.

- [ ] **Step 1: Add focused stage primitives**

Implement these composables without changing simulator state:

```kotlin
@Composable fun TacticalTeamBand(label: String, accent: Color, modifier: Modifier = Modifier)
@Composable fun TacticalHeroRow(camp: SimulatorCamp, position: Int, hero: LocalSimHeroConfig, ...)
@Composable fun TacticalDiamondAction(label: String, enabled: Boolean, onClick: () -> Unit)
@Composable fun TacticalStageAction(label: String, onClick: () -> Unit)
```

- [ ] **Step 2: Replace the two vertical team cards**

Use one `LazyColumn` stage with the order: title, blue band, three blue rows, center duel/seal, red band, three red rows, diamond action, stage action strip, existing error/result content.

- [ ] **Step 3: Wire the existing intents**

`开始推演` dispatches `Run(1)`. `战报库` dispatches `SelectTacticalView(REPORTS)`. `阵容编辑` scrolls/retains the current editable rows; `批量推演` dispatches `Run(100)`. Keep 1000-run access in the batch action/expanded control.

- [ ] **Step 4: Verify GREEN**

Run Task 1 command. Expected: all `BattleSimulatorScreenTest` cases pass.

### Task 3: Reference-Structured Report Views

**Files:**
- Modify: `astzb/app/src/main/java/com/local/stzb/feature/simulator/TacticalReportScreen.kt`
- Modify: `astzb/app/src/androidTest/java/com/local/stzb/feature/simulator/BattleSimulatorScreenTest.kt`

**Interfaces:**
- Consumes: `LocalSimulationRun`, typed event list, selected report tab/event index.
- Produces: blue/red troop bands, compact hero snapshots, centered outcome seal, filtered tactical event strips, unchanged event-detail dialog.

- [ ] **Step 1: Extend report UI test before changing details**

Assert `蓝色方`, `红色方`, `战报详情`, `回合`, `状态`, `触发`, and `战报过程` remain visible in detail mode.

- [ ] **Step 2: Preserve typed-event semantics while changing layout**

Keep `LocalSimulationEvent.matches`, the `TacticalEventDialog` fields, and `onSelectEvent(index)` unchanged. Change only their visual hierarchy to compact battle strips and troop meter sections.

- [ ] **Step 3: Verify targeted instrumentation**

Run Task 1 command. Expected: all tests pass.

### Task 4: Pixel_6 Visual and Flow Verification

**Files:**
- No production file required unless verification reveals a defect.

- [ ] **Step 1: Install Debug on Pixel_6**

Run: `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 2: Open Tools → 战术演练**

Verify the stage has blue/red team bands, row-level hero configuration, the centered start action, and lower stage actions. Capture a screenshot.

- [ ] **Step 3: Validate one real report**

Tap `开始推演`; verify report detail opens. Tap a damage event and verify source/target/action/amount/remaining troops fields.

### Task 5: Release Verification

**Files:**
- No source changes expected.

- [ ] **Step 1: Run release tests and build**

Run: `GRADLE_USER_HOME=/private/tmp/stzb-watcher-gradle-cache ./gradlew :app:testReleaseUnitTest :app:assembleRelease`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Verify signing and metadata**

Run:

```bash
TOOL_DIR=$(ls -d /Users/bytedance/Library/Android/sdk/build-tools/* | sort -V | tail -1)
APK=app/build/outputs/apk/release/app-release.apk
"$TOOL_DIR/apksigner" verify --verbose "$APK"
"$TOOL_DIR/aapt" dump badging "$APK" | rg "package:"
shasum -a 256 "$APK"
```

- [ ] **Step 3: Install signed Release and cold-start**

Uninstall only `com.local.stzb.random` from `emulator-5554`, install the Release APK, then run `am start -W` for `StzbAppActivity`. Verify the top activity and no `AndroidRuntime` fatal log.

## Plan Self-Review

- Scope coverage: Tasks 1-2 cover the reference duel composition; Task 3 covers report/detail structure; Task 4 validates the real flow; Task 5 validates the deliverable.
- No placeholders: paths, commands, required UI labels, and intent routing are explicit.
- Type consistency: uses existing `BattleSimulatorUiState`, `BattleSimulatorIntent`, `LocalSimHeroConfig`, and typed events without contract changes.
