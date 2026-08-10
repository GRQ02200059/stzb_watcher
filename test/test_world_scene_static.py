from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class WorldSceneStaticTest(unittest.TestCase):
    def test_dashboard_contains_world_scene_tab(self):
        html = (ROOT / "static" / "dashboard.html").read_text(encoding="utf-8")
        self.assertIn("switchTab(30,this)", html)
        self.assertIn("id='tab30'", html)
        self.assertIn("id='ws-map-body'", html)
        self.assertIn("id='ws-march-body'", html)
        self.assertIn("id='ws-army-body'", html)
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
        # read-only: must not issue any write / action calls
        self.assertNotIn("method:'POST'", js.replace(" ", ""))


if __name__ == "__main__":
    unittest.main()
