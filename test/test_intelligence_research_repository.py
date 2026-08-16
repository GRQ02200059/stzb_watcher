import unittest
from pathlib import Path

from intelligence.config_repository import IntelligenceConfigRepository
from intelligence.research_repository import ResearchCatalogRepository


ROOT = Path(__file__).resolve().parents[1]


class IntelligenceResearchRepositoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        config = IntelligenceConfigRepository(
            ROOT / "data/intelligence/client-9.2.2"
        )
        cls.repo = ResearchCatalogRepository(
            ROOT / "data/intelligence/client-9.2.2/research",
            config_repository=config,
        )

    def test_summary_reports_validated_catalog_counts(self):
        summary = self.repo.summary()
        self.assertEqual(summary["cardPackCount"], 271)
        self.assertEqual(summary["protocolVersions"]["9.2.2"], 2594)
        self.assertEqual(summary["protocolVersions"]["9.2.4"], 2655)
        self.assertEqual(
            summary["protocolDiff"],
            {"added": 63, "removed": 2, "renamed": 1},
        )
        self.assertEqual(summary["schemaTableCount"], 12)

    def test_card_pack_search_detail_and_hero_reverse_lookup(self):
        result = self.repo.search_card_packs(query="802", page=1, size=10)
        self.assertTrue(any(row["packId"] == 802 for row in result["rows"]))
        detail = self.repo.card_pack_detail(802)
        self.assertEqual(detail["packId"], 802)
        self.assertEqual(detail["heroCount"], len(detail["heroes"]))
        self.assertTrue(detail["countryDistribution"])
        hero_id = detail["heroes"][0]["heroid"]
        reverse = self.repo.hero_card_packs(hero_id)
        self.assertTrue(any(row["packId"] == 802 for row in reverse))

    def test_command_search_and_versioned_detail(self):
        result = self.repo.search_commands(query="5028", page=1, size=10)
        self.assertEqual(result["rows"][0]["id"], 5028)
        detail = self.repo.command_detail(5028)
        self.assertIn("9.2.2", detail["versions"])
        self.assertIn("9.2.4", detail["versions"])
        self.assertIn("SEND_WORLD_SCENCE_CHANGE_INFO", detail["names"])
        added = self.repo.search_commands(change="added", page=1, size=100)
        self.assertEqual(added["total"], 63)

    def test_schema_search_and_detail_stay_allowlisted(self):
        result = self.repo.search_schema(query="world")
        self.assertTrue(any(row["table"] == "Tb_world_city" for row in result["rows"]))
        detail = self.repo.schema_detail("Tb_world_city")
        self.assertGreater(detail["fieldCount"], 20)
        self.assertTrue(any(field["name"] == "wid" for field in detail["fields"]))
        self.assertIsNone(self.repo.schema_detail("Tb_private_debug"))

    def test_repository_loads_once(self):
        count = self.repo.load_count
        self.repo.search_card_packs(query="8")
        self.repo.search_commands(query="WORLD")
        self.repo.search_schema(query="army")
        self.assertEqual(self.repo.load_count, count)


if __name__ == "__main__":
    unittest.main()
