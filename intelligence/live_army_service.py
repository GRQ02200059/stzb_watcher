from __future__ import annotations

import json
import sqlite3
import time
from typing import Any, Dict, Iterable, List, Optional, Sequence

import sim_data

from .world_service import freshness


ARMY_STATES = {
    0: ("normal", "待命", "stationary", False),
    1: ("expedition", "出征中", "moving", True),
    2: ("reside-going", "驻守前往", "moving", True),
    3: ("reinforce-going", "增援前往", "moving", True),
    4: ("returning", "返回中", "moving", True),
    5: ("reside", "驻守", "stationary", False),
    6: ("reinforce", "增援", "stationary", False),
    25: ("stay", "停留", "stationary", False),
}

UNKNOWN_PORTRAIT = "/static/hero-portraits/placeholder.svg"


def army_state_meta(state: int) -> Dict[str, Any]:
    state = _int(state)
    row = ARMY_STATES.get(state)
    if row is None:
        return {
            "key": "unknown",
            "label": f"状态 {state}",
            "category": "unknown",
            "isMoving": False,
        }
    key, label, category, is_moving = row
    return {
        "key": key,
        "label": label,
        "category": category,
        "isMoving": is_moving,
    }


class LiveArmyService:
    def __init__(self, connection: sqlite3.Connection, *, now_ms=None) -> None:
        self.connection = connection
        self.connection.row_factory = sqlite3.Row
        self._now_ms = now_ms
        self._tables = _table_names(connection)
        self._columns: Dict[str, set] = {}

    def snapshot(self, offline_minutes: int = 10) -> Dict[str, Any]:
        now_ms = self.now_ms()
        if "world_armies" not in self._tables:
            return self._empty_snapshot(now_ms)

        current_rows = self._army_rows(current=True)
        offline_rows = self._army_rows(
            current=False,
            minimum_deleted_at_ms=now_ms - max(0, int(offline_minutes)) * 60_000,
            include_offline=bool(offline_minutes),
        )
        all_rows = current_rows + offline_rows
        lineup_by_army = self._lineups(
            [_int(row["army_id"]) for row in all_rows]
        )

        current = [
            self._project_army(row, lineup_by_army, now_ms, offline=False)
            for row in current_rows
        ]
        recent_offline = [
            self._project_army(row, lineup_by_army, now_ms, offline=True)
            for row in offline_rows
        ]
        current.sort(key=lambda row: (row["armyId"]))
        recent_offline.sort(
            key=lambda row: (
                -_int(row.get("offline", {}).get("deletedAtMs")),
                row["armyId"],
            )
        )

        version = self._current_version()
        moving = sum(1 for row in current if row["isMoving"])
        stationary = sum(
            1 for row in current if row["stateCategory"] == "stationary"
        )
        stale_current = sum(
            1 for row in current if row["source"]["isStale"]
        )
        usable_current = sum(
            1
            for row in current
            if row["source"]["freshness"] in ("fresh", "aging")
        )
        exact = sum(
            1 for row in current if row["lineup"]["status"] == "exact"
        )
        observed_at_ms = _int(version["observed_at_ms"])
        return {
            "ok": True,
            "generatedAtMs": now_ms,
            "worldStateVersion": version["version"],
            "worldStateObservedAtMs": observed_at_ms,
            "worldStateAgeMs": _age_ms(observed_at_ms, now_ms),
            "freshness": freshness(observed_at_ms, now_ms),
            "completeness": version["completeness"],
            "summary": {
                "current": len(current),
                "usableCurrent": usable_current,
                "staleCurrent": stale_current,
                "moving": moving,
                "stationary": stationary,
                "exactLineups": exact,
                "unknownLineups": len(current) - exact,
                "recentOffline": len(recent_offline),
            },
            "bounds": _bounds_for_armies(current),
            "current": current,
            "recentOffline": recent_offline,
        }

    def now_ms(self) -> int:
        if callable(self._now_ms):
            return int(self._now_ms())
        if self._now_ms is not None:
            return int(self._now_ms)
        return int(time.time() * 1000)

    def _empty_snapshot(self, now_ms: int) -> Dict[str, Any]:
        return {
            "ok": True,
            "generatedAtMs": now_ms,
            "worldStateVersion": 0,
            "worldStateObservedAtMs": 0,
            "worldStateAgeMs": 0,
            "freshness": "unknown",
            "completeness": "uninitialized",
            "summary": {
                "current": 0,
                "usableCurrent": 0,
                "staleCurrent": 0,
                "moving": 0,
                "stationary": 0,
                "exactLineups": 0,
                "unknownLineups": 0,
                "recentOffline": 0,
            },
            "bounds": None,
            "current": [],
            "recentOffline": [],
        }

    def _current_version(self) -> Dict[str, Any]:
        if "world_state_versions" not in self._tables:
            return {
                "version": 0,
                "observed_at_ms": 0,
                "completeness": "unknown",
            }
        row = self.connection.execute(
            """
            SELECT version, observed_at_ms, completeness
            FROM world_state_versions
            ORDER BY version DESC
            LIMIT 1
            """
        ).fetchone()
        if row is None:
            return {
                "version": 0,
                "observed_at_ms": 0,
                "completeness": "uninitialized",
            }
        return dict(row)

    def _army_rows(
        self,
        *,
        current: bool,
        minimum_deleted_at_ms: int = 0,
        include_offline: bool = True,
    ) -> List[sqlite3.Row]:
        if not current and (
            not include_offline or "world_scene_packets" not in self._tables
        ):
            return []

        users = "world_map_users" in self._tables
        unions = "world_unions" in self._tables
        tiles = "world_tiles" in self._tables
        marches = "world_real_marches" in self._tables
        packets = "world_scene_packets" in self._tables

        select = [
            "a.*",
            (
                "source_packet.observed_at_ms AS source_observed_at_ms,"
                "source_packet.cmd_id AS source_cmd_id,"
                "source_packet.server_order_id AS source_server_order_id"
                if packets
                else "0 AS source_observed_at_ms,0 AS source_cmd_id,"
                "0 AS source_server_order_id"
            ),
            (
                "deleted_packet.observed_at_ms AS deleted_at_ms,"
                "deleted_packet.cmd_id AS deleted_cmd_id,"
                "deleted_packet.server_order_id AS deleted_server_order_id"
                if packets
                else "0 AS deleted_at_ms,0 AS deleted_cmd_id,"
                "0 AS deleted_server_order_id"
            ),
            (
                "u.name AS owner_name,u.union_id AS owner_union_id,"
                "u.union_name AS owner_user_union_name"
                if users
                else "'' AS owner_name,0 AS owner_union_id,"
                "'' AS owner_user_union_name"
            ),
            (
                "un.name AS resolved_union_name"
                if unions and users
                else "'' AS resolved_union_name"
            ),
            (
                "t.name AS target_name,t.force AS target_force,"
                "t.union_id AS target_union_id"
                if tiles
                else "'' AS target_name,0 AS target_force,"
                "0 AS target_union_id"
            ),
            (
                "m.last_wid AS march_last_wid,"
                "m.current_wid AS march_current_wid,"
                "m.next_wid AS march_next_wid,"
                "m.start_time AS march_start_time,"
                "m.next_time AS march_next_time,"
                "m.end_time AS march_end_time,"
                "m.path_id AS march_path_id,"
                "m.unit_time_cost AS march_unit_time_cost,"
                "m.march_type AS march_type,"
                "m.belong_id AS march_belong_id"
                if marches
                else "0 AS march_last_wid,0 AS march_current_wid,"
                "0 AS march_next_wid,0 AS march_start_time,"
                "0 AS march_next_time,0 AS march_end_time,"
                "0 AS march_path_id,0 AS march_unit_time_cost,"
                "0 AS march_type,0 AS march_belong_id"
            ),
        ]
        joins = []
        if packets:
            joins.extend(
                (
                    "LEFT JOIN world_scene_packets source_packet "
                    "ON source_packet.seq=a.source_seq",
                    "LEFT JOIN world_scene_packets deleted_packet "
                    "ON deleted_packet.seq=a.deleted_at_seq",
                )
            )
        if users:
            joins.append(
                "LEFT JOIN world_map_users u ON u.user_id=a.user_id"
            )
        if unions and users:
            joins.append(
                "LEFT JOIN world_unions un ON un.union_id=u.union_id"
            )
        if tiles:
            joins.append("LEFT JOIN world_tiles t ON t.wid=a.wid_to")
        if marches:
            joins.append(
                "LEFT JOIN world_real_marches m "
                "ON m.real_march_id=a.real_march_id"
            )

        parameters: List[Any] = []
        if current:
            where = "a.deleted_at_seq IS NULL"
        else:
            where = (
                "a.deleted_at_seq IS NOT NULL "
                "AND COALESCE(deleted_packet.observed_at_ms,0)>=?"
            )
            parameters.append(int(minimum_deleted_at_ms))
        sql = (
            f"SELECT {','.join(select)} FROM world_armies a "
            f"{' '.join(joins)} WHERE {where} ORDER BY a.army_id"
        )
        return self.connection.execute(sql, parameters).fetchall()

    def _project_army(
        self,
        row: sqlite3.Row,
        lineup_by_army: Dict[int, Dict[str, Any]],
        now_ms: int,
        *,
        offline: bool,
    ) -> Dict[str, Any]:
        item = dict(row)
        army_id = _int(item.get("army_id"))
        state = _int(item.get("state"))
        state_meta = army_state_meta(state)
        march_id = _int(item.get("real_march_id"))
        march_current = _int(item.get("march_current_wid"))
        if march_id and march_current:
            current_wid = march_current
            location_source = "real-march"
        elif _int(item.get("stay_wid")):
            current_wid = _int(item.get("stay_wid"))
            location_source = "stay"
        elif _int(item.get("reside_wid")):
            current_wid = _int(item.get("reside_wid"))
            location_source = "reside"
        elif _int(item.get("wid_from")):
            current_wid = _int(item.get("wid_from"))
            location_source = "from"
        else:
            current_wid = 0
            location_source = "unknown"

        march = None
        if march_id and any(
            _int(item.get(key))
            for key in (
                "march_current_wid",
                "march_next_wid",
                "march_end_time",
            )
        ):
            march = {
                "realMarchId": march_id,
                "lastWid": _int(item.get("march_last_wid")),
                "currentWid": march_current,
                "nextWid": _int(item.get("march_next_wid")),
                "startTime": _int(item.get("march_start_time")),
                "nextTime": _int(item.get("march_next_time")),
                "endTime": _int(item.get("march_end_time")),
                "pathId": _int(item.get("march_path_id")),
                "unitTimeCost": _int(
                    item.get("march_unit_time_cost")
                ),
                "marchType": _int(item.get("march_type")),
                "belongId": _int(item.get("march_belong_id")),
            }

        source_observed_at_ms = _int(item.get("source_observed_at_ms"))
        source_freshness = freshness(source_observed_at_ms, now_ms)
        source = {
            "seq": _int(item.get("source_seq")),
            "observedAtMs": source_observed_at_ms,
            "ageMs": _age_ms(source_observed_at_ms, now_ms),
            "freshness": source_freshness,
            "isStale": source_freshness == "stale",
            "cmdId": _int(item.get("source_cmd_id")),
            "serverOrderId": _int(
                item.get("source_server_order_id")
            ),
        }
        deleted_at_ms = _int(item.get("deleted_at_ms"))
        offline_model = None
        if offline:
            deleted_cmd = _int(item.get("deleted_cmd_id"))
            offline_model = {
                "deletedAtMs": deleted_at_ms,
                "ageMs": max(0, now_ms - deleted_at_ms),
                "sourceCmd": deleted_cmd,
                "sourceLabel": _offline_source_label(deleted_cmd),
                "serverOrderId": _int(
                    item.get("deleted_server_order_id")
                ),
            }

        owner_union_name = str(
            item.get("resolved_union_name")
            or item.get("owner_user_union_name")
            or ""
        )
        end_time = (
            march["endTime"]
            if march is not None and march["endTime"]
            else _int(item.get("end_time"))
        )
        next_time = (
            march["nextTime"] if march is not None else 0
        )
        begin_time = (
            march["startTime"]
            if march is not None and march["startTime"]
            else _int(item.get("begin_time"))
        )
        return {
            "armyId": army_id,
            "userId": _int(item.get("user_id")),
            "ownerName": str(item.get("owner_name") or ""),
            "ownerUnionId": _int(item.get("owner_union_id")),
            "ownerUnionName": owner_union_name,
            "state": state,
            "stateKey": state_meta["key"],
            "stateLabel": state_meta["label"],
            "stateCategory": state_meta["category"],
            "isMoving": state_meta["isMoving"],
            "source": source,
            "location": {
                "currentWid": current_wid,
                "nextWid": _int(item.get("march_next_wid")),
                "targetWid": _int(item.get("wid_to")),
                "fromWid": _int(item.get("wid_from")),
                "resideWid": _int(item.get("reside_wid")),
                "stayWid": _int(item.get("stay_wid")),
                "source": location_source,
            },
            "timing": {
                "beginTime": begin_time,
                "nextTime": next_time,
                "endTime": end_time,
            },
            "march": march,
            "morale": _int(item.get("morale")),
            "armyHeroType": str(item.get("army_hero_type") or ""),
            "targetType": _int(item.get("target_type")),
            "target": {
                "name": str(item.get("target_name") or ""),
                "force": _int(item.get("target_force")),
                "unionId": _int(item.get("target_union_id")),
            },
            "buffIds": str(item.get("buff_ids") or ""),
            "obstacleWid": _int(item.get("obstacle_wid")),
            "battleShow": str(item.get("battle_show") or ""),
            "lineup": lineup_by_army.get(
                army_id, _unknown_lineup()
            ),
            "offline": offline_model,
        }

    def _lineups(
        self, army_ids: Sequence[int]
    ) -> Dict[int, Dict[str, Any]]:
        army_ids = sorted({_int(value) for value in army_ids if _int(value)})
        if not army_ids or "battles_v2" not in self._tables:
            return {}
        columns = self._table_columns("battles_v2")
        required = {
            "battle_id",
            "time",
            "atk_team_id",
            "def_team_id",
        }
        if not required.issubset(columns):
            return {}

        placeholders = ",".join("?" for _ in army_ids)
        select_columns = [
            "battle_id",
            "time",
            "time_str" if "time_str" in columns else "'' AS time_str",
            "atk_team_id",
            "def_team_id",
            (
                "all_skill_info"
                if "all_skill_info" in columns
                else "'' AS all_skill_info"
            ),
        ]
        for side in ("atk", "def"):
            for position in (1, 2, 3):
                for suffix in ("id", "level", "star"):
                    name = f"{side}_hero{position}_{suffix}"
                    select_columns.append(
                        name if name in columns else f"0 AS {name}"
                    )
        parameters = list(army_ids) + list(army_ids)
        rows = self.connection.execute(
            f"""
            SELECT {','.join(select_columns)}
            FROM battles_v2
            WHERE atk_team_id IN ({placeholders})
               OR def_team_id IN ({placeholders})
            ORDER BY COALESCE(time,0) DESC, battle_id DESC
            """,
            parameters,
        ).fetchall()
        hero_meta = sim_data.hero_index()
        skill_meta = sim_data.skill_index()
        result: Dict[int, Dict[str, Any]] = {}
        army_id_set = set(army_ids)
        for raw_row in rows:
            row = dict(raw_row)
            candidates = []
            atk_team_id = _int(row.get("atk_team_id"))
            def_team_id = _int(row.get("def_team_id"))
            if atk_team_id in army_id_set:
                candidates.append((atk_team_id, "atk"))
            if def_team_id in army_id_set:
                candidates.append((def_team_id, "def"))
            skills = _parse_skill_info(row.get("all_skill_info"))
            for army_id, side in candidates:
                if army_id in result:
                    continue
                hero_ids = [
                    _int(row.get(f"{side}_hero{position}_id"))
                    for position in (1, 2, 3)
                ]
                if not any(hero_ids):
                    continue
                heroes = []
                for position, hero_id in enumerate(hero_ids, start=1):
                    if hero_id <= 0:
                        continue
                    meta = dict(hero_meta.get(hero_id) or {})
                    meta.setdefault("id", hero_id)
                    meta.setdefault("name", f"武将 {hero_id}")
                    meta.setdefault("camp", 0)
                    meta.setdefault("army", 0)
                    meta.setdefault("quality", 0)
                    meta.setdefault("iconId", hero_id)
                    meta.setdefault("portraitUrl", UNKNOWN_PORTRAIT)
                    meta.setdefault(
                        "portraitFallbackUrl", UNKNOWN_PORTRAIT
                    )
                    meta.setdefault("portraitLocal", False)
                    skill_position = (
                        position if side == "atk" else position + 3
                    )
                    hero_skills = []
                    for skill in skills.get(skill_position, []):
                        skill_id = _int(skill.get("skillId"))
                        resolved = skill_meta.get(skill_id) or {}
                        hero_skills.append(
                            {
                                "id": skill_id,
                                "name": resolved.get("name")
                                or f"战法 {skill_id}",
                                "level": _int(skill.get("level")),
                            }
                        )
                    heroes.append(
                        {
                            **meta,
                            "position": position - 1,
                            "level": _int(
                                row.get(
                                    f"{side}_hero{position}_level"
                                )
                            ),
                            "advance": _int(
                                row.get(
                                    f"{side}_hero{position}_star"
                                )
                            ),
                            "skills": hero_skills,
                        }
                    )
                result[army_id] = {
                    "status": "exact",
                    "complete": len(heroes) == 3,
                    "battleId": _int(row.get("battle_id")),
                    "battleTime": _int(row.get("time")),
                    "battleTimeText": str(
                        row.get("time_str") or ""
                    ),
                    "side": side,
                    "heroes": heroes,
                    "message": (
                        "精确阵容"
                        if len(heroes) == 3
                        else "精确命中，阵容不完整"
                    ),
                }
        return result

    def _table_columns(self, table: str) -> set:
        if table not in self._columns:
            self._columns[table] = {
                row[1]
                for row in self.connection.execute(
                    f"PRAGMA table_info({table})"
                ).fetchall()
            }
        return self._columns[table]


def _unknown_lineup() -> Dict[str, Any]:
    return {
        "status": "unknown",
        "complete": False,
        "battleId": 0,
        "battleTime": 0,
        "battleTimeText": "",
        "side": "",
        "heroes": [],
        "message": "无同 ID 战报，阵容未知",
    }


def _parse_skill_info(raw: Any) -> Dict[int, List[Dict[str, int]]]:
    result: Dict[int, List[Dict[str, int]]] = {}
    for part in str(raw or "").split(";"):
        values = [item.strip() for item in part.split(",")]
        if len(values) < 2 or not values[0].lstrip("-").isdigit():
            continue
        position = int(values[0])
        skills = []
        for index in range(1, len(values), 2):
            skill_id = _int(values[index] if index < len(values) else 0)
            level = _int(
                values[index + 1] if index + 1 < len(values) else 0
            )
            if skill_id > 0:
                skills.append({"skillId": skill_id, "level": level})
        if skills:
            result[position] = skills
    return result


def _bounds_for_armies(armies: Iterable[Dict[str, Any]]) -> Optional[Dict[str, int]]:
    points = []
    for army in armies:
        location = army.get("location") or {}
        for key in ("currentWid", "nextWid", "targetWid"):
            wid = _int(location.get(key))
            if wid > 0:
                points.append(divmod(wid, 10000))
    if not points:
        return None
    rows = [point[0] for point in points]
    cols = [point[1] for point in points]
    return {
        "rowUp": max(0, min(rows) - 4),
        "rowDown": max(rows) + 4,
        "colLeft": max(0, min(cols) - 4),
        "colRight": max(cols) + 4,
    }


def _offline_source_label(cmd_id: int) -> str:
    if cmd_id == 5028:
        return "5028 增量"
    if cmd_id == 5026:
        return "5026 基线清理"
    return f"cmd {cmd_id}" if cmd_id else "来源未知"


def _table_names(connection: sqlite3.Connection) -> set:
    try:
        return {
            row[0]
            for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type='table'"
            ).fetchall()
        }
    except sqlite3.Error:
        return set()


def _int(value: Any, default: int = 0) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return default


def _age_ms(observed_at_ms: int, now_ms: int) -> int:
    observed_at_ms = _int(observed_at_ms)
    if observed_at_ms <= 0:
        return 0
    return max(0, _int(now_ms) - observed_at_ms)
