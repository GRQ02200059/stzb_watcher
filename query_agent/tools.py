import sqlite3
from typing import Any, Callable, Dict, List, Optional


class QueryTools:
    def __init__(
        self,
        get_connection: Callable[[], sqlite3.Connection],
        config_repository=None,
        lineup_service=None,
        world_service_factory=None,
        research_repository=None,
    ) -> None:
        self.get_connection = get_connection
        self.config_repository = config_repository
        self.lineup_service = lineup_service
        self.world_service_factory = world_service_factory
        self.research_repository = research_repository

    def tile(self, wid: int) -> Dict[str, Any]:
        conn = self.get_connection()
        row = _fetch_optional(conn, "SELECT * FROM world_tiles WHERE wid=?", (int(wid),))
        if row is None:
            row = _fetch_optional(conn, "SELECT * FROM map_cells WHERE wid=?", (int(wid),))
        return dict(row) if row is not None else {}

    def armies(
        self,
        army_id: Optional[int] = None,
        wid: Optional[int] = None,
    ) -> List[Dict[str, Any]]:
        conn = self.get_connection()
        where = ["deleted_at_seq IS NULL"]
        args: List[Any] = []
        if army_id is not None:
            where.append("army_id=?")
            args.append(int(army_id))
        if wid is not None:
            where.append("(wid_from=? OR wid_to=? OR reside_wid=? OR stay_wid=?)")
            args.extend([int(wid)] * 4)
        try:
            rows = conn.execute(
                f"""
                SELECT * FROM world_armies
                WHERE {' AND '.join(where)}
                ORDER BY end_time, army_id
                LIMIT 50
                """,
                args,
            ).fetchall()
        except sqlite3.OperationalError:
            rows = conn.execute(
                """
                SELECT * FROM battle_monitor_moves
                ORDER BY arrive_time DESC
                LIMIT 50
                """
            ).fetchall()
        return [dict(row) for row in rows]

    def battle_search(
        self,
        query: str = "",
        wid: Optional[int] = None,
        limit: int = 5,
    ) -> List[Dict[str, Any]]:
        conn = self.get_connection()
        where = ["1=1"]
        args: List[Any] = []
        if query:
            where.append("(atk_name LIKE ? OR def_name LIKE ?)")
            args.extend([f"%{query}%", f"%{query}%"])
        if wid is not None:
            where.append("wid=?")
            args.append(int(wid))
        args.append(int(limit))
        rows = conn.execute(
            f"""
            SELECT battle_id,time,atk_name,def_name,wid,result,atk_gongxun
            FROM battles_v2
            WHERE {' AND '.join(where)}
            ORDER BY time DESC
            LIMIT ?
            """,
            args,
        ).fetchall()
        return [dict(row) for row in rows]

    def alliance_member(self, query: str, limit: int = 5) -> List[Dict[str, Any]]:
        conn = self.get_connection()
        rows = conn.execute(
            """
            SELECT uid,name,group_name,power,wuxun
            FROM team_users
            WHERE name LIKE ?
            ORDER BY power DESC
            LIMIT ?
            """,
            (f"%{query}%", int(limit)),
        ).fetchall()
        return [dict(row) for row in rows]

    def hero_search(self, query: str, limit: int = 5) -> List[Dict[str, Any]]:
        if self.config_repository is None:
            return []
        return self.config_repository.search_heroes(
            query, page=1, size=limit
        )["rows"]

    def skill_search(self, query: str, limit: int = 5) -> List[Dict[str, Any]]:
        if self.config_repository is None:
            return []
        return self.config_repository.search_skills(
            query, page=1, size=limit
        )["rows"]

    def card_pack(
        self,
        pack_id: Optional[int] = None,
        query: str = "",
    ) -> Dict[str, Any]:
        if self.research_repository is None:
            return {}
        if pack_id is not None:
            return self.research_repository.card_pack_detail(int(pack_id)) or {}
        rows = self.research_repository.search_card_packs(
            query=query,
            page=1,
            size=5,
        )["rows"]
        return rows[0] if rows else {}

    def hero_card_packs(self, hero_id: int) -> List[Dict[str, Any]]:
        if self.research_repository is None:
            return []
        return self.research_repository.hero_card_packs(int(hero_id))

    def lineup(self, key: str) -> Dict[str, Any]:
        if self.lineup_service is None:
            return {}
        return self.lineup_service.get_lineup(key) or {}

    def world_summary(self) -> Dict[str, Any]:
        if self.world_service_factory is None:
            return {}
        return self.world_service_factory().summary()

    def explain_risk(self, wid: int) -> Dict[str, Any]:
        if self.world_service_factory is None:
            return {}
        return self.world_service_factory().risk_for_tile(int(wid))


def _fetch_optional(conn: sqlite3.Connection, sql: str, args) -> Optional[sqlite3.Row]:
    try:
        return conn.execute(sql, args).fetchone()
    except sqlite3.OperationalError:
        return None
