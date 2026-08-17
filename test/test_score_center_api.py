import sqlite3
import unittest

from flask import Flask

from score_center.api import register_score_center_api
from score_center.calculator import PRESETS
from score_center.repository import ScoreRepository
from test.test_score_center_aggregation import build_connection


class ScoreCenterApiTest(unittest.TestCase):
    def setUp(self):
        self.connection, repository = build_connection()
        rule = repository.create_rule(
            "current",
            "默认规则",
            "alliance_contribution",
            PRESETS["alliance_contribution"],
        )
        repository.activate_rule(rule["id"])
        app = Flask(__name__)
        register_score_center_api(app, lambda: self.connection)
        self.client = app.test_client()

    def tearDown(self):
        self.connection.close()

    def test_rule_adjustment_preview_and_recalc_flow(self):
        rules = self.client.get(
            "/api/custom_scores/rules?season=current"
        ).get_json()
        self.assertTrue(rules["ok"])
        created = self.client.post(
            "/api/custom_scores/rules",
            json={
                "season": "current",
                "name": "奖励规则",
                "presetKey": "season_reward",
                "config": PRESETS["season_reward"],
            },
        )
        self.assertEqual(created.status_code, 200)
        rule_id = created.get_json()["rule"]["id"]
        self.assertEqual(
            self.client.post(
                f"/api/custom_scores/rules/{rule_id}/activate"
            ).status_code,
            200,
        )
        adjustment = self.client.post(
            "/api/custom_scores/adjustments",
            json={
                "season": "current",
                "playerName": "玩家甲",
                "playerUid": "1",
                "points": 5,
                "reason": "组织奖励",
            },
        )
        self.assertEqual(adjustment.status_code, 200)
        preview = self.client.post(
            "/api/custom_scores/preview",
            json={"season": "current"},
        ).get_json()
        self.assertTrue(preview["ok"])
        recalc = self.client.post(
            "/api/custom_scores/recalc",
            json={
                "season": "current",
                "previewToken": preview["previewToken"],
            },
        )
        self.assertEqual(recalc.status_code, 200)
        board = self.client.get(
            "/api/custom_scores?season=current&board=overall"
        ).get_json()
        self.assertTrue(board["ok"])
        self.assertGreater(len(board["rows"]), 0)

    def test_legacy_recalc_without_preview_is_rejected(self):
        response = self.client.post(
            "/api/custom_scores/recalc",
            json={"season": "current"},
        )
        self.assertEqual(response.status_code, 400)
        self.assertIn("preview", response.get_json()["error"])

    def test_invalid_rule_and_adjustment_are_rejected(self):
        invalid_rule = self.client.post(
            "/api/custom_scores/rules",
            json={
                "season": "current",
                "name": "错误",
                "presetKey": "custom",
                "config": {"battleWeight": "script"},
            },
        )
        self.assertEqual(invalid_rule.status_code, 400)
        invalid_adjustment = self.client.post(
            "/api/custom_scores/adjustments",
            json={
                "season": "current",
                "playerName": "玩家甲",
                "points": 5,
                "reason": "",
            },
        )
        self.assertEqual(invalid_adjustment.status_code, 400)


if __name__ == "__main__":
    unittest.main()
