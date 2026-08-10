import json
import subprocess
from pathlib import Path


class BattleEngineAdapter:
    def __init__(self, run_cli=None, cli_command=None, timeout_sec=20):
        self.run_cli = run_cli or self._run_cli
        self.cli_command = cli_command or [
            "gradle",
            "-p",
            "battle-engine",
            "run",
            "--quiet",
        ]
        self.timeout_sec = timeout_sec

    def simulate(self, payload):
        cli_input = self.to_cli_input(payload)
        cli_output = self.run_cli(cli_input)
        return self.from_cli_output(cli_output)

    def to_cli_input(self, payload):
        return {
            "seed": int(payload.get("seed", 20260810)),
            "repeat": int(payload.get("repeat", 1)),
            "attacker": self._team(payload.get("blue") or payload.get("attacker") or {}),
            "defender": self._team(payload.get("red") or payload.get("defender") or {}),
        }

    def from_cli_output(self, output):
        if not output.get("ok"):
            return {
                "ok": False,
                "error": output.get("error", "battle engine failed"),
                "engine": "stzb-kotlin",
            }
        repeat = int(output.get("repeat", 1))
        first = output.get("firstRun") or {}
        response = {
            "ok": True,
            "engine": "stzb-kotlin",
            "engineResult": output,
        }
        if repeat == 1:
            response["result"] = {
                "winner": self._winner(first.get("outcome")),
                "blue": {
                    "total_arms": int(first.get("attackerRemain") or 0),
                    "hurt_arms": 0,
                },
                "red": {
                    "total_arms": int(first.get("defenderRemain") or 0),
                    "hurt_arms": 0,
                },
                "records": first.get("textLog") or [],
            }
            return response

        blue = int(output.get("attackerWins") or 0)
        red = int(output.get("defenderWins") or 0)
        draws = int(output.get("draws") or 0)
        response.update(
            {
                "repeat": repeat,
                "blue_wins": blue,
                "red_wins": red,
                "draws": draws,
                "blue_rate": round(blue / repeat * 100, 1),
                "red_rate": round(red / repeat * 100, 1),
                "draw_rate": round(draws / repeat * 100, 1),
            }
        )
        return response

    def _team(self, team):
        heroes = []
        for index, hero in enumerate(team.get("heros") or team.get("heroes") or []):
            hero_id = int(hero.get("id") or hero.get("heroId"))
            heroes.append(
                {
                    "heroId": hero_id,
                    "position": int(hero.get("position", index)),
                    "level": int(hero.get("level", 40)),
                    "advanceLevel": int(hero.get("up", hero.get("advanceLevel", 0))),
                    "troops": int(hero.get("troops", 9000)),
                    "extraSkillIds": [
                        int(value)
                        for value in hero.get(
                            "equip_skills", hero.get("extraSkillIds", [])
                        )
                        if int(value) > 0
                    ],
                    "attributePoints": hero.get("attributePoints")
                    or hero.get("extra_attrs")
                    or {
                        "attack": 0,
                        "defense": 0,
                        "strategy": 0,
                        "speed": 0,
                    },
                }
            )
        return {"morale": int(team.get("morale", 100)), "heroes": heroes}

    def _winner(self, outcome):
        return {
            "ATTACKER_WIN": "攻方胜",
            "DEFENDER_WIN": "守方胜",
            "DRAW": "平局",
        }.get(str(outcome), "平局")

    def _run_cli(self, cli_input):
        command = list(self.cli_command)
        cwd = Path(__file__).resolve().parent
        proc = subprocess.run(
            command,
            input=json.dumps(cli_input, ensure_ascii=False),
            text=True,
            capture_output=True,
            timeout=self.timeout_sec,
            check=False,
            cwd=cwd,
        )
        if proc.returncode != 0:
            raise RuntimeError(
                proc.stderr or proc.stdout or f"battle engine exited {proc.returncode}"
            )
        return json.loads(proc.stdout)
