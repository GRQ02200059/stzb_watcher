# Battlefield Team Overwrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the realtime battlefield show only the newest march card for each `teamId`, including across pause/resume, without collapsing battle or siege history.

**Architecture:** Add a repository-internal presentation key derived from `EventTarget.Team` for march events and from the existing event ID for all other event types. Normalize incoming collections newest-first before updating the visible, buffered and previous-source maps so every refresh path shares identical overwrite semantics.

**Tech Stack:** Kotlin, coroutines/StateFlow, JUnit 4, Gradle Android build.

## Global Constraints

- Only march events targeting `EventTarget.Team` overwrite by `teamId`.
- Battles and sieges retain their existing history behavior.
- Do not change 5026/5028 parsing or persisted database history.
- Preserve the modified root `README.md` and untracked `astzb/third_party`.

---

### Task 1: Repository overwrite behavior

**Files:**
- Modify: `app/src/main/java/com/local/stzb/data/battlefield/LegacyBattlefieldRepository.kt`
- Test: `app/src/test/java/com/local/stzb/data/battlefield/LegacyBattlefieldRepositoryTest.kt`

**Interfaces:**
- Consumes existing `BattlefieldEvent.target`, `category`, `occurredAt`, and `id`.
- Produces snapshots where march events share presentation key `march-team:<teamId>` and all other events retain `event.id`.

- [ ] Add failing repository tests with two `LocalTeamMove` entries sharing a team ID but different arrival times; assert only the newest card remains.
- [ ] Add a failing pause/resume test where the same team changes twice while paused; assert buffered count is one and resume exposes only the newest state.
- [ ] Include distinct teams plus battle/siege events in the fixture and assert they are not collapsed.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.local.stzb.data.battlefield.LegacyBattlefieldRepositoryTest'`; expect the same-team assertions to fail with multiple cards.
- [ ] Implement `BattlefieldEvent.presentationKey`, normalize newest events by that key, and use the key consistently in incoming, visible, buffered and previous maps.
- [ ] Re-run focused and all unit tests; expect `BUILD SUCCESSFUL`.
- [ ] Commit `fix: overwrite repeated battlefield teams`.

### Task 2: Full verification and device acceptance

**Files:**
- Verify only; modify only if acceptance exposes a defect.

- [ ] Run `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug`; expect `BUILD SUCCESSFUL`.
- [ ] Run `git diff --check`; expect no output and confirm root `README.md`/`astzb/third_party` remain unstaged.
- [ ] Overlay install with `./gradlew :app:installDebug`; do not uninstall.
- [ ] Open realtime battlefield and inspect the UI hierarchy for duplicate march cards with the same team/player identity.
- [ ] Allow at least one refresh cycle and confirm updated route/time replaces the existing card instead of adding a second card.
- [ ] Verify battle and siege history cards remain available.
- [ ] Capture and inspect the battlefield screenshot for ordering and clipping.
