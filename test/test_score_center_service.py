import sqlite3
import unittest
from datetime import datetime

from score_center.calculator import PRESETS
from score_center.repository import ScoreRepository
from score_center.service import ScoreCenterService, normalize_score_request
from test.test_score_center_aggregation import build_connection


class ScoreCenterServiceTest(unittest.TestCase):
    def setUp(self):
        self.connection, self.repository = build_connection()
        self.rule = self.repository.create_rule(
            "current",
            "默认规则",
            "alliance_contribution",
            PRESETS["alliance_contribution"],
        )
        self.repository.activate_rule(self.rule["id"])
        self.service = ScoreCenterService(
            lambda: self.connection,
            preview_ttl_seconds=900,
        )

    def tearDown(self):
        self.connection.close()

    def test_preview_does_not_write_and_reports_rank_deltas(self):
        before = self.connection.execute(
            "SELECT COUNT(*) FROM custom_scores"
        ).fetchone()[0]
        preview = self.service.preview({"season": "current"})
        after = self.connection.execute(
            "SELECT COUNT(*) FROM custom_scores"
        ).fetchone()[0]

        self.assertEqual(before, after)
        self.assertTrue(preview["previewToken"])
        self.assertGreater(preview["summary"]["players"], 0)
        self.assertIn("newRank", preview["rows"][0])
        self.assertIn("scoreDelta", preview["rows"][0])
        self.assertIn("breakdown", preview["rows"][0])

    def test_date_range_uses_local_midnight_and_includes_end_date(self):
        request = normalize_score_request(
            {
                "season": "current",
                "startDate": "1970-01-01",
                "endDate": "1970-01-01",
            }
        )
        self.assertEqual(
            request["startTime"],
            int(datetime(1970, 1, 1, 0, 0, 0).timestamp()),
        )
        self.assertEqual(
            request["endTimeExclusive"],
            int(datetime(1970, 1, 2, 0, 0, 0).timestamp()),
        )
        preview = self.service.preview(
            {
                "season": "current",
                "startDate": "1970-01-01",
                "endDate": "1970-01-01",
            }
        )
        self.assertEqual(preview["summary"]["players"], 2)
        self.assertEqual(
            preview["dateRange"],
            {"startDate": "1970-01-01", "endDate": "1970-01-01"},
        )

    def test_invalid_or_reversed_date_range_is_rejected(self):
        with self.assertRaises(ValueError):
            normalize_score_request({"startDate": "2026/08/01"})
        with self.assertRaises(ValueError):
            normalize_score_request(
                {"startDate": "2026-08-15", "endDate": "2026-08-01"}
            )

    def test_recalculate_requires_valid_matching_preview(self):
        with self.assertRaises(ValueError):
            self.service.recalculate("", {"season": "current"})
        preview = self.service.preview({"season": "current"})
        with self.assertRaises(ValueError):
            self.service.recalculate(
                preview["previewToken"],
                {"season": "other"},
            )
        result = self.service.recalculate(
            preview["previewToken"],
            {"season": "current"},
        )
        self.assertGreater(result["updated"], 0)
        self.assertEqual(
            self.connection.execute(
                "SELECT COUNT(*) FROM custom_scores WHERE season_id='current'"
            ).fetchone()[0],
            result["updated"],
        )
        with self.assertRaises(ValueError):
            self.service.recalculate(
                preview["previewToken"],
                {"season": "current"},
            )

    def test_list_boards_and_player_detail(self):
        preview = self.service.preview({"season": "current"})
        self.service.recalculate(
            preview["previewToken"],
            {"season": "current"},
        )
        overall = self.service.list_scores("current", board="overall")
        battle = self.service.list_scores("current", board="battle")
        siege = self.service.list_scores("current", board="siege")
        self.assertEqual(overall["board"], "overall")
        self.assertEqual(battle["board"], "battle")
        self.assertEqual(siege["board"], "siege")
        player = overall["rows"][0]["playerName"]
        detail = self.service.player_detail("current", player)
        self.assertEqual(detail["playerName"], player)
        self.assertIn("breakdown", detail)
        self.assertIn("adjustments", detail)


if __name__ == "__main__":
    unittest.main()
