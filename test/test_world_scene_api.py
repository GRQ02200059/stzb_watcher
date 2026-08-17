import sqlite3
import unittest

from flask import Flask

from test.test_world_scene_parser import world_payload
from world_scene.api import register_world_scene_api
from world_scene.parser import parse_world_scene_packet
from world_scene.store import WorldSceneStore


class WorldSceneApiTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.row_factory = sqlite3.Row
        self.store = WorldSceneStore(self.conn)
        self.store.ensure_schema()
        packet = parse_world_scene_packet(
            5026,
            repr(
                world_payload(
                    visual_field={"10004": 1},
                    armies={
                        "1001": [
                            1,
                            42,
                            10001,
                            10004,
                            1,
                            9,
                            0,
                            0,
                            0,
                            0,
                            10001,
                            0,
                            0,
                            0,
                            0,
                            "",
                            "1,2,3",
                            "",
                            "",
                            None,
                            None,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            100,
                            9001,
                            "",
                            0,
                            "",
                            77,
                        ]
                    },
                    chunks={
                        "10004": {
                            "0": [
                                1,
                                0,
                                42,
                                1005,
                                0,
                                "",
                                "土地名",
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                "",
                                0,
                                0,
                                0,
                                0,
                                0,
                                88,
                                2,
                            ]
                        }
                    },
                    war_ships={"3001": [1, 42, 10001]},
                    short_messages={"6001": ["msg", 10004]},
                )
            ),
            "fixture",
            1000,
        )
        self.store.apply_packet(packet)
        app = Flask(__name__)
        register_world_scene_api(app, lambda: self.conn)
        self.client = app.test_client()

    def test_viewport_returns_tiles(self):
        response = self.client.get(
            "/api/world/viewport?rowUp=1&rowDown=2&colLeft=1&colRight=10"
        )
        self.assertEqual(response.status_code, 200)
        payload = response.get_json()
        self.assertEqual(payload["tiles"][0]["wid"], 10004)
        self.assertEqual(payload["visualField"]["raw"], {"10004": 1})

    def test_armies_returns_active_rows(self):
        response = self.client.get("/api/world/armies")
        self.assertEqual(response.status_code, 200)
        row = response.get_json()["armies"][0]
        self.assertEqual(row["army_id"], 1001)
        self.assertEqual(row["owner_name"], "主公")
        self.assertEqual(row["owner_union_name"], "同盟")
        self.assertEqual(row["target_name"], "土地名")

    def test_entities_endpoint_returns_protocol_breadth_rows(self):
        response = self.client.get("/api/world/entities")
        self.assertEqual(response.status_code, 200)
        rows = response.get_json()["entities"]
        self.assertEqual({row["category"] for row in rows}, {"war_ship", "short_message"})

        filtered = self.client.get("/api/world/entities?category=war_ship")
        self.assertEqual(filtered.status_code, 200)
        self.assertEqual(filtered.get_json()["entities"][0]["entity_id"], 3001)


if __name__ == "__main__":
    unittest.main()
