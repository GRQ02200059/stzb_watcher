import unittest

from intelligence.hero_ids import normalize_hero_id


class HeroIdNormalizationTest(unittest.TestCase):
    def test_converts_season_13_and_14_ids_to_base_ids(self):
        self.assertEqual(100497, normalize_hero_id(130497))
        self.assertEqual(100003, normalize_hero_id(140003))

    def test_keeps_base_and_non_season_ids_unchanged(self):
        for hero_id in (0, 100027, 129999, 150001, -1):
            with self.subTest(hero_id=hero_id):
                self.assertEqual(hero_id, normalize_hero_id(hero_id))

    def test_accepts_numeric_strings(self):
        self.assertEqual(100497, normalize_hero_id("130497"))
        self.assertEqual(0, normalize_hero_id("not-an-id"))


if __name__ == "__main__":
    unittest.main()
