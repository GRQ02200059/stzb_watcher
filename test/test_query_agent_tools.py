import sqlite3
import unittest

from query_agent.tools import QueryTools


class QueryAgentToolsTest(unittest.TestCase):
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

    def test_tile_and_army_queries(self):
        tools = QueryTools(lambda: self.conn)
        self.assertEqual(tools.tile(10004)["name"], "土地名")
        self.assertEqual(tools.armies(army_id=1001)[0]["army_id"], 1001)

    def test_battle_and_member_queries(self):
        tools = QueryTools(lambda: self.conn)
        self.assertEqual(tools.battle_search(query="张三")[0]["battle_id"], 77)
        self.assertEqual(tools.alliance_member("张三")[0]["uid"], 42)


if __name__ == "__main__":
    unittest.main()
