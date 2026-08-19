import unittest
import sqlite3
from unittest.mock import patch

import api_server


class DashboardHudApiTest(unittest.TestCase):
    def test_hud_health_includes_protocol_and_data_quality_components(self):
        conn = sqlite3.connect(":memory:")
        conn.row_factory = sqlite3.Row
        conn.executescript(
            """
            CREATE TABLE world_state_versions(version INTEGER,observed_at_ms INTEGER);
            CREATE TABLE wuxun_weekly_snapshots(week_start TEXT);
            INSERT INTO world_state_versions VALUES(1,1);
            """
        )
        with (
            patch("api_server.get_db", return_value=conn),
            patch.object(api_server, "_writer") as writer,
        ):
            writer.stats = {}
            body = api_server.app.test_client().get("/api/hud/health").get_json()
        self.assertIn("protocolContract", body["components"])
        self.assertIn("worldState", body["components"])
        self.assertIn("weeklyWuxun", body["components"])
        self.assertEqual(94, body["components"]["protocolContract"]["commandCount"])
        self.assertEqual("degraded", body["components"]["worldState"]["status"])
    def test_hud_health_reports_optional_components_without_500(self):
        with patch.object(api_server, "_writer") as writer:
            writer.stats = {"errors": 2, "battles": 3}
            response = api_server.app.test_client().get("/api/hud/health")

        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertTrue(body["ok"])
        self.assertEqual("degraded", body["overall"])
        self.assertIn("backend", body["components"])
        self.assertIn("writer", body["components"])
        self.assertIn("battleEngine", body["components"])
        self.assertIn("portraits", body["components"])

    def test_hud_health_degrades_when_optional_manifest_is_missing(self):
        with patch("api_server.os.path.isfile", return_value=False):
            response = api_server.app.test_client().get("/api/hud/health")

        self.assertEqual(200, response.status_code)
        self.assertEqual(
            "unknown",
            response.get_json()["components"]["portraits"]["status"],
        )


if __name__ == "__main__":
    unittest.main()
