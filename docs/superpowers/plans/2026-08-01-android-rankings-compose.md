# ASTZB Android Rankings and Team Report Compose Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将排行与团队报表从旧 Dashboard 迁移为本机 SQLite 驱动的 Compose 页面，并从“更多”页直接进入。

**Architecture:** 新建 `RankingRepository` 将 `LocalStzbRepository` 的本地查询结果映射为不可变领域模型。`RankingsViewModel` 在 IO 调度器加载榜单或团队报表，Compose 页面只消费状态和意图，并通过 Navigation Compose 路由接入现有应用。

**Tech Stack:** Kotlin 2.0.21、Jetpack Compose、Material 3、Navigation Compose、Coroutines/Flow、SQLiteOpenHelper、JUnit 4。

## Global Constraints

- 最低系统版本 Android 13（minSdk 33），targetSdk 35。
- UI 不直接访问 Cursor、表名、旧 Activity 或原始消息号。
- 数据只来自本机 SQLite，不依赖 PC Flask。
- 页面必须具有加载、空、错误和内容状态。
- 排行必须覆盖战功、同盟势力、个人势力三类；团队报表必须覆盖分组/成员维度与全部/今日/本周周期。
- 点击区域不小于 48dp，状态不能只通过颜色表达。
- 旧 Dashboard 只保留未迁移工具的回退能力，不作为排行/团队报表完成标准。

---

### Task 1: Rankings and Team Report Feature

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/domain/rankings/RankingModels.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/data/rankings/LegacyRankingRepository.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/rankings/RankingsViewModel.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/rankings/RankingsScreen.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/data/rankings/LegacyRankingRepositoryTest.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/feature/rankings/RankingsViewModelTest.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/StzbApplication.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/core/navigation/StzbApp.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/feature/tools/LegacyToolsScreen.kt`

**Interfaces:**
- `RankingRepository.loadRankings(): RankingSnapshot` maps `loadBattleRankings`, `loadUnionRanks`, and `loadPlayerPowerRanks`.
- `RankingRepository.loadTeamReport(dimension: ReportDimension, period: ReportPeriod, group: String): TeamReportSnapshot` maps `loadTeamReport`.
- `RankingsViewModel` exposes `StateFlow<RankingsUiState>` and accepts page/category/dimension/period/group/refresh intents.
- Navigation exposes a `rankings` route from the More page without launching `DashboardActivity`.

- [ ] **Step 1: Write failing repository mapping tests**

  Create a fake source returning one row for each legacy result type. Assert that ranks, names, group names, power/gongxun, battles, wins, losses, draws, siege counts, member counts and win rates survive mapping, while source message IDs are absent from the domain model.

- [ ] **Step 2: Run the repository test and verify it fails**

  Run: `./gradlew :app:testDebugUnitTest --tests 'com.local.stzb.data.rankings.LegacyRankingRepositoryTest'`

  Expected: compilation fails because the ranking domain and adapter do not exist.

- [ ] **Step 3: Implement domain models and repository adapter**

  Define sealed/enum categories for battle contribution, union power and player power; define report dimension and period enums with the exact legacy query values `group`/`player` and `all`/`today`/`week`; define immutable snapshot/row models. Put all calls to `LocalStzbRepository` behind an injectable source so unit tests do not require Android SQLite.

- [ ] **Step 4: Write failing ViewModel state tests**

  Verify initial loading, successful ranking content, category switching, report dimension/period/group reload, empty state and repository error. Use a test dispatcher so assertions do not depend on wall-clock timing.

- [ ] **Step 5: Implement coroutine-backed ViewModel**

  Load repository calls on `Dispatchers.IO`, retain selected controls across refreshes, clear stale errors on reload, and expose immutable state through `StateFlow`.

- [ ] **Step 6: Implement the Compose page**

  Add top-level “排行榜/团队报表” controls. Render three ranking category chips, report dimension and period chips, group filtering when member dimension is selected, summary metrics, ranked cards, and explicit loading/empty/error panels. Every chip/button must have a minimum height of 48dp and visible text selection.

- [ ] **Step 7: Wire dependency injection and navigation**

  Create `rankingRepository` in `StzbApplication`, pass it to `StzbApp`, add the `rankings` destination, and change the More-page ranking entry to navigate there. Keep the classic Dashboard entry only for tools that have not yet migrated.

- [ ] **Step 8: Run verification**

  Run: `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug`

  Expected: `BUILD SUCCESSFUL` with repository/ViewModel tests passing.

- [ ] **Step 9: Commit**

  Stage only the files listed in this task and commit with `feat(android): migrate rankings and team reports`.
