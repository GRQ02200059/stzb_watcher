# World Scene Read Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a protocol-first 5026/5028 world-scene read model for map, march, and battlefield-monitoring pages.

**Architecture:** Add a focused `world_scene/` Python package that parses decoded 5026/5028 JSON into typed domain events, persists raw packets plus projections in SQLite, and exposes read-only Flask APIs. Keep existing `map_cells` and `battle_monitor_moves` as compatibility surfaces while new APIs read from normalized world-scene tables.

**Tech Stack:** Python 3, Flask, SQLite, `unittest`, existing `api_server.py` and `realtime_writer.py`.

## Global Constraints

- Scope is map, march, and battlefield-monitoring improvements only.
- Automation, Agent control, and command execution are excluded.
- 5026 and 5028 payloads are fixed 31-slot JSON arrays; reject other lengths.
- WID conversion must use `row = wid / 10000`, `col = wid % 10000`.
- Preserve raw tuples for protocol correction.
- JavaScript-facing APIs must not force signed int64 visual-field masks through JS `Number`.
- Existing legacy endpoints remain usable while new read model is introduced.

---

## File Structure

- Create `world_scene/__init__.py`: package exports.
- Create `world_scene/models.py`: dataclasses for packets, users, tiles, armies, real marches, and events.
- Create `world_scene/parser.py`: 5026/5028 parser, multiframe assembly, 5028 gating, delete semantics.
- Create `world_scene/store.py`: SQLite schema and upsert/query logic.
- Create `world_scene/api.py`: Flask blueprint/register function for read-only APIs.
- Modify `api_server.py`: initialize/register the new API and ensure schema.
- Modify `realtime_writer.py`: call the parser/store for decoded 5026/5028 packets while preserving existing behavior.
- Test `test/test_world_scene_parser.py`: protocol parser unit tests.
- Test `test/test_world_scene_store.py`: SQLite persistence tests.
- Test `test/test_world_scene_api.py`: Flask endpoint tests.

---

### Task 1: Protocol Parser Core

**Files:**
- Create: `world_scene/__init__.py`
- Create: `world_scene/models.py`
- Create: `world_scene/parser.py`
- Test: `test/test_world_scene_parser.py`

**Interfaces:**
- Produces: `parse_world_scene_packet(cmd_id: int, decoded_text: str, source: str, observed_at_ms: int) -> WorldScenePacket`
- Produces: `WorldSceneAssembler.apply(packet: WorldScenePacket) -> WorldSceneApplyResult`
- Consumes: only decoded JSON text and metadata.

- [ ] **Step 1: Write failing parser tests**

```python
# test/test_world_scene_parser.py
import unittest
from world_scene.parser import WorldSceneAssembler, parse_world_scene_packet


def world_payload(*, marker=1, armies=None, chunks=None, real_march=None):
    slots = [{} for _ in range(31)]
    slots[1] = {"42": ["主公", 10001, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, [1005, 0, "同盟"], None, None, 0, 0, 0, 0, 0, 0, "", "", 0, 0]}
    slots[3] = {"1005": [1005, 0, "同盟"]}
    slots[6] = armies or {}
    slots[7] = []
    slots[8] = {}
    slots[9] = []
    slots[10] = {}
    slots[11] = []
    slots[12] = {}
    slots[13] = {}
    slots[14] = chunks or {}
    slots[15] = {}
    slots[16] = {}
    slots[17] = None
    slots[18] = marker
    slots[19] = {}
    slots[20] = None
    slots[21] = {}
    slots[22] = {}
    slots[23] = {}
    slots[24] = {}
    slots[25] = []
    slots[26] = []
    slots[27] = []
    slots[28] = []
    slots[29] = real_march or {}
    slots[30] = None
    return slots


class WorldSceneParserTest(unittest.TestCase):
    def test_rejects_non_31_slot_payload(self):
        with self.assertRaises(ValueError):
            parse_world_scene_packet(5026, "[1,2,3]", "fixture", 1000)

    def test_parses_army_world_city_and_real_march(self):
        payload = world_payload(
            armies={"1001": [1, 42, 10001, 10004, 1700000000, 1700000030, 0, 0, 0, 0, 10001, 0, 0, 0, 0, "", "1,2,3", "", "", None, None, 0, 0, 0, 0, 0, 0, 100, 9001, "501,502", 0, "show", 77]},
            chunks={"10004": {"0": [1, 0, 42, 1005, 0, "facade", "土地名", 0, 0, 0, 0, 0, 0, "build", 0, 0, 0, 0, 0, 88, 2]}},
            real_march={"9001": [10001, 10002, 10004, 1700000000, 1700000010, 1700000030, 123, 5, 1, 42, 0, 100, 0, 0]},
        )
        packet = parse_world_scene_packet(5026, repr(payload), "fixture", 1000)
        self.assertEqual(packet.server_order_id, 1)
        self.assertEqual(packet.armies[1001].army_id, 1001)
        self.assertEqual(packet.armies[1001].state_id, 77)
        self.assertEqual(packet.tiles[10004].state_id, 88)
        self.assertEqual(packet.real_marches[9001].next_wid, 10004)

    def test_assembler_waits_for_final_5026_frame(self):
        assembler = WorldSceneAssembler()
        mid = parse_world_scene_packet(5026, repr(world_payload(marker=0)), "mid", 1)
        final = parse_world_scene_packet(5026, repr(world_payload(marker=8)), "final", 2)
        self.assertFalse(assembler.apply(mid).snapshot_complete)
        self.assertTrue(assembler.apply(final).snapshot_complete)
        self.assertEqual(assembler.last_completed_server_order_id, 8)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests to verify failure**

Run: `python -m unittest test.test_world_scene_parser -v`

Expected: FAIL with `ModuleNotFoundError: No module named 'world_scene'`.

- [ ] **Step 3: Implement dataclasses and parser**

```python
# world_scene/models.py
from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class WorldMapUser:
    user_id: int
    name: str
    role_id: int
    union_id: int
    union_name: str
    raw: list[Any]


@dataclass(frozen=True)
class WorldTile:
    wid: int
    row: int
    col: int
    city_type: int
    city_param: int
    user_id: int
    union_id: int
    protect_end_time: int
    name: str
    belong_city: int
    world_city_state: int
    guard_end_time: int
    force: int
    state_id: int | None
    view_range_add: int
    raw_world_city: list[Any]


@dataclass(frozen=True)
class WorldArmy:
    army_id: int
    state: int
    user_id: int
    wid_from: int
    wid_to: int
    begin_time: int
    end_time: int
    target_type: int
    reside_wid: int
    stay_wid: int
    army_hero_type: str
    morale: int
    real_march_id: int
    buff_ids: str
    obstacle_wid: int
    battle_show: str
    state_id: int | None
    raw: list[Any]


@dataclass(frozen=True)
class WorldRealMarch:
    real_march_id: int
    last_wid: int
    current_wid: int
    next_wid: int
    start_time: int
    next_time: int
    end_time: int
    path_id: int
    unit_time_cost: int
    march_type: int
    belong_id: int
    raw: list[Any]


@dataclass(frozen=True)
class WorldScenePacket:
    cmd_id: int
    source: str
    observed_at_ms: int
    server_order_id: int
    payload_len: int
    visual_field_raw: dict[str, Any]
    users: dict[int, WorldMapUser] = field(default_factory=dict)
    unions: dict[int, tuple[int, int, str]] = field(default_factory=dict)
    armies: dict[int, WorldArmy] = field(default_factory=dict)
    direct_deleted_army_ids: tuple[int, ...] = ()
    block_deleted_army_ids: tuple[int, ...] = ()
    block_info: tuple[int, int] | None = None
    block_armies: dict[int, tuple[int, ...]] = field(default_factory=dict)
    tiles: dict[int, WorldTile] = field(default_factory=dict)
    clear_chunks: dict[int, tuple[str, ...]] = field(default_factory=dict)
    real_marches: dict[int, WorldRealMarch] = field(default_factory=dict)
    raw_payload: str = ""


@dataclass(frozen=True)
class WorldSceneApplyResult:
    accepted: bool
    snapshot_complete: bool
    reason: str = ""
    packet: WorldScenePacket | None = None
```

```python
# world_scene/parser.py
import ast
import json
import re
from typing import Any

from .models import (
    WorldArmy, WorldMapUser, WorldRealMarch, WorldSceneApplyResult,
    WorldScenePacket, WorldTile,
)


SPECIAL_ORDER_ID = -999999999


def _load_payload(decoded_text: str) -> list[Any]:
    text = decoded_text.strip().rstrip("\x00").strip()
    text = re.sub(r"(?<=[{,])\s*(\d+)\s*(?=:)", r'"\1"', text)
    try:
        value = json.loads(text)
    except json.JSONDecodeError:
        value = ast.literal_eval(text)
    if not isinstance(value, list) or len(value) != 31:
        raise ValueError("world scene payload must be a 31-slot array")
    return value


def _as_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except Exception:
        return default


def _entries(value: Any):
    if not isinstance(value, dict):
        return
    for key, item in value.items():
        ident = _as_int(key)
        if ident > 0:
            yield ident, item


def parse_world_scene_packet(cmd_id: int, decoded_text: str, source: str, observed_at_ms: int) -> WorldScenePacket:
    payload = _load_payload(decoded_text)
    users = {}
    for user_id, raw in _entries(payload[1]):
        if isinstance(raw, list) and len(raw) >= 25:
            extra = raw[12] if len(raw) > 12 and isinstance(raw[12], list) else []
            users[user_id] = WorldMapUser(
                user_id=user_id,
                name=str(raw[0] or ""),
                role_id=_as_int(raw[1]),
                union_id=_as_int(raw[2]),
                union_name=str(extra[2] if len(extra) > 2 else ""),
                raw=raw,
            )
    unions = {
        union_id: (_as_int(raw[0]), _as_int(raw[1]), str(raw[2] if len(raw) > 2 else ""))
        for union_id, raw in _entries(payload[3])
        if isinstance(raw, list) and len(raw) >= 3
    }
    armies = {}
    direct_deleted = []
    for army_id, raw in _entries(payload[6]):
        if not isinstance(raw, list) or not raw:
            continue
        if _as_int(raw[0], -1) == 0:
            direct_deleted.append(army_id)
            continue
        if len(raw) < 32:
            continue
        armies[army_id] = WorldArmy(
            army_id=army_id,
            state=_as_int(raw[0]),
            user_id=_as_int(raw[1]),
            wid_from=_as_int(raw[2]),
            wid_to=_as_int(raw[3]),
            begin_time=_as_int(raw[4]),
            end_time=_as_int(raw[5]),
            target_type=_as_int(raw[9]),
            reside_wid=_as_int(raw[10]),
            stay_wid=_as_int(raw[11]),
            army_hero_type=str(raw[16] or ""),
            morale=_as_int(raw[27]),
            real_march_id=_as_int(raw[28]),
            buff_ids=str(raw[29] or ""),
            obstacle_wid=_as_int(raw[30]),
            battle_show=str(raw[31] or ""),
            state_id=None if len(raw) <= 32 or raw[32] is None else _as_int(raw[32]),
            raw=raw,
        )
    tiles = {}
    for wid, chunk_map in _entries(payload[14]):
        if isinstance(chunk_map, dict):
            raw = chunk_map.get("0") or chunk_map.get(0)
            if isinstance(raw, list) and len(raw) >= 21:
                tiles[wid] = WorldTile(
                    wid=wid,
                    row=wid // 10000,
                    col=wid % 10000,
                    city_type=_as_int(raw[0]),
                    city_param=_as_int(raw[1]),
                    user_id=_as_int(raw[2]),
                    union_id=_as_int(raw[3]),
                    protect_end_time=_as_int(raw[4]),
                    name=str(raw[6] or ""),
                    belong_city=_as_int(raw[7]),
                    world_city_state=_as_int(raw[8]),
                    guard_end_time=_as_int(raw[9]),
                    force=_as_int(raw[12]),
                    state_id=None if raw[19] is None else _as_int(raw[19]),
                    view_range_add=_as_int(raw[20]),
                    raw_world_city=raw,
                )
    real_marches = {}
    for real_id, raw in _entries(payload[29]):
        if isinstance(raw, list) and len(raw) >= 14:
            real_marches[real_id] = WorldRealMarch(
                real_march_id=real_id,
                last_wid=_as_int(raw[0]),
                current_wid=_as_int(raw[1]),
                next_wid=_as_int(raw[2]),
                start_time=_as_int(raw[3]),
                next_time=_as_int(raw[4]),
                end_time=_as_int(raw[5]),
                path_id=_as_int(raw[6]),
                unit_time_cost=_as_int(raw[7]),
                march_type=_as_int(raw[8]),
                belong_id=_as_int(raw[9]),
                raw=raw,
            )
    block_info = None
    if isinstance(payload[20], list) and len(payload[20]) >= 2:
        block_info = (_as_int(payload[20][0]), _as_int(payload[20][1]))
    return WorldScenePacket(
        cmd_id=cmd_id,
        source=source,
        observed_at_ms=observed_at_ms,
        server_order_id=_as_int(payload[18]),
        payload_len=len(payload),
        visual_field_raw=payload[0] if isinstance(payload[0], dict) else {},
        users=users,
        unions=unions,
        armies=armies,
        direct_deleted_army_ids=tuple(direct_deleted),
        block_deleted_army_ids=tuple(_as_int(x) for x in payload[7] if _as_int(x) > 0) if isinstance(payload[7], list) else (),
        block_info=block_info,
        block_armies={
            block: tuple(_as_int(x) for x in ids if _as_int(x) > 0)
            for block, ids in _entries(payload[21])
            if isinstance(ids, list)
        },
        tiles=tiles,
        clear_chunks={
            wid: tuple(str(x) for x in values)
            for wid, values in _entries(payload[15])
            if isinstance(values, list)
        },
        real_marches=real_marches,
        raw_payload=decoded_text,
    )


class WorldSceneAssembler:
    def __init__(self) -> None:
        self.last_completed_server_order_id = -1
        self._assembling_5026 = False

    def apply(self, packet: WorldScenePacket) -> WorldSceneApplyResult:
        if packet.cmd_id == 5026:
            self._assembling_5026 = packet.server_order_id <= 0
            if packet.server_order_id > 0:
                self.last_completed_server_order_id = packet.server_order_id
                return WorldSceneApplyResult(True, True, packet=packet)
            return WorldSceneApplyResult(True, False, packet=packet)
        if packet.cmd_id == 5028 and packet.server_order_id != SPECIAL_ORDER_ID:
            if packet.server_order_id <= self.last_completed_server_order_id:
                return WorldSceneApplyResult(False, False, "STALE_5028", packet)
        return WorldSceneApplyResult(True, False, packet=packet)
```

```python
# world_scene/__init__.py
from .parser import WorldSceneAssembler, parse_world_scene_packet

__all__ = ["WorldSceneAssembler", "parse_world_scene_packet"]
```

- [ ] **Step 4: Run parser tests**

Run: `python -m unittest test.test_world_scene_parser -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add world_scene/__init__.py world_scene/models.py world_scene/parser.py test/test_world_scene_parser.py
git commit -m "feat: parse world scene packets"
```

---

### Task 2: SQLite Projections

**Files:**
- Create: `world_scene/store.py`
- Test: `test/test_world_scene_store.py`

**Interfaces:**
- Consumes: `WorldScenePacket`
- Produces: `WorldSceneStore.ensure_schema() -> None`
- Produces: `WorldSceneStore.apply_packet(packet: WorldScenePacket) -> int`
- Produces: `WorldSceneStore.viewport(row_up: int, row_down: int, col_left: int, col_right: int) -> dict`
- Produces: `WorldSceneStore.active_armies() -> list[dict]`

- [ ] **Step 1: Write failing store tests**

```python
# test/test_world_scene_store.py
import sqlite3
import unittest

from test.test_world_scene_parser import world_payload
from world_scene.parser import parse_world_scene_packet
from world_scene.store import WorldSceneStore


class WorldSceneStoreTest(unittest.TestCase):
    def test_apply_packet_persists_tiles_armies_and_marches(self):
        conn = sqlite3.connect(":memory:")
        conn.row_factory = sqlite3.Row
        store = WorldSceneStore(conn)
        store.ensure_schema()
        packet = parse_world_scene_packet(
            5026,
            repr(world_payload(
                armies={"1001": [1, 42, 10001, 10004, 1, 9, 0, 0, 0, 0, 10001, 0, 0, 0, 0, "", "1,2,3", "", "", None, None, 0, 0, 0, 0, 0, 0, 100, 9001, "", 0, "", 77]},
                chunks={"10004": {"0": [1, 0, 42, 1005, 0, "", "土地名", 0, 0, 0, 0, 0, 0, "", 0, 0, 0, 0, 0, 88, 2]}},
                real_march={"9001": [10001, 10002, 10004, 1, 2, 9, 123, 5, 1, 42, 0, 100, 0, 0]},
            )),
            "fixture",
            1000,
        )
        seq = store.apply_packet(packet)
        self.assertEqual(seq, 1)
        view = store.viewport(1, 2, 1, 10)
        self.assertEqual(view["tiles"][0]["wid"], 10004)
        self.assertEqual(store.active_armies()[0]["army_id"], 1001)
        self.assertEqual(store.active_marches()[0]["real_march_id"], 9001)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests to verify failure**

Run: `python -m unittest test.test_world_scene_store -v`

Expected: FAIL with `ModuleNotFoundError: No module named 'world_scene.store'`.

- [ ] **Step 3: Implement store**

```python
# world_scene/store.py
import json
import sqlite3
from typing import Any

from .models import WorldScenePacket


class WorldSceneStore:
    def __init__(self, conn: sqlite3.Connection) -> None:
        self.conn = conn
        self.conn.row_factory = sqlite3.Row

    def ensure_schema(self) -> None:
        self.conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS world_scene_packets (
                seq INTEGER PRIMARY KEY AUTOINCREMENT,
                cmd_id INTEGER NOT NULL,
                source TEXT NOT NULL,
                observed_at_ms INTEGER NOT NULL,
                server_order_id INTEGER NOT NULL,
                payload_len INTEGER NOT NULL,
                raw_payload TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS world_map_users (
                user_id INTEGER PRIMARY KEY,
                name TEXT,
                role_id INTEGER,
                union_id INTEGER,
                union_name TEXT,
                raw_json TEXT,
                source_seq INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS world_unions (
                union_id INTEGER PRIMARY KEY,
                force INTEGER,
                name TEXT,
                source_seq INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS world_tiles (
                wid INTEGER PRIMARY KEY,
                row INTEGER NOT NULL,
                col INTEGER NOT NULL,
                city_type INTEGER,
                city_param INTEGER,
                user_id INTEGER,
                union_id INTEGER,
                protect_end_time INTEGER,
                name TEXT,
                belong_city INTEGER,
                world_city_state INTEGER,
                guard_end_time INTEGER,
                force INTEGER,
                state_id INTEGER,
                view_range_add INTEGER,
                raw_world_city TEXT,
                source_seq INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS world_armies (
                army_id INTEGER PRIMARY KEY,
                state INTEGER,
                user_id INTEGER,
                wid_from INTEGER,
                wid_to INTEGER,
                begin_time INTEGER,
                end_time INTEGER,
                target_type INTEGER,
                reside_wid INTEGER,
                stay_wid INTEGER,
                army_hero_type TEXT,
                morale INTEGER,
                real_march_id INTEGER,
                buff_ids TEXT,
                obstacle_wid INTEGER,
                battle_show TEXT,
                state_id INTEGER,
                raw_json TEXT,
                source_seq INTEGER NOT NULL,
                deleted_at_seq INTEGER
            );
            CREATE TABLE IF NOT EXISTS world_real_marches (
                real_march_id INTEGER PRIMARY KEY,
                last_wid INTEGER,
                current_wid INTEGER,
                next_wid INTEGER,
                start_time INTEGER,
                next_time INTEGER,
                end_time INTEGER,
                path_id INTEGER,
                unit_time_cost INTEGER,
                march_type INTEGER,
                belong_id INTEGER,
                raw_json TEXT,
                source_seq INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_world_tiles_row_col ON world_tiles(row, col);
            CREATE INDEX IF NOT EXISTS idx_world_armies_user ON world_armies(user_id);
            """
        )
        self.conn.commit()

    def apply_packet(self, packet: WorldScenePacket) -> int:
        cur = self.conn.execute(
            """
            INSERT INTO world_scene_packets(cmd_id,source,observed_at_ms,server_order_id,payload_len,raw_payload)
            VALUES(?,?,?,?,?,?)
            """,
            (packet.cmd_id, packet.source, packet.observed_at_ms, packet.server_order_id, packet.payload_len, packet.raw_payload),
        )
        seq = int(cur.lastrowid)
        for user in packet.users.values():
            self.conn.execute(
                """
                INSERT INTO world_map_users(user_id,name,role_id,union_id,union_name,raw_json,source_seq)
                VALUES(?,?,?,?,?,?,?)
                ON CONFLICT(user_id) DO UPDATE SET
                  name=excluded.name, role_id=excluded.role_id, union_id=excluded.union_id,
                  union_name=excluded.union_name, raw_json=excluded.raw_json, source_seq=excluded.source_seq
                """,
                (user.user_id, user.name, user.role_id, user.union_id, user.union_name, json.dumps(user.raw, ensure_ascii=False), seq),
            )
        for union_id, (_, force, name) in packet.unions.items():
            self.conn.execute(
                """
                INSERT INTO world_unions(union_id,force,name,source_seq)
                VALUES(?,?,?,?)
                ON CONFLICT(union_id) DO UPDATE SET force=excluded.force,name=excluded.name,source_seq=excluded.source_seq
                """,
                (union_id, force, name, seq),
            )
        for tile in packet.tiles.values():
            self.conn.execute(
                """
                INSERT INTO world_tiles(wid,row,col,city_type,city_param,user_id,union_id,protect_end_time,name,belong_city,world_city_state,guard_end_time,force,state_id,view_range_add,raw_world_city,source_seq)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(wid) DO UPDATE SET
                  row=excluded.row,col=excluded.col,city_type=excluded.city_type,city_param=excluded.city_param,
                  user_id=excluded.user_id,union_id=excluded.union_id,protect_end_time=excluded.protect_end_time,
                  name=excluded.name,belong_city=excluded.belong_city,world_city_state=excluded.world_city_state,
                  guard_end_time=excluded.guard_end_time,force=excluded.force,state_id=excluded.state_id,
                  view_range_add=excluded.view_range_add,raw_world_city=excluded.raw_world_city,source_seq=excluded.source_seq
                """,
                (tile.wid, tile.row, tile.col, tile.city_type, tile.city_param, tile.user_id, tile.union_id, tile.protect_end_time, tile.name, tile.belong_city, tile.world_city_state, tile.guard_end_time, tile.force, tile.state_id, tile.view_range_add, json.dumps(tile.raw_world_city, ensure_ascii=False), seq),
            )
        for army_id in packet.direct_deleted_army_ids:
            self.conn.execute("UPDATE world_armies SET deleted_at_seq=? WHERE army_id=?", (seq, army_id))
        for army in packet.armies.values():
            self.conn.execute(
                """
                INSERT INTO world_armies(army_id,state,user_id,wid_from,wid_to,begin_time,end_time,target_type,reside_wid,stay_wid,army_hero_type,morale,real_march_id,buff_ids,obstacle_wid,battle_show,state_id,raw_json,source_seq,deleted_at_seq)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)
                ON CONFLICT(army_id) DO UPDATE SET
                  state=excluded.state,user_id=excluded.user_id,wid_from=excluded.wid_from,wid_to=excluded.wid_to,
                  begin_time=excluded.begin_time,end_time=excluded.end_time,target_type=excluded.target_type,
                  reside_wid=excluded.reside_wid,stay_wid=excluded.stay_wid,army_hero_type=excluded.army_hero_type,
                  morale=excluded.morale,real_march_id=excluded.real_march_id,buff_ids=excluded.buff_ids,
                  obstacle_wid=excluded.obstacle_wid,battle_show=excluded.battle_show,state_id=excluded.state_id,
                  raw_json=excluded.raw_json,source_seq=excluded.source_seq,deleted_at_seq=NULL
                """,
                (army.army_id, army.state, army.user_id, army.wid_from, army.wid_to, army.begin_time, army.end_time, army.target_type, army.reside_wid, army.stay_wid, army.army_hero_type, army.morale, army.real_march_id, army.buff_ids, army.obstacle_wid, army.battle_show, army.state_id, json.dumps(army.raw, ensure_ascii=False), seq),
            )
        for march in packet.real_marches.values():
            self.conn.execute(
                """
                INSERT INTO world_real_marches(real_march_id,last_wid,current_wid,next_wid,start_time,next_time,end_time,path_id,unit_time_cost,march_type,belong_id,raw_json,source_seq)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(real_march_id) DO UPDATE SET
                  last_wid=excluded.last_wid,current_wid=excluded.current_wid,next_wid=excluded.next_wid,
                  start_time=excluded.start_time,next_time=excluded.next_time,end_time=excluded.end_time,
                  path_id=excluded.path_id,unit_time_cost=excluded.unit_time_cost,march_type=excluded.march_type,
                  belong_id=excluded.belong_id,raw_json=excluded.raw_json,source_seq=excluded.source_seq
                """,
                (march.real_march_id, march.last_wid, march.current_wid, march.next_wid, march.start_time, march.next_time, march.end_time, march.path_id, march.unit_time_cost, march.march_type, march.belong_id, json.dumps(march.raw, ensure_ascii=False), seq),
            )
        self.conn.commit()
        return seq

    def viewport(self, row_up: int, row_down: int, col_left: int, col_right: int) -> dict[str, Any]:
        rows = self.conn.execute(
            """
            SELECT * FROM world_tiles
            WHERE row BETWEEN ? AND ? AND col BETWEEN ? AND ?
            ORDER BY row, col
            """,
            (row_up, row_down, col_left, col_right),
        ).fetchall()
        return {"tiles": [dict(row) for row in rows]}

    def active_armies(self) -> list[dict[str, Any]]:
        rows = self.conn.execute(
            "SELECT * FROM world_armies WHERE deleted_at_seq IS NULL ORDER BY end_time, army_id"
        ).fetchall()
        return [dict(row) for row in rows]

    def active_marches(self) -> list[dict[str, Any]]:
        rows = self.conn.execute(
            "SELECT * FROM world_real_marches ORDER BY end_time, real_march_id"
        ).fetchall()
        return [dict(row) for row in rows]
```

- [ ] **Step 4: Run store tests**

Run: `python -m unittest test.test_world_scene_store -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add world_scene/store.py test/test_world_scene_store.py
git commit -m "feat: persist world scene projections"
```

---

### Task 3: Flask Read APIs

**Files:**
- Create: `world_scene/api.py`
- Modify: `api_server.py`
- Test: `test/test_world_scene_api.py`

**Interfaces:**
- Consumes: `WorldSceneStore`
- Produces: `register_world_scene_api(app, get_connection) -> None`
- Produces endpoints:
  - `GET /api/world/viewport`
  - `GET /api/world/armies`
  - `GET /api/world/marches`

- [ ] **Step 1: Write failing API tests**

```python
# test/test_world_scene_api.py
import sqlite3
import unittest
from flask import Flask

from test.test_world_scene_parser import world_payload
from world_scene.api import register_world_scene_api
from world_scene.parser import parse_world_scene_packet
from world_scene.store import WorldSceneStore


class WorldSceneApiTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.row_factory = sqlite3.Row
        self.store = WorldSceneStore(self.conn)
        self.store.ensure_schema()
        packet = parse_world_scene_packet(
            5026,
            repr(world_payload(
                armies={"1001": [1, 42, 10001, 10004, 1, 9, 0, 0, 0, 0, 10001, 0, 0, 0, 0, "", "1,2,3", "", "", None, None, 0, 0, 0, 0, 0, 0, 100, 9001, "", 0, "", 77]},
                chunks={"10004": {"0": [1, 0, 42, 1005, 0, "", "土地名", 0, 0, 0, 0, 0, 0, "", 0, 0, 0, 0, 0, 88, 2]}},
            )),
            "fixture",
            1000,
        )
        self.store.apply_packet(packet)
        app = Flask(__name__)
        register_world_scene_api(app, lambda: self.conn)
        self.client = app.test_client()

    def test_viewport_returns_tiles(self):
        response = self.client.get("/api/world/viewport?rowUp=1&rowDown=2&colLeft=1&colRight=10")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()["tiles"][0]["wid"], 10004)

    def test_armies_returns_active_rows(self):
        response = self.client.get("/api/world/armies")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()["armies"][0]["army_id"], 1001)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run API tests to verify failure**

Run: `python -m unittest test.test_world_scene_api -v`

Expected: FAIL with `ModuleNotFoundError: No module named 'world_scene.api'`.

- [ ] **Step 3: Implement API module**

```python
# world_scene/api.py
from flask import jsonify, request

from .store import WorldSceneStore


def _int_arg(name: str, default: int | None = None) -> int:
    raw = request.args.get(name)
    if raw is None:
        if default is None:
            raise ValueError(f"{name} is required")
        return default
    value = int(raw)
    if value < 0:
        raise ValueError(f"{name} must be non-negative")
    return value


def register_world_scene_api(app, get_connection):
    @app.route("/api/world/viewport")
    def api_world_viewport():
        try:
            row_up = _int_arg("rowUp")
            row_down = _int_arg("rowDown")
            col_left = _int_arg("colLeft")
            col_right = _int_arg("colRight")
        except (TypeError, ValueError) as error:
            return jsonify({"ok": False, "error": str(error)}), 400
        store = WorldSceneStore(get_connection())
        return jsonify({"ok": True, **store.viewport(row_up, row_down, col_left, col_right)})

    @app.route("/api/world/armies")
    def api_world_armies():
        store = WorldSceneStore(get_connection())
        return jsonify({"ok": True, "armies": store.active_armies()})

    @app.route("/api/world/marches")
    def api_world_marches():
        store = WorldSceneStore(get_connection())
        return jsonify({"ok": True, "marches": store.active_marches()})
```

- [ ] **Step 4: Register in `api_server.py`**

Add this import near the Flask app setup:

```python
from world_scene.api import register_world_scene_api
from world_scene.store import WorldSceneStore
```

After `app = Flask(...)` and `CORS(app)`, register:

```python
def _world_scene_connection():
    conn = get_db()
    WorldSceneStore(conn).ensure_schema()
    return conn

register_world_scene_api(app, _world_scene_connection)
```

- [ ] **Step 5: Run tests**

Run: `python -m unittest test.test_world_scene_api -v`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add world_scene/api.py api_server.py test/test_world_scene_api.py
git commit -m "feat: expose world scene read APIs"
```

---

### Task 4: Writer Integration

**Files:**
- Modify: `realtime_writer.py`
- Test: `test/test_world_scene_writer_integration.py`

**Interfaces:**
- Consumes: `parse_world_scene_packet`
- Consumes: `WorldSceneStore`
- Produces: `process_data` side effect persists 5026/5028 projections.

- [ ] **Step 1: Write failing integration test**

```python
# test/test_world_scene_writer_integration.py
import sqlite3
import unittest
from unittest.mock import patch

import realtime_writer
from test.test_world_scene_parser import world_payload


class WorldSceneWriterIntegrationTest(unittest.TestCase):
    def test_process_data_persists_world_scene_packet(self):
        conn = sqlite3.connect(":memory:")
        conn.row_factory = sqlite3.Row
        writer = realtime_writer.RealtimeWriter()
        with patch.object(realtime_writer, "get_db", return_value=conn):
            writer.process_data("000013a4", world_payload(marker=1), "fixture")
        row = conn.execute("SELECT COUNT(*) AS c FROM world_scene_packets").fetchone()
        self.assertEqual(row["c"], 1)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify failure**

Run: `python -m unittest test.test_world_scene_writer_integration -v`

Expected: FAIL because `world_scene_packets` is not created or populated.

- [ ] **Step 3: Add world-scene persistence helper to `realtime_writer.py`**

Add imports:

```python
from world_scene.parser import parse_world_scene_packet
from world_scene.store import WorldSceneStore
```

Add helper:

```python
def persist_world_scene_packet(conn, msg_type, data, fpath):
    cmd_id = int(msg_type, 16) if isinstance(msg_type, str) and msg_type.startswith("0000") else int(msg_type)
    if cmd_id not in (5026, 5028):
        return None
    text = data if isinstance(data, str) else json.dumps(data, ensure_ascii=False)
    packet = parse_world_scene_packet(cmd_id, text, os.path.basename(str(fpath or "")) or "live", int(time.time() * 1000))
    store = WorldSceneStore(conn)
    store.ensure_schema()
    return store.apply_packet(packet)
```

Call it at the top of `_dispatch`. The legacy folder names are hexadecimal command IDs:
`000013a2 == 5026` and `000013a4 == 5028`.

```python
        if msg_type in {"000013a2", "000013a4"}:
            try:
                persist_world_scene_packet(conn, "5026" if msg_type == "000013a2" else "5028", data, fpath)
            except Exception as e:
                print(f"[world_scene ERR] {msg_type}: {e}")
```

- [ ] **Step 4: Run integration test**

Run: `python -m unittest test.test_world_scene_writer_integration -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add realtime_writer.py test/test_world_scene_writer_integration.py
git commit -m "feat: persist world scene packets from writer"
```

---

### Task 5: Compatibility Views

**Files:**
- Modify: `world_scene/store.py`
- Test: `test/test_world_scene_store.py`

**Interfaces:**
- Consumes: normalized world tables.
- Produces: `WorldSceneStore.backfill_legacy_views() -> None`

- [ ] **Step 1: Add failing test for legacy compatibility**

Append to `WorldSceneStoreTest`:

```python
    def test_backfills_legacy_map_cells_and_monitor_moves(self):
        packet = parse_world_scene_packet(
            5026,
            repr(world_payload(
                armies={"1001": [1, 42, 10001, 10004, 1, 9, 0, 0, 0, 0, 10001, 0, 0, 0, 0, "", "1,2,3", "", "", None, None, 0, 0, 0, 0, 0, 0, 100, 9001, "", 0, "", 77]},
                chunks={"10004": {"0": [1, 0, 42, 1005, 0, "", "土地名", 0, 0, 0, 0, 0, 0, "", 0, 0, 0, 0, 0, 88, 2]}},
            )),
            "fixture",
            1000,
        )
        self.store.apply_packet(packet)
        self.store.backfill_legacy_views()
        self.assertEqual(self.conn.execute("SELECT city_name FROM map_cells WHERE wid=10004").fetchone()[0], "土地名")
        self.assertEqual(self.conn.execute("SELECT team_id FROM battle_monitor_moves WHERE team_id=1001").fetchone()[0], 1001)
```

- [ ] **Step 2: Run test to verify failure**

Run: `python -m unittest test.test_world_scene_store.WorldSceneStoreTest.test_backfills_legacy_map_cells_and_monitor_moves -v`

Expected: FAIL with missing method.

- [ ] **Step 3: Implement compatibility backfill**

Add method:

```python
    def backfill_legacy_views(self) -> None:
        self.conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS map_cells (
                wid INTEGER PRIMARY KEY,
                x INTEGER DEFAULT 0,
                y INTEGER DEFAULT 0,
                cell_type INTEGER DEFAULT 0,
                type_name TEXT,
                building_id INTEGER DEFAULT 0,
                owner_name TEXT,
                city_name TEXT,
                parent_wid INTEGER DEFAULT 0,
                source_msg_id TEXT,
                updated_at INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS battle_monitor_moves (
                team_id INTEGER PRIMARY KEY,
                move_type INTEGER,
                subject_id INTEGER,
                owner_uid INTEGER,
                owner_name TEXT,
                owner_union TEXT,
                from_wid INTEGER,
                to_wid INTEGER,
                current_wid INTEGER,
                from_xy TEXT,
                to_xy TEXT,
                current_xy TEXT,
                start_time INTEGER,
                arrive_time INTEGER,
                speed INTEGER,
                target_type INTEGER DEFAULT 0,
                reside_wid INTEGER DEFAULT 0,
                stay_wid INTEGER DEFAULT 0,
                army_hero_type TEXT,
                morale INTEGER DEFAULT 0,
                buff_ids TEXT,
                battle_show TEXT,
                state_id INTEGER,
                marker INTEGER,
                captured_at INTEGER NOT NULL
            );
            """
        )
        now = 0
        for row in self.conn.execute("SELECT * FROM world_tiles"):
            self.conn.execute(
                """
                INSERT INTO map_cells(wid,x,y,cell_type,type_name,building_id,owner_name,city_name,parent_wid,source_msg_id,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(wid) DO UPDATE SET
                  x=excluded.x,y=excluded.y,cell_type=excluded.cell_type,type_name=excluded.type_name,
                  owner_name=excluded.owner_name,city_name=excluded.city_name,updated_at=excluded.updated_at
                """,
                (row["wid"], row["row"], row["col"], row["city_type"], f"type{row['city_type']}", row["city_param"], "", row["name"], row["belong_city"], "world_scene", now),
            )
        for row in self.conn.execute("SELECT * FROM world_armies WHERE deleted_at_seq IS NULL"):
            def xy(wid):
                return "" if not wid else f"{wid // 10000},{wid % 10000}"
            current = row["stay_wid"] or row["reside_wid"]
            self.conn.execute(
                """
                INSERT INTO battle_monitor_moves(team_id,move_type,subject_id,owner_uid,owner_name,owner_union,from_wid,to_wid,current_wid,from_xy,to_xy,current_xy,start_time,arrive_time,speed,target_type,reside_wid,stay_wid,army_hero_type,morale,buff_ids,battle_show,state_id,marker,captured_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(team_id) DO UPDATE SET
                  move_type=excluded.move_type,subject_id=excluded.subject_id,from_wid=excluded.from_wid,
                  to_wid=excluded.to_wid,current_wid=excluded.current_wid,start_time=excluded.start_time,
                  arrive_time=excluded.arrive_time,morale=excluded.morale,buff_ids=excluded.buff_ids,
                  battle_show=excluded.battle_show,state_id=excluded.state_id,captured_at=excluded.captured_at
                """,
                (row["army_id"], row["state"], row["user_id"], row["user_id"], "", "", row["wid_from"], row["wid_to"], current, xy(row["wid_from"]), xy(row["wid_to"]), xy(current), row["begin_time"], row["end_time"], 0, row["target_type"], row["reside_wid"], row["stay_wid"], row["army_hero_type"], row["morale"], row["buff_ids"], row["battle_show"], row["state_id"], 0, now),
            )
        self.conn.commit()
```

- [ ] **Step 4: Run compatibility test**

Run: `python -m unittest test.test_world_scene_store.WorldSceneStoreTest.test_backfills_legacy_map_cells_and_monitor_moves -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add world_scene/store.py test/test_world_scene_store.py
git commit -m "feat: backfill legacy world scene views"
```

---

## Plan Self-Review

Spec coverage:
- 31-slot validation: Task 1.
- Multiframe and `serverOrderId`: Task 1.
- `MapUserTuple`, `MapArmyTuple`, `WORLD_CITY`, `realMarch`: Task 1 and Task 2.
- Raw packet plus typed projections: Task 2.
- Read-only viewport/march APIs: Task 3.
- Realtime writer integration: Task 4.
- Compatibility surfaces: Task 5.

No placeholders remain. All functions used later are introduced earlier with signatures.
