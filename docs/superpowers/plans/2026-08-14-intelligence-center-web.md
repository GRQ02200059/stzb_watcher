# STZB Intelligence Center Web and Research Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved A-layout intelligence map, tile evidence workflow, lineup/skill research center, simulator handoff, and Query Agent intelligence tools.

**Architecture:** New intelligence pages live in focused JS/CSS modules and consume only `/api/intelligence/*`. A Canvas renderer handles dense map tiles; DOM is reserved for controls, detail panels, timeline, and research tables. Configuration facts, battle statistics, and simulation results remain separate evidence classes.

**Tech Stack:** Vanilla JS, Canvas 2D, CSS, Flask, Python CSV indexes, Kotlin simulator adapter, Node test runner, Playwright with system Chrome

## Global Constraints

- Use the approved A layout: map canvas, right intelligence panel, bottom timeline.
- Frontend never displays 5026 and 5028 as separate datasets.
- Source command appears only in evidence/provenance.
- Every risk score returns components, confidence, freshness, and unknown components.
- CONFIG_FACT, BATTLE_STAT, and SIMULATION must be visually distinct.
- No active game command, packet send, or automation.
- No third-party frontend dependency.
- Support 375/768/1024/1440 widths and reduced motion.
- Do not commit automatically.

---

### Task 1: Canvas Intelligence Map Runtime

**Files:**
- Create: `static/intelligence-map.mjs`
- Create: `test/js/intelligence-map.test.mjs`
- Modify: `test/test_dashboard_runtime_node.py`

**Interfaces:**
- Produces: `LEVEL_COLORS`, `levelColor(level)`, `drawTile(ctx,tile,viewport,layers)`, `hitTest(x,y,viewport)`

- [ ] Write Node tests for exact colors, illegal level fallback, overlay order, and zoom thresholds 7/8/13/14px.
- [ ] Verify RED due missing module.
- [ ] Implement pure layout/drawing command generation separated from Canvas side effects:

```js
export function tileDrawPlan(tile, size, layers) {
  return [
    {kind: "fill", color: levelColor(tile.level)},
    // ownership, stale hatch, path, army, selected
  ];
}
```

- [ ] Implement Canvas renderer and BigInt-safe visual field input.
- [ ] Run Node tests GREEN.

### Task 2: A-Layout Page Shell and Layer Controls

**Files:**
- Modify: `static/dashboard.html`
- Create: `static/intelligence-center.css`
- Create: `static/intelligence-center.js`
- Modify: `static/dashboard-design-system.js`
- Create: `test/test_intelligence_center_static.py`

**Interfaces:**
- Adds `tab33` labeled `战场情报`
- Global: `loadIntelligenceCenter()`

- [ ] Add failing static tests for tab, canvas, nine layer buttons, detail tabs, timeline, and asset order.
- [ ] Implement semantic page shell matching the approved visual companion.
- [ ] Add navigation metadata/icon and `switchTab(33)` loader.
- [ ] Persist layer selection in `stzb.intelligence.layers`.
- [ ] Run static tests and existing 33-tab HTML audit GREEN.

### Task 3: Viewport Loading, Pan/Zoom, Selection, and Freshness

**Files:**
- Modify: `static/intelligence-center.js`
- Modify: `static/intelligence-map.mjs`
- Modify: `test/js/dashboard-e2e.mjs`

**Interfaces:**
- Consumes `/api/intelligence/world/viewport`
- Produces `IntelligenceCenter.selectWid(wid)`, `setViewport(bounds)`, `refresh()`

- [ ] Extend Chrome E2E with intercepted deterministic viewport data.
- [ ] Assert wheel zoom, drag pan, click selection, Shift-click favorite, and no page overflow.
- [ ] Implement debounced viewport fetch with `AbortController`.
- [ ] Draw stale hatch, owner marker, risk, path, armies, selected border, and zoom-level labels.
- [ ] Verify E2E GREEN with external network blocked.

### Task 4: Tile Detail Tabs and Explainable Risk

**Files:**
- Modify: `static/intelligence-center.js`
- Modify: `static/intelligence-center.css`
- Modify: `test/js/dashboard-e2e.mjs`

**Interfaces:**
- Consumes `/api/intelligence/world/tile/<wid>`
- Tabs: risk, armies, battles, evidence

- [ ] Add failing E2E assertions for risk components, unknown markers, freshness, army countdown, battle sample size, and provenance.
- [ ] Implement accessible tablist with keyboard arrows.
- [ ] Add links to existing battles/world scene/monitor pages.
- [ ] Ensure countdown uses normalized timestamps and pauses while hidden.
- [ ] Run E2E GREEN.

### Task 5: WorldState Timeline and Alert Integration

**Files:**
- Modify: `static/intelligence-center.js`
- Modify: `static/dashboard-command-center.js`
- Modify: `static/intelligence-center.css`
- Create: `test/test_intelligence_events_api.py`
- Modify: `test/js/dashboard-e2e.mjs`

**Interfaces:**
- Consumes `/api/intelligence/world/events`
- SSE event: `world_state_changed`

- [ ] Add API tests for `sinceVersion`, event type, entity filters, and bounded pagination.
- [ ] Add browser tests for timeline filter and click-to-select.
- [ ] Implement event polling/SSE refresh without a second EventSource.
- [ ] Reuse risks in command-center alerts and favorites.
- [ ] Run API/E2E tests GREEN.

### Task 6: Configuration Index and Hero/Skill APIs

**Files:**
- Create: `intelligence/config_repository.py`
- Create: `intelligence/config_api.py`
- Modify: `api_server.py`
- Create: `test/test_intelligence_config_repository.py`
- Create: `test/test_intelligence_config_api.py`

**Interfaces:**
- Produces cached repository methods:
  - `search_heroes(query, filters, page, size)`
  - `hero_detail(hero_id)`
  - `search_skills(query, filters, page, size)`
  - `skill_detail(skill_id)`

- [ ] Write failing repository tests for BOM headers, placeholder-hero filtering, joins, unresolved placeholders, and cache reuse.
- [ ] Implement immutable in-memory indexes loaded from project snapshot.
- [ ] Add APIs from the approved spec with pagination and CONFIG_FACT evidence labels.
- [ ] Add manifest endpoint and checksum/version to every config response.
- [ ] Run repository/API tests GREEN.

### Task 7: Lineup Statistics and Evidence Separation

**Files:**
- Create: `intelligence/lineup_service.py`
- Create: `intelligence/lineup_api.py`
- Create: `test/test_intelligence_lineup_service.py`
- Modify: `api_server.py`

**Interfaces:**
- Produces lineup evidence:
  - `configFacts`
  - `battleStats`
  - `simulationLink`
  - `confidence`

- [ ] Add failing tests for canonical lineup key, minimum sample display, opponent aggregation, and confidence labels.
- [ ] Implement SQL aggregation over `battles_v2` and `battle_heroes`.
- [ ] Never label historical win rate as simulation or deterministic counter.
- [ ] Register lineup endpoints and run tests GREEN.

### Task 8: Research Center UI and Simulator Handoff

**Files:**
- Modify: `static/dashboard.html`
- Create: `static/intelligence-research.js`
- Create: `static/intelligence-research.css`
- Modify: `static/app1.js`
- Modify: `static/sim.js`
- Modify: `test/js/dashboard-e2e.mjs`

**Interfaces:**
- Adds `tab34` labeled `阵容战法研究`
- Produces `ResearchCenter.openHero(id)`, `openSkill(id)`, `openLineup(key)`, `sendToSimulator(lineup)`

- [ ] Add E2E fixtures and failing tests for search, hero detail, skill detail, evidence badges, lineup detail, and simulator handoff.
- [ ] Implement virtualized/paginated result lists.
- [ ] Render CONFIG_FACT/BATTLE_STAT/SIMULATION badges and sample sizes.
- [ ] Serialize lineup into existing simulator state and navigate to tab25.
- [ ] Run E2E GREEN.

### Task 9: Query Agent Intelligence Tools

**Files:**
- Modify: `query_agent/tools.py`
- Modify: `query_agent/service.py`
- Modify: `query_agent/context.py`
- Modify: `query_agent/models.py`
- Modify: `test/test_query_agent_tools.py`
- Modify: `test/test_query_agent_service.py`

**Interfaces:**
- Adds read-only tool methods from the approved spec.
- Evidence includes `datasetVersion`, `worldStateVersion`, `freshness`, `sampleSize`, `evidenceClass`.

- [ ] Add failing tests for tile risk explanation, hero lookup, skill lookup, lineup evidence, stale data, and action rejection.
- [ ] Implement bounded read-only tool calls.
- [ ] Extend intent routing without granting raw SQL, packet, file, shell, or write access.
- [ ] Add UI actions for tab33/tab34 and selected entity.
- [ ] Run Query Agent tests GREEN.

### Task 10: Final Verification and Completion Audit

**Files:**
- Modify: `README.md`
- Create: `docs/superpowers/audits/2026-08-14-unified-world-intelligence-completion-audit.md`

- [ ] Run all Python tests:

```bash
.venv/bin/python -m unittest discover -s test -v
```

- [ ] Run JS syntax and Node behavior tests.
- [ ] Run system Chrome E2E at 1440, 1024, 768, and 375 widths.
- [ ] Run snapshot `--check`.
- [ ] Run `git diff --check`.
- [ ] Audit each specification requirement to concrete code/test/runtime evidence.
- [ ] Confirm no runtime access to `/Users/bytedance/stzb`.
- [ ] Confirm no copied capture/account/alliance data.
- [ ] Record any uncovered requirement as incomplete; do not mark complete from proxy signals alone.
