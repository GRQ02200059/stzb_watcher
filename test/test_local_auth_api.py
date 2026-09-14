import json
import sqlite3
import tempfile
import unittest
from pathlib import Path
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

    def test_verify_starts_silent_battle_report_sync_for_current_profile(self):
        with tempfile.TemporaryDirectory() as directory:
            database_path = Path(directory) / "stzb_1.2.3.4.db"
            connection = sqlite3.connect(database_path)
            connection.executescript(
                """
                CREATE TABLE battles_v2 (
                    battle_id INTEGER PRIMARY KEY,
                    time INTEGER,
                    result INTEGER,
                    atk_name TEXT,
                    captured_at INTEGER
                );
                CREATE TABLE battle_heroes (
                    battle_id INTEGER, side TEXT, pos INTEGER,
                    hero_id INTEGER, hero_name TEXT
                );
                CREATE TABLE battle_skills (
                    battle_id INTEGER, side TEXT, pos INTEGER,
                    skill_id INTEGER, skill_name TEXT
                );
                INSERT INTO battles_v2 VALUES (42, 1700000000, 1, '玩家甲', 1700000010);
                INSERT INTO battle_heroes VALUES (42, 'atk', 0, 1001, '赵云');
                INSERT INTO battle_skills VALUES (42, 'atk', 0, 2001, '一骑当千');
                """
            )
            connection.commit()
            connection.close()
            profile = {
                "profile_id": "1.2.3.4:42",
                "server_ip": "1.2.3.4",
                "role_id": "42",
                "role_name": "玩家甲",
                "db_path": str(database_path),
            }
            with (
                patch("api_server._auth_service_request", return_value=({"ok": True}, 200)),
                patch("api_server.profile_manager.load_current_profile", return_value=profile),
                patch("api_server._start_battle_report_sync") as sync_mock,
            ):
                response = api_server.app.test_client().post(
                    "/api/local-auth/verify", json={"token": "opaque-token"}
                )

        self.assertEqual(200, response.status_code)
        sync_mock.assert_called_once_with("opaque-token", profile)

    def test_desktop_auth_uses_server_supported_client_version(self):
        with patch("api_server._auth_service_request", return_value=({"ok": True}, 200)) as request_mock:
            response = api_server.app.test_client().post(
                "/api/local-auth/verify", json={"token": "opaque-token"}
            )

        self.assertEqual(200, response.status_code)
        self.assertEqual("1.2.0", request_mock.call_args.args[1]["clientVersion"])

    def test_login_starts_silent_battle_report_sync_with_issued_token(self):
        profile = {"profile_id": "1.2.3.4:42"}
        with (
            patch("api_server._auth_service_request", return_value=({"ok": True, "sessionToken": "issued-token"}, 200)),
            patch("api_server.profile_manager.load_current_profile", return_value=profile),
            patch("api_server._start_battle_report_sync") as sync_mock,
        ):
            response = api_server.app.test_client().post(
                "/api/local-auth/login", json={"username": "alice", "password": "secret"}
            )

        self.assertEqual(200, response.status_code)
        sync_mock.assert_called_once_with("issued-token", profile)

    def test_register_starts_silent_battle_report_sync_with_issued_token(self):
        profile = {"profile_id": "1.2.3.4:42"}
        with (
            patch("api_server._auth_service_request", return_value=({"ok": True, "sessionToken": "issued-token"}, 200)),
            patch("api_server.profile_manager.load_current_profile", return_value=profile),
            patch("api_server._start_battle_report_sync") as sync_mock,
        ):
            response = api_server.app.test_client().post(
                "/api/local-auth/register", json={"username": "alice", "password": "secret"}
            )

        self.assertEqual(200, response.status_code)
        sync_mock.assert_called_once_with("issued-token", profile)

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
