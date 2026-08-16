import unittest
from unittest.mock import patch

from flask import Flask

import api_server
from intelligence.live_army_api import register_live_army_api


class LiveArmyApiRegistrationTest(unittest.TestCase):
    def test_application_registers_live_army_endpoint(self):
        response = api_server.app.test_client().get(
            "/api/intelligence/live-armies"
        )

        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertTrue(body["ok"])
        self.assertIn("summary", body)
        self.assertIn("current", body)
        self.assertIn("recentOffline", body)


class LiveArmyApiValidationTest(unittest.TestCase):
    def setUp(self):
        app = Flask(__name__)
        register_live_army_api(app, lambda: None)
        self.client = app.test_client()

    def test_default_window_is_ten_minutes(self):
        with patch(
            "intelligence.live_army_api.LiveArmyService"
        ) as service:
            service.return_value.snapshot.return_value = {
                "ok": True,
                "current": [],
                "recentOffline": [],
            }
            response = self.client.get("/api/intelligence/live-armies")

        self.assertEqual(200, response.status_code)
        service.return_value.snapshot.assert_called_once_with(
            offline_minutes=10
        )

    def test_offline_minutes_accepts_zero_and_sixty(self):
        for value in (0, 60):
            with self.subTest(value=value):
                with patch(
                    "intelligence.live_army_api.LiveArmyService"
                ) as service:
                    service.return_value.snapshot.return_value = {
                        "ok": True,
                        "current": [],
                        "recentOffline": [],
                    }
                    response = self.client.get(
                        "/api/intelligence/live-armies"
                        f"?offlineMinutes={value}"
                    )
                self.assertEqual(200, response.status_code)
                service.return_value.snapshot.assert_called_once_with(
                    offline_minutes=value
                )

    def test_invalid_offline_minutes_is_400_without_opening_database(self):
        for value in ("-1", "61", "bad"):
            with self.subTest(value=value):
                response = self.client.get(
                    "/api/intelligence/live-armies"
                    f"?offlineMinutes={value}"
                )
                self.assertEqual(400, response.status_code)
                self.assertFalse(response.get_json()["ok"])


if __name__ == "__main__":
    unittest.main()
