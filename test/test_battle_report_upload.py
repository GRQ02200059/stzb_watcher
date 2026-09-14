import sqlite3
import tempfile
import unittest
from pathlib import Path

from desktop_battle_report_sync import BattleReportSynchronizer, SyncStatus


class BattleReportSynchronizerTest(unittest.TestCase):
    def setUp(self):
        self.temp_directory = tempfile.TemporaryDirectory()
        self.database_path = Path(self.temp_directory.name) / "stzb_1.2.3.4.db"
        connection = sqlite3.connect(self.database_path)
        connection.executescript(
            """
            CREATE TABLE battles_v2 (
                battle_id INTEGER PRIMARY KEY,
                time INTEGER,
                result INTEGER,
                atk_name TEXT,
                def_name TEXT,
                captured_at INTEGER,
                raw_json TEXT
            );
            CREATE TABLE battle_heroes (
                battle_id INTEGER, side TEXT, pos INTEGER, hero_id INTEGER,
                hero_name TEXT, level INTEGER, raw_payload TEXT
            );
            CREATE TABLE battle_skills (
                battle_id INTEGER, side TEXT, pos INTEGER, skill_id INTEGER,
                skill_name TEXT, skill_level INTEGER
            );
            INSERT INTO battles_v2 VALUES
                (41, 1700000000, 1, '玩家甲', '玩家乙', 1700000010, 'must-not-upload'),
                (42, 1700000100, 2, '玩家甲', '玩家丙', 1700000110, 'must-not-upload');
            INSERT INTO battle_heroes VALUES
                (42, 'atk', 0, 1001, '赵云', 50, 'must-not-upload');
            INSERT INTO battle_skills VALUES
                (42, 'atk', 0, 2001, '一骑当千', 10);
            """
        )
        connection.commit()
        connection.close()
        self.profile = {
            "profile_id": "1.2.3.4:42",
            "server_ip": "1.2.3.4",
            "role_id": "42",
            "role_name": "玩家甲",
            "db_path": str(self.database_path),
        }

    def tearDown(self):
        self.temp_directory.cleanup()

    def test_uploads_only_manifest_required_reports_with_allowed_details(self):
        requests = []

        def post(path, payload):
            requests.append((path, payload))
            if path == "battle-reports/manifest":
                return {"ok": True, "requiredBattleIds": [42]}, 200
            return {"ok": True, "accepted": 1}, 200

        result = BattleReportSynchronizer(post).sync("opaque-token", self.profile)

        self.assertEqual(SyncStatus.UPLOADED, result.status)
        self.assertEqual(1, result.uploaded)
        self.assertEqual(["battle-reports/manifest", "battle-reports/upload"], [path for path, _ in requests])
        manifest = requests[0][1]
        self.assertEqual("1.2.0", manifest["clientVersion"])
        self.assertEqual([41, 42], [item["battleId"] for item in manifest["reports"]])
        uploaded = requests[1][1]["reports"]
        self.assertEqual([42], [item["battleId"] for item in uploaded])
        self.assertNotIn("raw_json", uploaded[0]["battle"])
        self.assertEqual(["atk_name", "battle_id", "captured_at", "def_name", "result", "time"], sorted(uploaded[0]["battle"]))
        self.assertEqual([{"side": "atk", "pos": 0, "hero_id": 1001, "hero_name": "赵云", "level": 50}], uploaded[0]["heroes"])
        self.assertEqual([{"side": "atk", "pos": 0, "skill_id": 2001, "skill_name": "一骑当千", "skill_level": 10}], uploaded[0]["skills"])

    def test_missing_detail_tables_skips_without_network_request(self):
        connection = sqlite3.connect(self.database_path)
        connection.execute("DROP TABLE battle_skills")
        connection.commit()
        connection.close()
        requests = []

        result = BattleReportSynchronizer(lambda path, payload: requests.append((path, payload))).sync(
            "opaque-token", self.profile
        )

        self.assertEqual(SyncStatus.NO_REPORTS, result.status)
        self.assertEqual([], requests)

    def test_pages_manifests_in_200_report_windows(self):
        connection = sqlite3.connect(self.database_path)
        connection.executemany(
            "INSERT INTO battles_v2 VALUES(?,?,?,?,?,?,?)",
            [
                (battle_id, 1700000000 + battle_id, 1, "玩家甲", "玩家乙", 1700000010 + battle_id, "raw")
                for battle_id in range(43, 244)
            ],
        )
        connection.commit()
        connection.close()
        manifest_sizes = []

        def post(path, payload):
            if path == "battle-reports/manifest":
                manifest_sizes.append(len(payload["reports"]))
                return {"ok": True, "requiredBattleIds": []}, 200
            self.fail("no reports should be uploaded")

        result = BattleReportSynchronizer(post).sync("opaque-token", self.profile)

        self.assertEqual(SyncStatus.NO_CHANGES, result.status)
        self.assertEqual([200, 3], manifest_sizes)

    def test_exactly_full_manifest_with_no_changes_is_not_reported_as_empty_database(self):
        connection = sqlite3.connect(self.database_path)
        connection.executemany(
            "INSERT INTO battles_v2 VALUES(?,?,?,?,?,?,?)",
            [
                (battle_id, 1700000000 + battle_id, 1, "玩家甲", "玩家乙", 1700000010 + battle_id, "raw")
                for battle_id in range(43, 241)
            ],
        )
        connection.commit()
        connection.close()

        result = BattleReportSynchronizer(
            lambda _path, _payload: ({"ok": True, "requiredBattleIds": []}, 200)
        ).sync("opaque-token", self.profile)

        self.assertEqual(SyncStatus.NO_CHANGES, result.status)


if __name__ == "__main__":
    unittest.main()
