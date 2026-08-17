from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class QueryAgentStaticTest(unittest.TestCase):
    def test_dashboard_contains_query_agent_panel(self):
        html = (ROOT / "static" / "dashboard.html").read_text(encoding="utf-8")
        self.assertIn("id=\"query-agent-panel\"", html)
        self.assertIn("id=\"query-agent-input\"", html)
        self.assertIn("战术检索", html)

    def test_app1_contains_query_agent_functions(self):
        js = (ROOT / "static" / "app1.js").read_text(encoding="utf-8")
        self.assertIn("function sendQueryAgentMessage()", js)
        self.assertIn("function applyQueryAgentAction(action)", js)
        self.assertIn("/api/query-agent/messages", js)
        self.assertIn("action.params?.packId", js)
        self.assertNotIn("action.params?.commandId", js)
        self.assertNotIn("action.params?.table", js)

    def test_simulator_displays_engine_source(self):
        js = (ROOT / "static" / "sim.js").read_text(encoding="utf-8")
        self.assertIn("const engineLabel = r.engine", js)
        self.assertIn("statusEl.textContent = (repeat===1 ? '战斗结束'", js)

    def test_ancient_map_uses_inline_dataset(self):
        js = (ROOT / "static" / "app2.js").read_text(encoding="utf-8")
        self.assertIn("function loadAncientChinaMapDemo()", js)
        self.assertNotIn("/static/china_full.geojson", js)

    def test_notification_feed_has_type_fallback(self):
        js = (ROOT / "static" / "app1.js").read_text(encoding="utf-8")
        self.assertIn("n.type || evt.type || 'notification'", js)


if __name__ == "__main__":
    unittest.main()
