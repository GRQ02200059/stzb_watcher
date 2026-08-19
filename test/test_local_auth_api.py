import json
import unittest
from unittest.mock import patch

import api_server


class LocalAuthApiTest(unittest.TestCase):
    def test_login_proxy_forwards_credentials_without_creating_server_session(self):
        with patch("api_server._auth_service_request", return_value=({
            "ok": True, "sessionToken": "opaque-token", "user": {"username": "alice"}
        }, 200)) as request_mock:
            response = api_server.app.test_client().post(
                "/api/local-auth/login",
                json={"username": "alice", "password": "secret"},
            )

        self.assertEqual(200, response.status_code)
        self.assertEqual("opaque-token", response.get_json()["sessionToken"])
        request_mock.assert_called_once_with(
            "login",
            {"username": "alice", "password": "secret", "clientVersion": api_server.AUTH_CLIENT_VERSION},
        )

    def test_verify_proxy_requires_token_and_never_sets_cookie(self):
        with patch("api_server._auth_service_request", return_value=({"ok": True}, 200)):
            response = api_server.app.test_client().post(
                "/api/local-auth/verify", json={"token": "opaque-token"}
            )

        self.assertEqual(200, response.status_code)
        self.assertIsNone(response.headers.get("Set-Cookie"))

    def test_index_places_github_support_before_login_form(self):
        html = api_server.app.test_client().get("/").get_data(as_text=True)
        self.assertLess(html.index("local-auth-hero-support"), html.index("local-auth-form"))

    def test_index_contains_local_gate_and_github_requests(self):
        html = api_server.app.test_client().get("/").get_data(as_text=True)
        self.assertIn("local-auth-gate", html)
        self.assertIn("local-auth-hero-support", html)
        self.assertIn("★ 点 Star", html)
        self.assertIn("GitHub Star", html)
        self.assertIn("Fork 项目", html)
        self.assertIn("禁止倒卖", html)


if __name__ == "__main__":
    unittest.main()
