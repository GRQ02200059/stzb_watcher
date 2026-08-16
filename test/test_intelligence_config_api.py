import unittest
from pathlib import Path

from flask import Flask

from intelligence.config_api import register_intelligence_config_api


ROOT = Path(__file__).resolve().parents[1]


class IntelligenceConfigApiTest(unittest.TestCase):
    def setUp(self):
        app = Flask(__name__)
        register_intelligence_config_api(
            app, ROOT / "data/intelligence/client-9.2.2"
        )
        self.client = app.test_client()

    def test_manifest_and_search(self):
        manifest = self.client.get("/api/intelligence/config/manifest").get_json()
        self.assertEqual(manifest["datasetVersion"], "client-9.2.2")
        heroes = self.client.get(
            "/api/intelligence/heroes?q=张辽&page=1&size=10"
        ).get_json()
        self.assertTrue(heroes["ok"])
        self.assertGreater(heroes["total"], 0)

    def test_hero_and_skill_detail(self):
        hero = self.client.get("/api/intelligence/heroes/100027")
        self.assertEqual(hero.status_code, 200)
        self.assertEqual(hero.get_json()["hero"]["name"], "张辽")
        skill = self.client.get("/api/intelligence/skills/200027")
        self.assertEqual(skill.status_code, 200)
        self.assertEqual(skill.get_json()["skill"]["name"], "其疾如风")

    def test_missing_entity_is_404(self):
        self.assertEqual(
            self.client.get("/api/intelligence/heroes/999999999").status_code,
            404,
        )


if __name__ == "__main__":
    unittest.main()
