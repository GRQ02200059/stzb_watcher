import sqlite3
import unittest

from flask import Flask

from intelligence.world_api import register_world_intelligence_api
from test.test_world_intelligence_service import army_tuple
from test.test_world_scene_parser import world_city, world_payload
from world_scene.parser import parse_world_scene_packet
from world_scene.state_store import WorldStateStore


class WorldIntelligenceApiTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.row_factory = sqlite3.Row
        store = WorldStateStore(self.conn)
        store.ensure_schema()
        payload = world_payload(
            marker=10,
            armies={"100": army_tuple()},
            chunks={"10004": {"0": world_city("资源地"), "8": 73}},
        )
        payload[17] = [1, 1, 4, 4]
        store.apply_baseline(
            parse_world_scene_packet(5026, repr(payload), "fixture", 1000)
        )
        app = Flask(__name__)
        register_world_intelligence_api(
            app,
            lambda: self.conn,
            now_ms=lambda: 2000,
        )
        self.client = app.test_client()

    def test_summary_and_viewport_have_stable_envelope(self):
        summary = self.client.get("/api/intelligence/world/summary").get_json()
        self.assertTrue(summary["ok"])
        self.assertEqual(summary["worldStateVersion"], 1)
        self.assertIn("latestBaseline", summary)
        self.assertEqual(summary["focusWid"], 10004)
        self.assertEqual(summary["dataBounds"]["colLeft"], 4)
        viewport = self.client.get(
            "/api/intelligence/world/viewport"
            "?rowUp=1&rowDown=1&colLeft=4&colRight=4"
        ).get_json()
        self.assertEqual(viewport["tiles"][0]["landLevel"], 7)
        self.assertEqual(viewport["worldStateVersion"], 1)

    def test_tile_events_and_risks(self):
        detail = self.client.get("/api/intelligence/world/tile/10004").get_json()
        self.assertEqual(detail["tile"]["wid"], 10004)
        events = self.client.get("/api/intelligence/world/events").get_json()
        self.assertEqual(events["events"][0]["event_type"], "snapshot_completed")
        risks = self.client.get(
            "/api/intelligence/world/risks"
            "?rowUp=1&rowDown=1&colLeft=4&colRight=4"
        ).get_json()
        self.assertEqual(risks["risks"][0]["wid"], 10004)

    def test_overview_route_returns_non_empty_buckets(self):
        overview = self.client.get(
            "/api/intelligence/world/overview"
            "?rowUp=0&rowDown=20&colLeft=0&colRight=20"
            "&bucketRows=10&bucketCols=10"
        )
        self.assertEqual(overview.status_code, 200)
        body = overview.get_json()
        self.assertTrue(body["ok"])
        self.assertEqual(body["buckets"][0]["focusWid"], 10004)
        self.assertEqual(body["buckets"][0]["tileCount"], 1)

    def test_overview_route_rejects_excessive_bucket_count(self):
        response = self.client.get(
            "/api/intelligence/world/overview"
            "?rowUp=0&rowDown=999&colLeft=0&colRight=999"
            "&bucketRows=10&bucketCols=10"
        )
        self.assertEqual(response.status_code, 400)

    def test_invalid_bounds_are_rejected(self):
        response = self.client.get(
            "/api/intelligence/world/viewport"
            "?rowUp=2&rowDown=1&colLeft=4&colRight=4"
        )
        self.assertEqual(response.status_code, 400)


if __name__ == "__main__":
    unittest.main()
