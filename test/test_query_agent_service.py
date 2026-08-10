import sqlite3
import unittest

from query_agent.service import QueryAgentService
from query_agent.tools import QueryTools


class QueryAgentServiceTest(unittest.TestCase):
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
            INSERT INTO world_armies VALUES(1001,42,10001,10004,9,0,0,NULL);
            CREATE TABLE battles_v2(
                battle_id INTEGER PRIMARY KEY,
                time INTEGER,
                atk_name TEXT,
                def_name TEXT,
                wid INTEGER,
                result INTEGER,
                atk_gongxun INTEGER
            );
            INSERT INTO battles_v2 VALUES(77,1700000000,'张三','李四',10004,1,1234);
            CREATE TABLE team_users(
                uid INTEGER PRIMARY KEY,
                name TEXT,
                group_name TEXT,
                power INTEGER,
                wuxun INTEGER
            );
            INSERT INTO team_users VALUES(42,'张三','一团',50000,6000);
            """
        )
        self.service = QueryAgentService(QueryTools(lambda: self.conn))

    def test_answers_wid_query_with_navigation(self):
        response = self.service.answer("查 10004")
        body = response.to_json()
        self.assertTrue(body["ok"])
        self.assertIn("10004", body["answer"])
        self.assertEqual(body["uiActions"][0]["route"], "map")

    def test_rejects_execution_request(self):
        response = self.service.answer("派主力出征 10004")
        body = response.to_json()
        self.assertFalse(body["ok"])
        self.assertIn("只读", body["error"])


if __name__ == "__main__":
    unittest.main()
