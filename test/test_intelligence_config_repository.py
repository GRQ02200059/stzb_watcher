import unittest
from pathlib import Path

from intelligence.config_repository import IntelligenceConfigRepository


ROOT = Path(__file__).resolve().parents[1]


class IntelligenceConfigRepositoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo = IntelligenceConfigRepository(
            ROOT / "data/intelligence/client-9.2.2"
        )

    def test_search_filters_placeholder_heroes(self):
        result = self.repo.search_heroes("张辽", page=1, size=20)
        self.assertGreater(result["total"], 0)
        self.assertTrue(any(row["name"] == "张辽" for row in result["rows"]))
        defaults = self.repo.search_heroes("默认画像", page=1, size=20)
        self.assertEqual(defaults["total"], 0)

    def test_hero_detail_joins_initial_skill(self):
        result = self.repo.hero_detail(100027)
        self.assertEqual(result["hero"]["name"], "张辽")
        self.assertGreater(result["hero"]["attack_base"], 0)
        self.assertEqual(result["evidenceClass"], "CONFIG_FACT")
        self.assertEqual(result["datasetVersion"], "client-9.2.2")
        self.assertIsNotNone(result["initialSkill"])

    def test_hero_detail_accepts_season_hero_id(self):
        result = self.repo.hero_detail(130497)
        self.assertIsNotNone(result)
        self.assertEqual(100497, result["hero"]["heroid"])

    def test_skill_detail_joins_details_and_effects(self):
        result = self.repo.skill_detail(200027)
        self.assertEqual(result["skill"]["name"], "其疾如风")
        self.assertGreater(len(result["details"]), 0)
        self.assertTrue(all("effect" in row for row in result["details"]))
        self.assertEqual(result["evidenceClass"], "CONFIG_FACT")

    def test_repository_loads_once(self):
        first = self.repo.load_count
        self.repo.search_skills("攻击", page=1, size=5)
        self.repo.search_heroes("张", page=1, size=5)
        self.assertEqual(self.repo.load_count, first)


if __name__ == "__main__":
    unittest.main()
