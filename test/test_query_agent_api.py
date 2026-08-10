import sqlite3
import unittest

from flask import Flask

from query_agent.api import register_query_agent_api


class QueryAgentApiTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.row_factory = sqlite3.Row
        self.conn.executescript(
            """
            CREATE TABLE world_tiles(
                wid INTEGER PRIMARY KEY,
                row INTEGER,
                col INTEGER,
                name TEXT,
                city_type INTEGER,
                source_seq INTEGER
            );
            INSERT INTO world_tiles VALUES(10004,1,4,'土地名',1,7);
            CREATE TABLE world_armies(
                army_id INTEGER PRIMARY KEY,
                user_id INTEGER,
                wid_from INTEGER,
                wid_to INTEGER,
                end_time INTEGER,
                reside_wid INTEGER,
                stay_wid INTEGER,
                deleted_at_seq INTEGER
            );
            CREATE TABLE battles_v2(
                battle_id INTEGER PRIMARY KEY,
                time INTEGER,
                atk_name TEXT,
                def_name TEXT,
                wid INTEGER,
                result INTEGER,
                atk_gongxun INTEGER
            );
            CREATE TABLE team_users(
                uid INTEGER PRIMARY KEY,
                name TEXT,
                group_name TEXT,
                power INTEGER,
                wuxun INTEGER
            );
            """
        )
        app = Flask(__name__)
        register_query_agent_api(app, lambda: self.conn)
        self.client = app.test_client()

    def test_post_message(self):
        response = self.client.post(
            "/api/query-agent/messages", json={"message": "查 10004"}
        )
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.get_json()["ok"])


if __name__ == "__main__":
    unittest.main()
