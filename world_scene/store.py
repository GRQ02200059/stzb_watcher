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
            CREATE TABLE IF NOT EXISTS world_visual_fields (
                source_seq INTEGER PRIMARY KEY,
                cmd_id INTEGER NOT NULL,
                server_order_id INTEGER NOT NULL,
                raw_json TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS world_scene_entities (
                category TEXT NOT NULL,
                entity_id INTEGER NOT NULL,
                raw_json TEXT NOT NULL,
                source_seq INTEGER NOT NULL,
                deleted_at_seq INTEGER,
                PRIMARY KEY(category, entity_id)
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
            CREATE INDEX IF NOT EXISTS idx_world_scene_entities_category ON world_scene_entities(category);
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
        self._insert_visual_field(packet, seq)
        self._upsert_users(packet, seq)
        self._upsert_unions(packet, seq)
        self._upsert_tiles(packet, seq)
        self._upsert_armies(packet, seq)
        self._upsert_real_marches(packet, seq)
        self._upsert_entities(packet, seq)
        self.conn.commit()
        return seq

    def _insert_visual_field(self, packet: WorldScenePacket, seq: int) -> None:
        if packet.visual_field_raw in ({}, [], None, ""):
            return
        self.conn.execute(
            """
            INSERT INTO world_visual_fields(source_seq, cmd_id, server_order_id, raw_json)
            VALUES(?,?,?,?)
            """,
            (
                seq,
                packet.cmd_id,
                packet.server_order_id,
                json.dumps(packet.visual_field_raw, ensure_ascii=False),
            ),
        )

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
        for wid, chunk_ids in packet.clear_chunks.items():
            # 当前投影只承载 WORLD_CITY chunk("0")；5028 clearChunks 指定 "0"
            # 时需要移除对应城市/地块投影，避免前端继续展示已清除的旧城池。
            if "0" in chunk_ids:
                self.conn.execute("DELETE FROM world_tiles WHERE wid=?", (wid,))
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
        deleted_army_ids = set(packet.direct_deleted_army_ids) | set(
            packet.block_deleted_army_ids
        )
        for army_id in deleted_army_ids:
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

    def _upsert_entities(self, packet: WorldScenePacket, seq: int) -> None:
        for entities in packet.entities.values():
            for entity in entities.values():
                if entity.deleted:
                    self.conn.execute(
                        """
                        UPDATE world_scene_entities
                        SET deleted_at_seq=?
                        WHERE category=? AND entity_id=?
                        """,
                        (seq, entity.category, entity.entity_id),
                    )
                    continue
                self.conn.execute(
                    """
                    INSERT INTO world_scene_entities(
                        category, entity_id, raw_json, source_seq, deleted_at_seq
                    )
                    VALUES(?,?,?,?,NULL)
                    ON CONFLICT(category, entity_id) DO UPDATE SET
                        raw_json=excluded.raw_json,
                        source_seq=excluded.source_seq,
                        deleted_at_seq=NULL
                    """,
                    (
                        entity.category,
                        entity.entity_id,
                        json.dumps(entity.raw, ensure_ascii=False),
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
        visual = self.conn.execute(
            """
            SELECT source_seq, cmd_id, server_order_id, raw_json
            FROM world_visual_fields
            ORDER BY source_seq DESC
            LIMIT 1
            """
        ).fetchone()
        visual_payload = None
        if visual:
            visual_payload = dict(visual)
            try:
                visual_payload["raw"] = json.loads(visual_payload.pop("raw_json") or "null")
            except Exception:
                visual_payload["raw"] = visual_payload.pop("raw_json")
        return {"tiles": [dict(row) for row in rows], "visualField": visual_payload}

    def active_armies(self) -> List[Dict[str, Any]]:
        rows = self.conn.execute(
            """
            SELECT
                a.*,
                u.name AS owner_name,
                u.union_id AS owner_union_id,
                COALESCE(un.name, u.union_name) AS owner_union_name,
                t.name AS target_name,
                t.force AS target_force,
                t.union_id AS target_union_id
            FROM world_armies a
            LEFT JOIN world_map_users u ON u.user_id = a.user_id
            LEFT JOIN world_unions un ON un.union_id = u.union_id
            LEFT JOIN world_tiles t ON t.wid = a.wid_to
            WHERE a.deleted_at_seq IS NULL
            ORDER BY a.end_time, a.army_id
            """
        ).fetchall()
        return [dict(row) for row in rows]

    def active_marches(self) -> List[Dict[str, Any]]:
        rows = self.conn.execute(
            "SELECT * FROM world_real_marches ORDER BY end_time, real_march_id"
        ).fetchall()
        return [dict(row) for row in rows]

    def active_entities(self, category: str | None = None) -> List[Dict[str, Any]]:
        if category:
            rows = self.conn.execute(
                """
                SELECT category, entity_id, raw_json, source_seq
                FROM world_scene_entities
                WHERE deleted_at_seq IS NULL AND category=?
                ORDER BY category, entity_id
                """,
                (category,),
            ).fetchall()
        else:
            rows = self.conn.execute(
                """
                SELECT category, entity_id, raw_json, source_seq
                FROM world_scene_entities
                WHERE deleted_at_seq IS NULL
                ORDER BY category, entity_id
                """
            ).fetchall()
        out = []
        for row in rows:
            item = dict(row)
            try:
                item["raw"] = json.loads(item.pop("raw_json") or "null")
            except Exception:
                item["raw"] = item.pop("raw_json")
            out.append(item)
        return out

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
                INSERT INTO map_cells(
                    wid, x, y, cell_type, type_name, building_id, owner_name,
                    city_name, parent_wid, source_msg_id, updated_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(wid) DO UPDATE SET
                    x=excluded.x,
                    y=excluded.y,
                    cell_type=excluded.cell_type,
                    type_name=excluded.type_name,
                    owner_name=excluded.owner_name,
                    city_name=excluded.city_name,
                    updated_at=excluded.updated_at
                """,
                (
                    row["wid"],
                    row["row"],
                    row["col"],
                    row["city_type"],
                    f"type{row['city_type']}",
                    row["city_param"],
                    "",
                    row["name"],
                    row["belong_city"],
                    "world_scene",
                    now,
                ),
            )

        for row in self.conn.execute(
            "SELECT * FROM world_armies WHERE deleted_at_seq IS NULL"
        ):
            current = row["stay_wid"] or row["reside_wid"]
            self.conn.execute(
                """
                INSERT INTO battle_monitor_moves(
                    team_id, move_type, subject_id, owner_uid, owner_name,
                    owner_union, from_wid, to_wid, current_wid, from_xy, to_xy,
                    current_xy, start_time, arrive_time, speed, target_type,
                    reside_wid, stay_wid, army_hero_type, morale, buff_ids,
                    battle_show, state_id, marker, captured_at
                )
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(team_id) DO UPDATE SET
                    move_type=excluded.move_type,
                    subject_id=excluded.subject_id,
                    from_wid=excluded.from_wid,
                    to_wid=excluded.to_wid,
                    current_wid=excluded.current_wid,
                    start_time=excluded.start_time,
                    arrive_time=excluded.arrive_time,
                    morale=excluded.morale,
                    buff_ids=excluded.buff_ids,
                    battle_show=excluded.battle_show,
                    state_id=excluded.state_id,
                    captured_at=excluded.captured_at
                """,
                (
                    row["army_id"],
                    row["state"],
                    row["user_id"],
                    row["user_id"],
                    "",
                    "",
                    row["wid_from"],
                    row["wid_to"],
                    current,
                    _wid_xy(row["wid_from"]),
                    _wid_xy(row["wid_to"]),
                    _wid_xy(current),
                    row["begin_time"],
                    row["end_time"],
                    0,
                    row["target_type"],
                    row["reside_wid"],
                    row["stay_wid"],
                    row["army_hero_type"],
                    row["morale"],
                    row["buff_ids"],
                    row["battle_show"],
                    row["state_id"],
                    0,
                    now,
                ),
            )
        self.conn.commit()


def _wid_xy(wid: int) -> str:
    if not wid:
        return ""
    return f"{wid // 10000},{wid % 10000}"
