import unittest
from unittest.mock import Mock

from battle_engine_adapter import BattleEngineAdapter


class BattleEngineAdapterTest(unittest.TestCase):
    def test_converts_legacy_request_to_cli_input(self):
        adapter = BattleEngineAdapter(run_cli=Mock())
        cli = adapter.to_cli_input(
            {
                "repeat": 1,
                "blue": {
                    "morale": 100,
                    "heros": [
                        {
                            "id": 100027,
                            "level": 40,
                            "up": 5,
                            "equip_skills": [200101],
                        }
                    ],
                },
                "red": {
                    "morale": 95,
                    "heros": [
                        {
                            "id": 100013,
                            "level": 40,
                            "up": 4,
                            "equip_skills": [],
                        }
                    ],
                },
            }
        )
        self.assertEqual(cli["attacker"]["heroes"][0]["heroId"], 100027)
        self.assertEqual(cli["attacker"]["heroes"][0]["advanceLevel"], 5)
        self.assertEqual(cli["attacker"]["heroes"][0]["extraSkillIds"], [200101])
        self.assertEqual(cli["defender"]["morale"], 95)

    def test_converts_cli_multi_output_to_legacy_response(self):
        adapter = BattleEngineAdapter(run_cli=Mock())
        response = adapter.from_cli_output(
            {
                "ok": True,
                "repeat": 100,
                "attackerWins": 60,
                "defenderWins": 30,
                "draws": 10,
                "firstRun": {
                    "outcome": "ATTACKER_WIN",
                    "attackerRemain": 1,
                    "defenderRemain": 0,
                    "textLog": ["x"],
                    "events": [],
                },
            }
        )
        self.assertEqual(response["engine"], "stzb-kotlin")
        self.assertEqual(response["blue_wins"], 60)
        self.assertEqual(response["blue_rate"], 60.0)

    def test_converts_cli_single_output_to_legacy_result(self):
        adapter = BattleEngineAdapter(run_cli=Mock())
        response = adapter.from_cli_output(
            {
                "ok": True,
                "repeat": 1,
                "attackerWins": 1,
                "defenderWins": 0,
                "draws": 0,
                "firstRun": {
                    "outcome": "ATTACKER_WIN",
                    "attackerRemain": 123,
                    "defenderRemain": 0,
                    "textLog": ["BattleStart"],
                    "events": ["BattleStart"],
                },
            }
        )
        self.assertEqual(response["result"]["winner"], "攻方胜")
        self.assertEqual(response["result"]["blue"]["total_arms"], 123)
        self.assertEqual(response["result"]["records"], ["BattleStart"])


if __name__ == "__main__":
    unittest.main()
