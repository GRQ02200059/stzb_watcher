"""Desktop-side, one-shot incremental upload for local battle reports."""
from __future__ import annotations

import hashlib
import json
import sqlite3
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Callable, Dict, List, Mapping, Optional, Set, Tuple


CLIENT_VERSION = "1.2.0"
SCHEMA_VERSION = 1
MANIFEST_BATCH = 200
UPLOAD_BATCH = 50
MAX_UPLOAD_BYTES = 2 * 1024 * 1024
REQUIRED_TABLES = frozenset(("battles_v2", "battle_heroes", "battle_skills"))
BATTLE_FIELDS = frozenset((
    "battle_id", "time", "time_str", "result", "result_desc", "fight_type",
    "wid", "wid_name", "wid_code", "atk_name", "atk_uid", "atk_union",
    "atk_unionid", "atk_power", "atk_gongxun", "atk_hp", "def_name",
    "def_uid", "def_union", "def_unionid", "def_level", "def_power",
    "def_gongxun", "def_hp", "weather", "in_night", "is_npc", "is_ai",
    "block_id", "city_type", "borrow_land", "garrison",
    "first_occupy_lvn_land", "atk_team_id", "def_team_id", "atk_advance",
    "def_advance", "atk_hero_type", "def_hero_type", "atk_gear_info",
    "def_gear_info", "all_skill_info", "attack_all_hero_info",
    "defend_all_hero_info", "attack_all_sub_hero_info",
    "defend_all_sub_hero_info", "attack_support_user_info",
    "defend_support_user_info", "captured_at",
))
HERO_FIELDS = frozenset((
    "battle_id", "side", "pos", "hero_id", "hero_name", "level", "star",
    "max_hp", "remain_hp", "damage_taken",
))
SKILL_FIELDS = frozenset((
    "battle_id", "side", "pos", "skill_id", "skill_name", "skill_level",
))


class SyncStatus(str, Enum):
    UPLOADED = "uploaded"
    NO_REPORTS = "no_reports"
    NO_CHANGES = "no_changes"
    SESSION_REJECTED = "session_rejected"
    RETRYABLE_FAILURE = "retryable_failure"
    PERMANENT_FAILURE = "permanent_failure"


@dataclass(frozen=True)
class SyncResult:
    status: SyncStatus
    uploaded: int = 0


class BattleReportSynchronizer:
    def __init__(self, post: Callable[[str, dict], Tuple[dict, int]]):
        self._post = post

    def sync(self, token: str, profile: Mapping[str, object]) -> SyncResult:
        if not isinstance(token, str) or not token:
            return SyncResult(SyncStatus.SESSION_REJECTED)
        sync_profile = _sync_profile(profile)
        if sync_profile is None:
            return SyncResult(SyncStatus.NO_REPORTS)
        try:
            database_path = Path(str(profile.get("db_path") or ""))
        except sqlite3.Error:
            return SyncResult(SyncStatus.NO_REPORTS)

        uploaded = 0
        saw_reports = False
        after_battle_id: Optional[int] = None
        while True:
            try:
                report_batch = _read_batch(database_path, after_battle_id, MANIFEST_BATCH)
            except sqlite3.Error:
                return SyncResult(SyncStatus.NO_REPORTS if uploaded == 0 else SyncStatus.RETRYABLE_FAILURE, uploaded)
            if not report_batch:
                return SyncResult(
                    SyncStatus.UPLOADED if uploaded else (
                        SyncStatus.NO_CHANGES if saw_reports else SyncStatus.NO_REPORTS
                    ),
                    uploaded,
                )
            saw_reports = True
            manifest_payload = _common_payload(token, sync_profile)
            manifest_payload["reports"] = [
                {"battleId": report["battleId"], "contentHash": report["contentHash"]}
                for report in report_batch
            ]
            response, status = self._post("battle-reports/manifest", manifest_payload)
            outcome = _http_status(status)
            if outcome is not None:
                return SyncResult(outcome, uploaded)
            required = response.get("requiredBattleIds") if isinstance(response, dict) else None
            if not isinstance(required, list) or any(isinstance(item, bool) or not isinstance(item, int) for item in required):
                return SyncResult(SyncStatus.PERMANENT_FAILURE, uploaded)
            requested = set(required)
            to_upload = [report for report in report_batch if report["battleId"] in requested]
            for upload_batch in _upload_batches(token, sync_profile, to_upload):
                upload_payload = _common_payload(token, sync_profile)
                upload_payload["reports"] = upload_batch
                response, status = self._post("battle-reports/upload", upload_payload)
                outcome = _http_status(status)
                if outcome is not None:
                    return SyncResult(outcome, uploaded)
                if not isinstance(response, dict) or response.get("accepted") != len(upload_batch):
                    return SyncResult(SyncStatus.PERMANENT_FAILURE, uploaded)
                uploaded += len(upload_batch)
            after_battle_id = report_batch[-1]["battleId"]
            if len(report_batch) < MANIFEST_BATCH:
                return SyncResult(SyncStatus.UPLOADED if uploaded else SyncStatus.NO_CHANGES, uploaded)


def _sync_profile(profile: Mapping[str, object]) -> Optional[dict]:
    profile_id = str(profile.get("profile_id") or "")
    server_address = str(profile.get("server_ip") or "")
    role_id = str(profile.get("role_id") or "")
    display_name = str(profile.get("role_name") or "")
    if not all((profile_id, server_address, role_id, display_name)):
        return None
    return {
        "profileId": profile_id,
        "serverAddress": server_address,
        "roleId": role_id,
        "displayName": display_name,
    }


def _read_batch(
    database_path: Path, after_battle_id: Optional[int], limit: int
) -> List[dict]:
    if not database_path.is_file():
        return []
    connection = sqlite3.connect(f"file:{database_path}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        table_names = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        if not REQUIRED_TABLES.issubset(table_names):
            return []
        battle_columns = _columns(connection, "battles_v2") & BATTLE_FIELDS
        if "battle_id" not in battle_columns:
            return []
        ordered_battle_columns = sorted(battle_columns)
        where = "" if after_battle_id is None else " WHERE battle_id > ?"
        parameters = () if after_battle_id is None else (after_battle_id,)
        connection.execute("BEGIN")
        try:
            rows = connection.execute(
                "SELECT " + ",".join(ordered_battle_columns) +
                " FROM battles_v2" + where + " ORDER BY battle_id LIMIT ?",
                parameters + (limit,),
            ).fetchall()
            battle_ids = [row["battle_id"] for row in rows]
            heroes = _children(connection, "battle_heroes", HERO_FIELDS, "hero_id", battle_ids)
            skills = _children(connection, "battle_skills", SKILL_FIELDS, "skill_id", battle_ids)
            reports = []
            for row in rows:
                battle_id = row["battle_id"]
                report = {
                    "schemaVersion": SCHEMA_VERSION,
                    "battleId": battle_id,
                    "battle": dict(row),
                    "heroes": heroes.get(battle_id, []),
                    "skills": skills.get(battle_id, []),
                }
                report["contentHash"] = _content_hash(report)
                reports.append(report)
            connection.execute("COMMIT")
            return reports
        except Exception:
            connection.execute("ROLLBACK")
            raise
    finally:
        connection.close()


def _columns(connection: sqlite3.Connection, table: str) -> Set[str]:
    return {row[1] for row in connection.execute("PRAGMA table_info(" + table + ")")}


def _children(
    connection: sqlite3.Connection,
    table: str,
    allowed: frozenset,
    identity: str,
    battle_ids: List[int],
) -> Dict[int, List[dict]]:
    columns = _columns(connection, table) & allowed
    if not {"battle_id", "side", "pos", identity}.issubset(columns) or not battle_ids:
        return {}
    ordered_columns = sorted(columns)
    placeholders = ",".join("?" for _ in battle_ids)
    rows = connection.execute(
        "SELECT " + ",".join(ordered_columns) + " FROM " + table +
        " WHERE battle_id IN (" + placeholders + ")" +
        " ORDER BY battle_id,side,pos," + identity,
        battle_ids,
    ).fetchall()
    result: Dict[int, List[dict]] = {}
    for row in rows:
        value = dict(row)
        battle_id = value.pop("battle_id")
        result.setdefault(battle_id, []).append(value)
    return result


def _content_hash(report: dict) -> str:
    canonical = {
        "schemaVersion": SCHEMA_VERSION,
        "battle": report["battle"],
        "heroes": sorted(report["heroes"], key=lambda row: (row["side"], row["pos"], row.get("hero_id") or 0)),
        "skills": sorted(report["skills"], key=lambda row: (row["side"], row["pos"], row.get("skill_id") or 0)),
    }
    payload = json.dumps(canonical, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def _common_payload(token: str, profile: dict) -> dict:
    return {
        "token": token,
        "clientVersion": CLIENT_VERSION,
        "schemaVersion": SCHEMA_VERSION,
        "profile": profile,
    }


def _upload_batches(token: str, profile: dict, reports: List[dict]) -> List[List[dict]]:
    batches: List[List[dict]] = []
    current: List[dict] = []
    for report in reports:
        single_payload = _common_payload(token, profile)
        single_payload["reports"] = [report]
        if _encoded_size(single_payload) > MAX_UPLOAD_BYTES:
            continue
        candidate = current + [report]
        candidate_payload = _common_payload(token, profile)
        candidate_payload["reports"] = candidate
        if current and (len(candidate) > UPLOAD_BATCH or _encoded_size(candidate_payload) > MAX_UPLOAD_BYTES):
            batches.append(current)
            current = [report]
        else:
            current = candidate
    if current:
        batches.append(current)
    return batches


def _encoded_size(payload: dict) -> int:
    return len(json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))


def _http_status(status: int) -> Optional[SyncStatus]:
    if status == 429 or status >= 500:
        return SyncStatus.RETRYABLE_FAILURE
    if status in (401, 403):
        return SyncStatus.SESSION_REJECTED
    if status < 200 or status >= 300:
        return SyncStatus.PERMANENT_FAILURE
    return None
