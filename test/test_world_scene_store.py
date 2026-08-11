import sqlite3
import unittest

from test.test_world_scene_parser import world_payload
from world_scene.parser import parse_world_scene_packet
from world_scene.store import WorldSceneStore


class WorldSceneStoreTest(unittest.TestCase):
    def test_apply_packet_persists_tiles_armies_and_marches(self):
        conn = sqlite3.connect(":memory:")
        conn.row_factory = sqlite3.Row
        store = WorldSceneStore(conn)
        store.ensure_schema()
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
                    real_march={
                        "9001": [
                            10001,
                            10002,
                            10004,
                            1,
                            2,
                            9,
                            123,
                            5,
                            1,
                            42,
                            0,
                            100,
                            0,
                            0,
                        ]
                    },
                )
            ),
            "fixture",
            1000,
        )
        seq = store.apply_packet(packet)
        self.assertEqual(seq, 1)
        view = store.viewport(1, 2, 1, 10)
        self.assertEqual(view["tiles"][0]["wid"], 10004)
        self.assertEqual(store.active_armies()[0]["army_id"], 1001)
        self.assertEqual(store.active_marches()[0]["real_march_id"], 9001)

    def test_backfills_legacy_map_cells_and_monitor_moves(self):
        conn = sqlite3.connect(":memory:")
        conn.row_factory = sqlite3.Row
        store = WorldSceneStore(conn)
        store.ensure_schema()
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
        store.apply_packet(packet)
        store.backfill_legacy_views()
        map_row = conn.execute(
            "SELECT city_name FROM map_cells WHERE wid=10004"
        ).fetchone()
        move_row = conn.execute(
            "SELECT team_id FROM battle_monitor_moves WHERE team_id=1001"
        ).fetchone()
        self.assertEqual(map_row["city_name"], "土地名")
        self.assertEqual(move_row["team_id"], 1001)

    def test_applies_block_deletes_and_clear_chunks(self):
        conn = sqlite3.connect(":memory:")
        conn.row_factory = sqlite3.Row
        store = WorldSceneStore(conn)
        store.ensure_schema()
        first = parse_world_scene_packet(
            5026,
            repr(
                world_payload(
                    armies={
                        "1001": [
                            1, 42, 10001, 10004, 1, 9, 0, 0, 0, 0, 10001,
                            0, 0, 0, 0, "", "1,2,3", "", "", None, None,
                            0, 0, 0, 0, 0, 0, 100, 9001, "", 0, "", 77,
                        ]
                    },
                    chunks={
                        "10004": {
                            "0": [
                                1, 0, 42, 1005, 0, "", "土地名", 0, 0, 0,
                                0, 0, 0, "", 0, 0, 0, 0, 0, 88, 2,
                            ]
                        }
                    },
                )
            ),
            "first",
            1000,
        )
        store.apply_packet(first)
        self.assertEqual(len(store.active_armies()), 1)
        self.assertEqual(len(store.viewport(1, 2, 1, 10)["tiles"]), 1)

        delta = parse_world_scene_packet(
            5028,
            repr(world_payload(marker=9, block_deleted=[1001], clear_chunks={"10004": ["0"]})),
            "delta",
            1001,
        )
        store.apply_packet(delta)

        self.assertEqual(store.active_armies(), [])
        self.assertEqual(store.viewport(1, 2, 1, 10)["tiles"], [])


if __name__ == "__main__":
    unittest.main()
