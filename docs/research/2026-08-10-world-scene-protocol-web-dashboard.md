# World Scene Protocol Findings for Web Dashboard Redesign

Date: 2026-08-10

Scope: map, march, and battlefield-monitoring improvements for `stzb_watcher`, using local first-party protocol/source material only. No web search was used. Automation, agent control, and command execution are excluded.

## Primary Sources Inspected

- Protocol field notes for `5026 SEND_WORLD_SCENCE_FULL_INFO` and `5028 SEND_WORLD_SCENCE_CHANGE_INFO`: [`5026-5028-world-scene-fields.md:L1-L20`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L1-L20).
- `5025 GET_WORLD_SCENCE_INFO` packet notes: [`cmd-5025-world-scene-packet.md:L1-L13`](file:///Users/bytedance/stzb/tools/monitor-agent/docs/cmd-5025-world-scene-packet.md#L1-L13).
- Current Android/local parser and storage code in `stzb_watcher`: [`LocalBattleMonitorParser.kt:L6-L24`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalBattleMonitorParser.kt#L6-L24), [`LocalAuxiliaryParser.kt:L16-L22`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalAuxiliaryParser.kt#L16-L22), [`Local13A2Parser.kt:L8-L55`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/Local13A2Parser.kt#L8-L55), [`LocalStzbDatabase.kt:L78-L104`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalStzbDatabase.kt#L78-L104).
- Current PC writer/API/static dashboard code: [`realtime_writer.py:L178-L297`](file:///Users/bytedance/stzb_watcher/realtime_writer.py#L178-L297), [`api_server.py:L1730-L1773`](file:///Users/bytedance/stzb_watcher/api_server.py#L1730-L1773), [`static/app2.js:L1048-L1078`](file:///Users/bytedance/stzb_watcher/static/app2.js#L1048-L1078).

## Protocol Contract

### 5025 Request Window

`cmd=5025` payload is six integers: `[rowUp, rowDown, colLeft, colRight, zoomLevel, simpleType]`. The four bounds are inclusive, and WID conversion is `row = wid / 10000`, `col = wid % 10000`. The last two fields are not disposable placeholders: `zoomLevel` is the client world-info zoom level, and `simpleType` distinguishes normal/M1 from simple/M2 data. Sources: [`cmd-5025-world-scene-packet.md:L7-L12`](file:///Users/bytedance/stzb/tools/monitor-agent/docs/cmd-5025-world-scene-packet.md#L7-L12), [`cmd-5025-world-scene-packet.md:L15-L26`](file:///Users/bytedance/stzb/tools/monitor-agent/docs/cmd-5025-world-scene-packet.md#L15-L26).

The command family is one world-scene flow: 5025 requests the window, 5026 pushes full scene data, 5028 pushes incremental changes, and 6087 asks the client to request world scene again. Sources: [`cmd-5025-world-scene-packet.md:L41-L54`](file:///Users/bytedance/stzb/tools/monitor-agent/docs/cmd-5025-world-scene-packet.md#L41-L54), [`5026-5028-world-scene-fields.md:L41-L49`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L41-L49).

Dashboard implication: a future API should expose viewport queries using explicit `rowUp,rowDown,colLeft,colRight` or `rowMin,rowMax,colMin,colMax`, not ambiguous `x/y` names. Existing code already has inconsistent naming: Python stores `x = wid % 10000`, `y = wid // 10000` in the old 13a2 map parser, while Android stores `x = wid / 10000`, `y = wid % 10000`. Sources: [`realtime_writer.py:L1467-L1483`](file:///Users/bytedance/stzb_watcher/realtime_writer.py#L1467-L1483), [`LocalAuxiliaryParser.kt:L207-L220`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalAuxiliaryParser.kt#L207-L220), [`LocalStzbDatabase.kt:L517-L519`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalStzbDatabase.kt#L517-L519).

### 5026 and 5028 Fixed 31-Slot Payloads

Both 5026 and 5028 use a fixed 31-slot JSON array. Slot positions are part of the protocol and must not be shifted or truncated. Captures showed all parseable 5026 samples and all 5028 samples at payload length 31. Sources: [`5026-5028-world-scene-fields.md:L59-L67`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L59-L67), [`5026-5028-world-scene-fields.md:L75-L98`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L75-L98), [`5026-5028-world-scene-fields.md:L1237-L1248`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L1237-L1248).

Key slots for dashboard work:

- `[1] mapUsers`: player/NPC map identity.
- `[3] unions`: union identity, needed before users/chunks/armies.
- `[6] armies`: map army tuples.
- `[7] deletedArmies`: 5028 block-scoped army removals.
- `[8] warShips`: not assist armies.
- `[10] assistArmies`: not short messages.
- `[12] armyGroups`.
- `[13] shortMessages`.
- `[14] worldChunks`: tile/city chunk dictionary.
- `[15] clearChunks`: 5028 chunk-type deletion dictionary.
- `[18] serverOrderId`.
- `[20] blockInfo`: `null` for official 5026, `[mode, blockId]` for 5028.
- `[21..23]`: block membership dictionaries for full 5026.
- `[29] realMarch`.

Sources: [`5026-5028-world-scene-fields.md:L139-L171`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L139-L171), [`5026-5028-world-scene-fields.md:L201-L233`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L201-L233).

The current monitor-agent static command model still labels some 5026 slots incorrectly: slot 8 as `assistArmies`, 10 as `shortMessages`, 12 as `extGarrison`, 13 as `manorFamily`, 16 as `extGarrisonChanges`, and 19 as `manorFamilyChanges`. The protocol note gives the corrected mapping and direct client-call evidence. Sources: [`command_model.mjs:L208-L240`](file:///Users/bytedance/stzb/tools/monitor-agent/web/static/command_model.mjs#L208-L240), [`5026-5028-world-scene-fields.md:L1285-L1329`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L1285-L1329).

### serverOrderId and Multiframe Rules

5026 can arrive as multiple frames. The client merges slot `[14]` into an assembly cache, treats `[18] == 0` as an intermediate frame, and only finalizes world chunks when `[18] > 0`. Sources: [`5026-5028-world-scene-fields.md:L173-L193`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L173-L193).

For 5028, the client compares packet `[18]` against the most recent `mServerOrderId` written by 5026. It does not advance `mServerOrderId` after each 5028. The special value `-999999999` bypasses the `<= mServerOrderId` drop check. Sources: [`5026-5028-world-scene-fields.md:L237-L273`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L237-L273).

Dashboard implication: the ingestion layer needs a domain event sequence and packet provenance separate from 5028 `serverOrderId`. Treat 5028 order as a gate relative to the last completed 5026 snapshot, not as the dashboard's own durable monotonic event ID.

### MapUserTuple

`MapUserTuple` is 25 slots. The client reads slots 0..23, while slot 24 is reserved/unknown and should be preserved as raw data. Important display fields include name, representative WID, union ID, force, union detail, affiliated union detail, clan detail, AI marker, appearance strings, and protection end time. Sources: [`5026-5028-world-scene-fields.md:L317-L389`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L317-L389).

Dashboard implication: do not reduce users to only `owner_name` and `unionName`. Store the raw tuple and a typed projection. The UI can use the projection, while future protocol corrections still have the original tuple.

### MapArmyTuple

`MapArmyTuple` is 33 slots. Slots 0..31 are business fields; slot 32 is `stateId` for duplicate-packet detection. Fields needed by map/march pages include state, userId, from/to WID, begin/end time, armyGroupId, targetType, reside/stay WID, facade fields, serious injury time, morale, realMarchId, buff list, obstacle WID, battle show string, and `stateId`. Sources: [`5026-5028-world-scene-fields.md:L476-L570`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L476-L570).

Army deletion has two distinct meanings:

- `[6][armyId] = [0]` directly removes the army.
- 5028 `[7]` removes an army from the current block only when `[20] == [2, blockId]`; the army is deleted only after no blocks still reference it.

Sources: [`5026-5028-world-scene-fields.md:L572-L592`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L572-L592).

Dashboard implication: battle-monitoring should track both logical army state and block membership. A simple current-row table can display active armies, but it cannot explain why an army disappeared unless block-scoped deletes and direct deletes are preserved as events.

### WORLD_CITY Chunk

World chunks live in slot `[14]` as `{wid: {chunkMsgType: payload}}`. `ChunkMsgType 0` is `WORLD_CITY`; official captures have a 21-slot tuple. It contains city/tile type, cityParam, userId, unionId, protection/guard/state timings, facade/name/build data, clan and linked-city fields, `stateId` at slot 19, and `viewRangeAdd` at slot 20. Sources: [`5026-5028-world-scene-fields.md:L768-L833`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L768-L833), [`5026-5028-world-scene-fields.md:L834-L868`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L834-L868).

5028 `[15] clearChunks` is not a list of WIDs. It is `{wid: [chunkMsgType...]}` and clears selected chunk subtypes for a WID. Source: [`5026-5028-world-scene-fields.md:L879-L892`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L879-L892).

Dashboard implication: map state should be modeled as per-WID chunk subrecords, not as one flat `map_cells` row. The UI needs at least a WORLD_CITY projection plus raw chunk payloads keyed by `wid` and `chunkMsgType`.

### realMarch

Slot `[29] realMarch` is `{realMarchId: RealMarchTuple}` with 14 slots: last/current/next WIDs, timing fields, pathId, unit time cost, march type, belongId, and morale timestamps. 5026 with `isAll=true` clears all realMarch entries before applying the packet; 5028 only overlays incoming entries. Source: [`5026-5028-world-scene-fields.md:L1011-L1057`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L1011-L1057).

Dashboard implication: the march page should not infer all movement from `MapArmyTuple` begin/end alone. `realMarch` can support smoother current-position rendering, next-hop timing, and morale/hunger display once persisted.

### Block Info

Official 5026 uses `[20] = null` and full block membership dictionaries in `[21] blockArmies`, `[22] blockShips`, and `[23] blockAssistArmies`. 5028 uses `[20] = [mode, blockId]`; mode 2 registers changed armies/ships/assist armies to the block and applies deletion arrays as block unlinks. Sources: [`5026-5028-world-scene-fields.md:L933-L974`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L933-L974).

Dashboard implication: block membership is important for viewport completeness and disappearance semantics. It should be persisted independently from the displayed active-army rows.

### 64-Bit Visual Field

Slot `[0] visualField` contains signed 64-bit bitmasks for 8x8 fog blocks. JavaScript `Number` cannot safely round-trip arbitrary signed int64 values. Sources: [`5026-5028-world-scene-fields.md:L277-L314`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L277-L314).

Dashboard implication: any React/Web/API redesign should either keep visual field values as strings/raw JSON text or parse them as `BigInt` client-side. Do not deserialize and reserialize these masks through JavaScript `Number`.

## Current stzb_watcher Parsing and Storage Gaps

### Android `LocalBattleMonitorParser`

The Android monitor parser correctly gates on `msgId` 5026/5028 and requires a 31-slot payload. It extracts slot `[1] mapUsers`, slot `[14] worldChunks` only as `LocalMapState(wid, stateCount, blockIndex)`, and slot `[6] armies` into `LocalTeamMove`. Sources: [`LocalBattleMonitorParser.kt:L8-L24`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalBattleMonitorParser.kt#L8-L24), [`LocalBattleMonitorParser.kt:L27-L140`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalBattleMonitorParser.kt#L27-L140).

It already handles some protocol-critical mechanics: pending assembly for 5026 until `marker > 0`, direct deletes via `[6][id] = [0]`, 5028 marker gating with `-999999999`, and mode-2 army block membership. Source: [`LocalBattleMonitorParser.kt:L244-L329`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalBattleMonitorParser.kt#L244-L329).

Remaining gaps for Web map/march/monitor pages:

- No typed `WORLD_CITY` parsing from `[14][wid]["0"]`; only the number of chunk subtypes is retained.
- No `realMarch` parsing from slot `[29]`.
- No `visualField`, `unions`, `warShips`, `assistArmies`, `armyGroups`, `shortMessages`, `clearChunks`, or observed area persistence.
- Block membership is tracked only for armies, not ships or assist armies.
- Snapshot persistence stores the current move set, not the packet/event history or raw slot payloads.

### Android `LocalAuxiliaryParser`

`LocalAuxiliaryParser` only saves map-cell rows for `msgId == "5026"` and never applies 5028 map chunk changes or clearChunks. Sources: [`LocalAuxiliaryParser.kt:L16-L22`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalAuxiliaryParser.kt#L16-L22), [`LocalAuxiliaryParser.kt:L83-L89`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalAuxiliaryParser.kt#L83-L89).

Its map-cell extraction recursively walks the entire decoded payload. Any numeric-key JSON object can be treated as a map-cell source, and string-first arrays become pseudo cells using tuple slot 1 as WID. That shape matches `MapUserTuple` as well as old cell rows, so the parser can misclassify `mapUsers` as `map_cells` unless later overwritten. Sources: [`LocalAuxiliaryParser.kt:L224-L263`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalAuxiliaryParser.kt#L224-L263), [`LocalAuxiliaryParser.kt:L265-L294`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalAuxiliaryParser.kt#L265-L294), [`5026-5028-world-scene-fields.md:L317-L355`](file:///Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md#L317-L355).

The typed `LocalMapCell` row persists only WID, derived coordinates, cell type, config/building ID, owner name, city name, parent WID, and source message. Source: [`LocalAuxiliaryParser.kt:L207-L220`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalAuxiliaryParser.kt#L207-L220). This is too lossy for a protocol-accurate map page because `WORLD_CITY` includes owner user ID, union ID, timings, stateId, view range, facade/build strings, clan/link fields, and raw subtype payloads.

### `Local13A2Parser` and Legacy 13a2/13a4 Paths

`Local13A2Parser` is useful for existing battlefield/team insight because it infers subject dictionaries, teams, cell-team maps, cell details, and area range from a looser payload. Source: [`Local13A2Parser.kt:L8-L55`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/Local13A2Parser.kt#L8-L55). It is not a replacement for canonical 5026/5028 world-scene parsing because it identifies structures heuristically instead of by fixed slot indexes.

The PC writer similarly treats old `000013a2` and `000013a4` folders as sources: 13a2 is parsed into `map_cells`; 13a4 is parsed into battle-monitor events and pushed to SSE. Sources: [`realtime_writer.py:L2328-L2374`](file:///Users/bytedance/stzb_watcher/realtime_writer.py#L2328-L2374), [`realtime_writer.py:L2588-L2626`](file:///Users/bytedance/stzb_watcher/realtime_writer.py#L2588-L2626).

`parse_battle_monitor_13a4` accepts any list, scans all dictionary blocks, infers subjects/map states/team moves by value shape, then reads marker from slot 18 and context from slot 20. It does not enforce 31 slots or implement 5026 multiframe assembly, 5028 serverOrderId gating, direct/block deletes, realMarch, or typed WORLD_CITY chunk semantics. Source: [`realtime_writer.py:L178-L297`](file:///Users/bytedance/stzb_watcher/realtime_writer.py#L178-L297).

### Database Shape

Current Android storage has:

- `battle_monitor_moves` as a current flattened move table keyed by `team_id`.
- `map_cells` as a minimal flattened tile/city table.
- `march_events` for `msgId=301`, not protocol slot `[29] realMarch`.

Sources: [`LocalStzbDatabase.kt:L78-L104`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalStzbDatabase.kt#L78-L104), [`LocalStzbDatabase.kt:L145-L158`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalStzbDatabase.kt#L145-L158), [`LocalStzbDatabase.kt:L472-L480`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalStzbDatabase.kt#L472-L480).

`saveBattleMonitor` deletes and rewrites all `battle_monitor_moves` on each saved current snapshot. Source: [`LocalStzbDatabase.kt:L686-L735`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalStzbDatabase.kt#L686-L735). That is acceptable for "current active monitor" display, but it loses the packet trail needed for debugging deltas, disappearance reasons, and future replay.

Map loaders return the minimal `map_cells` projection and type stats. Sources: [`LocalStzbDatabase.kt:L2790-L2865`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalStzbDatabase.kt#L2790-L2865). Monitor move loaders expose flattened army fields but not realMarch or block provenance. Source: [`LocalStzbDatabase.kt:L1241-L1288`](file:///Users/bytedance/stzb_watcher/astzb/app/src/main/java/com/example/myapplication/LocalStzbDatabase.kt#L1241-L1288).

## Current Web/API Surface

The Flask API exposes legacy map endpoints:

- `/api/map_cells`: returns up to 500 rows from `map_cells` with optional `cell_type` and `city_name` filters.
- `/api/map_stats`: returns total cells, type/name distribution, and up to 500 named city rows.

Sources: [`api_server.py:L1730-L1773`](file:///Users/bytedance/stzb_watcher/api_server.py#L1730-L1773).

The static dashboard's map tab consumes only `/api/map_stats` and renders cards, type bars, and a named-city table. Source: [`static/app2.js:L1048-L1078`](file:///Users/bytedance/stzb_watcher/static/app2.js#L1048-L1078). It does not request viewport tiles, armies, block membership, `realMarch`, or raw chunk data.

The live channel already exists: `/api/stream` replays recent events and then streams each new event via SSE. Source: [`api_server.py:L2416-L2454`](file:///Users/bytedance/stzb_watcher/api_server.py#L2416-L2454). The static frontend listens for `battle_monitor_13a4` SSE events. Source: [`static/app1.js:L283-L293`](file:///Users/bytedance/stzb_watcher/static/app1.js#L283-L293).

Current battle monitor APIs are file-scan based:

- `/api/battle_monitor_13a2` scans `capture_new/**/000013a2/*_plain_str.txt`.
- `/api/battle_monitor` scans the active capture directory's `000013a4` folder and parses the latest plain/json file.

Sources: [`api_server.py:L2457-L2491`](file:///Users/bytedance/stzb_watcher/api_server.py#L2457-L2491), [`api_server.py:L3086-L3162`](file:///Users/bytedance/stzb_watcher/api_server.py#L3086-L3162). The static page polls both monitor endpoints every 5 seconds in addition to SSE. Source: [`static/app2.js:L1038-L1046`](file:///Users/bytedance/stzb_watcher/static/app2.js#L1038-L1046).

The battlefield page calls `/api/battle_field` and `/api/battle_queue` and renders aggregate cards for active battlefield cities, nearby member counts, queue counts, target-city counts, and total power. Sources: [`api_server.py:L3165-L3208`](file:///Users/bytedance/stzb_watcher/api_server.py#L3165-L3208), [`static/app2.js:L2279-L2302`](file:///Users/bytedance/stzb_watcher/static/app2.js#L2279-L2302). The current `/api/battle_field` query does not join live `WORLD_CITY` details; it sets `city_name`, `x`, `y`, and `cell_type` to empty/null placeholders.

## Actionable Recommendations for Future React/Web/API Redesign

### 1. Build a protocol-first read model before redesigning UI widgets

Add a parser module that accepts decoded 5026/5028 JSON text and emits typed domain events:

- Validate top-level length exactly 31.
- Assemble 5026 multiframe `[14]` chunks until `[18] > 0`.
- Apply 5028 only when allowed by the latest completed 5026 `serverOrderId`, with `-999999999` bypass support.
- Parse and preserve raw tuples for `MapUserTuple`, `MapArmyTuple`, `WORLD_CITY`, and `realMarch`.
- Apply direct army deletes and block-scoped deletes separately.
- Preserve unknown/reserved slots as raw JSON for later protocol correction.

This should replace shape-scanning for world-scene data in `LocalAuxiliaryParser` and `realtime_writer`.

### 2. Store raw packets plus typed projections

Minimum durable tables or equivalents:

- `world_scene_packets`: cmd, direction/source, observedAt, payload length, serverOrderId, full/intermediate/final flags, raw payload text/hash.
- `world_map_users`: userId, name, wid, unionId, force, union/clan detail fields, raw tuple.
- `world_unions`: unionId, force, name.
- `world_tiles`: wid, row, col, cityType, cityParam, userId, unionId, state timing fields, force, clan/link fields, stateId, viewRangeAdd, raw WORLD_CITY tuple.
- `world_tile_chunks`: wid, chunkMsgType, raw payload, source sequence, clear/apply status.
- `world_armies`: armyId, tuple fields from `MapArmyTuple`, stateId, source sequence, raw tuple.
- `world_army_blocks`: armyId, blockId, last source sequence.
- `world_real_marches`: realMarchId, 14 realMarch fields, source sequence, raw tuple.
- Optional later: war ships, assist armies, army groups, short messages, visual field masks.

Keep current `map_cells` and `battle_monitor_moves` as compatibility views or materialized read models, not as the only source of truth.

### 3. Expose viewport and stream APIs instead of fixed 500-row lists

Suggested read-only endpoints:

- `GET /api/world/viewport?rowUp=&rowDown=&colLeft=&colRight=&include=tiles,armies,marches,users`
- `GET /api/world/armies?state=&owner=&within=rowUp,rowDown,colLeft,colRight`
- `GET /api/world/marches?active=1`
- `GET /api/world/users?ids=...`
- `GET /api/world/events?sinceSeq=...`
- Existing `/api/stream` can carry domain event types such as `world_snapshot_complete`, `world_tile_changes`, `world_army_changes`, `world_real_march_changes`, and `world_packet_gap`.

The current `/api/map_cells` and `/api/map_stats` limits are too coarse for a real map page. They can remain for legacy dashboards, but React should query by viewport and receive typed rows plus source freshness.

### 4. Redesign map UI around protocol domains

For the map page:

- Use row/col terminology everywhere and derive WID explicitly.
- Render static terrain/resources from a versioned static map source when available.
- Overlay live `WORLD_CITY` chunks for ownership, city state, protect/guard timers, names, linked city state, view range, and raw subtype badges.
- Display chunk freshness and whether data came from a completed 5026 snapshot or a 5028 delta.
- Do not route visual-field masks through JS `Number`; keep masks as strings or BigInt-safe values.

### 5. Redesign march UI around `MapArmyTuple` plus `realMarch`

For the march page:

- Use `MapArmyTuple` for army owner, from/to, state, target type, begin/end time, stay/reside WID, morale, buffs, obstacle WID, battle show, and `stateId`.
- Use `realMarch` for last/current/next WID, next-hop timing, pathId, unit time cost, march type, belongId, and morale timing.
- Show direct delete versus block unlink reason in event history.
- Keep `msgId=301` march events as auxiliary context only; they are not a substitute for slot `[29] realMarch`.

### 6. Redesign battlefield monitoring as correlation, not file scanning

For battlefield monitoring:

- Drive current armies from normalized 5026/5028 state rather than scanning only latest `000013a4`.
- Correlate armies to battle reports by `armyId`, owner, target WID, time window, and `army_hero_type`.
- Join battlefield rows with live `WORLD_CITY` details so target city names, coordinates, city types, owner/union, and state timers are not empty placeholders.
- Retain the current battle-monitor card UI idea, but feed it from durable world-state projections and SSE deltas.

### 7. Suggested migration order

1. Add parser tests from the protocol docs for 31-slot 5026/5028, `MapUserTuple`, `MapArmyTuple`, `WORLD_CITY`, `realMarch`, block deletes, and serverOrderId gating.
2. Add raw-packet and typed-projection storage without changing existing UI.
3. Backfill compatibility views for `map_cells` and `battle_monitor_moves`.
4. Add read-only viewport/march/events APIs and route the static dashboard to them.
5. Build the future React map/march/monitor pages on the new APIs.
6. Retire filesystem scanning for 13a2/13a4 after Android/local packet ingestion proves equivalent or better.
