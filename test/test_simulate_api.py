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


if __name__ == "__main__":
    unittest.main()
