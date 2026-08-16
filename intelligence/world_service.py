import json
import math
import sqlite3
import time

from world_scene.state_store import WorldStateStore


def freshness(observed_at_ms: int, now_ms: int) -> str:
    if not observed_at_ms:
        return "unknown"
    age_ms = max(0, now_ms - observed_at_ms)
    if age_ms < 120_000:
        return "fresh"
    if age_ms < 600_000:
        return "aging"
    return "stale"


def decode_resource_level(raw):
    try:
        value = int(raw)
    except (TypeError, ValueError):
        return None, None
    if value <= 0:
        return None, None
    return value // 10, value % 10


class WorldIntelligenceService:
    def __init__(self, conn: sqlite3.Connection, now_ms=None) -> None:
        self.conn = conn
        self.conn.row_factory = sqlite3.Row
        self.now_ms = now_ms or (lambda: int(time.time() * 1000))
        self.state_store = WorldStateStore(conn)
        self.state_store.ensure_schema()

    def envelope(self) -> dict:
        current = self.state_store.current_version()
        baseline = self.conn.execute(
            """
            SELECT version,packet_seq,latest_baseline_order_id,observed_at_ms,
                   completeness,coverage_json
            FROM world_state_versions
            WHERE source_cmd=5026
            ORDER BY version DESC LIMIT 1
            """
        ).fetchone()
        delta = self.conn.execute(
            """
            SELECT version,packet_seq,observed_at_ms
            FROM world_state_versions
            WHERE source_cmd=5028
            ORDER BY version DESC LIMIT 1
            """
        ).fetchone()
        observed_at = current["observed_at_ms"]
        return {
            "worldStateVersion": current["version"],
            "latestBaseline": _version_row(baseline),
            "latestDelta": _version_row(delta),
            "freshness": freshness(observed_at, self.now_ms()),
            "completeness": current["completeness"],
            "coverage": current["coverage"],
        }

    def summary(self) -> dict:
        counts = {}
        for key, table, where in (
            ("tiles", "world_tiles", ""),
            ("armies", "world_armies", " WHERE deleted_at_seq IS NULL"),
            ("marches", "world_real_marches", ""),
            ("events", "world_state_events", ""),
        ):
            counts[key] = self.conn.execute(
                f"SELECT COUNT(*) FROM {table}{where}"
            ).fetchone()[0]
        focus = self.conn.execute(
            """
            SELECT wid,row,col FROM world_tiles
            ORDER BY source_seq DESC,wid DESC
            LIMIT 1
            """
        ).fetchone()
        bounds = self.conn.execute(
            """
            SELECT MIN(row) AS row_up,MAX(row) AS row_down,
                   MIN(col) AS col_left,MAX(col) AS col_right
            FROM world_tiles
            """
        ).fetchone()
        data_bounds = None
        focus_wid = None
        suggested_bounds = None
        if focus is not None and bounds is not None and bounds["row_up"] is not None:
            focus_wid = int(focus["wid"])
            data_bounds = {
                "rowUp": int(bounds["row_up"]),
                "rowDown": int(bounds["row_down"]),
                "colLeft": int(bounds["col_left"]),
                "colRight": int(bounds["col_right"]),
            }
            suggested_bounds = _centered_bounds(
                int(focus["row"]),
                int(focus["col"]),
                size=20,
            )
        return {
            **self.envelope(),
            "counts": counts,
            "dataBounds": data_bounds,
            "focusWid": focus_wid,
            "suggestedBounds": suggested_bounds,
        }

    def viewport(self, row_up, row_down, col_left, col_right) -> dict:
        rows = self.conn.execute(
            """
            SELECT * FROM world_tiles
            WHERE row BETWEEN ? AND ? AND col BETWEEN ? AND ?
            ORDER BY row,col
            """,
            (row_up, row_down, col_left, col_right),
        ).fetchall()
        return {
            **self.envelope(),
            "tiles": [self._tile_projection(dict(row)) for row in rows],
        }

    def overview(
        self,
        row_up,
        row_down,
        col_left,
        col_right,
        bucket_rows,
        bucket_cols,
    ) -> dict:
        row_up = int(row_up)
        row_down = int(row_down)
        col_left = int(col_left)
        col_right = int(col_right)
        bucket_rows = int(bucket_rows)
        bucket_cols = int(bucket_cols)
        if min(row_up, row_down, col_left, col_right) < 0:
            raise ValueError("bounds must be non-negative")
        if row_up > row_down or col_left > col_right:
            raise ValueError("invalid bounds")
        if bucket_rows <= 0 or bucket_cols <= 0:
            raise ValueError("bucket dimensions must be positive")
        grid_rows = math.ceil((row_down - row_up + 1) / bucket_rows)
        grid_cols = math.ceil((col_right - col_left + 1) / bucket_cols)
        if grid_rows * grid_cols > 2500:
            raise ValueError("bucket grid exceeds 2500")

        rows = [
            dict(row)
            for row in self.conn.execute(
                """
                SELECT * FROM world_tiles
                WHERE row BETWEEN ? AND ? AND col BETWEEN ? AND ?
                ORDER BY row,col,wid
                """,
                (row_up, row_down, col_left, col_right),
            ).fetchall()
        ]
        identity = self._current_identity()
        army_counts = {
            int(row["wid_to"]): int(row["count"])
            for row in self.conn.execute(
                """
                SELECT wid_to,COUNT(*) AS count
                FROM world_armies
                WHERE deleted_at_seq IS NULL
                  AND wid_to BETWEEN ? AND ?
                GROUP BY wid_to
                """,
                (row_up * 10000 + col_left, row_down * 10000 + col_right),
            ).fetchall()
        }
        event_counts = {}
        for event in self.conn.execute(
            """
            SELECT entity_id,COUNT(*) AS count
            FROM world_state_events
            WHERE entity_type='tile'
            GROUP BY entity_id
            """
        ).fetchall():
            try:
                event_counts[int(event["entity_id"])] = int(event["count"])
            except (TypeError, ValueError):
                continue

        buckets = {}
        for tile in rows:
            bucket_row = (int(tile["row"]) - row_up) // bucket_rows
            bucket_col = (int(tile["col"]) - col_left) // bucket_cols
            key = (bucket_row, bucket_col)
            if key not in buckets:
                start_row = row_up + bucket_row * bucket_rows
                start_col = col_left + bucket_col * bucket_cols
                buckets[key] = {
                    "rowUp": start_row,
                    "rowDown": min(row_down, start_row + bucket_rows - 1),
                    "colLeft": start_col,
                    "colRight": min(col_right, start_col + bucket_cols - 1),
                    "tileCount": 0,
                    "riskMax": 0,
                    "riskAverage": 0.0,
                    "selfCount": 0,
                    "allyCount": 0,
                    "enemyCount": 0,
                    "unknownCount": 0,
                    "unownedCount": 0,
                    "armyCount": 0,
                    "changeCount": 0,
                    "focusWid": 0,
                    "_riskTotal": 0,
                    "_focusOrder": (-1, -1, -1),
                }
            bucket = buckets[key]
            wid = int(tile["wid"])
            relation = self._ownership_relation(
                int(tile.get("user_id") or 0),
                int(tile.get("union_id") or 0),
                identity,
            )["relation"]
            relation_key = {
                "self": "selfCount",
                "ally": "allyCount",
                "enemy": "enemyCount",
                "unknown": "unknownCount",
                "unowned": "unownedCount",
            }.get(relation, "unknownCount")
            risk = self.risk_for_tile(wid)
            score = int(risk.get("score") or 0)
            bucket["tileCount"] += 1
            bucket[relation_key] += 1
            bucket["riskMax"] = max(bucket["riskMax"], score)
            bucket["_riskTotal"] += score
            bucket["armyCount"] += army_counts.get(wid, 0)
            bucket["changeCount"] += event_counts.get(wid, 0)
            focus_order = (score, int(tile.get("source_seq") or 0), wid)
            if focus_order > bucket["_focusOrder"]:
                bucket["_focusOrder"] = focus_order
                bucket["focusWid"] = wid

        result_buckets = []
        for key in sorted(buckets):
            bucket = buckets[key]
            bucket["riskAverage"] = round(
                bucket.pop("_riskTotal") / bucket["tileCount"], 1
            )
            bucket.pop("_focusOrder")
            result_buckets.append(bucket)
        return {
            **self.envelope(),
            "dataBounds": self._data_bounds(),
            "bucketRows": bucket_rows,
            "bucketCols": bucket_cols,
            "buckets": result_buckets,
        }

    def tile_detail(self, wid: int) -> dict:
        row = self.conn.execute(
            "SELECT * FROM world_tiles WHERE wid=?", (wid,)
        ).fetchone()
        if row is None:
            return {**self.envelope(), "tile": None, "incomingArmies": []}
        armies = self.conn.execute(
            """
            SELECT a.*,u.name AS owner_name,
                   COALESCE(un.name,u.union_name) AS owner_union_name
            FROM world_armies a
            LEFT JOIN world_map_users u ON u.user_id=a.user_id
            LEFT JOIN world_unions un ON un.union_id=u.union_id
            WHERE a.deleted_at_seq IS NULL AND
                  (a.wid_to=? OR a.reside_wid=? OR a.stay_wid=?)
            ORDER BY a.end_time,a.army_id
            """,
            (wid, wid, wid),
        ).fetchall()
        events = self.events(entity_id=str(wid), limit=20)
        return {
            **self.envelope(),
            "tile": self._tile_projection(dict(row)),
            "incomingArmies": [dict(item) for item in armies],
            "risk": self.risk_for_tile(wid),
            "battleStats": self._tile_battle_stats(wid),
            "events": events,
        }

    def risk_for_tile(self, wid: int) -> dict:
        row = self.conn.execute(
            "SELECT * FROM world_tiles WHERE wid=?", (wid,)
        ).fetchone()
        if row is None:
            return {
                "wid": wid,
                "score": 0,
                "components": {},
                "unknownComponents": ["tile"],
                "confidence": 0.0,
            }
        tile = self._tile_projection(dict(row))
        armies = [
            dict(item)
            for item in self.conn.execute(
            """
            SELECT * FROM world_armies
            WHERE deleted_at_seq IS NULL AND wid_to=?
            ORDER BY end_time
            """,
            (wid,),
            ).fetchall()
        ]
        identity = self._current_identity()
        ownership = self._ownership_relation(
            int(tile.get("user_id") or 0),
            int(tile.get("union_id") or 0),
            identity,
        )
        army_relations = [
            (
                army,
                self._user_relation(int(army.get("user_id") or 0), identity),
            )
            for army in armies
        ]
        enemy_armies = [
            army for army, relation in army_relations if relation == "enemy"
        ]
        level = tile.get("landLevel")
        unknown = []
        components = {
            "landLevel": min(25, int(level or 0) * 3) if level else 0,
            "enemyOwnership": 15 if ownership["relation"] == "enemy" else 0,
            "incomingArmyCount": min(20, len(enemy_armies) * 7),
            "earliestArrival": 0,
            "estimatedTroops": 0,
            "protectionGuard": 0,
            "staleIntel": 0,
        }
        if level is None:
            unknown.append("landLevel")
        if ownership["relation"] == "unknown":
            unknown.append("enemyOwnership")
        if identity is None and armies:
            unknown.append("incomingArmyCount")
        if enemy_armies:
            arrival = min(int(item["end_time"] or 0) for item in enemy_armies)
            remaining = arrival - self.now_ms() // 1000
            components["earliestArrival"] = (
                15 if remaining <= 300 else 10 if remaining <= 900 else 5
            )
        elif identity is None and armies:
            unknown.append("earliestArrival")
        unknown.append("estimatedTroops")
        now_sec = self.now_ms() // 1000
        if int(tile.get("protect_end_time") or 0) > now_sec:
            components["protectionGuard"] = 5
        elif int(tile.get("guard_end_time") or 0) > now_sec:
            components["protectionGuard"] = 5
        tile_freshness = tile["freshness"]
        if tile_freshness == "stale":
            components["staleIntel"] = 10
        elif tile_freshness == "unknown":
            unknown.append("staleIntel")
        score = min(100, sum(components.values()))
        confidence = round(max(0.0, 1.0 - len(set(unknown)) / 7), 2)
        return {
            "wid": wid,
            "score": score,
            "level": "high" if score >= 70 else "medium" if score >= 40 else "low",
            "components": components,
            "ownership": ownership,
            "incomingArmyRelations": {
                "enemy": len(enemy_armies),
                "ally": sum(
                    1 for _, relation in army_relations if relation == "ally"
                ),
                "self": sum(
                    1 for _, relation in army_relations if relation == "self"
                ),
                "unknown": sum(
                    1 for _, relation in army_relations if relation == "unknown"
                ),
            },
            "unknownComponents": sorted(set(unknown)),
            "confidence": confidence,
            "freshness": tile_freshness,
        }

    def risks(self, row_up, row_down, col_left, col_right) -> list:
        rows = self.conn.execute(
            """
            SELECT wid FROM world_tiles
            WHERE row BETWEEN ? AND ? AND col BETWEEN ? AND ?
            ORDER BY row,col
            """,
            (row_up, row_down, col_left, col_right),
        ).fetchall()
        return sorted(
            (self.risk_for_tile(row["wid"]) for row in rows),
            key=lambda item: (-item["score"], item["wid"]),
        )

    def events(self, since_version=0, event_type=None, entity_id=None, limit=100):
        where = ["state_version>?"]
        args = [int(since_version)]
        if event_type:
            where.append("event_type=?")
            args.append(event_type)
        if entity_id:
            where.append("entity_id=?")
            args.append(str(entity_id))
        args.append(min(200, max(1, int(limit))))
        rows = self.conn.execute(
            f"""
            SELECT * FROM world_state_events
            WHERE {' AND '.join(where)}
            ORDER BY seq DESC LIMIT ?
            """,
            args,
        ).fetchall()
        result = []
        for row in rows:
            item = dict(row)
            item["evidence"] = json.loads(item.pop("evidence_json") or "{}")
            item["diff"] = json.loads(item.pop("diff_json") or "{}")
            result.append(item)
        return result

    def _tile_projection(self, row):
        chunk = self.conn.execute(
            """
            SELECT raw_json,observed_at_ms FROM world_tile_chunks
            WHERE wid=? AND chunk_type='8'
            """,
            (row["wid"],),
        ).fetchone()
        level = resource = None
        observed_at = 0
        if chunk is not None:
            try:
                raw = json.loads(chunk["raw_json"])
            except Exception:
                raw = None
            level, resource = decode_resource_level(raw)
            observed_at = int(chunk["observed_at_ms"] or 0)
        if not observed_at:
            packet = self.conn.execute(
                """
                SELECT observed_at_ms FROM world_scene_packets
                WHERE seq=? LIMIT 1
                """,
                (row["source_seq"],),
            ).fetchone()
            observed_at = int(packet["observed_at_ms"] or 0) if packet else 0
        return {
            **row,
            "landLevel": level,
            "resourceKind": resource,
            "observedAtMs": observed_at,
            "freshness": freshness(observed_at, self.now_ms()),
        }

    def _data_bounds(self):
        row = self.conn.execute(
            """
            SELECT MIN(row) AS row_up,MAX(row) AS row_down,
                   MIN(col) AS col_left,MAX(col) AS col_right
            FROM world_tiles
            """
        ).fetchone()
        if row is None or row["row_up"] is None:
            return None
        return {
            "rowUp": int(row["row_up"]),
            "rowDown": int(row["row_down"]),
            "colLeft": int(row["col_left"]),
            "colRight": int(row["col_right"]),
        }

    def _tile_battle_stats(self, wid):
        empty = {
            "evidenceClass": "BATTLE_STAT",
            "sampleSize": 0,
            "attackWins": 0,
            "attackDraws": 0,
            "attackLosses": 0,
            "attackWinRate": 0.0,
            "recentBattles": [],
            "commonLineups": [],
        }
        if not _table_exists(self.conn, "battles_v2"):
            return empty
        try:
            rows = self.conn.execute(
                """
                SELECT battle_id,time,result,atk_name,def_name
                FROM battles_v2 WHERE wid=?
                ORDER BY time DESC,battle_id DESC
                """,
                (int(wid),),
            ).fetchall()
        except sqlite3.OperationalError:
            return empty
        wins = sum(1 for row in rows if int(row["result"] or 0) in {1, 7, 11})
        losses = sum(1 for row in rows if int(row["result"] or 0) in {2, 6, 12})
        draws = len(rows) - wins - losses
        common_lineups = []
        if rows and _table_exists(self.conn, "battle_heroes"):
            lineup_rows = self.conn.execute(
                """
                SELECT bv.battle_id,bh.pos,bh.hero_id,bh.hero_name
                FROM battles_v2 bv
                JOIN battle_heroes bh
                  ON bh.battle_id=bv.battle_id AND bh.side='atk'
                WHERE bv.wid=? AND COALESCE(bh.hero_id,0)>0
                ORDER BY bv.battle_id,bh.pos,bh.id
                """,
                (int(wid),),
            ).fetchall()
            grouped = {}
            for row in lineup_rows:
                grouped.setdefault(int(row["battle_id"]), []).append(dict(row))
            counts = {}
            names = {}
            for heroes in grouped.values():
                by_position = {}
                for hero in heroes:
                    by_position.setdefault(int(hero["pos"]), hero)
                ordered = [by_position[position] for position in sorted(by_position)]
                if len(ordered) != 3:
                    continue
                hero_ids = [int(hero["hero_id"]) for hero in ordered]
                if len(set(hero_ids)) != 3:
                    continue
                key = ".".join(str(hero_id) for hero_id in hero_ids)
                counts[key] = counts.get(key, 0) + 1
                names[key] = [hero["hero_name"] for hero in ordered]
            common_lineups = [
                {"key": key, "names": names[key], "sampleSize": sample_size}
                for key, sample_size in sorted(
                    counts.items(), key=lambda item: (-item[1], item[0])
                )[:10]
            ]
        sample_size = len(rows)
        return {
            **empty,
            "sampleSize": sample_size,
            "attackWins": wins,
            "attackDraws": draws,
            "attackLosses": losses,
            "attackWinRate": (
                round((wins + draws * 0.5) * 100.0 / sample_size, 1)
                if sample_size
                else 0.0
            ),
            "recentBattles": [dict(row) for row in rows[:10]],
            "commonLineups": common_lineups,
        }

    def _current_identity(self):
        if not _table_exists(self.conn, "player_self"):
            return None
        columns = {
            row["name"]
            for row in self.conn.execute(
                "PRAGMA table_info(player_self)"
            ).fetchall()
        }
        selected = [
            column
            for column in ("uid", "role_id", "name", "union_name")
            if column in columns
        ]
        if not selected:
            return None
        order = " ORDER BY id DESC" if "id" in columns else ""
        row = self.conn.execute(
            f"SELECT {','.join(selected)} FROM player_self{order} LIMIT 1"
        ).fetchone()
        if row is None:
            return None
        player = dict(row)
        if not any(str(value or "").strip() for value in player.values()):
            return None
        users = [
            dict(item)
            for item in self.conn.execute(
                """
                SELECT user_id,name,role_id,union_id,union_name
                FROM world_map_users
                """
            ).fetchall()
        ]
        identifiers = {
            str(player.get(key) or "").strip()
            for key in ("uid", "role_id")
            if str(player.get(key) or "").strip()
        }
        matched = next(
            (
                user
                for user in users
                if str(user.get("user_id") or "") in identifiers
                or str(user.get("role_id") or "") in identifiers
            ),
            None,
        )
        if matched is None and str(player.get("name") or "").strip():
            name = str(player["name"]).strip()
            matched = next(
                (user for user in users if str(user.get("name") or "").strip() == name),
                None,
            )
        if matched is not None:
            return {
                "userId": int(matched.get("user_id") or 0),
                "roleId": int(matched.get("role_id") or 0),
                "unionId": int(matched.get("union_id") or 0),
                "unionName": matched.get("union_name") or player.get("union_name") or "",
                "source": "player_self+world_map_users",
            }
        union_name = str(player.get("union_name") or "").strip()
        union_id = 0
        if union_name:
            union = self.conn.execute(
                "SELECT union_id FROM world_unions WHERE name=? LIMIT 1",
                (union_name,),
            ).fetchone()
            union_id = int(union["union_id"] or 0) if union else 0
        if union_id:
            return {
                "userId": 0,
                "roleId": 0,
                "unionId": union_id,
                "unionName": union_name,
                "source": "player_self+world_unions",
            }
        return None

    def _ownership_relation(self, user_id, union_id, identity):
        if user_id <= 0:
            return {
                "relation": "unowned",
                "ownerUserId": 0,
                "ownerUnionId": int(union_id or 0),
                "identitySource": identity["source"] if identity else "",
            }
        relation = self._relation(user_id, union_id, identity)
        return {
            "relation": relation,
            "ownerUserId": int(user_id),
            "ownerUnionId": int(union_id or 0),
            "identitySource": identity["source"] if identity else "",
        }

    def _user_relation(self, user_id, identity):
        if user_id <= 0 or identity is None:
            return "unknown"
        row = self.conn.execute(
            "SELECT union_id FROM world_map_users WHERE user_id=?",
            (int(user_id),),
        ).fetchone()
        union_id = int(row["union_id"] or 0) if row else 0
        return self._relation(user_id, union_id, identity)

    @staticmethod
    def _relation(user_id, union_id, identity):
        if identity is None:
            return "unknown"
        if identity["userId"] and int(user_id) == identity["userId"]:
            return "self"
        if (
            identity["unionId"]
            and int(union_id or 0)
            and int(union_id) == identity["unionId"]
        ):
            return "ally"
        return "enemy"


def _version_row(row):
    if row is None:
        return None
    item = dict(row)
    if "coverage_json" in item:
        item["coverage"] = json.loads(item.pop("coverage_json") or "null")
    return item


def _table_exists(conn, name):
    return (
        conn.execute(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            (name,),
        ).fetchone()
        is not None
    )


def _centered_bounds(row, col, size=20):
    size = min(40, max(5, int(size)))
    row_up = max(0, int(row) - size // 2)
    col_left = max(0, int(col) - size // 2)
    return {
        "rowUp": row_up,
        "rowDown": row_up + size - 1,
        "colLeft": col_left,
        "colRight": col_left + size - 1,
    }
