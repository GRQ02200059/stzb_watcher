from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class WorldSceneStaticTest(unittest.TestCase):
    def test_dashboard_contains_world_scene_tab(self):
        html = (ROOT / "static" / "dashboard.html").read_text(encoding="utf-8")
        self.assertIn("switchTab(30,this)", html)
        self.assertIn("id='tab30'", html)
        self.assertIn("id='ws-map-body'", html)
        self.assertIn("id='ws-grid'", html)
        self.assertIn("id='ws-march-body'", html)
        self.assertIn("id='ws-army-body'", html)
        self.assertIn("id='ws-entity-body'", html)
        self.assertIn("world_scene.js", html)

    def test_app1_wires_world_scene_loader(self):
        js = (ROOT / "static" / "app1.js").read_text(encoding="utf-8")
        self.assertIn("i===30", js)
        self.assertIn("loadWorldScene", js)

    def test_world_scene_js_consumes_read_only_apis(self):
        js = (ROOT / "static" / "world_scene.js").read_text(encoding="utf-8")
        self.assertIn("/api/world/viewport", js)
        self.assertIn("/api/world/armies", js)
        self.assertIn("/api/world/marches", js)
        self.assertIn("/api/world/entities", js)
        self.assertIn("new EventSource('/api/stream')", js)
        self.assertIn("world_snapshot_complete", js)
        # read-only: must not issue any write / action calls
        self.assertNotIn("method:'POST'", js.replace(" ", ""))

    def test_world_scene_army_view_shows_enriched_fields(self):
        html = (ROOT / "static" / "dashboard.html").read_text(encoding="utf-8")
        js = (ROOT / "static" / "world_scene.js").read_text(encoding="utf-8")
        self.assertIn("<th>玩家</th><th>同盟</th>", html)
        self.assertIn("<th>目标名</th>", html)
        self.assertIn("<th>Buff</th><th>障碍</th>", html)
        self.assertIn("owner_name", js)
        self.assertIn("owner_union_name", js)
        self.assertIn("target_name", js)
        self.assertIn("obstacle_wid", js)

    def test_world_scene_map_grid_and_protocol_breadth_ui(self):
        html = (ROOT / "static" / "dashboard.html").read_text(encoding="utf-8")
        js = (ROOT / "static" / "world_scene.js").read_text(encoding="utf-8")
        self.assertIn("世界地图网格", html)
        self.assertIn("📡 协议广度", html)
        self.assertIn("warShips / assistArmies / armyGroups / shortMessages", html)
        self.assertIn("function renderWsGrid", js)
        self.assertIn("visualField", js)
        self.assertIn("function renderWsEntities", js)


if __name__ == "__main__":
    unittest.main()
