# ASTZB Android Battles Compose Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将战报 Tab 从“迁移中”占位页替换为本机 SQLite 驱动的 Compose 战报列表、筛选和详情。

**Architecture:** 复用现有 `LocalStzbRepository` 查询能力，通过 `BattleRepository` 适配成不可变领域模型；ViewModel 持有筛选与加载状态，Compose 只渲染模型。战报列表和详情都留在 Navigation Compose 图中，不再跳到旧 `BattleDetailActivity`。

**Tech Stack:** Kotlin 2.0.21、Jetpack Compose、Material 3、Navigation Compose、Coroutines/Flow、SQLiteOpenHelper、JUnit 4。

## Global Constraints

- 最低系统版本 Android 13（minSdk 33），targetSdk 35。
- UI 不直接访问 Cursor、表名或原始消息号。
- 数据来自本机 `battles_v2` 与 `battle_heroes`，不依赖 PC Flask。
- 页面必须具有加载、空、错误和内容状态。
- 点击区域不小于 48dp，状态不能只通过颜色表达。
- 旧战报页只保留回退能力，不作为迁移完成标准。

---

### Task 1: Battle Domain and Repository

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/domain/battles/BattleModels.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/domain/battles/BattleRepository.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/data/battles/LegacyBattleRepository.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/data/battles/LegacyBattleRepositoryTest.kt`

**Interfaces:**
- Produces `BattleSummary`, `BattleDetail`, `BattleFilters` and `BattleRepository.loadBattles/loadBattle`.

- [ ] Write repository mapping tests for result, coordinates, metrics and heroes.
- [ ] Run the focused test and verify missing symbols fail compilation.
- [ ] Implement the immutable models and adapter over `LocalStzbRepository`.
- [ ] Run focused and full unit tests.
- [ ] Commit `feat(android): add battle domain repository`.

### Task 2: Battle ViewModel

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/feature/battles/BattlesContract.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/battles/BattlesViewModel.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/feature/battles/BattlesViewModelTest.kt`

**Interfaces:**
- Consumes `BattleRepository`; produces `BattlesUiState` and filter/refresh/select intents.

- [ ] Write failing state tests for initial load, filters, empty and error.
- [ ] Implement coroutine-backed loading and filter updates.
- [ ] Verify tests and commit `feat(android): add battles state model`.

### Task 3: Battle List and Detail Compose Screens

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/feature/battles/BattlesScreen.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/feature/battles/BattleDetailScreen.kt`
- Test: `astzb/app/src/androidTest/java/com/local/stzb/feature/battles/BattlesScreenTest.kt`

**Interfaces:**
- Consumes `BattlesUiState`; produces cards, quick filters and structured detail UI.

- [ ] Write failing semantics tests for summary, filter and detail.
- [ ] Implement summary cards and quick filters for all/victory/failure/siege.
- [ ] Implement structured detail sections for result, sides, heroes and metrics.
- [ ] Verify compilation/UI tests and commit `feat(android): build Compose battle pages`.

### Task 4: Replace the Placeholder Route

**Files:**
- Modify: `astzb/app/src/main/java/com/local/stzb/StzbApplication.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/core/navigation/StzbApp.kt`
- Modify: `astzb/app/src/androidTest/java/com/local/stzb/core/navigation/StzbNavigationTest.kt`

**Interfaces:**
- `StzbApp` receives a `BattleRepository`; `battles` renders real content and `battle/{id}` renders detail.

- [ ] Change the navigation test to reject “战报迁移中” and require real empty/content UI.
- [ ] Wire repository, list route and detail route.
- [ ] Run unit, instrumentation and Debug build verification.
- [ ] Commit `feat(android): migrate battles into Compose navigation`.
