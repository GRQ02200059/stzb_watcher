import sqlite3
import unittest

from score_center.calculator import PRESETS
from score_center.repository import ScoreRepository


def legacy_connection():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    connection.executescript(
        """
        CREATE TABLE custom_scores (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            season_id TEXT DEFAULT 'current',
            player_name TEXT,
            player_uid TEXT,
            union_name TEXT,
            battles INTEGER DEFAULT 0,
            wins INTEGER DEFAULT 0,
            gongxun_total INTEGER DEFAULT 0,
            power_total INTEGER DEFAULT 0,
            main_city_cnt INTEGER DEFAULT 0,
            tear_cnt INTEGER DEFAULT 0,
            score REAL DEFAULT 0,
            updated_at TEXT
        );
        CREATE UNIQUE INDEX idx_cs_player
        ON custom_scores(season_id, player_name);
        """
    )
    return connection


class ScoreCenterRepositoryTest(unittest.TestCase):
    def setUp(self):
        self.connection = legacy_connection()
        self.repository = ScoreRepository(self.connection)

    def tearDown(self):
        self.connection.close()

    def test_schema_migration_is_idempotent(self):
        self.repository.ensure_schema()
        self.repository.ensure_schema()
        columns = {
            row["name"]
            for row in self.connection.execute(
                "PRAGMA table_info(custom_scores)"
            ).fetchall()
        }
        self.assertTrue(
            {
                "rule_version_id",
                "draws",
                "attendance_cnt",
                "battle_score",
                "siege_score",
                "adjustment_score",
                "breakdown_json",
                "calculated_at",
            }.issubset(columns)
        )
        tables = {
            row["name"]
            for row in self.connection.execute(
                "SELECT name FROM sqlite_master WHERE type='table'"
            ).fetchall()
        }
        self.assertIn("score_rule_versions", tables)
        self.assertIn("score_adjustments", tables)

    def test_rule_versions_increment_and_active_rule_is_unique(self):
        self.repository.ensure_schema()
        first = self.repository.create_rule(
            "s1", "规则一", "alliance_contribution",
            PRESETS["alliance_contribution"],
        )
        second = self.repository.create_rule(
            "s1", "规则二", "season_reward", PRESETS["season_reward"]
        )
        self.assertEqual(first["version"], 1)
        self.assertEqual(second["version"], 2)
        self.repository.activate_rule(first["id"])
        self.repository.activate_rule(second["id"])
        rules = self.repository.list_rules("s1")
        active = [rule for rule in rules if rule["status"] == "active"]
        self.assertEqual([rule["id"] for rule in active], [second["id"]])
        self.assertEqual(self.repository.active_rule("s1")["id"], second["id"])

    def test_adjustments_require_reason_and_support_season_safe_delete(self):
        self.repository.ensure_schema()
        with self.assertRaises(ValueError):
            self.repository.add_adjustment(
                "s1", "玩家甲", "1", 5, "", "tester"
            )
        adjustment = self.repository.add_adjustment(
            "s1", "玩家甲", "1", -3.5, "迟到", "tester"
        )
        self.assertEqual(adjustment["points"], -3.5)
        with self.assertRaises(ValueError):
            self.repository.delete_adjustment(adjustment["id"], "s2")
        self.repository.delete_adjustment(adjustment["id"], "s1")
        self.assertEqual(self.repository.list_adjustments("s1"), [])

    def test_scores_keep_rule_version_and_breakdown(self):
        self.repository.ensure_schema()
        rule = self.repository.create_rule(
            "s1", "规则", "alliance_contribution",
            PRESETS["alliance_contribution"],
        )
        self.repository.activate_rule(rule["id"])
        self.repository.replace_scores(
            "s1",
            rule["id"],
            [{
                "playerName": "玩家甲",
                "playerUid": "1",
                "unionName": "同盟",
                "metrics": {
                    "battles": 10, "wins": 4, "draws": 1,
                    "gongxunTotal": 1000, "mainCityCnt": 2,
                    "tearCnt": 1, "attendanceCnt": 1,
                },
                "battleScore": 19.5,
                "siegeScore": 14.0,
                "adjustmentScore": 2.0,
                "score": 35.5,
                "breakdown": {"score": 35.5},
            }],
        )
        row = self.repository.list_scores("s1")[0]
        self.assertEqual(row["rule_version_id"], rule["id"])
        self.assertEqual(row["breakdown"]["score"], 35.5)


if __name__ == "__main__":
    unittest.main()
