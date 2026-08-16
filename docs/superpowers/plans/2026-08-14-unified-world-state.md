# STZB Unified World State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge 5026 baselines and 5028 deltas into one versioned WorldState with correct multiframe, coverage, block-membership, deletion, freshness, and event semantics.

**Architecture:** Parsing remains protocol-shaped, while a new transaction assembler emits complete baseline or delta transactions. `WorldStateStore` persists raw packets, normalized current projections, membership tables, and compact domain events. Intelligence APIs read only merged projections and never expose separate 5026/5028 datasets.

**Tech Stack:** Python 3.11, Flask, SQLite, dataclasses, unittest

## Global Constraints

- 5026 and 5028 describe one WorldState.
- 5026 is authoritative only for the observed area/blocks it covers, not the whole server world.
- 5028 is an incremental mutation and must never clear omitted entities.
- Block unlink is not global deletion while another membership remains.
- `state=0` is explicit global deletion.
- 5026 realMarch replaces the complete collection; 5028 realMarch only overlays.
- visualField signed int64 must remain JSON string/BigInt-safe.
- Preserve old `/api/world/*`, `map_cells`, and `battle_monitor_moves`.
- All changes are read-only from the Web user's perspective.
- Do not commit automatically.

---

### Task 1: Protocol Model Completeness and Transaction Types

**Files:**
- Modify: `world_scene/models.py`
- Modify: `world_scene/parser.py`
- Create: `world_scene/transactions.py`
- Modify: `test/test_world_scene_parser.py`

**Interfaces:**
- Produces:
  - `ObservedArea(row_up,row_down,col_left,col_right)`
  - `WorldSceneTransaction(kind, packets, packet, completeness, coverage)`
  - packet fields for observed area, global delete IDs, and all block memberships

- [ ] Write failing parser tests for slot 17, 5028 block info, state-zero global delete, and 14-slot realMarch indexes.
- [ ] Run parser tests and verify failures identify missing fields.
- [ ] Extend immutable models without changing existing constructor call sites by adding defaulted fields.
- [ ] Correct realMarch mapping to documented tuple:

```python
last_wid=raw[0]
current_wid=raw[1]
current_arrive_time=raw[2]
next_wid=raw[3]
next_begin_time=raw[4]
next_need_time=raw[5]
next_spend_time=raw[6]
path_id=raw[7]
unit_time_cost=raw[8]
march_type=raw[9]
belong_id=raw[10]
morale=raw[11]
```

- [ ] Run parser tests and verify GREEN.
- [ ] Review all field names against `world_scene_schema.json`.

### Task 2: Real 5026 Multiframe Assembly

**Files:**
- Modify: `world_scene/parser.py`
- Modify: `world_scene/transactions.py`
- Modify: `test/test_world_scene_parser.py`

**Interfaces:**
- Produces: `WorldSceneAssembler.apply(packet) -> WorldSceneApplyResult`
- `WorldSceneApplyResult.packet` on final 5026 contains merged intermediate packets.

- [ ] Add failing tests with two intermediate 5026 frames and one final frame:

```python
first = parse_world_scene_packet(5026, payload(chunks={"10001": {"0": tile1}}, marker=0), ...)
second = parse_world_scene_packet(5026, payload(chunks={"10002": {"0": tile2}}, marker=0), ...)
final = parse_world_scene_packet(5026, payload(chunks={"10003": {"0": tile3}}, marker=90), ...)
assembler.apply(first)
assembler.apply(second)
result = assembler.apply(final)
self.assertEqual(set(result.packet.tiles), {10001,10002,10003})
```

- [ ] Verify RED: only final tile currently survives.
- [ ] Implement immutable packet merge helpers for maps, tuple deletes, chunks, visual fields, and memberships.
- [ ] Add assembly reset/timeout API:

```python
assembler.expire(now_ms, timeout_ms=15_000) -> bool
```

- [ ] Verify multiframe, timeout, and new-cycle reset tests GREEN.

### Task 3: Version, Coverage, Membership, and Event Schema

**Files:**
- Modify: `world_scene/store.py`
- Create: `world_scene/migrations.py`
- Modify: `test/test_world_scene_store.py`

**Interfaces:**
- Produces schema tables listed in the approved spec.
- Produces `WorldStateStore.current_version() -> dict`.

- [ ] Add failing schema/migration tests asserting exact columns and idempotent double migration.
- [ ] Implement `ensure_world_state_schema(conn)` with `CREATE TABLE IF NOT EXISTS` and safe `ALTER TABLE`.
- [ ] Add `world_state_versions` row for every accepted complete baseline/delta.
- [ ] Add current membership tables keyed by `(block_id, entity_id)`:
  - `world_army_blocks`
  - `world_ship_blocks`
  - `world_assist_army_blocks`
- [ ] Add `world_state_events(seq,state_version,event_type,entity_type,entity_id,observed_at_ms,evidence_json,diff_json)`.
- [ ] Run migration/store tests GREEN.

### Task 4: Baseline Application Within Coverage

**Files:**
- Create: `world_scene/state_store.py`
- Modify: `realtime_writer.py`
- Modify: `test/test_world_scene_store.py`
- Modify: `test/test_world_scene_writer_integration.py`

**Interfaces:**
- Produces: `WorldStateStore.apply_baseline(transaction) -> WorldStateChangeSet`
- Consumes complete 5026 transaction only.

- [ ] Write failing tests for:
  - covered tile disappears and is expired;
  - uncovered tile remains;
  - missing observed area performs upsert-only;
  - covered block membership is replaced;
  - 5026 realMarch replacement removes omitted march.
- [ ] Verify RED against current upsert-only store.
- [ ] Implement transactional baseline application with one SQLite transaction.
- [ ] Set completeness to `full-baseline` only when coverage is explainable; otherwise `partial-baseline`.
- [ ] Emit compact change events and one `world_snapshot_complete` SSE event.
- [ ] Run store and writer integration tests GREEN.

### Task 5: Delta Application and Correct Block Unlink

**Files:**
- Modify: `world_scene/state_store.py`
- Modify: `world_scene/parser.py`
- Modify: `test/test_world_scene_store.py`

**Interfaces:**
- Produces: `WorldStateStore.apply_delta(transaction) -> WorldStateChangeSet`

- [ ] Add failing tests:

```python
link_army(block=1, army=100)
link_army(block=2, army=100)
apply_5028_unlink(block=1, army=100)
self.assertIsNone(army.deleted_at_seq)
apply_5028_unlink(block=2, army=100)
self.assertIsNotNone(army.deleted_at_seq)
```

- [ ] Assert Block unlink preserves the entity while another membership remains.
- [ ] Add clearChunks subtype test and direct state-zero delete test.
- [ ] Implement mode-2 unlink semantics independently for army/ship/assist.
- [ ] Overlay realMarch and visualField changes without replacing omitted entries.
- [ ] Record stale-rejected and special-bypass evidence.
- [ ] Run store tests GREEN.

### Task 6: Freshness, Risk Projection, and Tile Detail

**Files:**
- Create: `intelligence/world_service.py`
- Create: `intelligence/risk.py`
- Create: `test/test_world_intelligence_service.py`

**Interfaces:**
- Produces:
  - `freshness(observed_at_ms, now_ms) -> fresh|aging|stale|unknown`
  - `risk_for_tile(tile, armies, battles, now_ms) -> RiskAssessment`
  - `tile_detail(wid) -> dict`

- [ ] Write failing exact-boundary freshness tests at 119/120/599/600 seconds.
- [ ] Write failing explainable risk tests asserting each component and `unknownComponents`.
- [ ] Implement risk weights exactly from the spec; do not treat unknown as zero.
- [ ] Join tile, owner, incoming armies, marches, recent battles, provenance, coverage, and freshness.
- [ ] Run service tests GREEN.

### Task 7: Unified Intelligence APIs

**Files:**
- Create: `intelligence/world_api.py`
- Modify: `api_server.py`
- Create: `test/test_world_intelligence_api.py`

**Interfaces:**
- Endpoints:
  - `/api/intelligence/world/summary`
  - `/api/intelligence/world/viewport`
  - `/api/intelligence/world/tile/<wid>`
  - `/api/intelligence/world/events`
  - `/api/intelligence/world/risks`

- [ ] Add Flask tests for stable response envelope, pagination, bounds validation, empty state, and missing optional tables.
- [ ] Implement `register_world_intelligence_api(app, get_connection, rules_loader)`.
- [ ] Include `worldStateVersion/latestBaseline/latestDelta/freshness/completeness/coverage`.
- [ ] Ensure responses never expose raw packet paths or account data.
- [ ] Run API tests and existing `/api/world/*` regressions GREEN.
