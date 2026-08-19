import json
import math
from datetime import datetime, timedelta

from .models import ScoreRule


CUSTOM_SCORE_COLUMNS = {
    "rule_version_id": "INTEGER",
    "draws": "INTEGER DEFAULT 0",
    "attendance_cnt": "INTEGER DEFAULT 0",
    "battle_score": "REAL DEFAULT 0",
    "siege_score": "REAL DEFAULT 0",
    "adjustment_score": "REAL DEFAULT 0",
    "breakdown_json": "TEXT DEFAULT '{}'",
    "calculated_at": "TEXT",
}


class ScoreRepository:
    def __init__(self, connection):
        self.connection = connection
        self.connection.row_factory = __import__("sqlite3").Row

    def ensure_schema(self):
        self.connection.execute(
            """
            CREATE TABLE IF NOT EXISTS wuxun_weekly_snapshots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id TEXT NOT NULL DEFAULT '',
                week_start TEXT NOT NULL,
                uid TEXT NOT NULL DEFAULT '',
                player_name TEXT NOT NULL,
                union_name TEXT NOT NULL DEFAULT '',
                group_name TEXT NOT NULL DEFAULT '',
                wuxun INTEGER NOT NULL DEFAULT 0,
                captured_at TEXT NOT NULL,
                UNIQUE(profile_id, week_start, uid, player_name)
            )
            """
        )
        self.connection.execute(
            "CREATE INDEX IF NOT EXISTS idx_wuxun_weekly_player "
            "ON wuxun_weekly_snapshots(week_start, player_name)"
        )
        snapshot_columns = _columns(self.connection, "wuxun_weekly_snapshots")
        if snapshot_columns and "profile_id" not in snapshot_columns:
            self.connection.execute("ALTER TABLE wuxun_weekly_snapshots RENAME TO wuxun_weekly_snapshots_legacy")
            self.connection.execute(
                """
                CREATE TABLE wuxun_weekly_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    profile_id TEXT NOT NULL DEFAULT '',
                    week_start TEXT NOT NULL,
                    uid TEXT NOT NULL DEFAULT '',
                    player_name TEXT NOT NULL,
                    union_name TEXT NOT NULL DEFAULT '',
                    group_name TEXT NOT NULL DEFAULT '',
                    wuxun INTEGER NOT NULL DEFAULT 0,
                    captured_at TEXT NOT NULL,
                    UNIQUE(profile_id, week_start, uid, player_name)
                )
                """
            )
            self.connection.execute(
                """
                INSERT INTO wuxun_weekly_snapshots(
                    id,profile_id,week_start,uid,player_name,union_name,group_name,wuxun,captured_at
                )
                SELECT id,'',week_start,uid,player_name,union_name,group_name,wuxun,captured_at
                FROM wuxun_weekly_snapshots_legacy
                """
            )
            self.connection.execute("DROP TABLE wuxun_weekly_snapshots_legacy")
        self.connection.executescript(
            """
            CREATE TABLE IF NOT EXISTS score_rule_versions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                season_id TEXT NOT NULL,
                version INTEGER NOT NULL,
                name TEXT NOT NULL,
                preset_key TEXT NOT NULL,
                config_json TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'draft',
                created_at TEXT NOT NULL,
                activated_at TEXT,
                UNIQUE(season_id, version)
            );
            CREATE INDEX IF NOT EXISTS idx_score_rules_season_status
            ON score_rule_versions(season_id, status);

            CREATE TABLE IF NOT EXISTS score_adjustments(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                season_id TEXT NOT NULL,
                player_name TEXT NOT NULL,
                player_uid TEXT,
                points REAL NOT NULL,
                reason TEXT NOT NULL,
                created_by TEXT,
                created_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_score_adjustments_season_player
            ON score_adjustments(season_id, player_name);
            """
        )
        existing = {
            row["name"]
            for row in self.connection.execute(
                "PRAGMA table_info(custom_scores)"
            ).fetchall()
        }
        for name, definition in CUSTOM_SCORE_COLUMNS.items():
            if name not in existing:
                self.connection.execute(
                    f"ALTER TABLE custom_scores ADD COLUMN {name} {definition}"
                )
        self.connection.commit()

    def create_rule(self, season_id, name, preset_key, config):
        rule = ScoreRule.from_mapping(config)
        now = _now()
        version = self.connection.execute(
            """
            SELECT COALESCE(MAX(version),0)+1
            FROM score_rule_versions WHERE season_id=?
            """,
            (season_id,),
        ).fetchone()[0]
        cursor = self.connection.execute(
            """
            INSERT INTO score_rule_versions(
                season_id,version,name,preset_key,config_json,status,created_at
            ) VALUES(?,?,?,?,?,'draft',?)
            """,
            (
                str(season_id),
                int(version),
                str(name).strip(),
                str(preset_key),
                json.dumps(rule.to_json(), sort_keys=True),
                now,
            ),
        )
        self.connection.commit()
        return self.get_rule(cursor.lastrowid)

    def capture_sunday_wuxun_snapshot(self, now=None, profile_id=''):
        """Save the alliance-member wuxun before the weekly reset."""
        now = now or datetime.now()
        if now.weekday() != 6 or not _table_exists(self.connection, "team_users"):
            return 0
        columns = _columns(self.connection, "team_users")
        if not {"uid", "name", "wuxun"}.issubset(columns):
            return 0
        week_start = (now - timedelta(days=6)).date().isoformat()
        captured_at = now.isoformat(timespec="seconds")
        profile_id = str(profile_id or '')
        selected = ["uid", "name", "wuxun"] + [
            name for name in ("union_name", "group_name") if name in columns
        ]
        query = f"SELECT {','.join(selected)} FROM team_users"
        args = ()
        if "profile_id" in columns:
            query += " WHERE profile_id=?"
            args = (profile_id,)
        rows = self.connection.execute(query, args).fetchall()
        for row in rows:
            player_name = str(row["name"] or "")
            if not player_name:
                continue
            self.connection.execute(
                """
                INSERT INTO wuxun_weekly_snapshots(
                    profile_id,week_start,uid,player_name,union_name,group_name,wuxun,captured_at
                ) VALUES(?,?,?,?,?,?,?,?)
                ON CONFLICT(profile_id,week_start,uid,player_name) DO UPDATE SET
                    union_name=excluded.union_name,
                    group_name=excluded.group_name,
                    wuxun=CASE WHEN wuxun > 0 THEN wuxun ELSE excluded.wuxun END,
                    captured_at=excluded.captured_at
                """,
                (
                    profile_id,
                    week_start,
                    str(row["uid"] or ""),
                    player_name,
                    str(row["union_name"] or "") if "union_name" in columns else "",
                    str(row["group_name"] or "") if "group_name" in columns else "",
                    int(row["wuxun"] or 0),
                    captured_at,
                ),
            )
        self.connection.commit()
        return len(rows)

    def cumulative_wuxun_snapshots(self, now=None, profile_id=''):
        now = now or datetime.now()
        week_start = (now - timedelta(days=now.weekday())).date().isoformat()
        rows = self.connection.execute(
            "SELECT uid,player_name,MAX(union_name) AS union_name,"
            "MAX(group_name) AS group_name,SUM(wuxun) AS wuxun "
            "FROM wuxun_weekly_snapshots WHERE profile_id=? AND week_start<=? "
            "GROUP BY uid,player_name",
            (str(profile_id or ''), week_start),
        ).fetchall()
        result = {}
        for row in rows:
            key = str(row["uid"] or row["player_name"] or "")
            result[key] = dict(row)
        return result

    def get_rule(self, rule_id):
        row = self.connection.execute(
            "SELECT * FROM score_rule_versions WHERE id=?", (int(rule_id),)
        ).fetchone()
        return _rule_row(row) if row else None

    def list_rules(self, season_id):
        rows = self.connection.execute(
            """
            SELECT * FROM score_rule_versions
            WHERE season_id=? ORDER BY version DESC
            """,
            (str(season_id),),
        ).fetchall()
        return [_rule_row(row) for row in rows]

    def activate_rule(self, rule_id):
        rule = self.get_rule(rule_id)
        if rule is None:
            raise ValueError("score rule not found")
        self.connection.execute(
            """
            UPDATE score_rule_versions SET status='retired'
            WHERE season_id=? AND status='active'
            """,
            (rule["season_id"],),
        )
        self.connection.execute(
            """
            UPDATE score_rule_versions
            SET status='active',activated_at=?
            WHERE id=?
            """,
            (_now(), int(rule_id)),
        )
        self.connection.commit()
        return self.get_rule(rule_id)

    def active_rule(self, season_id):
        row = self.connection.execute(
            """
            SELECT * FROM score_rule_versions
            WHERE season_id=? AND status='active'
            ORDER BY version DESC LIMIT 1
            """,
            (str(season_id),),
        ).fetchone()
        return _rule_row(row) if row else None

    def add_adjustment(
        self,
        season_id,
        player_name,
        player_uid,
        points,
        reason,
        created_by,
    ):
        reason = str(reason or "").strip()
        player_name = str(player_name or "").strip()
        number = float(points)
        if not player_name:
            raise ValueError("player name is required")
        if not reason:
            raise ValueError("adjustment reason is required")
        if not math.isfinite(number) or number == 0:
            raise ValueError("adjustment points must be finite and non-zero")
        cursor = self.connection.execute(
            """
            INSERT INTO score_adjustments(
                season_id,player_name,player_uid,points,reason,created_by,created_at
            ) VALUES(?,?,?,?,?,?,?)
            """,
            (
                str(season_id),
                player_name,
                str(player_uid or ""),
                number,
                reason,
                str(created_by or ""),
                _now(),
            ),
        )
        self.connection.commit()
        return dict(
            self.connection.execute(
                "SELECT * FROM score_adjustments WHERE id=?",
                (cursor.lastrowid,),
            ).fetchone()
        )

    def list_adjustments(self, season_id, player_name=None):
        where = ["season_id=?"]
        args = [str(season_id)]
        if player_name:
            where.append("player_name=?")
            args.append(str(player_name))
        rows = self.connection.execute(
            f"""
            SELECT * FROM score_adjustments
            WHERE {' AND '.join(where)}
            ORDER BY created_at DESC,id DESC
            """,
            args,
        ).fetchall()
        return [dict(row) for row in rows]

    def delete_adjustment(self, adjustment_id, season_id):
        row = self.connection.execute(
            "SELECT season_id FROM score_adjustments WHERE id=?",
            (int(adjustment_id),),
        ).fetchone()
        if row is None:
            raise ValueError("adjustment not found")
        if row["season_id"] != str(season_id):
            raise ValueError("adjustment season mismatch")
        self.connection.execute(
            "DELETE FROM score_adjustments WHERE id=?", (int(adjustment_id),)
        )
        self.connection.commit()

    def replace_scores(self, season_id, rule_version_id, rows):
        self.connection.execute(
            "DELETE FROM custom_scores WHERE season_id=?", (str(season_id),)
        )
        calculated_at = _now()
        for row in rows:
            metrics = row["metrics"]
            self.connection.execute(
                """
                INSERT INTO custom_scores(
                    season_id,player_name,player_uid,union_name,
                    battles,wins,draws,gongxun_total,power_total,
                    main_city_cnt,tear_cnt,attendance_cnt,
                    battle_score,siege_score,adjustment_score,score,
                    rule_version_id,breakdown_json,updated_at,calculated_at
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    str(season_id),
                    row["playerName"],
                    row.get("playerUid", ""),
                    row.get("unionName", ""),
                    metrics.get("battles", 0),
                    metrics.get("wins", 0),
                    metrics.get("draws", 0),
                    metrics.get("gongxunTotal", 0),
                    metrics.get("powerTotal", 0),
                    metrics.get("mainCityCnt", 0),
                    metrics.get("tearCnt", 0),
                    metrics.get("attendanceCnt", 0),
                    row.get("battleScore", 0),
                    row.get("siegeScore", 0),
                    row.get("adjustmentScore", 0),
                    row.get("score", 0),
                    int(rule_version_id),
                    json.dumps(row.get("breakdown", {}), ensure_ascii=False),
                    calculated_at,
                    calculated_at,
                ),
            )
        self.connection.commit()

    def list_scores(self, season_id):
        rows = self.connection.execute(
            """
            SELECT * FROM custom_scores
            WHERE season_id=?
            ORDER BY score DESC,battle_score DESC,siege_score DESC,player_name
            """,
            (str(season_id),),
        ).fetchall()
        return [_score_row(row) for row in rows]

    def score_detail(self, season_id, player_name):
        row = self.connection.execute(
            """
            SELECT * FROM custom_scores
            WHERE season_id=? AND player_name=?
            """,
            (str(season_id), str(player_name)),
        ).fetchone()
        return _score_row(row) if row else None


def _rule_row(row):
    item = dict(row)
    item["config"] = json.loads(item.pop("config_json") or "{}")
    return item


def _score_row(row):
    item = dict(row)
    item["breakdown"] = json.loads(item.pop("breakdown_json") or "{}")
    return item


def _now():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _table_exists(connection, table):
    return connection.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
        (table,),
    ).fetchone() is not None


def _columns(connection, table):
    if not _table_exists(connection, table):
        return set()
    return {
        row["name"]
        for row in connection.execute(f"PRAGMA table_info({table})").fetchall()
    }
