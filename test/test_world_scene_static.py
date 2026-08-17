from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class WorldSceneStaticTest(unittest.TestCase):
    def test_world_scene_is_embedded_in_intelligence_with_compatibility_tab(self):
        html = (ROOT / "static" / "dashboard.html").read_text(encoding="utf-8")
        self.assertIn("id='tab30'", html)
        nav = html.split('<nav aria-label="主导航">', 1)[1].split("</nav>", 1)[0]
        self.assertNotIn("switchTab(30,this)", nav)
        self.assertIn('data-intel-view="map"', html)
        self.assertIn('data-intel-view="march"', html)
        self.assertIn('data-intel-view="army"', html)
        self.assertIn('data-intel-view="entity"', html)
        self.assertIn('id="intel-view-map"', html)
        self.assertIn('id="intel-view-march"', html)
        self.assertIn('id="intel-view-army"', html)
        self.assertIn('id="intel-view-entity"', html)
        self.assertIn("id='ws-march-body'", html)
        self.assertIn("id='ws-army-body'", html)
        self.assertIn("id='ws-entity-body'", html)
        self.assertIn("world_scene.js", html)

    def test_app1_wires_world_scene_loader(self):
        js = (ROOT / "static" / "app1.js").read_text(encoding="utf-8")
        self.assertIn("i===30", js)
        self.assertIn("switchTab(33", js)
        self.assertIn("IntelligenceCenter?.openView", js)

    def test_world_scene_js_consumes_read_only_apis(self):
        js = (ROOT / "static" / "world_scene.js").read_text(encoding="utf-8")
        self.assertIn("/api/world/viewport", js)
        self.assertIn("/api/world/armies", js)
        self.assertIn("/api/world/marches", js)
        self.assertIn("/api/world/entities", js)
        self.assertIn("stzb:stream-event", js)
        self.assertNotIn("new EventSource", js)
        self.assertIn("world_snapshot_complete", js)
        self.assertIn("window.WorldScenePanel", js)
        self.assertIn("WorldScenePanel.locateWid", js)
        # read-only: must not issue any write / action calls
        self.assertNotIn("method:'POST'", js.replace(" ", ""))

    def test_legacy_loader_delegates_before_stream_or_direct_fetch_setup(self):
        js = (ROOT / "static" / "world_scene.js").read_text(encoding="utf-8")
        loader = js.split(
            "async function loadWorldScenePanel(view, force=false){",
            1,
        )[1].split("\n}", 1)[0]
        delegate_index = loader.index("return delegate(view, force)")
        stream_index = loader.index("initWorldSceneStream()")
        fetch_index = loader.index("await apiFetch(endpoint)")
        self.assertLess(delegate_index, stream_index)
        self.assertLess(delegate_index, fetch_index)
        self.assertIn("delegate !== loadWorldScenePanel", loader)

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
        self.assertIn("📡 协议广度", html)
        self.assertIn("warShips / assistArmies / armyGroups / shortMessages", html)
        self.assertIn("function renderWsEntities", js)


if __name__ == "__main__":
    unittest.main()
