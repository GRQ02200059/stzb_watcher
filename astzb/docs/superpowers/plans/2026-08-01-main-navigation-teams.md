# Teams-First Main Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the primary 战报/同盟 destinations with a real full-server team lineup page and a direct team-report page, while moving the displaced tools under 更多.

**Architecture:** Introduce a focused teams domain/repository/view-model path over `loadPlayerBattleTeams(0)`, render its rows with the existing cached hero portrait component, and split the combined ranking screen into direct report and ranking entry modes. Navigation owns only routes and callbacks; each primary page keeps independent state.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, coroutines/StateFlow, JUnit 4, Compose UI Test.

## Global Constraints

- Bottom navigation must contain exactly 战场 / 队伍 / 团队 / 更多.
- 队伍 means 全服玩家队伍 and uses three compact hero portraits.
- 团队 opens 团队报表 directly with group/all-time defaults.
- 战报、排行榜、同盟成员、地图、公告、抓包控制 remain reachable under 更多.
- Do not fabricate hero level or advance data absent from `LocalPlayerBattleTeam`.
- Preserve the user's modified root `README.md` and untracked `astzb/third_party`.

---

### Task 1: Full-server teams domain and repository

**Files:**
- Create: `app/src/main/java/com/local/stzb/domain/teams/TeamModels.kt`
- Create: `app/src/main/java/com/local/stzb/data/teams/LegacyTeamsRepository.kt`
- Test: `app/src/test/java/com/local/stzb/data/teams/LegacyTeamsRepositoryTest.kt`

**Interfaces:**
- Produces `PlayerTeam(player, unionName, side, heroes, skillNames, battles, wins, winRate)`.
- Produces `TeamsRepository.loadTeams(): List<PlayerTeam>`.

- [ ] Add a failing repository test with `heroes="陆逊+周瑜+吕蒙"`, `heroIds="101+102+103"`, duplicate/blank skill tokens, and assert three ordered heroes with icon IDs plus normalized unique skills.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.local.stzb.data.teams.LegacyTeamsRepositoryTest'`; expect compilation failure before the domain exists.
- [ ] Implement `AndroidLegacyTeamsSource` over `LocalStzbRepository.loadPlayerBattleTeams(0)` and map the plus/slash/comma-delimited fields without inventing level/advance values.
- [ ] Re-run the repository test and expect `BUILD SUCCESSFUL`.
- [ ] Commit `feat: add full-server teams repository`.

### Task 2: Teams filtering and state

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/teams/TeamsContract.kt`
- Create: `app/src/main/java/com/local/stzb/feature/teams/TeamsViewModel.kt`
- Test: `app/src/test/java/com/local/stzb/feature/teams/TeamsViewModelTest.kt`

**Interfaces:**
- Consumes `TeamsRepository.loadTeams()`.
- Produces `TeamsUiState(loading, query, side, allTeams, visibleTeams, error)` and intents for refresh/query/side.

- [ ] Add failing coroutine tests proving initial load sorting by battles then win rate, case-insensitive player/union/hero/skill search, and attack/defense side filtering.
- [ ] Run the focused tests and confirm failure because `TeamsViewModel` is missing.
- [ ] Implement the ViewModel with generation-safe refresh and pure local filtering after load.
- [ ] Run focused tests and expect success.
- [ ] Commit `feat: add teams screen state`.

### Task 3: Compact full-server teams screen

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/teams/TeamsScreen.kt`
- Modify: `app/src/main/java/com/local/stzb/feature/battlefield/BattlefieldHeroPortrait.kt`
- Test: `app/src/androidTest/java/com/local/stzb/feature/teams/TeamsScreenTest.kt`

**Interfaces:**
- Consumes `TeamsUiState` and `BattlefieldHeroPortrait` through a reusable portrait input carrying position, icon ID and name.
- Produces `TeamsScreen(state, onIntent)` and three-column `PlayerTeamCard`.

- [ ] Add a failing Compose test asserting title 全服玩家队伍, player/alliance/side, three hero names and portrait semantics, skill chips, battles and win rate.
- [ ] Add empty/loading/error tests and verify AndroidTest compilation initially fails.
- [ ] Generalize the portrait component minimally so team rows can use it without constructing fake levels or advances.
- [ ] Implement summary metrics, search field, side chips, lazy list and three equal-width portrait tiles; show team-level skill chips below the row.
- [ ] Run `:app:compileDebugAndroidTestKotlin :app:testDebugUnitTest` and expect success.
- [ ] Commit `feat: build full-server teams screen`.

### Task 4: Direct team-report primary page

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/teamreport/TeamReportScreen.kt`
- Create: `app/src/main/java/com/local/stzb/feature/teamreport/TeamReportViewModel.kt`
- Test: `app/src/test/java/com/local/stzb/feature/teamreport/TeamReportViewModelTest.kt`

**Interfaces:**
- Consumes `RankingRepository.loadTeamReport()` only.
- Produces a primary 团队报表 page defaulting to `ReportDimension.GROUP`, `ReportPeriod.ALL`, and blank group.

- [ ] Add failing tests for default group/all request and dimension/period/group refresh behavior.
- [ ] Implement the report-only ViewModel with independent state from `RankingsViewModel`.
- [ ] Extract/reuse report row rendering and implement the primary screen without a back button or rankings page tab.
- [ ] Run focused unit tests and AndroidTest compilation.
- [ ] Commit `feat: add primary team report page`.

### Task 5: Navigation and 更多 migration

**Files:**
- Modify: `app/src/main/java/com/local/stzb/core/navigation/AppDestination.kt`
- Modify: `app/src/main/java/com/local/stzb/core/navigation/StzbApp.kt`
- Modify: `app/src/main/java/com/local/stzb/StzbApplication.kt`
- Modify: `app/src/main/java/com/local/stzb/feature/tools/LegacyToolsScreen.kt`
- Modify: `app/src/androidTest/java/com/local/stzb/core/navigation/StzbNavigationTest.kt`

**Interfaces:**
- Primary routes: `battlefield`, `teams`, `team-report`, `more`.
- Secondary routes: `battles`, `rankings`, `alliance`, `map`, `announcements`.

- [ ] Rewrite the navigation test first to require exactly 战场/队伍/团队/更多 and verify the two new primary pages.
- [ ] Add assertions that 更多 opens 战报、排行榜、同盟成员、地图与公告 secondary pages and each exposes 返回.
- [ ] Run AndroidTest compilation; expect failure before navigation is changed.
- [ ] Wire `TeamsRepository` into `StzbApplication`, create primary ViewModels, replace primary routes/icons, and add displaced pages under secondary routes.
- [ ] Expand `LegacyToolsScreen` with explicit callbacks and labels for 战报、排行榜、同盟成员.
- [ ] Compile AndroidTests and run all unit tests.
- [ ] Commit `feat: make teams and reports primary navigation`.

### Task 6: Verification and device acceptance

**Files:**
- Verify only; modify only if acceptance exposes a defect.

- [ ] Run `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug`; expect `BUILD SUCCESSFUL`.
- [ ] Run `git diff --check`; expect no output and verify root README remains modified but unstaged.
- [ ] Overlay install with `./gradlew :app:installDebug`; do not uninstall.
- [ ] Launch the Compose activity and use UIAutomator to verify all four primary labels.
- [ ] Open 队伍 and verify real player cards expose three hero portrait semantics and skills.
- [ ] Open 团队 and verify 团队报表 appears directly with dimension/period controls.
- [ ] Open 更多 and verify 战报、排行榜、同盟成员、地图、公告 and capture tools are present.
- [ ] Capture screenshots of 队伍、团队、更多 and inspect for clipping or unreachable content.
- [ ] Commit acceptance-only corrections while excluding README and `third_party`.
