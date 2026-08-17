# World Scene Intelligence Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make 战场情报 the only product entry for map, marches, active armies and protocol entities while keeping tab30 and old functions as compatibility shims.

**Architecture:** Move the world-scene panels into tab33 without duplicating rendering logic. Refactor `world_scene.js` into `WorldScenePanel`, let `intelligence-center.js` own the active subview and WID navigation, and redirect old tab30 calls into tab33.

**Tech Stack:** Vanilla JavaScript, HTML/CSS, existing Flask read APIs, Python unittest, Node test, Chrome E2E.

## Global Constraints

- Keep `/api/world/*` read APIs compatible.
- Keep tab30 DOM for compatibility but remove all product navigation to it.
- Do not duplicate world-scene tables or renderer functions.
- Do not add any game write action.
- Do not commit changes.

---

### Task 1: Lock Navigation and Compatibility Contracts

**Files:**
- Modify: `test/test_world_scene_static.py`
- Modify: `test/test_intelligence_center_static.py`
- Modify: `test/test_sidebar_navigation.py`

- [ ] Assert nav, commands and settings contain no world-scene entry.
- [ ] Assert tab33 contains map/march/army/entity tabs and panels.
- [ ] Assert tab30 remains present as a hidden compatibility page.
- [ ] Assert `switchTab(30)` redirects to tab33.
- [ ] Run focused tests RED.

### Task 2: Move Panels and Refactor WorldScenePanel

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/world_scene.js`
- Modify: `static/intelligence-center.css`

- [ ] Move march, army and entity tables into tab33.
- [ ] Keep a minimal hidden tab30 compatibility container.
- [ ] Export `window.WorldScenePanel`.
- [ ] Keep `loadWorldScene()` and `wsSwitch()` aliases that delegate to tab33.
- [ ] Make WID cells and army rows call `IntelligenceCenter.locateWid()`.
- [ ] Run focused tests GREEN.

### Task 3: Integrate Subview State and Realtime Refresh

**Files:**
- Modify: `static/intelligence-center.js`
- Modify: `static/app1.js`
- Modify: `static/app2.js`
- Modify: `static/dashboard-command-center.js`
- Modify: `static/dashboard-design-system.js`

- [ ] Add `openView(map|march|army|entity)`.
- [ ] Load only the active scene panel and mark inactive panels dirty.
- [ ] Redirect tab30 and old navigation helpers to tab33.
- [ ] Remove world-scene command, setting and shortcut entries.
- [ ] Reuse shared SSE events without creating EventSource.

### Task 4: Chrome and Full Verification

**Files:**
- Modify: `test/js/dashboard-e2e.mjs`
- Modify: `README.md`
- Modify: completion audit

- [ ] Verify all four subviews.
- [ ] Click march/army WIDs and verify map location.
- [ ] Verify `switchTab(30)` redirects to tab33.
- [ ] Verify nav/command palette has no world-scene entry.
- [ ] Run full unittest, Node checks and `git diff --check`.
