# Configurable Season Score Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the broken fixed custom-score calculation with a versioned, explainable season score center that supports configurable weights, battle/siege sub-scores, preview-confirm recalculation, and audited manual adjustments.

**Architecture:** Put scoring, rule validation, aggregation and preview tokens in a new `score_center` package. Flask routes become a thin adapter that receives the existing `get_db` connection factory. The dashboard consumes typed score envelopes and never evaluates formulas itself.

**Tech Stack:** Python 3.9-compatible code, Flask, SQLite, Vanilla JavaScript, Python unittest, system Chrome + Playwright.

## Global Constraints

- Do not use `|` union type syntax without `from __future__ import annotations`.
- Do not execute arbitrary formulas, SQL, JavaScript or files from rule input.
- Every score result preserves raw metrics, weights and component scores.
- Every write route remains protected by optional `STZB_API_TOKEN`.
- Historical rule versions and score results are immutable.
- Preview does not write score rows.
- Recalculation requires a valid, unexpired preview token.
- Do not commit changes.

---

### Task 1: Score Domain Model and Formula

**Files:**
- Create: `score_center/__init__.py`
- Create: `score_center/models.py`
- Create: `score_center/calculator.py`
- Create: `test/test_score_center_calculator.py`

**Interfaces:**
- `ScoreRule.from_mapping(value: dict) -> ScoreRule`
- `ScoreMetrics`
- `ScoreBreakdown`
- `calculate_score(metrics: ScoreMetrics, rule: ScoreRule, adjustment: float=0) -> ScoreBreakdown`
- Presets:
  - `alliance_contribution`
  - `season_reward`
  - `siege_priority`

- [ ] **Step 1: Write failing tests for the default formula**

Use:

```python
metrics = ScoreMetrics(
    battles=10,
    wins=4,
    draws=2,
    gongxun_total=3000,
    main_city_cnt=2,
    tear_cnt=1,
    attendance_cnt=3,
)
```

Assert exact battle score, siege score, adjustment score and total.

- [ ] **Step 2: Write failing validation tests**

Reject:

- non-finite values;
- booleans;
- weights outside `[-1000, 1000]`;
- `gongxunDivisor <= 0`;
- missing required fields;
- unknown fields.

- [ ] **Step 3: Write failing serialization tests**

Assert `ScoreBreakdown.to_json()` contains:

- metrics;
- rule;
- component scores;
- `battleScore`;
- `siegeScore`;
- `adjustmentScore`;
- `score`.

- [ ] **Step 4: Run tests RED**

```bash
.venv/bin/python -m unittest test.test_score_center_calculator -v
```

- [ ] **Step 5: Implement immutable dataclasses and calculator**

Use `dataclass(frozen=True)` and explicit numeric operations. Round component and total scores to two decimal places only at output boundaries.

- [ ] **Step 6: Run tests GREEN**

---

### Task 2: Idempotent Schema and Repository

**Files:**
- Create: `score_center/repository.py`
- Create: `test/test_score_center_repository.py`
- Modify: `api_server.py`

**Interfaces:**
- `ScoreRepository.ensure_schema()`
- `ScoreRepository.list_rules(season_id)`
- `ScoreRepository.create_rule(...)`
- `ScoreRepository.activate_rule(rule_id)`
- `ScoreRepository.active_rule(season_id)`
- `ScoreRepository.list_adjustments(...)`
- `ScoreRepository.add_adjustment(...)`
- `ScoreRepository.delete_adjustment(...)`
- `ScoreRepository.replace_scores(...)`
- `ScoreRepository.list_scores(...)`
- `ScoreRepository.score_detail(...)`

- [ ] **Step 1: Write failing idempotent migration tests**

Start from the current legacy `custom_scores` schema. Run `ensure_schema()` twice and assert all new columns, tables and indexes exist.

- [ ] **Step 2: Write failing rule-version tests**

Assert:

- versions increment per season;
- each season has only one active rule;
- activating one rule retires the previous active rule;
- a rule row is never updated in place.

- [ ] **Step 3: Write failing adjustment tests**

Require non-empty reason, finite non-zero points and season/player identity. Deletion must reject a different season.

- [ ] **Step 4: Write failing score persistence tests**

Persist two rule versions and prove historical rows retain their original `rule_version_id` and `breakdown_json`.

- [ ] **Step 5: Run repository tests RED**

- [ ] **Step 6: Implement schema**

Create:

```sql
score_rule_versions
score_adjustments
```

Add to `custom_scores`:

```text
rule_version_id,draws,attendance_cnt,battle_score,siege_score,
adjustment_score,breakdown_json,calculated_at
```

Use parameterized SQL exclusively.

- [ ] **Step 7: Register repository schema in `ensure_all_tables()`**

Call the score repository migration explicitly from `ensure_all_tables(db_path)`. Importing `api_server` must still not start threads.

- [ ] **Step 8: Run repository tests GREEN**

---

### Task 3: Metrics Aggregation and Data Completeness

**Files:**
- Create: `score_center/aggregation.py`
- Create: `test/test_score_center_aggregation.py`

**Interfaces:**
- `ScoreAggregator(connection).aggregate(season_id, start_time=None, end_time=None, union_filter="", group_filter="")`
- Returns `AggregatedPlayer` rows with metrics, identity and completeness.

- [ ] **Step 1: Write failing battle aggregation tests**

Cover:

- `atk_name` and `atk_uid`;
- `atk_union` preferred over team-user fallback;
- never use `def_union`;
- attack win result set;
- explicit losses and draws;
- time-range filtering;
- stable grouping by player.

- [ ] **Step 2: Write failing attendance de-duplication tests**

Duplicate rows with the same `(session_id, player_name, role)` count once. Verify `main`, `tear` and other attendance metrics separately.

- [ ] **Step 3: Write failing missing-source tests**

When `attendance` is absent:

- battle metrics still return;
- siege metrics are marked unknown;
- `missingSources` includes `attendance`;
- `dataCompleteness` is `partial`.

When `atk_gongxun` is absent or all null, mark `gongxun` missing.

- [ ] **Step 4: Write failing adjustment-only-player test**

A player with no battle/attendance row but with a manual adjustment must still be present.

- [ ] **Step 5: Run aggregation tests RED**

- [ ] **Step 6: Implement bounded parameterized aggregation**

Inspect table columns before querying optional fields. Do not concatenate raw filters into SQL.

- [ ] **Step 7: Run aggregation tests GREEN**

---

### Task 4: Rule and Adjustment APIs

**Files:**
- Create: `score_center/api.py`
- Create: `test/test_score_center_api.py`
- Modify: `api_server.py`

**Interfaces:**
- `register_score_center_api(app, get_connection, token_store=None)`
- Routes:
  - `GET /api/custom_scores`
  - `GET /api/custom_scores/<player>`
  - `GET /api/custom_scores/rules`
  - `POST /api/custom_scores/rules`
  - `POST /api/custom_scores/rules/<id>/activate`
  - `GET /api/custom_scores/adjustments`
  - `POST /api/custom_scores/adjustments`
  - `DELETE /api/custom_scores/adjustments/<id>`

- [ ] **Step 1: Write failing list/detail API tests**

Lock a stable envelope:

```json
{
  "ok": true,
  "seasonId": "current",
  "ruleVersion": 1,
  "dataCompleteness": "complete",
  "rows": []
}
```

Support `board=overall|battle|siege`, union/group filters and stable sorting.

- [ ] **Step 2: Write failing rule API tests**

Cover presets, validation errors, immutable version creation and activation.

- [ ] **Step 3: Write failing adjustment API tests**

Cover positive rewards, negative penalties, required reason, player identity and delete behavior.

- [ ] **Step 4: Write failing token tests**

With `STZB_API_TOKEN`, all rule/adjustment writes return 401 without a token and succeed with `X-STZB-Token`.

- [ ] **Step 5: Run API tests RED**

- [ ] **Step 6: Implement API adapter**

Remove the old inline custom-score routes from `api_server.py` after registering the new blueprint/routes. Preserve endpoint URLs.

- [ ] **Step 7: Add score writes to the central mutating-endpoint guard**

Guard create, activate, adjustment add/delete and recalc. Preview remains read-only POST.

- [ ] **Step 8: Run API tests GREEN**

---

### Task 5: Preview Token and Confirmed Recalculation

**Files:**
- Create: `score_center/service.py`
- Create: `test/test_score_center_service.py`
- Modify: `score_center/api.py`
- Modify: `test/test_score_center_api.py`

**Interfaces:**
- `ScoreCenterService.preview(request) -> PreviewResult`
- `ScoreCenterService.recalculate(preview_token, request) -> RecalcResult`
- Preview-token payload includes:
  - season;
  - rule version/config hash;
  - filters/time range;
  - database snapshot fingerprint;
  - expiration.

- [ ] **Step 1: Write failing preview-no-write test**

Count `custom_scores` before and after preview; count must remain unchanged.

- [ ] **Step 2: Write failing ranking-delta tests**

Seed legacy scores and assert preview returns:

- old/new score;
- old/new rank;
- score delta;
- rank delta;
- full breakdown.

- [ ] **Step 3: Write failing token validity tests**

Reject:

- missing token;
- expired token;
- tampered token;
- changed rule;
- changed filters;
- changed database fingerprint.

- [ ] **Step 4: Write failing confirmed-write test**

After valid preview and recalc:

- replace only the requested season/filter scope;
- write component columns and breakdown JSON;
- preserve other seasons;
- return updated row count.

- [ ] **Step 5: Run service tests RED**

- [ ] **Step 6: Implement in-memory bounded preview store**

Use cryptographically random opaque tokens. Keep a maximum of 100 previews and 15-minute TTL. Do not expose signed database internals to the browser.

- [ ] **Step 7: Implement preview/recalc routes**

Legacy recalc without token returns HTTP 400 with a migration message.

- [ ] **Step 8: Run service and API tests GREEN**

---

### Task 6: Score Center UI and Three Boards

**Files:**
- Create: `static/score-center.js`
- Create: `static/score-center.css`
- Modify: `static/dashboard.html`
- Modify: `static/app1.js`
- Remove custom-score functions from: `static/app2.js`
- Create: `test/test_score_center_static.py`

**Interfaces:**
- `window.ScoreCenter.load()`
- `ScoreCenter.switchBoard(board)`
- `ScoreCenter.openPlayer(playerName)`
- `ScoreCenter.openRuleEditor()`
- `ScoreCenter.preview()`
- `ScoreCenter.confirmRecalculation()`
- `ScoreCenter.addAdjustment()`

- [ ] **Step 1: Write failing static contract tests**

Assert:

- season/time/union/group controls;
- current rule version;
- overall/battle/siege tabs;
- KPI region;
- score table;
- player detail drawer;
- rule editor dialog;
- preview dialog;
- adjustment dialog;
- new CSS/JS loaded.

- [ ] **Step 2: Run static test RED**

- [ ] **Step 3: Replace tab8 markup**

Use the accepted dark blue/cyan design tokens. Do not add emoji labels. Keep table and dialogs responsive.

- [ ] **Step 4: Implement typed API rendering**

Render:

- total, battle, siege and adjustment values;
- data-completeness badge;
- stable rank;
- score/rank deltas in preview.

- [ ] **Step 5: Implement rule editor**

Numeric inputs only. Show the expanded formula and sample calculation. Switching preset edits local values only.

- [ ] **Step 6: Implement player detail**

Show raw metrics, rule weights, each component score, adjustments, evidence counts, rule version and missing sources.

- [ ] **Step 7: Implement preview-confirm flow**

Preview first; disable confirm if API reports errors or data changed.

- [ ] **Step 8: Run static and focused UI tests GREEN**

---

### Task 7: Chrome E2E and Completion

**Files:**
- Modify: `test/js/dashboard-e2e.mjs`
- Modify: `README.md`
- Modify: completion audit

- [ ] **Step 1: Add E2E API fixtures**

Mock rules, three boards, player detail, preview, confirmed recalc and adjustments.

- [ ] **Step 2: Verify three boards**

Open tab8 and switch overall/battle/siege. Assert rank and score columns change appropriately.

- [ ] **Step 3: Verify rule editing**

Open editor, choose a preset, change a weight, verify live formula and validation.

- [ ] **Step 4: Verify preview-confirm**

Assert preview shows score/rank deltas and confirm sends the preview token.

- [ ] **Step 5: Verify player detail and manual adjustment**

Open a player, inspect component scores, add reward/penalty with reason, and verify list refresh.

- [ ] **Step 6: Verify responsive layout**

At 375, 768, 1024 and 1440 widths, dialogs and tables remain reachable without document overflow.

- [ ] **Step 7: Run full verification**

```bash
.venv/bin/python -m unittest discover -s test -v
.venv/bin/python -m py_compile score_center/*.py api_server.py
node --check static/score-center.js
git diff --check
```

- [ ] **Step 8: Verify against current real database**

Use `D:\\nettest\\stzb_192.168.31.198.db`:

- preview returns the current 16 battle players;
- the broken legacy index calculation is gone;
- attack union never equals unrelated defender union;
- attendance rows contribute to siege score;
- zero gongxun is reported as incomplete rather than hidden;
- preview performs no write.

- [ ] **Step 9: Update docs**

Document presets, preview-confirm workflow, Token protection, legacy compatibility and latest test count.
