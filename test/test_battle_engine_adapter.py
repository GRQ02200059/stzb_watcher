import unittest
from unittest.mock import Mock

from battle_engine_adapter import BattleEngineAdapter, _INSTALL_CLI


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
                    "roundsPlayed": 5,
                    "attackerHeroes": [
                        {"heroId": 100027, "position": 0, "troops": 123,
                         "initialTroops": 9000, "hurt": 8877, "alive": True},
                    ],
                    "defenderHeroes": [
                        {"heroId": 100013, "position": 0, "troops": 0,
                         "initialTroops": 9000, "hurt": 9000, "alive": False},
                    ],
                    "textLog": ["BattleStart"],
                    "events": ["BattleStart"],
                },
            }
        )
        self.assertEqual(response["result"]["winner"], "攻方胜")
        self.assertEqual(response["result"]["blue"]["total_arms"], 123)
        self.assertEqual(response["result"]["records"], ["BattleStart"])
        # 契约新增字段：回合数 + 逐将明细（供 sim.js _renderSingleResult 消费）
        self.assertEqual(response["result"]["rounds_played"], 5)
        blue_heros = response["result"]["blue"]["heros"]
        self.assertEqual(len(blue_heros), 1)
        self.assertEqual(blue_heros[0]["name"], "张辽")
        self.assertEqual(blue_heros[0]["pos"], "大营")
        self.assertEqual(blue_heros[0]["arms"], 123)
        self.assertEqual(blue_heros[0]["hurt"], 8877)
        red_heros = response["result"]["red"]["heros"]
        self.assertEqual(red_heros[0]["name"], "马超")
        self.assertFalse(red_heros[0]["alive"])

    def test_narrates_structured_log_into_chinese(self):
        adapter = BattleEngineAdapter(run_cli=Mock())
        response = adapter.from_cli_output(
            {
                "ok": True,
                "repeat": 1,
                "firstRun": {
                    "outcome": "ATTACKER_WIN",
                    "attackerRemain": 1,
                    "defenderRemain": 0,
                    "roundsPlayed": 2,
                    "attackerHeroes": [],
                    "defenderHeroes": [],
                    "structuredLog": [
                        {"type": "roundStart", "round": 1},
                        {"type": "skill", "round": 1,
                         "source": {"side": "ATTACKER", "position": 0, "heroId": 100027},
                         "skillId": 200027, "trigger": "BATTLE_COMMAND"},
                        {"type": "normalAttack", "round": 1,
                         "source": {"side": "ATTACKER", "position": 0, "heroId": 100027},
                         "target": {"side": "DEFENDER", "position": 0, "heroId": 100013},
                         "damage": 500, "targetTroopsAfter": 8500},
                        {"type": "battleEnd", "outcome": "ATTACKER_WIN"},
                    ],
                },
            }
        )
        records = response["result"]["records"]
        text = "\n".join(records)
        # 回合分隔 + 真实武将/战法名 + 中文伤害叙述
        self.assertIn("第 1 回合", text)
        self.assertIn("张辽", text)
        self.assertIn("发动", text)
        self.assertIn("500", text)


class BattleEngineAdapterIntegrationTest(unittest.TestCase):
    @unittest.skipUnless(
        _INSTALL_CLI.exists(),
        "battle-engine installDist 产物不存在，跳过端到端测试",
    )
    def test_real_engine_returns_per_hero_details(self):
        payload = {
            "repeat": 1,
            "blue": {"morale": 100, "heros": [
                {"id": 100027, "level": 40, "up": 5},
                {"id": 100016, "level": 40, "up": 5},
                {"id": 100090, "level": 40, "up": 5},
            ]},
            "red": {"morale": 100, "heros": [
                {"id": 100013, "level": 40, "up": 5},
                {"id": 100649, "level": 40, "up": 5},
                {"id": 100023, "level": 40, "up": 5},
            ]},
        }
        result = BattleEngineAdapter().simulate(payload)
        self.assertTrue(result["ok"])
        self.assertEqual(result["engine"], "stzb-kotlin")
        res = result["result"]
        self.assertGreaterEqual(res["rounds_played"], 1)
        self.assertEqual(len(res["blue"]["heros"]), 3)
        # 引擎逐将快照被补全出真实武将名
        self.assertEqual(res["blue"]["heros"][0]["name"], "张辽")


if __name__ == "__main__":
    unittest.main()
