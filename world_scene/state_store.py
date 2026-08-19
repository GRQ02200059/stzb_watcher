from dataclasses import dataclass
import json
import sqlite3

from .models import WorldScenePacket
from .store import WorldSceneStore


@dataclass(frozen=True)
class WorldStateChangeSet:
    state_version: int
    packet_seq: int
    completeness: str
    event_count: int


class WorldStateStore:
    def __init__(self, conn: sqlite3.Connection) -> None:
        self.conn = conn
        self.conn.row_factory = sqlite3.Row
        self.projection = WorldSceneStore(conn)

    def ensure_schema(self) -> None:
        self.projection.ensure_schema()
        self.conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS world_state_versions(
                version INTEGER PRIMARY KEY AUTOINCREMENT,
                packet_seq INTEGER NOT NULL,
                source_cmd INTEGER NOT NULL,
                server_order_id INTEGER NOT NULL DEFAULT 0,
                latest_baseline_order_id INTEGER NOT NULL,
                observed_at_ms INTEGER NOT NULL,
                completeness TEXT NOT NULL,
                coverage_json TEXT,
                change_summary_json TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS world_observed_areas(
                state_version INTEGER PRIMARY KEY,
                row_up INTEGER NOT NULL,
                row_down INTEGER NOT NULL,
                col_left INTEGER NOT NULL,
                col_right INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS world_tile_chunks(
                wid INTEGER NOT NULL,
                chunk_type TEXT NOT NULL,
                raw_json TEXT NOT NULL,
                source_seq INTEGER NOT NULL,
                observed_at_ms INTEGER NOT NULL,
                PRIMARY KEY(wid, chunk_type)
            );
            CREATE TABLE IF NOT EXISTS world_army_blocks(
                block_id INTEGER NOT NULL,
                army_id INTEGER NOT NULL,
                source_seq INTEGER NOT NULL,
                PRIMARY KEY(block_id, army_id)
            );
            CREATE TABLE IF NOT EXISTS world_ship_blocks(
                block_id INTEGER NOT NULL,
                ship_id INTEGER NOT NULL,
                source_seq INTEGER NOT NULL,
                PRIMARY KEY(block_id, ship_id)
            );
            CREATE TABLE IF NOT EXISTS world_assist_army_blocks(
                block_id INTEGER NOT NULL,
                assist_army_id INTEGER NOT NULL,
                source_seq INTEGER NOT NULL,
                PRIMARY KEY(block_id, assist_army_id)
            );
            CREATE TABLE IF NOT EXISTS world_state_events(
                seq INTEGER PRIMARY KEY AUTOINCREMENT,
                state_version INTEGER NOT NULL,
                packet_seq INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                entity_type TEXT,
                entity_id TEXT,
                observed_at_ms INTEGER NOT NULL,
                evidence_json TEXT NOT NULL,
                diff_json TEXT NOT NULL
            );
            """
        )
        columns = {
            row["name"]
            for row in self.conn.execute("PRAGMA table_info(world_state_versions)")
        }
        if "server_order_id" not in columns:
            self.conn.execute(
                "ALTER TABLE world_state_versions ADD COLUMN server_order_id INTEGER NOT NULL DEFAULT 0"
            )
        self.conn.commit()

    def current_version(self) -> dict:
        row = self.conn.execute(
            """
            SELECT version, packet_seq, source_cmd, latest_baseline_order_id,
                   observed_at_ms, completeness, coverage_json,
                   change_summary_json
            FROM world_state_versions
            ORDER BY version DESC LIMIT 1
            """
        ).fetchone()
        if row is None:
            return {
                "version": 0,
                "packet_seq": 0,
                "source_cmd": 0,
                "latest_baseline_order_id": -1,
                "observed_at_ms": 0,
                "completeness": "uninitialized",
                "coverage": None,
                "change_summary": {},
            }
        item = dict(row)
        item["coverage"] = json.loads(item.pop("coverage_json") or "null")
        item["change_summary"] = json.loads(
            item.pop("change_summary_json") or "{}"
        )
        return item

    def history(self, limit: int = 50) -> list[dict]:
        self.ensure_schema()
        rows = self.conn.execute(
            """
            SELECT version, source_cmd, server_order_id, latest_baseline_order_id,
                   observed_at_ms, completeness, coverage_json, change_summary_json
            FROM world_state_versions ORDER BY version DESC LIMIT ?
            """,
            (max(1, min(limit, 500)),),
        ).fetchall()
        return [self._history_version(row) for row in rows]

    def replay(self, version: int) -> dict | None:
        self.ensure_schema()
        row = self.conn.execute(
            """
            SELECT version, source_cmd, server_order_id, latest_baseline_order_id,
                   observed_at_ms, completeness, coverage_json, change_summary_json
            FROM world_state_versions WHERE version=?
            """,
            (version,),
        ).fetchone()
        if row is None:
            return None
        events = []
        for event in self.conn.execute(
            """
            SELECT seq,event_type,entity_type,entity_id,observed_at_ms,evidence_json,diff_json
            FROM world_state_events WHERE state_version=? ORDER BY seq
            """,
            (version,),
        ).fetchall():
            item = dict(event)
            events.append(
                {
                    "seq": item["seq"],
                    "eventType": item["event_type"],
                    "entityType": item["entity_type"],
                    "entityId": item["entity_id"],
                    "observedAtMs": item["observed_at_ms"],
                    "evidence": json.loads(item["evidence_json"] or "{}"),
                    "diff": json.loads(item["diff_json"] or "{}"),
                }
            )
        return {"version": self._history_version(row), "events": events}

    @staticmethod
    def _history_version(row) -> dict:
        item = dict(row)
        return {
            "version": item["version"],
            "sourceMsgId": str(item["source_cmd"]),
            "marker": item["server_order_id"],
            "latestBaselineMarker": item["latest_baseline_order_id"],
            "observedAtMs": item["observed_at_ms"],
            "completeness": item["completeness"],
            "coverage": json.loads(item["coverage_json"] or "null"),
            "changeSummary": json.loads(item["change_summary_json"] or "{}"),
        }

    def apply_baseline(self, packet: WorldScenePacket) -> WorldStateChangeSet:
        if packet.cmd_id != 5026 or packet.server_order_id <= 0:
            raise ValueError("baseline requires a completed 5026 packet")
        self.ensure_schema()
        area = packet.observed_area
        completeness = "full-baseline" if area is not None else "partial-baseline"
        if area is not None:
            incoming_wids = set(packet.tiles)
            rows = self.conn.execute(
                """
                SELECT wid FROM world_tiles
                WHERE row BETWEEN ? AND ? AND col BETWEEN ? AND ?
                """,
                (area.row_up, area.row_down, area.col_left, area.col_right),
            ).fetchall()
            for row in rows:
                if row["wid"] not in incoming_wids:
                    self.conn.execute(
                        "DELETE FROM world_tiles WHERE wid=?", (row["wid"],)
                    )
        self.conn.execute("DELETE FROM world_real_marches")
        packet_seq = self.projection.apply_packet(packet)
        self._apply_tile_chunks(packet, packet_seq)
        self._replace_baseline_memberships(packet, packet_seq)
        version = self._record_version(
            packet,
            packet_seq,
            completeness,
            {
                "tiles": len(packet.tiles),
                "armies": len(packet.armies),
                "marches": len(packet.real_marches),
            },
        )
        if area is not None:
            self.conn.execute(
                """
                INSERT OR REPLACE INTO world_observed_areas(
                    state_version,row_up,row_down,col_left,col_right
                ) VALUES(?,?,?,?,?)
                """,
                (
                    version,
                    area.row_up,
                    area.row_down,
                    area.col_left,
                    area.col_right,
                ),
            )
        self._record_event(
            version,
            packet_seq,
            "snapshot_completed",
            "world",
            str(version),
            packet,
            {"completeness": completeness},
        )
        self.conn.commit()
        return WorldStateChangeSet(version, packet_seq, completeness, 1)

    def apply_delta(self, packet: WorldScenePacket) -> WorldStateChangeSet:
        if packet.cmd_id != 5028:
            raise ValueError("delta requires a 5028 packet")
        self.ensure_schema()
        current = self.current_version()
        if current["version"] <= 0:
            raise ValueError("delta requires an initialized baseline")
        packet_seq = self.projection.apply_packet(
            _without_block_deleted_armies(packet)
        )
        removed_entities = {
            "career_support": packet.removed_career_support_ids,
            "short_message": packet.cleared_hunter_ids,
            "strategy": packet.cleared_strategy_ids,
        }
        for category, entity_ids in removed_entities.items():
            self._mark_entities_deleted(category, entity_ids, packet_seq)
        self._apply_tile_chunks(packet, packet_seq)
        self._apply_delta_memberships(packet, packet_seq)
        version = self._record_version(
            packet,
            packet_seq,
            "delta",
            {
                "tiles": len(packet.tiles),
                "armies": len(packet.armies),
                "deletedArmies": len(packet.block_deleted_army_ids),
                "marches": len(packet.real_marches),
            },
            latest_baseline_order_id=current["latest_baseline_order_id"],
        )
        self._record_event(
            version,
            packet_seq,
            "delta_applied",
            "world",
            str(version),
            packet,
            {"blockInfo": packet.block_info},
        )
        event_count = 1
        for army_id in packet.armies:
            self._record_event(
                version,
                packet_seq,
                "entity_upserted",
                "army",
                str(army_id),
                packet,
                {"source": "armyChanges"},
            )
            event_count += 1
        for wid in packet.tiles:
            self._record_event(
                version,
                packet_seq,
                "entity_upserted",
                "tile",
                str(wid),
                packet,
                {"source": "worldChunkChanges"},
            )
            event_count += 1
        for wid, chunk_types in packet.clear_chunks.items():
            for chunk_type in chunk_types:
                self._record_event(
                    version,
                    packet_seq,
                    "chunk_cleared",
                    "tile_chunk",
                    f"{wid}:{chunk_type}",
                    packet,
                    {"wid": wid, "chunkType": str(chunk_type)},
                )
                event_count += 1
        for category, entity_ids in removed_entities.items():
            for entity_id in entity_ids:
                self._record_event(
                    version,
                    packet_seq,
                    "entity_deleted",
                    category,
                    str(entity_id),
                    packet,
                    {"source": "5028_clear_slot"},
                )
                event_count += 1
        self.conn.commit()
        return WorldStateChangeSet(version, packet_seq, "delta", event_count)

    def _replace_baseline_memberships(
        self, packet: WorldScenePacket, packet_seq: int
    ) -> None:
        self._replace_memberships(
            "world_army_blocks", "army_id", packet.block_armies, packet_seq
        )
        self._replace_memberships(
            "world_ship_blocks", "ship_id", packet.block_ships, packet_seq
        )
        self._replace_memberships(
            "world_assist_army_blocks",
            "assist_army_id",
            packet.block_assist_armies,
            packet_seq,
        )

    def _replace_memberships(
        self,
        table: str,
        id_column: str,
        memberships,
        packet_seq: int,
    ) -> None:
        for block_id, entity_ids in memberships.items():
            self.conn.execute(
                f"DELETE FROM {table} WHERE block_id=?", (block_id,)
            )
            for entity_id in entity_ids:
                self.conn.execute(
                    f"""
                    INSERT OR REPLACE INTO {table}(
                        block_id,{id_column},source_seq
                    ) VALUES(?,?,?)
                    """,
                    (block_id, entity_id, packet_seq),
                )

    def _apply_tile_chunks(self, packet: WorldScenePacket, packet_seq: int) -> None:
        for wid, chunk_types in packet.clear_chunks.items():
            for chunk_type in chunk_types:
                self.conn.execute(
                    "DELETE FROM world_tile_chunks WHERE wid=? AND chunk_type=?",
                    (wid, str(chunk_type)),
                )
        for wid, chunks in packet.tile_chunks.items():
            for chunk_type, raw in chunks.items():
                self.conn.execute(
                    """
                    INSERT INTO world_tile_chunks(
                        wid,chunk_type,raw_json,source_seq,observed_at_ms
                    ) VALUES(?,?,?,?,?)
                    ON CONFLICT(wid,chunk_type) DO UPDATE SET
                        raw_json=excluded.raw_json,
                        source_seq=excluded.source_seq,
                        observed_at_ms=excluded.observed_at_ms
                    """,
                    (
                        wid,
                        str(chunk_type),
                        json.dumps(raw, ensure_ascii=False),
                        packet_seq,
                        packet.observed_at_ms,
                    ),
                )

    def _apply_delta_memberships(
        self, packet: WorldScenePacket, packet_seq: int
    ) -> None:
        if packet.block_info is None or packet.block_info[0] != 2:
            for army_id in packet.block_deleted_army_ids:
                self.conn.execute(
                    "UPDATE world_armies SET deleted_at_seq=? WHERE army_id=?",
                    (packet_seq, army_id),
                )
            self._mark_entities_deleted(
                "war_ship", packet.deleted_ship_ids, packet_seq
            )
            self._mark_entities_deleted(
                "assist_army", packet.deleted_assist_army_ids, packet_seq
            )
            return
        block_id = packet.block_info[1]
        self._apply_membership_delta(
            "world_army_blocks",
            "army_id",
            block_id,
            packet.armies,
            packet.block_deleted_army_ids,
            packet_seq,
            lambda entity_ids: self._mark_armies_deleted(entity_ids, packet_seq),
        )
        self._apply_membership_delta(
            "world_ship_blocks",
            "ship_id",
            block_id,
            packet.entities.get("war_ship", {}),
            packet.deleted_ship_ids,
            packet_seq,
            lambda entity_ids: self._mark_entities_deleted(
                "war_ship", entity_ids, packet_seq
            ),
        )
        self._apply_membership_delta(
            "world_assist_army_blocks",
            "assist_army_id",
            block_id,
            packet.entities.get("assist_army", {}),
            packet.deleted_assist_army_ids,
            packet_seq,
            lambda entity_ids: self._mark_entities_deleted(
                "assist_army", entity_ids, packet_seq
            ),
        )

    def _apply_membership_delta(
        self,
        table,
        id_column,
        block_id,
        added_entities,
        removed_ids,
        packet_seq,
        mark_deleted,
    ) -> None:
        for entity_id in added_entities:
            self.conn.execute(
                f"""
                INSERT OR REPLACE INTO {table}(
                    block_id,{id_column},source_seq
                ) VALUES(?,?,?)
                """,
                (block_id, entity_id, packet_seq),
            )
        deleted = []
        for entity_id in removed_ids:
            self.conn.execute(
                f"DELETE FROM {table} WHERE block_id=? AND {id_column}=?",
                (block_id, entity_id),
            )
            remaining = self.conn.execute(
                f"SELECT 1 FROM {table} WHERE {id_column}=? LIMIT 1",
                (entity_id,),
            ).fetchone()
            if remaining is None:
                deleted.append(entity_id)
        mark_deleted(deleted)

    def _mark_armies_deleted(self, army_ids, packet_seq):
        for army_id in army_ids:
            self.conn.execute(
                "UPDATE world_armies SET deleted_at_seq=? WHERE army_id=?",
                (packet_seq, army_id),
            )

    def _mark_entities_deleted(self, category, entity_ids, packet_seq):
        for entity_id in entity_ids:
            self.conn.execute(
                """
                UPDATE world_scene_entities SET deleted_at_seq=?
                WHERE category=? AND entity_id=?
                """,
                (packet_seq, category, entity_id),
            )

    def _record_version(
        self,
        packet,
        packet_seq,
        completeness,
        summary,
        latest_baseline_order_id=None,
    ) -> int:
        baseline_order = (
            packet.server_order_id
            if packet.cmd_id == 5026
            else latest_baseline_order_id
        )
        area = packet.observed_area
        coverage = (
            {
                "rowUp": area.row_up,
                "rowDown": area.row_down,
                "colLeft": area.col_left,
                "colRight": area.col_right,
            }
            if area is not None
            else None
        )
        cur = self.conn.execute(
            """
            INSERT INTO world_state_versions(
                packet_seq,source_cmd,server_order_id,latest_baseline_order_id,observed_at_ms,
                completeness,coverage_json,change_summary_json
            ) VALUES(?,?,?,?,?,?,?,?)
            """,
            (
                packet_seq,
                packet.cmd_id,
                packet.server_order_id,
                baseline_order,
                packet.observed_at_ms,
                completeness,
                json.dumps(coverage, ensure_ascii=False),
                json.dumps(summary, ensure_ascii=False, sort_keys=True),
            ),
        )
        return int(cur.lastrowid)

    def _record_event(
        self,
        version,
        packet_seq,
        event_type,
        entity_type,
        entity_id,
        packet,
        diff,
    ):
        self.conn.execute(
            """
            INSERT INTO world_state_events(
                state_version,packet_seq,event_type,entity_type,entity_id,
                observed_at_ms,evidence_json,diff_json
            ) VALUES(?,?,?,?,?,?,?,?)
            """,
            (
                version,
                packet_seq,
                event_type,
                entity_type,
                entity_id,
                packet.observed_at_ms,
                json.dumps(
                    {
                        "cmdId": packet.cmd_id,
                        "serverOrderId": packet.server_order_id,
                        "source": packet.source,
                    },
                    ensure_ascii=False,
                    sort_keys=True,
                ),
                json.dumps(diff, ensure_ascii=False, sort_keys=True),
            ),
        )


def _without_block_deleted_armies(packet: WorldScenePacket) -> WorldScenePacket:
    from dataclasses import replace

    return replace(packet, block_deleted_army_ids=())
