import unittest
from unittest.mock import patch

import api_server


class SimulateApiTest(unittest.TestCase):
    def test_simulate_uses_kotlin_engine_adapter(self):
        payload = {
            "repeat": 100,
            "blue": {
                "morale": 100,
                "heros": [{"id": 100027, "level": 40, "up": 5}],
            },
            "red": {
                "morale": 100,
                "heros": [{"id": 100013, "level": 40, "up": 5}],
            },
        }
        with patch("api_server.BattleEngineAdapter") as adapter_type:
            adapter_type.return_value.simulate.return_value = {
                "ok": True,
                "engine": "stzb-kotlin",
                "repeat": 100,
                "blue_wins": 60,
                "red_wins": 30,
                "draws": 10,
                "blue_rate": 60.0,
                "red_rate": 30.0,
                "draw_rate": 10.0,
            }
            client = api_server.app.test_client()
            response = client.post("/api/simulate", json=payload)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()["engine"], "stzb-kotlin")

    def test_heroes_endpoint_serves_engine_csv_data(self):
        client = api_server.app.test_client()
        response = client.get("/api/simulate/heroes")
        self.assertEqual(response.status_code, 200)
        body = response.get_json()
        self.assertTrue(body["ok"])
        self.assertGreater(len(body["heroes"]), 500)
        self.assertGreater(len(body["skills"]), 200)
        by_id = {h["id"]: h for h in body["heroes"]}
        self.assertIn(100027, by_id)
        self.assertEqual(by_id[100027]["name"], "张辽")

    def test_heroes_endpoint_exposes_portrait_metadata(self):
        response = api_server.app.test_client().get(
            "/api/simulate/heroes"
        )
        by_id = {
            hero["id"]: hero
            for hero in response.get_json()["heroes"]
        }

        self.assertTrue(by_id[100027]["portraitLocal"])
        self.assertTrue(
            by_id[100027]["portraitUrl"].endswith(".webp")
        )
        self.assertIn("portraitFallbackUrl", by_id[100027])
        self.assertFalse(by_id[100649]["portraitLocal"])

    def test_engine_endpoint_exposes_kotlin_source_metadata(self):
        metadata = {
            "name": "stzb-kotlin",
            "sourceRepository": "/Users/bytedance/stzb/server",
            "sourceCommit": "9" * 40,
            "generatedAt": "2026-08-15T00:00:00+08:00",
            "maxRepeat": 1000,
            "repeatOptions": [1, 100, 1000],
            "supportsDetailedReplay": True,
        }
        with patch("api_server.BattleEngineAdapter") as adapter_type:
            adapter_type.return_value.engine_metadata.return_value = metadata
            response = api_server.app.test_client().get(
                "/api/simulate/engine"
            )

        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertTrue(body["ok"])
        self.assertEqual(metadata["sourceCommit"], body["sourceCommit"])
        self.assertTrue(body["supportsDetailedReplay"])

    def test_invalid_simulation_payload_returns_client_error(self):
        with patch("api_server.BattleEngineAdapter") as adapter_type:
            adapter_type.return_value.simulate.side_effect = ValueError(
                "repeat must be one of 1, 100, 1000"
            )
            response = api_server.app.test_client().post(
                "/api/simulate",
                json={"repeat": 99},
            )

        self.assertEqual(400, response.status_code)
        body = response.get_json()
        self.assertFalse(body["ok"])
        self.assertEqual("stzb-kotlin", body["engine"])
        self.assertIn("repeat must be one of", body["error"])

    def test_engine_failure_is_explicit_without_traceback(self):
        with patch("api_server.BattleEngineAdapter") as adapter_type:
            adapter_type.return_value.simulate.side_effect = RuntimeError(
                "battle engine exited 1"
            )
            response = api_server.app.test_client().post(
                "/api/simulate",
                json={
                    "repeat": 1,
                    "blue": {"heros": [{"id": 100027}]},
                    "red": {"heros": [{"id": 100013}]},
                },
            )

        self.assertEqual(500, response.status_code)
        body = response.get_json()
        self.assertFalse(body["ok"])
        self.assertEqual("stzb-kotlin", body["engine"])
        self.assertNotIn("trace", body)


if __name__ == "__main__":
    unittest.main()
