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
        self.assertEqual(response.get_json()["tiles"][0]["wid"], 10004)

    def test_armies_returns_active_rows(self):
        response = self.client.get("/api/world/armies")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()["armies"][0]["army_id"], 1001)


if __name__ == "__main__":
    unittest.main()
