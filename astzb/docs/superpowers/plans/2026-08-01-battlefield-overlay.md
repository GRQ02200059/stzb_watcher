# Battlefield Overlay Monitor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional draggable, collapsible Android system overlay that keeps all observed battlefield teams in a newest-first scrollable list while showing only player, state, three heroes/red levels, destination, arrival/update time and historical win rate.

**Architecture:** Extend the existing battlefield presentation model with structured overlay fields, then maintain overlay history in a pure `OverlayTeamStore` keyed by `teamId`. A foreground `BattlefieldOverlayService` reuses the application `BattlefieldRepository`, refreshes every two seconds, and renders a ComposeView through `WindowManager`; the battlefield screen owns the permission/start/stop entry point.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android WindowManager/foreground service, coroutines/StateFlow, JUnit 4, Compose UI Test.

## Global Constraints

- Use the existing 5026/5028 parser and `BattlefieldRepository`; do not build a second packet parser.
- Same `teamId` overwrites and moves to the top; teams absent from later snapshots remain with their last state.
- Include marching, guarding, stationed/fort and other observed team states.
- Display no portraits, skills, troop counts or full battle cards.
- Require explicit `SYSTEM_ALERT_WINDOW` permission and a user-started foreground service.
- Preserve the modified root `README.md` and untracked `astzb/third_party`.

---

### Task 1: Structured overlay fields in battlefield events

**Files:**
- Modify: `app/src/main/java/com/local/stzb/domain/battlefield/BattlefieldEvent.kt`
- Modify: `app/src/main/java/com/local/stzb/data/battlefield/BattlefieldEventMapper.kt`
- Test: `app/src/test/java/com/local/stzb/data/battlefield/BattlefieldEventMapperTest.kt`

**Interfaces:**
- Extend `BattlefieldTeamPresentation` with `teamId: Int`, `destinationText: String`, `arrivalAt: Long?`, and `winRate: Double?`.
- Continue exposing `heroes`, `stateText`, `arrivalText`, and existing card fields unchanged.

- [ ] Add failing mapper assertions that a 5028 move emits its team ID, destination, arrival epoch, three hero names/red levels, and nullable win rate from `Local13A2TeamInsight.stats`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.local.stzb.data.battlefield.BattlefieldEventMapperTest'`; expect compilation failure for missing structured fields.
- [ ] Implement the fields without parsing existing display strings; return null win rate when battle count is zero.
- [ ] Re-run focused and all unit tests; expect `BUILD SUCCESSFUL`.
- [ ] Commit `feat: expose battlefield overlay fields`.

### Task 2: Persistent newest-first overlay state

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/overlay/BattlefieldOverlayModels.kt`
- Create: `app/src/main/java/com/local/stzb/feature/overlay/OverlayTeamStore.kt`
- Test: `app/src/test/java/com/local/stzb/feature/overlay/OverlayTeamStoreTest.kt`

**Interfaces:**
- `OverlayTeamStore.accept(snapshot: BattlefieldSnapshot): OverlayMonitorState`.
- `OverlayTeam` contains team ID, player, state, exactly up to three `OverlayHero(name, advance)`, destination, time label/epoch and nullable win rate.

- [ ] Write failing tests for initial newest-first insertion, same-team overwrite/move-to-top, absent-team retention, all move states, and nullable win rate.
- [ ] Run the focused test and confirm failure because the store is missing.
- [ ] Implement a bounded-memory store that retains all teams for the service lifetime and sorts by the latest accepted event timestamp, using acceptance order as a stable tie-breaker.
- [ ] Re-run focused and all unit tests.
- [ ] Commit `feat: retain battlefield overlay teams`.

### Task 3: Expand/collapse Compose overlay content

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/overlay/BattlefieldOverlayContent.kt`
- Test: `app/src/androidTest/java/com/local/stzb/feature/overlay/BattlefieldOverlayContentTest.kt`

**Interfaces:**
- `BattlefieldOverlayContent(state, collapsed, onCollapse, onExpand, onClose, dragHandleModifier)`.
- Expanded content renders a transparent scrollable list; collapsed content renders “战场” plus accumulated team count.

- [ ] Add a failing Compose test for player, state, three hero/red strings, destination, arrival/update time, win rate/unknown win rate, empty state, collapse, expand and close semantics.
- [ ] Compile AndroidTests and confirm failure before content exists.
- [ ] Implement the confirmed dark translucent layout with a maximum-height lazy list and touch-friendly controls.
- [ ] Re-run AndroidTest compilation and all unit tests.
- [ ] Commit `feat: build battlefield overlay content`.

### Task 4: Foreground overlay service and window behavior

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/overlay/BattlefieldOverlayService.kt`
- Create: `app/src/main/java/com/local/stzb/feature/overlay/OverlayServiceState.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/local/stzb/feature/overlay/OverlayWindowPositionTest.kt`

**Interfaces:**
- Service actions: `ACTION_START`, `ACTION_STOP`.
- `OverlayServiceState.running: StateFlow<Boolean>` exposes current process state to the battlefield page.
- Pure `clampOverlayPosition(x, y, window, screen)` keeps expanded panel/floating ball reachable.

- [ ] Write failing tests for position clamping across portrait screen edges and collapsed/expanded dimensions.
- [ ] Implement overlay permission declaration, non-exported `specialUse` foreground service declaration and notification stop action.
- [ ] Implement service lifecycle, repository refresh/collection, `OverlayTeamStore`, ComposeView lifecycle owners, WindowManager add/update/remove and safe permission-revocation handling.
- [ ] Implement drag gestures for header/floating ball; collapse updates size without losing position; close stops the service.
- [ ] Run focused tests, full unit tests and AndroidTest compilation.
- [ ] Commit `feat: run battlefield overlay service`.

### Task 5: Battlefield page permission and control entry

**Files:**
- Modify: `app/src/main/java/com/local/stzb/feature/battlefield/BattlefieldScreen.kt`
- Modify: `app/src/main/java/com/local/stzb/core/navigation/StzbApp.kt`
- Modify: `app/src/androidTest/java/com/local/stzb/feature/battlefield/BattlefieldScreenTest.kt`

**Interfaces:**
- Battlefield screen accepts `overlayRunning: Boolean` and `onToggleOverlay: () -> Unit`.
- App host uses `Settings.canDrawOverlays`, `ACTION_MANAGE_OVERLAY_PERMISSION`, and starts/stops `BattlefieldOverlayService`.

- [ ] Extend the Compose test first to require “开启悬浮”/“关闭悬浮” controls and click delegation.
- [ ] Compile AndroidTests and confirm signature/assertion failure.
- [ ] Add the compact action in the battlefield header, collect `OverlayServiceState.running`, request permission when missing, and start only after returning with permission granted.
- [ ] Show a Snackbar/message when permission remains denied; never loop the settings launcher.
- [ ] Re-run AndroidTest compilation and all unit tests.
- [ ] Commit `feat: add battlefield overlay control`.

### Task 6: Full verification and device acceptance

**Files:**
- Verify only; modify only if acceptance exposes a defect.

- [ ] Run `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug`; expect `BUILD SUCCESSFUL`.
- [ ] Run `git diff --check`; expect no output and confirm root `README.md`/`astzb/third_party` remain unstaged.
- [ ] Overlay install with `./gradlew :app:installDebug`; do not uninstall.
- [ ] Open battlefield, request overlay permission, return, and start the overlay.
- [ ] Switch to the game and verify the overlay stays visible above it.
- [ ] Verify all observed marching/guarding/stationed teams remain scrollable, same-team updates move to top, and missing teams remain.
- [ ] Verify player, three hero/red values, destination, arrival/update time, state and win rate/`--` are readable.
- [ ] Verify dragging, edge clamping, collapse/expand, panel close and notification stop.
- [ ] Capture expanded and collapsed screenshots and inspect transparency, clipping and touch behavior.
- [ ] Commit acceptance-only corrections while excluding `README.md` and `third_party`.
