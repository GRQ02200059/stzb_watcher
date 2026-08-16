import math
import unittest

from score_center.calculator import PRESETS, calculate_score
from score_center.models import ScoreMetrics, ScoreRule


class ScoreCenterCalculatorTest(unittest.TestCase):
    def test_default_formula_returns_explainable_components(self):
        metrics = ScoreMetrics(
            battles=10,
            wins=4,
            draws=2,
            gongxun_total=3000,
            main_city_cnt=2,
            tear_cnt=1,
            attendance_cnt=3,
        )
        rule = ScoreRule.from_mapping(PRESETS["alliance_contribution"])

        result = calculate_score(metrics, rule, adjustment=1.5)

        self.assertEqual(result.battle_score, 22.0)
        self.assertEqual(result.siege_score, 16.0)
        self.assertEqual(result.adjustment_score, 1.5)
        self.assertEqual(result.score, 39.5)
        payload = result.to_json()
        self.assertEqual(payload["metrics"]["battles"], 10)
        self.assertEqual(payload["rule"]["winWeight"], 2.0)
        self.assertEqual(payload["components"]["gongxun"], 3.0)

    def test_rule_validation_rejects_unsafe_values(self):
        valid = dict(PRESETS["alliance_contribution"])
        invalid_values = (True, math.inf, math.nan, "1")
        for value in invalid_values:
            rule = {**valid, "battleWeight": value}
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    ScoreRule.from_mapping(rule)
        with self.assertRaises(ValueError):
            ScoreRule.from_mapping({**valid, "gongxunDivisor": 0})
        with self.assertRaises(ValueError):
            ScoreRule.from_mapping({**valid, "winWeight": 1001})
        with self.assertRaises(ValueError):
            ScoreRule.from_mapping({**valid, "unknown": 1})
        missing = dict(valid)
        missing.pop("tearWeight")
        with self.assertRaises(ValueError):
            ScoreRule.from_mapping(missing)

    def test_all_presets_are_valid_and_distinct(self):
        self.assertEqual(
            set(PRESETS),
            {"alliance_contribution", "season_reward", "siege_priority"},
        )
        rules = [ScoreRule.from_mapping(PRESETS[key]) for key in sorted(PRESETS)]
        self.assertEqual(len({rule.to_json()["mainCityWeight"] for rule in rules}), 3)


if __name__ == "__main__":
    unittest.main()
