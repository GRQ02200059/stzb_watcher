# Battlefield Compact Team Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace text-heavy monitored-army cards with compact three-column hero cards showing portraits, positions, levels, advances, skills, march state, and recorded performance.

**Architecture:** Add typed hero presentation data to `BattlefieldEvent`, populate it from the already-resolved `Local13A2TeamInsight`, and select a focused `BattlefieldTeamCard` only for march events with a recorded lineup. A small Compose image loader wraps the existing card-image URL convention with memory caching and deterministic initial-letter fallback.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android `BitmapFactory`, JUnit 4, Compose UI Test.

## Global Constraints

- Only change the Compose battlefield feed; do not alter 5026/5028 parsing or team matching.
- Keep three equal-width columns ordered as 大营、中军、前锋.
- Use `card_medium_{iconId}.jpg?gameid=g10` and `HeroNameResolver.iconIdOf(heroId)`.
- Show no fabricated empty hero slots and no broken-image indicator.
- Preserve generic cards for unmatched marches and non-march events.
- Preserve the untracked `third_party` directory.

---

### Task 1: Typed team-card presentation model

**Files:**
- Modify: `app/src/main/java/com/local/stzb/domain/battlefield/BattlefieldEvent.kt`
- Modify: `app/src/main/java/com/local/stzb/data/battlefield/BattlefieldEventMapper.kt`
- Test: `app/src/test/java/com/local/stzb/data/battlefield/BattlefieldEventMapperTest.kt`

**Interfaces:**
- Produces: `BattlefieldHero(positionLabel, heroId, iconId, name, level, advance, skills)` and `BattlefieldTeamPresentation(heroes, recordText, arrivalText)`.
- Produces: nullable `BattlefieldEvent.teamPresentation` consumed by Task 3.

- [ ] **Step 1: Write a failing mapper test** asserting a mapped recorded lineup contains three typed heroes in 大营/中军/前锋 order, icon IDs resolved through `HeroNameResolver`, and no typed team presentation for an unmatched move.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'com.local.stzb.data.battlefield.BattlefieldEventMapperTest'`; expect compilation/test failure because the typed model is absent.
- [ ] **Step 3: Add the immutable presentation data classes** and populate them in `fromMove()` directly from `insight.lineup.heroes.sortedBy { it.pos }`; keep existing `details` only for low-frequency diagnostics.
- [ ] **Step 4: Re-run the mapper tests** and expect `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** with `git commit -m 'feat: model battlefield hero lineups'`.

### Task 2: Cached Compose hero portrait

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/battlefield/BattlefieldHeroPortrait.kt`
- Test: `app/src/androidTest/java/com/local/stzb/feature/battlefield/BattlefieldScreenTest.kt`

**Interfaces:**
- Consumes: `BattlefieldHero.iconId` and `BattlefieldHero.name` from Task 1.
- Produces: `@Composable BattlefieldHeroPortrait(hero, modifier)` with content description `"{positionLabel} {name}"` and initial-letter fallback.

- [ ] **Step 1: Add a failing Compose test** rendering an event with an invalid `iconId=0`, then assert the content description and initial-letter fallback are displayed.
- [ ] **Step 2: Run** `./gradlew :app:compileDebugAndroidTestKotlin`; expect failure because `BattlefieldHeroPortrait` and typed event data do not yet exist.
- [ ] **Step 3: Implement a focused loader** using `produceState`, `Dispatchers.IO`, `URL.openStream()`, `BitmapFactory.decodeStream()`, and a `ConcurrentHashMap<Long, Bitmap>`; skip network for non-positive IDs.
- [ ] **Step 4: Render the result** with clipped `Image` and `ContentScale.Crop`; render a theme-colored `Box` plus the first non-space character when loading fails or no ID exists.
- [ ] **Step 5: Run** `./gradlew :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest`; expect `BUILD SUCCESSFUL`.
- [ ] **Step 6: Commit** with `git commit -m 'feat: add cached hero portraits'`.

### Task 3: Compact three-column march card

**Files:**
- Create: `app/src/main/java/com/local/stzb/feature/battlefield/BattlefieldTeamCard.kt`
- Modify: `app/src/main/java/com/local/stzb/feature/battlefield/BattlefieldComponents.kt`
- Test: `app/src/androidTest/java/com/local/stzb/feature/battlefield/BattlefieldScreenTest.kt`

**Interfaces:**
- Consumes: `BattlefieldEvent.teamPresentation` and `BattlefieldHeroPortrait`.
- Produces: `BattlefieldTeamCard(event, onClick, modifier)`.

- [ ] **Step 1: Add a failing UI test** asserting 大营、中军、前锋, hero names, `Lv.50 · 进阶5`, skill names, route, morale, and record summary are displayed for a recorded march.
- [ ] **Step 2: Add a regression UI test** asserting an unmatched march still uses the generic event card and battle/siege cards remain visible.
- [ ] **Step 3: Run** `./gradlew :app:compileDebugAndroidTestKotlin`; expect failure before the new component exists.
- [ ] **Step 4: Implement `BattlefieldHeroTile`** as an equal-width column with a square portrait, one-line name, level/advance label, and up to three small rounded skill surfaces with ellipsis.
- [ ] **Step 5: Implement `BattlefieldTeamCard`** with player/alliance/time header, route/morale status row, three equal-width hero tiles, and compact record/arrival footer.
- [ ] **Step 6: Route only recorded march events** from `BattlefieldEventCard` to `BattlefieldTeamCard`; preserve the existing generic implementation for all other cases.
- [ ] **Step 7: Compile tests** with `./gradlew :app:compileDebugAndroidTestKotlin`; expect `BUILD SUCCESSFUL`.
- [ ] **Step 8: Commit** with `git commit -m 'feat: render compact battlefield team cards'`.

### Task 4: Full verification and device validation

**Files:**
- Verify only; modify production/test files only if a failing acceptance criterion exposes a defect.

**Interfaces:**
- Validates the completed typed-data → portrait → team-card path.

- [ ] **Step 1: Run** `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug`; expect `BUILD SUCCESSFUL`.
- [ ] **Step 2: Run** `git diff --check`; expect no output.
- [ ] **Step 3: Overlay install** with `./gradlew :app:installDebug`; expect `Installed on 1 device` and do not uninstall.
- [ ] **Step 4: Launch** `com.local.stzb.random/com.local.stzb.StzbAppActivity`, dump UIAutomator text, and verify a real recorded team exposes position labels, three hero names, and skill labels.
- [ ] **Step 5: Capture a device screenshot** and inspect it for equal column widths, readable truncation, stable fallback, and no overlap at the device's configured font scale.
- [ ] **Step 6: Commit any acceptance-only corrections**, excluding `third_party`, with `git commit -m 'fix: polish battlefield team card layout'`.
