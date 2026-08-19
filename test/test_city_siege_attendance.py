import json
import os
import sqlite3
import tempfile
import unittest
from unittest.mock import patch

import api_server
import realtime_writer


class CitySiegeDefensePhaseTest(unittest.TestCase):
    def test_classifies_normal_city_guard_as_main_and_last_guard_as_tear(self):
        normal = {
            "city_type": 8,
            "is_npc": 1,
            "world_npc_army": "1715178,17020707,0,0",
            "defend_all_hero_info": (
                "100040,20,3000,0,1746;"
                "100529,20,3000,0,1152;"
                "100089,20,3000,0,1546;"
            ),
            "def_hp": 9000,
        }
        last = {
            "city_type": 8,
            "is_npc": 1,
            "world_npc_army": "1715180,17020711,1786664272,1791",
            "defend_all_hero_info": (
                "100359,20,1791,0,1020;"
                "0,0,0,0,0;"
                "0,0,0,0,0;"
            ),
            "def_hp": 1791,
        }

        self.assertEqual(
            realtime_writer.classify_city_siege_defense(normal),
            realtime_writer.CITY_SIEGE_NORMAL_GUARD,
        )
        self.assertEqual(
            realtime_writer.attendance_role_for_city_siege_defense(normal),
            "main",
        )
        self.assertEqual(
            realtime_writer.classify_city_siege_defense(last),
            realtime_writer.CITY_SIEGE_LAST_GUARD,
        )
        self.assertEqual(
            realtime_writer.attendance_role_for_city_siege_defense(last),
            "tear",
        )

    def test_writer_persists_phase_and_attendance_role_from_defender_fields(self):
        with tempfile.NamedTemporaryFile(suffix=".db") as tmp:
            conn = sqlite3.connect(tmp.name)
            conn.executescript(
                """
                CREATE TABLE battles_v2 (
                    battle_id INTEGER PRIMARY KEY,
                    time INTEGER,
                    city_type INTEGER,
                    is_npc INTEGER,
                    world_npc_army TEXT,
                    defend_all_hero_info TEXT,
                    def_hp INTEGER,
                    defense_phase TEXT,
                    atk_name TEXT,
                    atk_uid TEXT,
                    atk_union TEXT,
                    atk_gongxun INTEGER,
                    atk_power INTEGER,
                    def_level INTEGER,
                    fight_type INTEGER,
                    result INTEGER,
                    wid INTEGER,
                    source_file TEXT
                );
                CREATE TABLE attendance (
                    battle_id INTEGER,
                    time INTEGER,
                    player_name TEXT,
                    player_uid TEXT,
                    union_name TEXT,
                    fight_type INTEGER,
                    wid INTEGER,
                    gongxun INTEGER,
                    result INTEGER,
                    role TEXT,
                    profile_id TEXT
                );
                CREATE TABLE battle_heroes (
                    battle_id INTEGER,
                    side TEXT,
                    pos INTEGER,
                    hero_id INTEGER,
                    hero_name TEXT,
                    level INTEGER,
                    max_hp INTEGER,
                    remain_hp INTEGER,
                    damage_taken INTEGER
                );
                """
            )

            def battle(battle_id, npc_army, defender_info, defender_hp):
                return {
                    "battle_id": battle_id,
                    "time": 2000 + battle_id,
                    "city_type": 8,
                    "is_npc": 1,
                    "world_npc_army": npc_army,
                    "defend_all_hero_info": defender_info,
                    "def_hp": defender_hp,
                    "atk_name": "玩家甲",
                    "atk_uid": "role-a",
                    "atk_union": "甲盟",
                    "atk_gongxun": 0,
                    "atk_power": 0,
                    "def_level": 8,
                    "fight_type": 0,
                    "result": 1,
                    "wid": 777,
                    "source_file": "fixture",
                    "atk_idu": "",
                    "def_idu": "",
                    "atk_heroes": [],
                    "def_heroes": [],
                }

            normal = battle(1, "1,17020707,0,0", "100040,20,3000,0,0;100529,20,3000,0,0;100089,20,3000,0,0;", 9000)
            last = battle(2, "2,17020711,1786664272,1791", "100359,20,1791,0,0;0,0,0,0,0;0,0,0,0,0;", 1791)

            self.assertTrue(realtime_writer.upsert_battle_0a(conn, normal))
            self.assertTrue(realtime_writer.upsert_battle_0a(conn, last))
            rows = conn.execute(
                "SELECT defense_phase FROM battles_v2 ORDER BY battle_id"
            ).fetchall()
            roles = conn.execute(
                "SELECT role FROM attendance ORDER BY battle_id"
            ).fetchall()
            conn.close()

        self.assertEqual([row[0] for row in rows], [
            "normal_city_guard",
            "last_city_guard",
        ])
        self.assertEqual([row[0] for row in roles], ["main", "tear"])


class CitySiegeTaskStatisticsTest(unittest.TestCase):
    def setUp(self):
        fd, self.db_path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        conn = sqlite3.connect(self.db_path)
        conn.executescript(
            """
            CREATE TABLE tasks (
                id INTEGER PRIMARY KEY,
                name TEXT,
                time INTEGER,
                pos TEXT,
                user_list TEXT,
                complete_user_num INTEGER DEFAULT 0,
                status INTEGER DEFAULT 0
            );
            CREATE TABLE battles_v2 (
                battle_id INTEGER PRIMARY KEY,
                wid INTEGER,
                atk_name TEXT,
                atk_uid TEXT,
                time INTEGER,
                city_type INTEGER,
                is_npc INTEGER,
                defense_phase TEXT,
                atk_hero1_id INTEGER,
                garrison INTEGER
            );
            CREATE TABLE zone_players (
                uid INTEGER PRIMARY KEY,
                role_id TEXT,
                name TEXT
            );
            """
        )
        users = {
            "101": {
                "uid": 101,
                "name": "玩家甲",
                "group": "一团",
                "atk_num": 0,
                "dis_num": 0,
                "atk_team_num": 0,
                "dis_team_num": 0,
            }
        }
        conn.execute(
            "INSERT INTO tasks VALUES (1, ?, 2000, '777', ?, 0, 0)",
            ("测试攻城", json.dumps(users, ensure_ascii=False)),
        )
        conn.execute(
            "INSERT INTO zone_players VALUES (101, 'role-a', '玩家甲')"
        )
        conn.executemany(
            "INSERT INTO battles_v2 VALUES (?,?,?,?,?,?,?,?,?,?)",
            [
                # 正常城池守军 -> 主力。
                (1, 777, "玩家甲", "role-a", 2001, 8, 1, "normal_city_guard", 101, 0),
                # 最后守军 -> 拆迁。
                (2, 777, "玩家甲", "role-a", 2002, 8, 1, "last_city_guard", 102, 0),
                # 任务开始前、非城池，以及同名异 UID 都不得计入。
                (3, 777, "玩家甲", "role-a", 1999, 8, 1, "normal_city_guard", 103, 0),
                (4, 777, "玩家甲", "role-a", 2003, 0, 0, "other", 104, 0),
                (5, 777, "玩家甲", "role-other", 2004, 8, 1, "normal_city_guard", 105, 0),
                (6, 777, "玩家甲", "role-a", 9201, 8, 1, "last_city_guard", 106, 0),
            ],
        )
        conn.commit()
        conn.close()

    def tearDown(self):
        os.unlink(self.db_path)

    def _connect(self):
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def test_task_statistics_uses_defender_phase_uid_and_task_window(self):
        with patch("api_server.get_db", self._connect):
            response = api_server.app.test_client().post(
                "/api/tasks/1/statistics"
            )

        self.assertEqual(response.status_code, 200)
        conn = self._connect()
        try:
            users = json.loads(
                conn.execute("SELECT user_list FROM tasks WHERE id=1").fetchone()[0]
            )
        finally:
            conn.close()
        player = users["101"]
        self.assertEqual(player["atk_num"], 1)
        self.assertEqual(player["dis_num"], 1)
        self.assertEqual(player["atk_team_num"], 1)
        self.assertEqual(player["dis_team_num"], 1)


class CitySiegeBackfillTest(unittest.TestCase):
    def test_backfill_updates_only_existing_battle_defender_phase_and_role(self):
        with tempfile.TemporaryDirectory() as root:
            capture_dir = os.path.join(root, "0000005c")
            os.makedirs(capture_dir)
            capture_path = os.path.join(capture_dir, "cap_fixture_0000005c.json")
            report = {
                "battle_id": 7001,
                "time": 2001,
                "result": 1,
                "attack_name": "玩家甲",
                "attack_role_id": "role-a",
                "attack_union_name": "甲盟",
                "city_type": 8,
                "npc": 1,
                "world_npc_army": "1715180,17020711,1786664272,1791",
                "defend_all_hero_info": (
                    "100359,20,1791,0,0;0,0,0,0,0;0,0,0,0,0;"
                ),
                "defend_hp": 1791,
            }
            with open(capture_path, "w", encoding="utf-8") as handle:
                json.dump([[report]], handle)

            db_path = os.path.join(root, "attendance.db")
            conn = sqlite3.connect(db_path)
            conn.executescript(
                """
                CREATE TABLE battles_v2 (
                    battle_id INTEGER PRIMARY KEY,
                    city_type INTEGER DEFAULT 0,
                    is_npc INTEGER DEFAULT 0,
                    world_npc_army TEXT DEFAULT '',
                    defend_all_hero_info TEXT DEFAULT '',
                    def_hp INTEGER DEFAULT 0,
                    defense_phase TEXT DEFAULT 'other'
                );
                CREATE TABLE attendance (
                    battle_id INTEGER,
                    role TEXT DEFAULT 'other'
                );
                INSERT INTO battles_v2(battle_id) VALUES (7001);
                INSERT INTO battles_v2(battle_id) VALUES (7002);
                INSERT INTO attendance VALUES (7001, 'other');
                INSERT INTO attendance VALUES (7002, 'main');
                """
            )

            updated = realtime_writer.backfill_city_siege_defense(
                conn, [root]
            )
            rows = conn.execute(
                "SELECT battle_id, defense_phase FROM battles_v2 ORDER BY battle_id"
            ).fetchall()
            roles = conn.execute(
                "SELECT battle_id, role FROM attendance ORDER BY battle_id"
            ).fetchall()
            conn.close()

        self.assertEqual(updated, 1)
        self.assertEqual(rows, [(7001, "last_city_guard"), (7002, "other")])
        self.assertEqual(roles, [(7001, "tear"), (7002, "other")])


if __name__ == "__main__":
    unittest.main()
