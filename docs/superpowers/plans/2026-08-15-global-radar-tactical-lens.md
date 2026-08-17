# Global Radar Tactical Lens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the easy-to-lose sparse grid experience with a three-level semantic map that combines global radar, risk hotspot aggregation, a precise tactical lens, and recoverable viewport navigation.

**Architecture:** `WorldIntelligenceService` produces bounded read-only overview buckets. `intelligence-map-overview.mjs` owns semantic zoom, hotspot and radar geometry; `intelligence-map-navigation.mjs` owns viewport history and persisted navigation state. `intelligence-center.js` remains the coordinator and delegates calculations and drawing to these modules.

**Tech Stack:** Python 3, Flask, SQLite, Vanilla JavaScript ES Modules, Canvas 2D, Node `node:test`, system Chrome + Playwright E2E.

## Global Constraints

- All new behavior is read-only and must not send game commands or mutate game state.
- A bucket is presentation-only and must never replace or modify a real tile row.
- Unknown ownership remains unknown and must not be inferred as enemy.
- The overview response returns at most 2,500 non-empty buckets.
- The renderer draws at most 2,500 buckets or real tile cells per frame.
- Existing `/api/intelligence/world/viewport`, tile detail, risk, event, and research behavior remains compatible.
- Do not create a Git commit; the working tree already contains unrelated user changes.

---

### Task 1: Overview Bucket Aggregation

**Files:**
- Modify: `intelligence/world_service.py`
- Modify: `intelligence/world_api.py`
- Modify: `test/test_world_intelligence_service.py`
- Modify: `test/test_world_intelligence_api.py`

**Interfaces:**
- Produces:
  - `WorldIntelligenceService.overview(row_up, row_down, col_left, col_right, bucket_rows, bucket_cols) -> dict`
  - `GET /api/intelligence/world/overview`
- Response keys:
  - existing WorldState envelope;
  - `dataBounds`;
  - `bucketRows`;
  - `bucketCols`;
  - `buckets`.
- Every bucket contains:
  - `rowUp`, `rowDown`, `colLeft`, `colRight`;
  - `tileCount`;
  - `riskMax`, `riskAverage`;
  - `selfCount`, `allyCount`, `enemyCount`, `unknownCount`, `unownedCount`;
  - `armyCount`, `changeCount`;
  - `focusWid`.

- [ ] **Step 1: Add failing service tests for bucket boundaries**

Create several tiles that fall into two known buckets and assert exact inclusive
`rowUp/rowDown/colLeft/colRight` values. Assert empty buckets are omitted.

- [ ] **Step 2: Add failing tests for aggregation semantics**

Use deterministic self, ally, enemy, unknown, risk, army and event fixtures.
Assert:

```python
self.assertEqual(bucket["tileCount"], 4)
self.assertEqual(bucket["enemyCount"], 1)
self.assertEqual(bucket["unknownCount"], 1)
self.assertEqual(bucket["riskMax"], 72)
self.assertEqual(bucket["riskAverage"], 34.5)
self.assertEqual(bucket["armyCount"], 3)
self.assertEqual(bucket["changeCount"], 2)
```

Also assert `focusWid` exists in that bucket and selects the highest-risk tile,
breaking ties by latest source sequence and WID.

- [ ] **Step 3: Add failing bounds and limit tests**

Assert invalid bucket dimensions return `ValueError`; assert a request that
would exceed 2,500 buckets is rejected rather than silently allocating a large
matrix.

- [ ] **Step 4: Run focused tests and verify RED**

Run:

```bash
.venv/bin/python -m unittest \
  test.test_world_intelligence_service \
  test.test_world_intelligence_api -v
```

Expected: failures because `overview()` and the route do not exist.

- [ ] **Step 5: Implement deterministic overview aggregation**

Add `overview()` and private helpers that:

1. validate inclusive bounds and positive bucket sizes;
2. query only non-empty tiles in the requested range;
3. assign each tile to a bucket using integer offsets from requested bounds;
4. reuse the current identity and risk relation rules;
5. count only active armies;
6. count `world_state_events` whose tile `entity_id` falls in the bucket;
7. sort buckets by `rowUp`, then `colLeft`.

Do not create empty bucket records.

- [ ] **Step 6: Register and validate the API route**

Parse:

```text
rowUp,rowDown,colLeft,colRight,bucketRows,bucketCols
```

Return HTTP 400 for invalid bounds or excessive bucket count.

- [ ] **Step 7: Run focused tests and verify GREEN**

Run the Step 4 command and require all tests to pass.

---

### Task 2: Semantic Zoom and Radar Geometry

**Files:**
- Create: `static/intelligence-map-overview.mjs`
- Create: `test/js/intelligence-map-overview.test.mjs`
- Create: `test/test_intelligence_map_node.py`
- Modify: `static/intelligence-map.mjs`

**Interfaces:**
- Produces:
  - `semanticMapMode(bounds, width, height) -> "far" | "middle" | "near"`
  - `bucketGridForBounds(bounds, width, height, maxBuckets=2500)`
  - `worldToRadar(row, col, dataBounds, radarRect)`
  - `radarToWorld(x, y, dataBounds, radarRect)`
  - `radarViewportRect(viewBounds, dataBounds, radarRect)`
  - `drawOverviewMap(canvas, buckets, options) -> renderState`
  - `drawRadar(canvas, buckets, options) -> radarState`
  - `hitTestBucket(x, y, renderState) -> bucket | null`
- Existing `drawIntelligenceMap()` remains the near-mode renderer.

- [ ] **Step 1: Write failing semantic zoom tests**

Lock the thresholds:

```javascript
assert.equal(semanticMapMode({rowUp:0,rowDown:160,colLeft:0,colRight:160}, 900, 560), "far");
assert.equal(semanticMapMode({rowUp:0,rowDown:80,colLeft:0,colRight:80}, 900, 560), "middle");
assert.equal(semanticMapMode({rowUp:0,rowDown:20,colLeft:0,colRight:20}, 900, 560), "near");
```

Also cover the single-cell pixel-size threshold from the design.

- [ ] **Step 2: Write failing radar round-trip tests**

Assert world-to-radar and radar-to-world round trips remain within one world
cell at all edges and center points. Assert the viewport rectangle is clipped
inside the radar.

- [ ] **Step 3: Write failing bucket geometry and hit-test tests**

Assert bucket screen rectangles do not exceed the canvas, empty inputs render
zero hit areas, and hit test returns the exact bucket under a point.

- [ ] **Step 4: Add a Python Node-test wrapper**

`test/test_intelligence_map_node.py` runs:

```bash
node --test \
  test/js/intelligence-map.test.mjs \
  test/js/intelligence-map-overview.test.mjs \
  test/js/intelligence-map-navigation.test.mjs
```

The wrapper must capture stdout/stderr and fail unittest on non-zero exit.

- [ ] **Step 5: Run Node tests and verify RED**

Expected: missing module/export failures.

- [ ] **Step 6: Implement pure geometry and rendering**

Implement the overview module without DOM queries or API calls. Render:

- far mode as two-ring hotspot glows with count labels;
- middle mode as bounded tile islands with risk/ownership accents;
- radar as density marks, selected WID marker and cyan viewport rectangle.

Cap device pixel ratio at 2 in both old and new Canvas renderers.

- [ ] **Step 7: Run Node tests and verify GREEN**

Run the wrapper and existing map tests. Require all tests to pass.

---

### Task 3: Recoverable Viewport Navigation

**Files:**
- Create: `static/intelligence-map-navigation.mjs`
- Create: `test/js/intelligence-map-navigation.test.mjs`

**Interfaces:**
- Produces:
  - `createViewportHistory(initialState, limit=30)`
  - history methods: `push`, `back`, `forward`, `replace`, `current`, `canBack`, `canForward`
  - `serializeMapState(state) -> string`
  - `parseMapState(hash, fallback) -> state`
  - `isBoundsUseful(bounds, dataBounds) -> boolean`
  - `interpolateBounds(from, to, progress) -> bounds`
- A history state contains:

```javascript
{
  bounds: {rowUp, rowDown, colLeft, colRight},
  selectedWid: 0,
  layers: {ownership, freshness, paths, armies}
}
```

- [ ] **Step 1: Write failing history behavior tests**

Cover:

- push, back and forward;
- pushing after back truncates the forward branch;
- duplicate consecutive state is ignored;
- limit retains only the newest 30 entries;
- one drag gesture creates one history entry.

- [ ] **Step 2: Write failing URL-state tests**

Round-trip bounds, selected WID and layers. Reject malformed, negative or
non-finite values. An invalid or data-disjoint state must fall back to
`suggestedBounds`.

- [ ] **Step 3: Write failing animation tests**

Assert interpolation at 0, 0.5 and 1. Assert reduced-motion chooses the target
state immediately and does not schedule intermediate frames.

- [ ] **Step 4: Run Node tests and verify RED**

Expected: module not found.

- [ ] **Step 5: Implement the navigation module**

Keep it independent of Canvas and network code. Store history in memory; use
URL hash only for the current state. Expose no write API beyond browser history.

- [ ] **Step 6: Run Node tests and verify GREEN**

Run all intelligence map Node tests through the Python wrapper.

---

### Task 4: Radar, Lens and Navigation Controls Integration

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/intelligence-center.css`
- Modify: `static/intelligence-center.js`
- Modify: `test/test_intelligence_center_static.py`

**Interfaces:**
- Adds:
  - `#intel-radar-canvas`;
  - `#intel-map-mode`;
  - `#intel-map-home`;
  - `#intel-map-back`;
  - `#intel-map-forward`;
  - `#intel-radar-toggle`.
- `IntelligenceCenter` adds:
  - `goHome()`;
  - `goBack()`;
  - `goForward()`;
  - `focusBucket(bucket)`;
  - `moveFromRadar(point)`.

- [ ] **Step 1: Add failing static contract tests**

Assert both new modules load before `intelligence-center.js`; assert radar and
navigation controls exist; assert coordinator references
`semanticMapMode`, `createViewportHistory`, `AbortController`, and
`requestAnimationFrame`.

- [ ] **Step 2: Run static tests and verify RED**

Run:

```bash
.venv/bin/python -m unittest test.test_intelligence_center_static -v
```

- [ ] **Step 3: Add radar and navigation markup**

Place the radar inside `.intel-map-wrap`, above the legend. Add Home, Back,
Forward and zoom controls at bottom right. Keep every control keyboard
reachable and provide Chinese `aria-label` values.

- [ ] **Step 4: Refactor the controller into explicit phases**

Replace the current single compressed load/render flow with:

1. `loadSummary()`;
2. `chooseMode()`;
3. `loadNearViewport()` or `loadOverviewBuckets()`;
4. `renderMainMap()`;
5. `renderRadar()`;
6. `syncSelection()`.

Use one `AbortController` per viewport request group. Abort the previous group
when bounds change.

- [ ] **Step 5: Integrate semantic modes**

- far/middle request `/api/intelligence/world/overview`;
- near requests existing viewport and risks;
- API failure in overview mode falls back to near viewport without clearing
  radar bounds;
- mode text is visible in `#intel-map-mode`.

- [ ] **Step 6: Integrate radar gestures**

- radar click recenters the current span;
- radar pointer drag updates bounds via requestAnimationFrame;
- radar drag end writes one history entry and triggers one request;
- double-click bucket calls `focusBucket()` and enters a 20×20 lens.

- [ ] **Step 7: Integrate Home, Back, Forward and URL state**

- Home uses `summary.suggestedBounds`;
- WID, event, bucket and radar jumps call one shared navigation method;
- manual drag and wheel write history only after the gesture settles;
- Back/Forward button disabled state tracks history;
- each settled state uses `history.replaceState()` or hash update;
- invalid restored state falls back to Home.

- [ ] **Step 8: Add animation and accessibility behavior**

Use 220–320ms ease-out movement. Under `prefers-reduced-motion`, jump
immediately. Pause hotspot pulse when `document.visibilityState !== "visible"`.

- [ ] **Step 9: Run static and Node tests GREEN**

Require the focused Python and all intelligence map Node tests to pass.

---

### Task 5: Performance, Error States and Chrome Closure

**Files:**
- Modify: `test/js/dashboard-e2e.mjs`
- Modify: `test/test_dashboard_e2e.py`
- Modify: `static/intelligence-center.css`
- Modify: `README.md`
- Modify: `docs/superpowers/audits/2026-08-14-unified-world-intelligence-completion-audit.md`

**Interfaces:**
- Chrome fixtures provide both overview buckets and near-mode real tiles.
- E2E exercises the public UI only.

- [ ] **Step 1: Add failing E2E assertions for the radar**

Assert first load displays:

- radar canvas;
- far or middle semantic mode;
- at least one hotspot;
- a visible viewport rectangle.

- [ ] **Step 2: Add failing E2E for hotspot-to-tile drill-down**

Double-click a hotspot, wait for near mode, click a real tile, and assert the
right detail panel displays the expected WID.

- [ ] **Step 3: Add failing E2E for recoverable navigation**

Exercise:

1. WID locate;
2. radar recenter;
3. Home;
4. Back;
5. Forward.

Assert main bounds, radar rectangle and selected WID remain synchronized.

- [ ] **Step 4: Add responsive assertions**

At 375, 768, 1024 and 1440 widths:

- no horizontal document overflow;
- radar remains reachable or collapsed to its 36px control;
- right detail panel remains usable;
- map controls do not overlap the legend.

- [ ] **Step 5: Add explicit error-state coverage**

Mock:

- uninitialized WorldState;
- empty tile database;
- overview 500 with viewport fallback;
- missing WID;
- stale intelligence.

Assert no fake hotspot is shown and Home remains available where applicable.

- [ ] **Step 6: Run Chrome E2E GREEN**

Run:

```bash
.venv/bin/python -m unittest test.test_dashboard_e2e -v
```

- [ ] **Step 7: Run full verification**

Run:

```bash
.venv/bin/python -m unittest discover -s test -v
.venv/bin/python -m py_compile intelligence/*.py api_server.py
node --check static/intelligence-center.js
node --check static/intelligence-map.mjs
node --check static/intelligence-map-overview.mjs
node --check static/intelligence-map-navigation.mjs
git diff --check
```

Expected: all commands exit 0.

- [ ] **Step 8: Verify against the current real database**

Using `D:\\nettest\\stzb_192.168.31.198.db`, assert:

- summary returns current `dataBounds`;
- global overview returns non-empty buckets;
- every returned `focusWid` exists in `world_tiles`;
- a 20×20 lens around one focus WID returns real tiles.

- [ ] **Step 9: Update documentation**

Document semantic zoom, radar controls, WID location, Home/Back/Forward and the
read-only boundary. Record exact test counts and real-database evidence in the
completion audit.
