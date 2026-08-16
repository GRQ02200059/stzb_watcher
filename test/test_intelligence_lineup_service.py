import sqlite3
import unittest

from flask import Flask

from intelligence.lineup_api import register_intelligence_lineup_api
from intelligence.lineup_service import LineupStatisticsService, canonical_lineup_key


class FakeConfigRepository:
    dataset_version = "test-config"

    def __init__(self):
        self.hero_by_id = {
            hero_id: {"heroid": hero_id, "name": name}
            for hero_id, name in {
                101: "大营甲",
                102: "中军甲",
                103: "前锋甲",
                201: "大营乙",
                202: "中军乙",
                203: "前锋乙",
                301: "大营丙",
                302: "中军丙",
                303: "前锋丙",
                401: "大营丁",
                402: "中军丁",
                403: "前锋丁",
            }.items()
        }


def build_connection():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    connection.executescript(
        """
        CREATE TABLE battles_v2 (
            battle_id INTEGER PRIMARY KEY,
            time INTEGER,
            result INTEGER,
            fight_type INTEGER DEFAULT 0,
            is_npc INTEGER DEFAULT 0
        );
        CREATE TABLE battle_heroes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            battle_id INTEGER,
            side TEXT,
            pos INTEGER,
            hero_id INTEGER,
            hero_name TEXT,
            level INTEGER DEFAULT 40
        );
        """
    )
    battles = [
        (1, 1000, 1),
        (2, 2000, 2),
        (3, 3000, 0),
        (4, 4000, 1),
    ]
    connection.executemany(
        "INSERT INTO battles_v2(battle_id,time,result) VALUES(?,?,?)",
        battles,
    )
    teams = {
        1: {"atk": (101, 102, 103), "def": (201, 202, 203)},
        2: {"atk": (401, 402, 403), "def": (101, 102, 103)},
        3: {"atk": (101, 102, 103), "def": (201, 202, 203)},
        4: {"atk": (201, 202, 203), "def": (301, 302, 303)},
    }
    hero_names = FakeConfigRepository().hero_by_id
    for battle_id, sides in teams.items():
        for side, hero_ids in sides.items():
            for position, hero_id in enumerate(hero_ids):
                connection.execute(
                    """
                    INSERT INTO battle_heroes(
                        battle_id,side,pos,hero_id,hero_name,level
                    ) VALUES(?,?,?,?,?,?)
                    """,
                    (
                        battle_id,
                        side,
                        position,
                        hero_id,
                        hero_names[hero_id]["name"],
                        40 + position,
                    ),
                )
    connection.commit()
    return connection


class LineupStatisticsServiceTest(unittest.TestCase):
    def setUp(self):
        self.connection = build_connection()
        self.service = LineupStatisticsService(
            lambda: self.connection,
            FakeConfigRepository(),
        )

    def tearDown(self):
        self.connection.close()

    def test_canonical_key_preserves_position_order(self):
        self.assertEqual(canonical_lineup_key([101, 102, 103]), "101.102.103")
        self.assertEqual(canonical_lineup_key([103, 102, 101]), "103.102.101")
        with self.assertRaises(ValueError):
            canonical_lineup_key([101, 102])
        with self.assertRaises(ValueError):
            canonical_lineup_key([101, 101, 102])

    def test_detail_aggregates_both_sides_and_common_opponents(self):
        detail = self.service.get_lineup("101.102.103")

        self.assertEqual(detail["key"], "101.102.103")
        self.assertEqual(detail["battleStats"]["sampleSize"], 3)
        self.assertEqual(detail["battleStats"]["wins"], 2)
        self.assertEqual(detail["battleStats"]["draws"], 1)
        self.assertEqual(detail["battleStats"]["losses"], 0)
        self.assertEqual(detail["battleStats"]["winRate"], 83.3)
        self.assertEqual(detail["battleStats"]["evidenceClass"], "BATTLE_STAT")
        self.assertEqual(
            detail["battleStats"]["commonOpponents"][0]["key"],
            "201.202.203",
        )
        self.assertEqual(
            detail["battleStats"]["commonOpponents"][0]["sampleSize"],
            2,
        )

    def test_low_sample_is_labeled_instead_of_overstated(self):
        detail = self.service.get_lineup("101.102.103")

        self.assertEqual(detail["confidence"]["label"], "low")
        self.assertEqual(detail["confidence"]["minimumRecommendedSample"], 10)
        self.assertIn("样本不足", detail["confidence"]["notice"])
        self.assertEqual(detail["simulationLink"]["evidenceClass"], "SIMULATION")
        self.assertFalse(detail["simulationLink"]["hasResult"])

    def test_list_supports_hero_filter_and_minimum_sample(self):
        result = self.service.list_lineups(
            hero_id=101,
            minimum_sample=2,
            page=1,
            size=10,
        )

        self.assertEqual(result["total"], 1)
        self.assertEqual(result["rows"][0]["key"], "101.102.103")
        self.assertEqual(result["rows"][0]["battleStats"]["sampleSize"], 3)

    def test_matchup_counts_each_battle_once_from_left_perspective(self):
        result = self.service.get_matchup("101.102.103", "201.202.203")

        self.assertEqual("101.102.103", result["leftKey"])
        self.assertEqual("201.202.203", result["rightKey"])
        self.assertEqual(
            {
                "evidenceClass": "BATTLE_STAT",
                "sampleSize": 2,
                "wins": 1,
                "draws": 1,
                "losses": 0,
                "winRate": 75.0,
                "latestBattleTime": 3000,
            },
            result["battleStats"],
        )
        self.assertEqual("low", result["confidence"]["label"])

    def test_matchup_reverses_outcome_for_right_perspective(self):
        result = self.service.get_matchup("201.202.203", "101.102.103")

        self.assertEqual(0, result["battleStats"]["wins"])
        self.assertEqual(1, result["battleStats"]["draws"])
        self.assertEqual(1, result["battleStats"]["losses"])
        self.assertEqual(25.0, result["battleStats"]["winRate"])

    def test_matchup_returns_zero_stats_for_valid_unknown_pair(self):
        result = self.service.get_matchup("101.102.103", "301.302.303")

        self.assertEqual(0, result["battleStats"]["sampleSize"])
        self.assertEqual(0.0, result["battleStats"]["winRate"])

    def test_matchup_rejects_invalid_lineup_key(self):
        self.assertIsNone(self.service.get_matchup("101.102", "201.202.203"))

    def test_matchup_rejects_duplicate_hero_ids(self):
        self.assertIsNone(
            self.service.get_matchup("101.101.102", "201.202.203")
        )


class LineupStatisticsApiTest(unittest.TestCase):
    def setUp(self):
        self.connection = build_connection()
        app = Flask(__name__)
        register_intelligence_lineup_api(
            app,
            lambda: self.connection,
            FakeConfigRepository(),
        )
        self.client = app.test_client()

    def tearDown(self):
        self.connection.close()

    def test_list_and_detail_routes(self):
        listing = self.client.get(
            "/api/intelligence/lineups?heroId=101&minSample=2"
        ).get_json()
        self.assertTrue(listing["ok"])
        self.assertEqual(listing["rows"][0]["key"], "101.102.103")

        detail = self.client.get(
            "/api/intelligence/lineups/101.102.103"
        ).get_json()
        self.assertTrue(detail["ok"])
        self.assertEqual(detail["battleStats"]["sampleSize"], 3)

    def test_invalid_or_missing_lineup_is_404(self):
        self.assertEqual(
            self.client.get("/api/intelligence/lineups/101.102").status_code,
            404,
        )
        self.assertEqual(
            self.client.get(
                "/api/intelligence/lineups/999.998.997"
            ).status_code,
            404,
        )

    def test_matchup_route_returns_zero_or_aggregated_stats(self):
        response = self.client.get(
            "/api/intelligence/lineups/101.102.103/"
            "matchup/201.202.203"
        )
        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertTrue(body["ok"])
        self.assertEqual(2, body["battleStats"]["sampleSize"])

    def test_matchup_route_rejects_invalid_key(self):
        response = self.client.get(
            "/api/intelligence/lineups/101.102/"
            "matchup/201.202.203"
        )
        self.assertEqual(404, response.status_code)

    def test_matchup_route_rejects_duplicate_hero_ids(self):
        response = self.client.get(
            "/api/intelligence/lineups/101.101.102/"
            "matchup/201.202.203"
        )
        self.assertEqual(404, response.status_code)

    def test_matchup_route_returns_zero_stats_for_valid_unknown_pair(self):
        response = self.client.get(
            "/api/intelligence/lineups/101.102.103/"
            "matchup/301.302.303"
        )
        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertTrue(body["ok"])
        self.assertEqual(0, body["battleStats"]["sampleSize"])


if __name__ == "__main__":
    unittest.main()
