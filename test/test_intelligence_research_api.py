import unittest
from pathlib import Path

from flask import Flask

from intelligence.config_repository import IntelligenceConfigRepository
from intelligence.research_api import register_intelligence_research_api


ROOT = Path(__file__).resolve().parents[1]


class IntelligenceResearchApiTest(unittest.TestCase):
    def setUp(self):
        app = Flask(__name__)
        register_intelligence_research_api(
            app,
            ROOT / "data/intelligence/client-9.2.2/research",
            config_repository=IntelligenceConfigRepository(
                ROOT / "data/intelligence/client-9.2.2"
            ),
        )
        self.client = app.test_client()

    def test_summary_and_card_pack_endpoints(self):
        summary = self.client.get(
            "/api/intelligence/research/summary"
        ).get_json()
        self.assertEqual(summary["cardPackCount"], 271)
        packs = self.client.get(
            "/api/intelligence/card-packs?q=802&page=1&size=10"
        ).get_json()
        self.assertTrue(packs["ok"])
        self.assertTrue(any(row["packId"] == 802 for row in packs["rows"]))
        detail = self.client.get("/api/intelligence/card-packs/802").get_json()
        self.assertGreater(detail["heroCount"], 0)

    def test_protocol_and_schema_endpoints(self):
        commands = self.client.get(
            "/api/intelligence/protocol/commands?q=5028"
        ).get_json()
        self.assertEqual(commands["rows"][0]["id"], 5028)
        command = self.client.get(
            "/api/intelligence/protocol/commands/5028"
        ).get_json()
        self.assertIn("9.2.4", command["versions"])
        schema = self.client.get(
            "/api/intelligence/protocol/schema?q=world"
        ).get_json()
        self.assertTrue(schema["ok"])
        table = self.client.get(
            "/api/intelligence/protocol/schema/Tb_world_city"
        ).get_json()
        self.assertGreater(table["fieldCount"], 20)

    def test_invalid_queries_and_missing_entities(self):
        self.assertEqual(
            self.client.get(
                "/api/intelligence/card-packs?page=0&size=10"
            ).status_code,
            400,
        )
        self.assertEqual(
            self.client.get(
                "/api/intelligence/protocol/commands?version=9.9.9"
            ).status_code,
            400,
        )
        self.assertEqual(
            self.client.get("/api/intelligence/card-packs/999999").status_code,
            404,
        )
        self.assertEqual(
            self.client.get(
                "/api/intelligence/protocol/schema/Tb_private_debug"
            ).status_code,
            404,
        )


if __name__ == "__main__":
    unittest.main()
