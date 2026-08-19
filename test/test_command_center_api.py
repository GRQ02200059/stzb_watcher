import os
import sqlite3
import tempfile
import time
import unittest
from datetime import datetime, timedelta
from unittest.mock import patch

import api_server


class CommandCenterApiTest(unittest.TestCase):
    def setUp(self):
        fd, self.db_path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        now = int(time.time())
        today = datetime.fromtimestamp(now).date()
        previous_monday = (today - timedelta(days=today.weekday() + 7)).isoformat()
        conn = sqlite3.connect(self.db_path)
        conn.executescript(
            f"""
            CREATE TABLE battles_v2(
                battle_id INTEGER PRIMARY KEY,
                time INTEGER,
                time_str TEXT,
                result INTEGER,
                result_desc TEXT,
                atk_name TEXT,
                atk_union TEXT,
                def_name TEXT,
                def_union TEXT,
                wid INTEGER,
                atk_gongxun INTEGER
            );
            CREATE TABLE team_users(uid INTEGER PRIMARY KEY, name TEXT);
            CREATE TABLE world_armies(
                army_id INTEGER PRIMARY KEY,
                owner_name TEXT,
                owner_union_name TEXT,
                wid_from INTEGER,
                wid_to INTEGER,
                target_name TEXT,
                end_time INTEGER,
                deleted_at_seq INTEGER
            );
            CREATE TABLE world_tiles(wid INTEGER PRIMARY KEY, name TEXT);
            CREATE TABLE world_state_versions(
                version INTEGER PRIMARY KEY,observed_at_ms INTEGER,source_cmd INTEGER
            );
            CREATE TABLE wuxun_weekly_snapshots(week_start TEXT,player_name TEXT,wuxun INTEGER);
            INSERT INTO battles_v2 VALUES
                (101, {now - 60}, '刚刚', 1, '攻方胜', '甲', '青龙', '乙', '白虎', 10004, 320),
                (102, {now - 90000}, '昨日', 2, '守方胜', '丙', '青龙', '丁', '朱雀', 10005, 120);
            INSERT INTO team_users VALUES (1, '甲'), (2, '丙');
            INSERT INTO world_armies VALUES
                (9001, '甲', '青龙', 10001, 10004, '洛阳', {now + 180}, NULL),
                (9002, '丙', '青龙', 10002, 10004, '洛阳', {now + 600}, NULL);
            INSERT INTO world_tiles VALUES (10004, '洛阳'), (10005, '虎牢关');
            INSERT INTO world_state_versions VALUES (1,{(now - 60) * 1000},5028);
            INSERT INTO wuxun_weekly_snapshots VALUES
                ('{previous_monday}','甲',1200);
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

    def test_overview_returns_stable_operational_summary(self):
        with patch("api_server.get_db", self._connect):
            response = api_server.app.test_client().get(
                "/api/command-center/overview"
            )

        self.assertEqual(response.status_code, 200)
        body = response.get_json()
        self.assertTrue(body["ok"])
        self.assertEqual(body["metrics"]["battlesTotal"], 2)
        self.assertEqual(body["metrics"]["battlesToday"], 1)
        self.assertEqual(body["metrics"]["allianceMembers"], 2)
        self.assertEqual(body["metrics"]["activeArmies"], 2)
        self.assertEqual(body["metrics"]["knownTiles"], 2)
        self.assertEqual(body["battles"][0]["battle_id"], 101)
        self.assertEqual(len(body["armies"]), 2)
        self.assertEqual(body["alerts"][0]["kind"], "convergence")
        self.assertEqual(body["alerts"][0]["entityId"], "10004")
        self.assertIn("profile", body)
        self.assertIn("freshness", body)

    def test_overview_degrades_when_optional_tables_are_missing(self):
        conn = sqlite3.connect(self.db_path)
        conn.executescript(
            """
            DROP TABLE battles_v2;
            DROP TABLE team_users;
            DROP TABLE world_armies;
            DROP TABLE world_tiles;
            """
        )
        conn.close()

        with patch("api_server.get_db", self._connect):
            response = api_server.app.test_client().get(
                "/api/command-center/overview"
            )

        self.assertEqual(response.status_code, 200)
        body = response.get_json()
        self.assertTrue(body["ok"])
        self.assertEqual(
            body["metrics"],
            {
                "battlesTotal": 0,
                "battlesToday": 0,
                "allianceMembers": 0,
                "activeArmies": 0,
                "knownTiles": 0,
            },
        )
        self.assertEqual(body["battles"], [])
        self.assertEqual(body["armies"], [])
        self.assertEqual(body["alerts"], [])

    def test_overview_reports_writer_errors_and_stale_data(self):
        with (
            patch("api_server.get_db", self._connect),
            patch.object(api_server._writer, "stats", {"errors": 3, "battles": 0}),
            patch("api_server.time.time", return_value=int(time.time()) + 90000),
        ):
            response = api_server.app.test_client().get(
                "/api/command-center/overview"
            )

        body = response.get_json()
        kinds = {alert["kind"] for alert in body["alerts"]}
        self.assertIn("writer_error", kinds)
        self.assertIn("stale_data", kinds)

    def test_overview_reports_world_staleness_and_missing_sunday_snapshot(self):
        conn = self._connect()
        conn.execute("UPDATE world_state_versions SET observed_at_ms=1")
        conn.execute("DELETE FROM wuxun_weekly_snapshots")
        conn.commit()
        conn.close()
        with patch("api_server.get_db", self._connect):
            body = api_server.app.test_client().get(
                "/api/command-center/overview"
            ).get_json()
        kinds = {alert["kind"] for alert in body["alerts"]}
        self.assertIn("world_state_stale", kinds)
        self.assertIn("weekly_wuxun_missing", kinds)


if __name__ == "__main__":
    unittest.main()
