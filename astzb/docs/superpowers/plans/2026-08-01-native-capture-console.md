# Native Capture Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the legacy capture-console launch path with a native Compose page that controls the existing per-App VPN capture pipeline, displays filtered parsed logs, and exports artifacts through user-selected document locations.

**Architecture:** A pure contract and ViewModel own UI state and filtering, while an Android controller adapts the existing `TProxyService`, `LocalSocksCaptureServer`, packet stores, installed-app query and export producers. Compose owns Activity Result launchers for VPN permission and document creation; navigation owns routes only.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, Android `VpnService`, Activity Result APIs, StateFlow/coroutines, JUnit 4, Compose UI Test.

## Global Constraints

- Reuse the existing `TProxyService` + `LocalSocksCaptureServer` + STZB parser pipeline; do not duplicate packet capture or parsing.
- Require an explicit target App before starting; do not silently start a global VPN.
- Use the system document picker for each export and request no broad storage permission.
- Keep the legacy console reachable as a compatibility action, but not as the primary “更多” entry.
- Do not delete existing capture files or SQLite data.
- Preserve the modified root `README.md` and untracked `astzb/third_party`.

---

### Task 1: Capture console state and log filtering

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/capture/CaptureConsoleContract.kt`
- Create: `app/src/main/java/com/local/stzb/feature/capture/CaptureConsoleViewModel.kt`
- Test: `app/src/test/java/com/local/stzb/feature/capture/CaptureConsoleViewModelTest.kt`

**Interfaces:**
- Consumes `CaptureConsoleController.observe(): Flow<CaptureRuntime>`, `start(targetPackage)`, `stop()`, `clear()`, and export preparation methods.
- Produces `CaptureConsoleUiState` and `CaptureConsoleIntent`; protocol filtering is exposed as a pure `filterParsedLogs(logs, query)` function.

- [ ] Write failing tests proving initial runtime mapping, required target-package validation, full-number protocol filtering for `5026, 5028`, clear/stop delegation, and export-result event emission.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.local.stzb.feature.capture.CaptureConsoleViewModelTest'`; expect compilation failure because the contract does not exist.
- [ ] Implement immutable state, controller interface, ViewModel intent handling, and STZB parsed-log marker filtering. Keep VPN permission outside the ViewModel by emitting `RequestVpnPermission` before `StartApproved` invokes the controller.
- [ ] Re-run the focused test and expect `BUILD SUCCESSFUL`.
- [ ] Commit `feat: add capture console state`.

### Task 2: Android capture controller

**Files:**
- Create: `app/src/main/java/com/local/stzb/data/capture/AndroidCaptureConsoleController.kt`
- Test: `app/src/test/java/com/local/stzb/data/capture/CaptureExportTest.kt`
- Modify: `app/src/main/java/com/local/stzb/StzbApplication.kt`

**Interfaces:**
- Produces `CaptureConsoleController` backed by `Preferences`, `TProxyService`, `LocalSocksCaptureServer`, `PacketLogStore`, `LocalStzbPacketStore`, installed applications and existing export producers.
- Export preparation returns `CaptureExport(name, mimeType, bytes)` for `STZB`, `DATABASE`, and `DIAGNOSTICS`.

- [ ] Add a failing pure export test for filenames, MIME types and byte preservation when converting existing producer files to `CaptureExport`.
- [ ] Run the focused test; expect failure because `CaptureExport`/adapter helpers are missing.
- [ ] Implement controller status observation using the packet-log listener plus periodic runtime refresh; configure loopback SOCKS and per-App `Preferences`, then send `ACTION_CONNECT`/`ACTION_DISCONNECT` intents.
- [ ] Implement installed-App search sorted by display name; use `PackageManager.ApplicationInfoFlags.of(0)` on API 33+.
- [ ] Adapt `LocalStzbCaptureWriter.exportSummary`, `LocalStzbRepository.exportDatabase`, and `LocalMigrationDiagnostics.export` into document payloads without deleting their source files.
- [ ] Register the controller lazily in `StzbApplication` and re-run focused/all unit tests.
- [ ] Commit `feat: bridge native capture controls`.

### Task 3: Native Compose capture page

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/capture/CaptureConsoleScreen.kt`
- Test: `app/src/androidTest/java/com/local/stzb/feature/capture/CaptureConsoleScreenTest.kt`

**Interfaces:**
- Consumes `CaptureConsoleUiState`, `onIntent`, `onRequestVpnPermission`, `onExport`, `onOpenLegacy`, and `onBack`.
- Produces the status, control, logs and export cards defined in the design.

- [ ] Write a failing Compose test asserting title “抓包启动台”, running/stopped status, target selector, start/stop, protocol filter, clear, three export actions, compatibility action and back action.
- [ ] Compile AndroidTests and confirm failure because `CaptureConsoleScreen` is missing.
- [ ] Implement a lazy-column page with Material 3 cards, accessible content descriptions and a searchable installed-App dialog. Disable start while the target is blank or native support is unavailable.
- [ ] Display current packet count, loopback endpoint, filtered parsed logs, operation feedback and actionable errors.
- [ ] Re-run `:app:compileDebugAndroidTestKotlin :app:testDebugUnitTest`; expect success.
- [ ] Commit `feat: build native capture console`.

### Task 4: VPN permission, exports and navigation

**Files:**
- Modify: `app/src/main/java/com/local/stzb/core/navigation/StzbApp.kt`
- Modify: `app/src/main/java/com/local/stzb/feature/tools/LegacyToolsScreen.kt`
- Modify: `app/src/main/java/com/local/stzb/StzbAppActivity.kt`
- Modify: `app/src/androidTest/java/com/local/stzb/core/navigation/StzbNavigationTest.kt`

**Interfaces:**
- Adds secondary route `capture-console`.
- Activity/Compose host uses `VpnService.prepare`, `StartActivityForResult`, and `CreateDocument(mimeType)`; successful permission dispatches `StartApproved` and selected URI receives the prepared bytes.

- [ ] Extend the navigation test first: click “抓包启动台”, assert native title/control buttons, click “返回更多”, and assert “更多工具”.
- [ ] Compile AndroidTests and confirm failure before route wiring.
- [ ] Replace the primary legacy callback with navigation to `capture-console`; add a separate “打开旧控制台” callback inside the native page.
- [ ] Wire VPN permission so cancellation produces a visible message and approval starts capture exactly once.
- [ ] Wire each export through the system document picker with suggested timestamped names; canceled selection is a no-op and write failures become visible errors.
- [ ] Compile AndroidTests and run all unit tests.
- [ ] Commit `feat: migrate capture launcher into compose`.

### Task 5: Full verification and device acceptance

**Files:**
- Verify only; modify only if device acceptance exposes a defect.

- [ ] Run `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug`; expect `BUILD SUCCESSFUL`.
- [ ] Run `git diff --check` and confirm root `README.md` remains modified and `astzb/third_party` remains untracked/unstaged.
- [ ] Overlay install with `./gradlew :app:installDebug`; do not uninstall.
- [ ] Open 更多 → 抓包启动台 and verify the native page, current status and selected target.
- [ ] Search/select the STZB package, request VPN authorization, start capture, verify running status/packet count/logs, then stop and verify stopped status.
- [ ] Trigger all three exports and confirm each opens a system create-document destination chooser; cancel without writing unwanted files.
- [ ] Verify “打开旧控制台” remains reachable and Android back returns to the native app.
- [ ] Capture and inspect screenshots for clipping, unreachable actions and readable logs.
- [ ] Commit acceptance-only corrections while excluding `README.md` and `third_party`.
