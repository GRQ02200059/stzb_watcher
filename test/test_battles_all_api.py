import os
import sqlite3
import tempfile
import unittest
from unittest.mock import patch

import api_server


class BattlesAllApiTest(unittest.TestCase):
    def setUp(self):
        fd, self.db_path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        conn = sqlite3.connect(self.db_path)
        conn.executescript(
            """
            CREATE TABLE battles_v2(
                battle_id INTEGER PRIMARY KEY,
                time INTEGER,
                time_str TEXT,
                result INTEGER,
                result_desc TEXT,
                fight_type INTEGER,
                atk_name TEXT,
                atk_union TEXT,
                def_name TEXT,
                def_union TEXT,
                atk_hero1_id INTEGER,
                atk_hero2_id INTEGER,
                atk_hero3_id INTEGER,
                def_hero1_id INTEGER,
                def_hero2_id INTEGER,
                def_hero3_id INTEGER,
                garrison INTEGER,
                is_npc INTEGER
            );
            CREATE TABLE battle_heroes(
                battle_id INTEGER,
                side TEXT,
                pos INTEGER,
                hero_id INTEGER
            );
            INSERT INTO battles_v2 VALUES(
                101, 1700000000, '2023-11-14 22:13:20', 2, '守方胜', 0,
                '一别西风', '诸丨天地', '小股流寇营地', '',
                100013, 102016, 100020, 100752, 100126, 0, 0, 1
            );
            """
        )
        conn.commit()
        conn.close()

    def tearDown(self):
        try:
            os.unlink(self.db_path)
        except FileNotFoundError:
            pass

    def _connect(self):
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def test_battles_all_includes_npc_reports_by_default(self):
        with patch("api_server.get_db", self._connect):
            response = api_server.app.test_client().get(
                "/api/battles_all?page=1&size=5"
            )

        self.assertEqual(response.status_code, 200)
        body = response.get_json()
        self.assertEqual(body["total"], 1)
        self.assertEqual(body["data"][0]["battle_id"], 101)

    def test_player_battle_teams_maps_defender_skills_in_6_5_4_order(self):
        conn = self._connect()
        conn.execute(
            """
            ALTER TABLE battles_v2 ADD COLUMN all_skill_info TEXT DEFAULT ''
            """
        )
        conn.execute(
            """
            ALTER TABLE battles_v2 ADD COLUMN defend_all_hero_info TEXT DEFAULT ''
            """
        )
        conn.execute(
            """
            UPDATE battles_v2
            SET def_name = '守方玩家',
                def_union = '守方同盟',
                is_npc = 0,
                all_skill_info = '4,4004,1;5,4005,1;6,4006,1',
                defend_all_hero_info =
                    '100001,40,100,100,0;100002,40,100,100,0;100003,40,100,100,0'
            WHERE battle_id = 101
            """
        )
        conn.commit()
        conn.close()

        with patch("api_server.get_db", self._connect):
            response = api_server.app.test_client().get(
                "/api/player_battle_teams?side=def&player=%E5%AE%88%E6%96%B9%E7%8E%A9%E5%AE%B6&debug=1"
            )

        self.assertEqual(response.status_code, 200)
        rows = response.get_json()["data"]
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["player_name"], "守方玩家")
        self.assertEqual(rows[0]["skills"], "4006,4005,4004")


if __name__ == "__main__":
    unittest.main()
