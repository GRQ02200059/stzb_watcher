import json
import sqlite3
from typing import Any, Dict, List

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
            INSERT INTO world_scene_packets(
                cmd_id, source, observed_at_ms, server_order_id, payload_len, raw_payload
            )
            VALUES(?,?,?,?,?,?)
            """,
            (
                packet.cmd_id,
                packet.source,
                packet.observed_at_ms,
                packet.server_order_id,
                packet.payload_len,
                packet.raw_payload,
            ),
        )
        seq = int(cur.lastrowid)
        self._upsert_users(packet, seq)
        self._upsert_unions(packet, seq)
        self._upsert_tiles(packet, seq)
        self._upsert_armies(packet, seq)
        self._upsert_real_marches(packet, seq)
        self.conn.commit()
        return seq

    def _upsert_users(self, packet: WorldScenePacket, seq: int) -> None:
        for user in packet.users.values():
            self.conn.execute(
                """
                INSERT INTO world_map_users(
                    user_id, name, role_id, union_id, union_name, raw_json, source_seq
                )
                VALUES(?,?,?,?,?,?,?)
                ON CONFLICT(user_id) DO UPDATE SET
                    name=excluded.name,
                    role_id=excluded.role_id,
                    union_id=excluded.union_id,
                    union_name=excluded.union_name,
                    raw_json=excluded.raw_json,
                    source_seq=excluded.source_seq
                """,
                (
                    user.user_id,
                    user.name,
                    user.role_id,
                    user.union_id,
                    user.union_name,
                    json.dumps(user.raw, ensure_ascii=False),
                    seq,
                ),
            )

    def _upsert_unions(self, packet: WorldScenePacket, seq: int) -> None:
        for union_id, (_, force, name) in packet.unions.items():
            self.conn.execute(
                """
                INSERT INTO world_unions(union_id, force, name, source_seq)
                VALUES(?,?,?,?)
                ON CONFLICT(union_id) DO UPDATE SET
                    force=excluded.force,
                    name=excluded.name,
                    source_seq=excluded.source_seq
                """,
                (union_id, force, name, seq),
            )

    def _upsert_tiles(self, packet: WorldScenePacket, seq: int) -> None:
        for tile in packet.tiles.values():
            self.conn.execute(
                """
                INSERT INTO world_tiles(
                    wid, row, col, city_type, city_param, user_id, union_id,
                    protect_end_time, name, belong_city, world_city_state,
                    guard_end_time, force, state_id, view_range_add,
                    raw_world_city, source_seq
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(wid) DO UPDATE SET
                    row=excluded.row,
                    col=excluded.col,
                    city_type=excluded.city_type,
                    city_param=excluded.city_param,
                    user_id=excluded.user_id,
                    union_id=excluded.union_id,
                    protect_end_time=excluded.protect_end_time,
                    name=excluded.name,
                    belong_city=excluded.belong_city,
                    world_city_state=excluded.world_city_state,
                    guard_end_time=excluded.guard_end_time,
                    force=excluded.force,
                    state_id=excluded.state_id,
                    view_range_add=excluded.view_range_add,
                    raw_world_city=excluded.raw_world_city,
                    source_seq=excluded.source_seq
                """,
                (
                    tile.wid,
                    tile.row,
                    tile.col,
                    tile.city_type,
                    tile.city_param,
                    tile.user_id,
                    tile.union_id,
                    tile.protect_end_time,
                    tile.name,
                    tile.belong_city,
                    tile.world_city_state,
                    tile.guard_end_time,
                    tile.force,
                    tile.state_id,
                    tile.view_range_add,
                    json.dumps(tile.raw_world_city, ensure_ascii=False),
                    seq,
                ),
            )

    def _upsert_armies(self, packet: WorldScenePacket, seq: int) -> None:
        for army_id in packet.direct_deleted_army_ids:
            self.conn.execute(
                "UPDATE world_armies SET deleted_at_seq=? WHERE army_id=?",
                (seq, army_id),
            )
        for army in packet.armies.values():
            self.conn.execute(
                """
                INSERT INTO world_armies(
                    army_id, state, user_id, wid_from, wid_to, begin_time,
                    end_time, target_type, reside_wid, stay_wid, army_hero_type,
                    morale, real_march_id, buff_ids, obstacle_wid, battle_show,
                    state_id, raw_json, source_seq, deleted_at_seq
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)
                ON CONFLICT(army_id) DO UPDATE SET
                    state=excluded.state,
                    user_id=excluded.user_id,
                    wid_from=excluded.wid_from,
                    wid_to=excluded.wid_to,
                    begin_time=excluded.begin_time,
                    end_time=excluded.end_time,
                    target_type=excluded.target_type,
                    reside_wid=excluded.reside_wid,
                    stay_wid=excluded.stay_wid,
                    army_hero_type=excluded.army_hero_type,
                    morale=excluded.morale,
                    real_march_id=excluded.real_march_id,
                    buff_ids=excluded.buff_ids,
                    obstacle_wid=excluded.obstacle_wid,
                    battle_show=excluded.battle_show,
                    state_id=excluded.state_id,
                    raw_json=excluded.raw_json,
                    source_seq=excluded.source_seq,
                    deleted_at_seq=NULL
                """,
                (
                    army.army_id,
                    army.state,
                    army.user_id,
                    army.wid_from,
                    army.wid_to,
                    army.begin_time,
                    army.end_time,
                    army.target_type,
                    army.reside_wid,
                    army.stay_wid,
                    army.army_hero_type,
                    army.morale,
                    army.real_march_id,
                    army.buff_ids,
                    army.obstacle_wid,
                    army.battle_show,
                    army.state_id,
                    json.dumps(army.raw, ensure_ascii=False),
                    seq,
                ),
            )

    def _upsert_real_marches(self, packet: WorldScenePacket, seq: int) -> None:
        for march in packet.real_marches.values():
            self.conn.execute(
                """
                INSERT INTO world_real_marches(
                    real_march_id, last_wid, current_wid, next_wid, start_time,
                    next_time, end_time, path_id, unit_time_cost, march_type,
                    belong_id, raw_json, source_seq
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(real_march_id) DO UPDATE SET
                    last_wid=excluded.last_wid,
                    current_wid=excluded.current_wid,
                    next_wid=excluded.next_wid,
                    start_time=excluded.start_time,
                    next_time=excluded.next_time,
                    end_time=excluded.end_time,
                    path_id=excluded.path_id,
                    unit_time_cost=excluded.unit_time_cost,
                    march_type=excluded.march_type,
                    belong_id=excluded.belong_id,
                    raw_json=excluded.raw_json,
                    source_seq=excluded.source_seq
                """,
                (
                    march.real_march_id,
                    march.last_wid,
                    march.current_wid,
                    march.next_wid,
                    march.start_time,
                    march.next_time,
                    march.end_time,
                    march.path_id,
                    march.unit_time_cost,
                    march.march_type,
                    march.belong_id,
                    json.dumps(march.raw, ensure_ascii=False),
                    seq,
                ),
            )

    def viewport(
        self, row_up: int, row_down: int, col_left: int, col_right: int
    ) -> Dict[str, List[Dict[str, Any]]]:
        rows = self.conn.execute(
            """
            SELECT * FROM world_tiles
            WHERE row BETWEEN ? AND ? AND col BETWEEN ? AND ?
            ORDER BY row, col
            """,
            (row_up, row_down, col_left, col_right),
        ).fetchall()
        return {"tiles": [dict(row) for row in rows]}

    def active_armies(self) -> List[Dict[str, Any]]:
        rows = self.conn.execute(
            """
            SELECT * FROM world_armies
            WHERE deleted_at_seq IS NULL
            ORDER BY end_time, army_id
            """
        ).fetchall()
        return [dict(row) for row in rows]

    def active_marches(self) -> List[Dict[str, Any]]:
        rows = self.conn.execute(
            "SELECT * FROM world_real_marches ORDER BY end_time, real_march_id"
        ).fetchall()
        return [dict(row) for row in rows]
