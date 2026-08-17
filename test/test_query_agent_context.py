import unittest

from query_agent.context import build_query_context


class QueryAgentContextTest(unittest.TestCase):
    def test_rejects_forbidden_context_keys(self):
        with self.assertRaises(ValueError):
            build_query_context("查这个队伍", {"sql": "select * from x"})

    def test_keeps_allowlisted_page_context(self):
        ctx = build_query_context(
            "查 10004",
            {
                "page": "map",
                "selectedWid": 10004,
                "selectedArmyId": 1001,
                "rawPacket": "[...]",
            },
        )
        self.assertEqual(ctx["message"], "查 10004")
        self.assertEqual(ctx["pageContext"]["selectedWid"], 10004)
        self.assertNotIn("rawPacket", ctx["pageContext"])


if __name__ == "__main__":
    unittest.main()
