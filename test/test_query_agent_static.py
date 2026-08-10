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


if __name__ == "__main__":
    unittest.main()
