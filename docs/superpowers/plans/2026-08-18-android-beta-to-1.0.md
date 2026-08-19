# Android Beta to 1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android 从“独立抓包与核心分析 Beta”收口为能验证真实抓包、数据语义正确、核心交互闭环并逐步覆盖 Web 高级能力的 1.0。

**Architecture:** 保留现有 `TProxyService + LocalSocksCaptureServer + LocalStzbRepository` 链路，在 `com.local.stzb` 下通过领域 Repository、ViewModel 和 Compose 页面渐进替代经典 `DashboardActivity`。先封闭协议正确性与真实设备证据，再迁移高级业务模块；经典页面只在对应原生模块未验收时保留。

**Tech Stack:** Kotlin 2.0.21、Android SDK 35、Jetpack Compose、Material 3、Navigation Compose、Coroutines/Flow、SQLiteOpenHelper、JUnit 4、Compose UI Test、Android VpnService、hev-socks5-tunnel JNI。

## Global Constraints

- 不提交、不推送，保留全部改动给用户自行 commit。
- 保留当前 macOS Liquid Glass 视觉改动和现有登录认证。
- 新功能必须从失败测试开始；每个阶段都要跑单元测试、AndroidTest 编译和 Release 构建。
- 真实抓包闭环必须由真实安装率土的 Android 设备完成；模拟器只能验证安装、权限和服务状态。
- `6314/6318` 不得再作为攻城战场或攻城队列数据。
- 新版 Compose 页面中的可点击元素必须产生可观察的导航或业务动作。
- 用户指南、README、导航入口与当前代码保持一致。

---

### Task 1: 修正 6314/6318 协议语义并隔离旧污染数据

**Files:**
- Modify: `astzb/app/src/main/java/com/example/myapplication/LocalAuxiliaryParser.kt`
- Modify: `astzb/app/src/main/java/com/example/myapplication/LocalStzbDatabase.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/data/battlefield/LegacyBattlefieldRepository.kt`
- Create: `astzb/app/src/test/java/com/example/myapplication/LocalAuxiliaryParser6314Test.kt`
- Modify: `astzb/app/src/test/java/com/local/stzb/data/battlefield/LegacyBattlefieldRepositoryTest.kt`

**Interfaces:**
- Produces: `LocalUnionBuildingHelp` records from message `6314`; battlefield repository excludes legacy `battle_field` rows sourced from `6314`.

- [ ] Write a failing parser test proving `6314` is emitted as `union_building_help`, never `battle_field`.
- [ ] Run the focused test and confirm it fails on the current parser.
- [ ] Replace the `6314` battle-field mapping with an explicit union-building-help parser and persistence model.
- [ ] Add a database migration that removes or marks legacy `battle_field.source_msg_id = '6314'` rows invalid.
- [ ] Add a battlefield repository test proving `6314` rows cannot contribute siege metrics/events.
- [ ] Run focused tests and the full Android unit suite.

### Task 2: 完成战场事件点击详情闭环

**Files:**
- Modify: `astzb/app/src/main/java/com/local/stzb/core/navigation/StzbApp.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/battlefield/BattlefieldEventDetailScreen.kt`
- Modify: `astzb/app/src/androidTest/java/com/local/stzb/feature/battlefield/BattlefieldScreenTest.kt`
- Create: `astzb/app/src/androidTest/java/com/local/stzb/feature/battlefield/BattlefieldEventDetailScreenTest.kt`

**Interfaces:**
- Produces: route `battlefield-event/{eventId}` and deterministic back navigation.

- [ ] Write a failing Compose test that clicks an event and observes the requested event ID.
- [ ] Replace the empty `onEventClick = {}` callback with navigation state.
- [ ] Render battle, march, siege and system event details with a visible back action.
- [ ] Link battle-backed events to the existing battle detail when a battle ID exists.
- [ ] Compile AndroidTest and run connected tests when a compatible device is available.

### Task 3: 建立真实游戏抓包闭环证据与诊断

**Files:**
- Modify: `astzb/app/src/main/java/com/local/stzb/data/capture/AndroidCaptureConsoleController.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/feature/capture/CaptureConsoleContract.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/feature/capture/CaptureConsoleScreen.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/capture/CaptureEvidence.kt`
- Create: `astzb/app/src/test/java/com/local/stzb/feature/capture/CaptureEvidenceTest.kt`
- Create: `docs/verification/android-real-capture-checklist.md`

**Interfaces:**
- Produces: evidence counters for VPN established, SOCKS accepted connections, protocol IDs `5026/5028/10/92`, database row deltas, start/stop timestamps, and network restoration.

- [ ] Write failing tests for evidence state transitions and completion criteria.
- [ ] Expose native/VPN/SOCKS/parser/database stages in the capture UI.
- [ ] Export a redacted evidence JSON/text bundle.
- [ ] Install Release on a real Android 13+ device with the target game.
- [ ] Capture at least one known protocol and verify the corresponding page/database update.
- [ ] Stop capture and verify ordinary networking recovers.

### Task 4: 同步导航、Beta 标识和发布文档

**Files:**
- Modify: `docs/USER_GUIDE.md`
- Modify: `README.md`
- Modify: `astzb/README.md`
- Modify: `astzb/TAB_FEATURE_GUIDE.md`
- Modify: `astzb/app/src/main/java/com/local/stzb/feature/tools/LegacyToolsScreen.kt`
- Test: `test/test_dashboard_css_structure.py`

**Interfaces:**
- Produces: one authoritative feature matrix split into native, classic compatibility, Web-only and planned.

- [ ] Add a failing documentation assertion for the four current bottom destinations.
- [ ] Replace stale five-tab instructions with `战场/战报/同盟/工具`.
- [ ] Label the Android release as Beta until Task 3 is evidenced.
- [ ] Document the exact native/classic/Web-only matrix and privacy constraints.
- [ ] Run documentation and README asset tests.

### Task 5: 迁移多账号与区服档案

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/profile/ProfileModels.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/profile/ProfileStore.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/profile/ProfileScreen.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/core/navigation/StzbApp.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/profile/ProfileStoreTest.kt`

**Interfaces:**
- Produces: `ProfileStore.observeProfiles()`, `switchProfile(profileId)`, current database selection and safe switch guard.

- [ ] Test profile identity, persistence, switching and write-in-progress rejection.
- [ ] Implement DataStore/SharedPreferences metadata with isolated database paths.
- [ ] Add a native profile selector and refresh all repositories after switching.
- [ ] Verify two fixture profiles never mix rows.

### Task 6: 迁移实时部队原生指挥台

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/domain/livearmy/LiveArmyModels.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/data/livearmy/LocalLiveArmyRepository.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/livearmy/LiveArmyScreen.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/livearmy/LiveArmyViewModel.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/data/livearmy/LocalLiveArmyRepositoryTest.kt`

- [ ] Lock Web/Android freshness, state, location and lineup evidence semantics in fixture tests.
- [ ] Build list, spatial summary and selected-detail states.
- [ ] Add locate-in-battlefield handoff and stale/offline labels.
- [ ] Verify search by team ID, player, hero and WID.

### Task 7: 迁移攻城考勤原生页面

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/domain/attendance/AttendanceModels.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/data/attendance/LocalAttendanceRepository.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/attendance/AttendanceScreen.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/attendance/AttendanceViewModel.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/feature/attendance/AttendanceViewModelTest.kt`

- [ ] Test create, select members, nearby allocation, calculate, export and delete flows.
- [ ] Implement task list/detail/member/battle subpages with back actions.
- [ ] Add CSV export and destructive confirmation.
- [ ] Compare results against `LocalStzbRepository.loadTaskAttendanceForTask`.

### Task 8: 迁移自定义积分

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/domain/score/ScoreModels.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/data/score/LocalScoreRepository.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/score/ScoreCenterScreen.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/data/score/LocalScoreRepositoryTest.kt`

- [ ] Port rule, adjustment, preview, activation and recalculation semantics with fixture parity tests.
- [ ] Require confirmation before recalculation writes.
- [ ] Render ranking, active rule and audit history.

### Task 9: 迁移阵容与战法研究

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/domain/research/ResearchModels.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/data/research/AssetResearchRepository.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/research/ResearchScreen.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/data/research/AssetResearchRepositoryTest.kt`

- [ ] Test hero/skill/card-pack/protocol allowlist snapshot loading.
- [ ] Separate configuration facts, historical evidence and simulator evidence in UI.
- [ ] Add stable handoff into the existing simulator with a prefilled lineup.

### Task 10: 1.0 完成审计与经典页退役门槛

**Files:**
- Modify: `docs/superpowers/plans/2026-08-01-android-full-migration-roadmap.md`
- Create: `docs/verification/android-1.0-completion-audit.md`

- [ ] Map every Web primary function to native Android, explicit classic compatibility or intentional exclusion.
- [ ] Run unit, AndroidTest, Release, signature and real-device capture verification.
- [ ] Verify Android 13/14/15 where devices are available.
- [ ] Remove classic routes only after native parity evidence exists.
- [ ] Confirm no requirement remains unverified before marking 1.0 complete.
