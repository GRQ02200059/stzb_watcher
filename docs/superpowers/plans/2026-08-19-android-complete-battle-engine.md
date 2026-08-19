# Android Complete Battle Engine Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Android tactical drill's lightweight simulator with the complete `battle-engine`, including full event output such as Liu Bei's post-damage emergency recovery.

**Architecture:** A new Android Library compiles the complete pure-Kotlin battle core and packages the battle configuration resources. A focused adapter converts current Android team config into complete-engine team specs and maps complete events back to the existing report model, so the Compose UI remains unchanged.

**Tech Stack:** Android Gradle Plugin, Kotlin, Jetpack Compose, Jackson Kotlin, complete `battle-engine` core, Android instrumentation tests.

## Global Constraints

- Keep simulation local and offline; do not call the desktop CLI or any remote API.
- Package only complete battle-core resources needed by Android.
- Exclude desktop CLI, server report store, and file-system client/NPC scanners.
- Preserve existing tactical UI, online card mapping, three skill slots, and event-detail dialog.
- Do not commit or push.
- Run device tests only against `emulator-5554`.

---

### Task 1: Android Battle Engine Library

**Files:**
- Create: `astzb/battle-engine-android/build.gradle.kts`
- Create: `astzb/battle-engine-android/src/main/AndroidManifest.xml`
- Modify: `astzb/settings.gradle.kts`
- Modify: `astzb/app/build.gradle.kts`

**Interfaces:**
- Produces: Android library package `com.stzb.battle.core` with `BattleEngine`, `BattleConfigRepository`, `BattleTeamBuilder`, and `BattleEvent`.
- Produces: classpath resources under `battle-config/`.

- [ ] **Step 1: Write failing Android integration test**

Create a test importing `BattleConfigRepository.loadDefault()` and asserting `skill(200016)?.name == "皇裔流离"`.

- [ ] **Step 2: Verify RED**

Run targeted Android test. Expected: compiler cannot resolve `com.stzb.battle.core`.

- [ ] **Step 3: Add library module and selected complete-engine source set**

Add external Kotlin sources from `../../battle-engine/src/main/kotlin`, excluding `cli/**`, `ClientBattleReportStore.kt`, `ClientBattleTextReplayAdapter.kt`, `ClientBattleTextReplayProtocol.kt`, `ClientBattlePreparationEventProjector.kt`, `ClientNpcArmyRepository.kt`, `BattleEquipmentRepository.kt`, `BattleReportCodec.kt`, and `ClientReportTextEncoder.kt`. Package `../../battle-engine/src/main/resources/battle-config`.

- [ ] **Step 4: Supply Android-compatible config-only construction**

Ensure `BattleTeamBuilder` can be built without desktop client/NPC/equipment repositories when no gear/troop-feature configuration is requested.

- [ ] **Step 5: Verify GREEN**

Run the targeted Android integration test. Expected: `皇裔流离` resolves from APK-packaged configuration.

### Task 2: Complete Engine Adapter and Event Mapping

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/feature/simulator/CompleteBattleSimulatorEngine.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/feature/simulator/BattleSimulatorEngine.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/feature/simulator/CompleteBattleSimulatorEngineTest.kt`

**Interfaces:**
- Consumes: `LocalSimulationConfig`.
- Produces: existing `LocalSimulationSummary` with complete-engine-derived `LocalSimulationRun` events.

- [ ] **Step 1: Write failing adapter mapping test**

Build a Liu Bei attacker config and assert the adapter's report contains a preparation event for `皇裔流离`, round-start events, and a final result event.

- [ ] **Step 2: Verify RED**

Run `:app:testDebugUnitTest --tests '*CompleteBattleSimulatorEngineTest'`. Expected: adapter class does not exist.

- [ ] **Step 3: Implement team and event mapper**

Convert hero IDs, level, advance, morale, and three skill slots into `BattleHeroSpec`. Map complete events to the current typed local event model with source/target names and post-event troops.

- [ ] **Step 4: Switch default engine**

Make `BattleSimulatorViewModel` default to the complete-engine adapter. Leave `LocalBattleSimulatorEngine` available but non-default.

- [ ] **Step 5: Verify GREEN**

Run adapter unit tests and existing ViewModel tests.

### Task 3: Liu Bei Emergency-Recovery Acceptance

**Files:**
- Create: `astzb/app/src/androidTest/java/com/local/stzb/feature/simulator/CompleteBattleEngineLiuBeiTest.kt`

**Interfaces:**
- Consumes: complete Android adapter.
- Produces: deterministic seed search/replay proving a post-damage `RECOVERY` event attributed to `皇裔流离`.

- [ ] **Step 1: Write failing deterministic acceptance test**

Search a bounded deterministic seed range with Liu Bei and assert one report has a `RECOVERY` event with skill name `皇裔流离` after an opposing damage event.

- [ ] **Step 2: Verify RED before full mapping**

Expected: no matching recovery event from the lightweight/default adapter.

- [ ] **Step 3: Implement any missing event attribution conversion**

Preserve `BattleEvent.Recovery.skillId` through the mapper and resolve to skill name.

- [ ] **Step 4: Verify GREEN on Pixel_6**

Run targeted instrumentation tests. Expected: Liu Bei recovery appears after damage.

### Task 4: UI and Release Verification

**Files:**
- Modify only if tests demonstrate a mapper/UI incompatibility.

- [ ] **Step 1: Run full tactical UI suite**

Run targeted `BattleSimulatorScreenTest`, existing tactical report test, and the Liu Bei engine acceptance test on `emulator-5554`.

- [ ] **Step 2: Real-device flow**

Install Debug, simulate a Liu Bei team, open the report, and confirm preparation, per-round events, damage, and recovery display in the existing timeline.

- [ ] **Step 3: Release build and installation**

Run `:app:testReleaseUnitTest :app:assembleRelease`, verify with `apksigner`, inspect package metadata with `aapt`, calculate SHA-256, install Release on Pixel_6, and cold-start `StzbAppActivity`.

## Plan Self-Review

- Task 1 covers Android module/resource packaging.
- Task 2 covers adapter and default backend replacement.
- Task 3 covers Liu Bei's previously missing post-damage recovery.
- Task 4 covers UI continuity and signed release delivery.
