import unittest
from unittest.mock import patch

import api_server


class DashboardHudApiTest(unittest.TestCase):
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
