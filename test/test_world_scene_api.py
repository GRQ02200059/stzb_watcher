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

    def test_history_lists_versions_and_replays_events_with_shared_fields(self):
        self.conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS world_state_versions(
                version INTEGER PRIMARY KEY AUTOINCREMENT, packet_seq INTEGER NOT NULL,
                source_cmd INTEGER NOT NULL, server_order_id INTEGER NOT NULL,
                latest_baseline_order_id INTEGER NOT NULL, observed_at_ms INTEGER NOT NULL,
                completeness TEXT NOT NULL, coverage_json TEXT, change_summary_json TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS world_state_events(
                seq INTEGER PRIMARY KEY AUTOINCREMENT, state_version INTEGER NOT NULL,
                packet_seq INTEGER NOT NULL, event_type TEXT NOT NULL, entity_type TEXT,
                entity_id TEXT, observed_at_ms INTEGER NOT NULL, evidence_json TEXT NOT NULL,
                diff_json TEXT NOT NULL
            );
            INSERT INTO world_state_versions(
                packet_seq,source_cmd,server_order_id,latest_baseline_order_id,
                observed_at_ms,completeness,coverage_json,change_summary_json
            ) VALUES(1,5026,10,10,100,'full-baseline',NULL,'{}');
            INSERT INTO world_state_versions(
                packet_seq,source_cmd,server_order_id,latest_baseline_order_id,
                observed_at_ms,completeness,coverage_json,change_summary_json
            ) VALUES(2,5028,11,10,200,'delta',NULL,'{}');
            INSERT INTO world_state_events(
                state_version,packet_seq,event_type,entity_type,entity_id,
                observed_at_ms,evidence_json,diff_json
            ) VALUES(2,2,'entity_deleted','army','7',200,
                '{"cmdId":5028,"serverOrderId":11}','{"blockId":40}');
            """
        )
        self.conn.commit()

        timeline = self.client.get("/api/world/history?limit=10").get_json()
        self.assertEqual([item["marker"] for item in timeline["versions"]], [11, 10])
        self.assertEqual(timeline["versions"][0]["sourceMsgId"], "5028")

        replay = self.client.get("/api/world/history/2").get_json()
        self.assertEqual(replay["version"]["marker"], 11)
        self.assertEqual(replay["events"][0]["eventType"], "entity_deleted")
        self.assertEqual(replay["events"][0]["entityId"], "7")


if __name__ == "__main__":
    unittest.main()
