from dataclasses import dataclass, field

from .models import ScoreMetrics


ATTACK_WINS = {1, 7, 11}
DEFENCE_WINS = {2, 6, 12}


@dataclass(frozen=True)
class AggregatedPlayer:
    player_name: str
    player_uid: str
    union_name: str
    group_name: str
    metrics: ScoreMetrics
    adjustment_score: float = 0.0
    data_completeness: str = "complete"
    missing_sources: tuple = field(default_factory=tuple)


class ScoreAggregator:
    def __init__(self, connection, repository, profile_id=""):
        self.connection = connection
        self.repository = repository
        self.profile_id = str(profile_id or "")

    def aggregate(
        self,
        season_id,
        start_time=None,
        end_time=None,
        end_time_exclusive=None,
        union_filter="",
        group_filter="",
    ):
        players = {}
        battle_columns = _columns(self.connection, "battles_v2")
        has_battles = bool(battle_columns)
        has_gongxun = "atk_gongxun" in battle_columns
        team_rows = self._team_users()
        team_by_uid = {
            str(row.get("uid") or ""): row for row in team_rows
        }
        team_by_name = {
            str(row.get("name") or ""): row for row in team_rows
        }
        snapshots = self.repository.cumulative_wuxun_snapshots(self.profile_id)
        for team in team_rows:
            name = str(team.get("name") or "")
            uid = str(team.get("uid") or "")
            snapshot = snapshots.get(uid) or snapshots.get(name)
            if not name or snapshot is None:
                continue
            player = players.setdefault(
                name,
                _mutable_player(
                    name,
                    uid,
                    str(team.get("union_name") or snapshot.get("union_name") or ""),
                    str(team.get("group_name") or snapshot.get("group_name") or ""),
                ),
            )
            player["gongxun_total"] = int(snapshot.get("wuxun") or 0)
        if has_battles:
            where = ["COALESCE(atk_name,'') != ''"]
            args = []
            if start_time is not None:
                where.append("time>=?")
                args.append(int(start_time))
            if end_time_exclusive is not None:
                where.append("time<?")
                args.append(int(end_time_exclusive))
            elif end_time is not None:
                where.append("time<=?")
                args.append(int(end_time))
            select_gongxun = (
                "COALESCE(atk_gongxun,0)" if has_gongxun else "0"
            )
            select_union = "COALESCE(atk_union,'')" if "atk_union" in battle_columns else "''"
            select_uid = "COALESCE(atk_uid,'')" if "atk_uid" in battle_columns else "''"
            select_power = "COALESCE(atk_power,0)" if "atk_power" in battle_columns else "0"
            select_fight = "COALESCE(fight_type,0)" if "fight_type" in battle_columns else "0"
            rows = self.connection.execute(
                f"""
                SELECT atk_name,{select_uid} AS atk_uid,{select_union} AS atk_union,
                       COALESCE(result,0) AS result,
                       {select_gongxun} AS atk_gongxun,
                       {select_power} AS atk_power,
                       {select_fight} AS fight_type
                FROM battles_v2
                WHERE {' AND '.join(where)}
                """,
                args,
            ).fetchall()
            for raw in rows:
                row = dict(raw)
                name = str(row["atk_name"])
                uid = str(row.get("atk_uid") or "")
                team = team_by_uid.get(uid) or team_by_name.get(name) or {}
                union_name = (
                    str(row.get("atk_union") or "").strip()
                    or str(team.get("union_name") or "")
                )
                group_name = str(team.get("group_name") or "")
                player = players.setdefault(
                    name,
                    _mutable_player(name, uid, union_name, group_name),
                )
                if not player["union_name"] and union_name:
                    player["union_name"] = union_name
                if not player["group_name"] and group_name:
                    player["group_name"] = group_name
                player["battles"] += 1
                result = int(row.get("result") or 0)
                if result in ATTACK_WINS:
                    player["wins"] += 1
                elif result not in DEFENCE_WINS:
                    player["draws"] += 1
                snapshot = snapshots.get(uid) or snapshots.get(name)
                if snapshot is not None:
                    player["gongxun_total"] = int(snapshot.get("wuxun") or 0)
                elif "wuxun" in team:
                    player["gongxun_total"] = int(team.get("wuxun") or 0)
                player["power_total"] = max(
                    player["power_total"], int(row.get("atk_power") or 0)
                )
                if int(row.get("fight_type") or 0) in {33, 80}:
                    player["battle_city_cnt"] += 1

        attendance_exists = _table_exists(self.connection, "attendance")
        if attendance_exists:
            attendance_columns = _columns(self.connection, "attendance")
            where = ["COALESCE(player_name,'') != ''"]
            args = []
            if start_time is not None and "time" in attendance_columns:
                where.append("time>=?")
                args.append(int(start_time))
            if end_time_exclusive is not None and "time" in attendance_columns:
                where.append("time<?")
                args.append(int(end_time_exclusive))
            elif end_time is not None and "time" in attendance_columns:
                where.append("time<=?")
                args.append(int(end_time))
            identity_column = "battle_id" if "battle_id" in attendance_columns else "session_id"
            attendance_scope = (
                "fight_type IN (33,80) AND "
                if "fight_type" in attendance_columns
                else ""
            )
            if "profile_id" in attendance_columns and self.profile_id:
                where.append("profile_id=?")
                args.append(self.profile_id)
            rows = self.connection.execute(
                f"""
                SELECT DISTINCT session_id,{identity_column} AS attendance_identity,player_name,
                       COALESCE(player_uid,'') AS player_uid,
                       COALESCE(union_name,'') AS union_name,
                       COALESCE(role,'other') AS role
                FROM attendance
                WHERE {attendance_scope}{' AND '.join(where)}
                """,
                args,
            ).fetchall()
            seen = set()
            for raw in rows:
                row = dict(raw)
                key = (
                    row.get("session_id") or row.get("attendance_identity"),
                    row.get("player_name"),
                    row.get("player_uid"),
                    row.get("role"),
                )
                if key in seen:
                    continue
                seen.add(key)
                name = str(row["player_name"])
                uid = str(row.get("player_uid") or "")
                team = team_by_uid.get(uid) or team_by_name.get(name) or {}
                player = players.setdefault(
                    name,
                    _mutable_player(
                        name,
                        uid,
                        str(row.get("union_name") or team.get("union_name") or ""),
                        str(team.get("group_name") or ""),
                    ),
                )
                role = str(row.get("role") or "other")
                if role == "main":
                    player["main_city_cnt"] += 1
                elif role == "tear":
                    player["tear_cnt"] += 1
                else:
                    player["attendance_cnt"] += 1

        adjustments = self.repository.list_adjustments(season_id)
        for adjustment in adjustments:
            name = adjustment["player_name"]
            player = players.setdefault(
                name,
                _mutable_player(
                    name,
                    str(adjustment.get("player_uid") or ""),
                    "",
                    "",
                ),
            )
            player["adjustment_score"] += float(adjustment["points"])

        result = []
        total_battles = sum(player["battles"] for player in players.values())
        total_gongxun = sum(
            player["gongxun_total"] for player in players.values()
        )
        has_member_wuxun = "wuxun" in _columns(self.connection, "team_users")
        gongxun_missing = not has_member_wuxun
        for player in players.values():
            if union_filter and union_filter not in player["union_name"]:
                continue
            if group_filter and group_filter not in player["group_name"]:
                continue
            missing = []
            if not attendance_exists:
                missing.append("attendance")
            if gongxun_missing:
                missing.append("gongxun")
            result.append(
                AggregatedPlayer(
                    player_name=player["player_name"],
                    player_uid=player["player_uid"],
                    union_name=player["union_name"],
                    group_name=player["group_name"],
                    metrics=ScoreMetrics(
                        battles=player["battles"],
                        wins=player["wins"],
                        draws=player["draws"],
                        gongxun_total=player["gongxun_total"],
                        main_city_cnt=player["main_city_cnt"],
                        tear_cnt=player["tear_cnt"],
                        attendance_cnt=player["attendance_cnt"],
                    ),
                    adjustment_score=round(player["adjustment_score"], 2),
                    data_completeness="partial" if missing else "complete",
                    missing_sources=tuple(missing),
                )
            )
        result.sort(key=lambda item: item.player_name)
        return result

    def _team_users(self):
        if not _table_exists(self.connection, "team_users"):
            return []
        columns = _columns(self.connection, "team_users")
        selected = [
            name
            for name in ("uid", "name", "union_name", "group_name", "wuxun")
            if name in columns
        ]
        if "uid" not in selected or "name" not in selected:
            return []
        query = f"SELECT {','.join(selected)} FROM team_users"
        args = ()
        if "profile_id" in columns:
            query += " WHERE profile_id=?"
            args = (self.profile_id,)
        rows = self.connection.execute(query, args).fetchall()
        return [dict(row) for row in rows]


def _mutable_player(name, uid, union_name, group_name):
    return {
        "player_name": name,
        "player_uid": uid,
        "union_name": union_name,
        "group_name": group_name,
        "battles": 0,
        "wins": 0,
        "draws": 0,
        "gongxun_total": 0,
        "power_total": 0,
        "battle_city_cnt": 0,
        "main_city_cnt": 0,
        "tear_cnt": 0,
        "attendance_cnt": 0,
        "adjustment_score": 0.0,
    }


def _table_exists(connection, table):
    return (
        connection.execute(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            (table,),
        ).fetchone()
        is not None
    )


def _columns(connection, table):
    if not _table_exists(connection, table):
        return set()
    return {
        row["name"]
        for row in connection.execute(f"PRAGMA table_info({table})").fetchall()
    }
