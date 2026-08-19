import json
import os
import subprocess
from pathlib import Path

import sim_data
from intelligence.hero_ids import normalize_hero_id

_ENGINE_DIR = Path(__file__).resolve().parent / "battle-engine"
_INSTALL_CLI = _ENGINE_DIR / "build" / "install" / "stzb-battle-engine" / "bin" / "stzb-battle-engine"
_SOURCE_MANIFEST = _ENGINE_DIR / "SOURCE.json"
_ALLOWED_REPEATS = {1, 100, 1000}
_REPLAY_FIELDS = (
    "entrySnapshots",
    "roundSnapshots",
    "finalSnapshots",
    "events",
    "replayActions",
    "replayText",
    "diagnostics",
)

# 前端 sim.js SIM_POS = ['大营','中军','前锋']
_POSITION_NAME = ["大营", "中军", "前锋"]

# BattleStatus 枚举 -> 率土风格中文名（未覆盖的走通用回退）
_STATUS_NAME = {
    "CONFUSION": "混乱", "BERSERK": "狂暴", "HESITATION": "犹豫", "PANIC": "恐慌",
    "SHAKE": "震慑", "BURN": "灼烧", "HEX": "妖术", "DISARM": "缴械",
    "INSIGHT": "洞察", "EVADE": "闪避", "IGNORE_EVADE": "无视闪避",
    "DOUBLE_ATTACK": "连击", "FIRST_ACTION": "先攻", "EMERGENCY_RECOVERY": "急救",
    "ATTACK_BUFF": "攻击提升", "DEFENSE_BUFF": "防御提升", "STRATEGY_BUFF": "谋略提升",
    "SPEED_BUFF": "速度提升", "ATTACK_DEBUFF": "攻击降低", "DEFENSE_DEBUFF": "防御降低",
    "STRATEGY_DEBUFF": "谋略降低", "SPEED_DEBUFF": "速度降低",
}


class BattleEngineAdapter:
    def __init__(self, run_cli=None, cli_command=None, timeout_sec=30):
        self.run_cli = run_cli or self._run_cli
        # 默认走 installDist 产物：避免每次请求拉起 gradle/JVM，
        # 且 cwd 固定在 battle-engine，引擎才能定位 battle-config 资源。
        self.cli_command = cli_command or [str(_INSTALL_CLI)]
        self.timeout_sec = timeout_sec

    def simulate(self, payload):
        self.validate_payload(payload)
        cli_input = self.to_cli_input(payload)
        cli_output = self.run_cli(cli_input)
        return self.from_cli_output(cli_output)

    def validate_payload(self, payload):
        if not isinstance(payload, dict):
            raise ValueError("simulation payload must be an object")
        repeat = int(payload.get("repeat", 1))
        if repeat not in _ALLOWED_REPEATS:
            raise ValueError("repeat must be one of 1, 100, 1000")
        for side, alias in (("blue", "attacker"), ("red", "defender")):
            team = payload.get(side) or payload.get(alias) or {}
            heroes = team.get("heros") or team.get("heroes") or []
            if not 1 <= len(heroes) <= 3:
                raise ValueError(
                    "%s must contain 1 to 3 heroes" % side
                )
            positions = [
                int(hero.get("position", index))
                for index, hero in enumerate(heroes)
            ]
            if len(set(positions)) != len(positions):
                raise ValueError(
                    "%s hero positions must be unique" % side
                )

    def engine_metadata(self):
        manifest = json.loads(
            _SOURCE_MANIFEST.read_text(encoding="utf-8")
        )
        return {
            "name": "stzb-kotlin",
            "sourceRepository": manifest["sourceRepository"],
            "sourceCommit": manifest["sourceCommit"],
            "generatedAt": manifest["generatedAt"],
            "maxRepeat": 1000,
            "repeatOptions": sorted(_ALLOWED_REPEATS),
            "supportsDetailedReplay": True,
        }

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
                "rounds_played": int(first.get("roundsPlayed") or 0),
                "blue": {
                    "total_arms": int(first.get("attackerRemain") or 0),
                    "hurt_arms": self._total_hurt(first.get("attackerHeroes")),
                    "heros": self._hero_details(first.get("attackerHeroes")),
                },
                "red": {
                    "total_arms": int(first.get("defenderRemain") or 0),
                    "hurt_arms": self._total_hurt(first.get("defenderHeroes")),
                    "heros": self._hero_details(first.get("defenderHeroes")),
                },
                "records": self._narrate_log(first),
                "replay": {
                    field: first.get(field, self._replay_default(field))
                    for field in _REPLAY_FIELDS
                },
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
                "firstRun": first,
            }
        )
        return response

    def _replay_default(self, field):
        if field == "replayText":
            return ""
        if field == "diagnostics":
            return {}
        return []

    def _team(self, team):
        heroes = []
        for index, hero in enumerate(team.get("heros") or team.get("heroes") or []):
            hero_id = normalize_hero_id(
                hero.get("id") or hero.get("heroId")
            )
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

    def _hero_details(self, snapshots):
        """把引擎逐将快照补全成 sim.js _renderSingleResult 需要的形状。"""
        details = []
        for snap in snapshots or []:
            hero_id = normalize_hero_id(snap.get("heroId") or 0)
            info = sim_data.hero_by_id(hero_id) or {}
            position = int(snap.get("position") or 0)
            details.append(
                {
                    "id": hero_id,
                    "name": info.get("name", str(hero_id)),
                    "camp": info.get("camp", 0),
                    "pos": _POSITION_NAME[position] if 0 <= position < len(_POSITION_NAME) else f"P{position}",
                    "arms": int(snap.get("troops") or 0),
                    "hurt": int(snap.get("hurt") or 0),
                    "alive": bool(snap.get("alive")),
                }
            )
        return details

    def _total_hurt(self, snapshots):
        return sum(int(s.get("hurt") or 0) for s in (snapshots or []))

    def _narrate_log(self, first):
        """把完整语义事件渲染成中文摘要，兼容旧 structuredLog。"""
        structured = first.get("structuredLog") or first.get("events")
        if not structured:
            return first.get("textLog") or []
        if not all(isinstance(record, dict) for record in structured):
            return first.get("textLog") or []
        skills = sim_data.skill_index()

        def who(ref):
            if not ref:
                return "?"
            hero_id = normalize_hero_id(ref.get("heroId") or 0)
            info = sim_data.hero_by_id(hero_id) or {}
            name = info.get("name") or str(ref.get("heroId"))
            side = "攻方" if ref.get("side") == "ATTACKER" else "守方"
            pos = int(ref.get("position") or 0)
            pos_name = _POSITION_NAME[pos] if 0 <= pos < len(_POSITION_NAME) else f"P{pos}"
            return f"{side}·{pos_name}·{name}"

        def skill_name(sid):
            info = skills.get(int(sid or 0), {})
            return info.get("name") or f"战法{sid}"

        def status_name(code):
            return _STATUS_NAME.get(code, code)

        lines = []
        for rec in structured:
            t = rec.get("type")
            if t in ("roundStart", "RoundStart"):
                lines.append(f"===== 第 {rec.get('round')} 回合 =====")
            elif t in ("skill", "SkillTriggered"):
                lines.append(f"  {who(rec.get('source'))} 发动【{skill_name(rec.get('skillId'))}】")
            elif t in ("normalAttack", "NormalAttack"):
                lines.append(
                    f"  {who(rec.get('source'))} 普通攻击 {who(rec.get('target'))}，"
                    f"损失 {rec.get('damage')} 兵力（剩余 {rec.get('targetTroopsAfter')}）"
                )
            elif t in ("skillDamage", "SkillDamage"):
                lines.append(
                    f"  {who(rec.get('source'))}【{skill_name(rec.get('skillId'))}】"
                    f"命中 {who(rec.get('target'))}，损失 {rec.get('damage')} 兵力"
                    f"（剩余 {rec.get('targetTroopsAfter')}）"
                )
            elif t in ("ongoingDamage", "OngoingDamage"):
                lines.append(
                    f"  {who(rec.get('target'))} 受【{status_name(rec.get('status'))}】效果，"
                    f"损失 {rec.get('damage')} 兵力（剩余 {rec.get('targetTroopsAfter')}）"
                )
            elif t in ("recovery", "Recovery"):
                lines.append(
                    f"  {who(rec.get('target'))} 恢复 {rec.get('amount')} 兵力"
                    f"（剩余 {rec.get('targetTroopsAfter')}）"
                )
            elif t in ("status", "StatusApplied"):
                lines.append(
                    f"  {who(rec.get('source'))} 对 {who(rec.get('target'))} "
                    f"施加【{status_name(rec.get('status'))}】{rec.get('durationRounds')} 回合"
                )
            elif t in ("evaded", "Evaded"):
                lines.append(f"  {who(rec.get('target'))} 闪避了 {who(rec.get('source'))} 的攻击")
            elif t == "StatChanged":
                lines.append(
                    f"  {who(rec.get('target'))} {rec.get('stat')} "
                    f"变化 {rec.get('deltaExact')}"
                )
            elif t == "EffectBlocked":
                lines.append(
                    f"  {who(rec.get('target'))} 阻挡了效果 "
                    f"{rec.get('effectId')}"
                )
            elif t in ("battleEnd", "BattleEnd"):
                lines.append(f"===== 战斗结束：{self._winner(rec.get('outcome'))} =====")
        return lines

    def _run_cli(self, cli_input):
        command = list(self.cli_command)
        env = dict(os.environ)
        # 强制引擎在 JDK 17 下运行：Kotlin 1.9.23 编译产物在更高版本 JVM 上可能异常。
        jdk17 = "/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home"
        if os.path.isdir(jdk17):
            env["JAVA_HOME"] = jdk17
        proc = subprocess.run(
            command,
            input=json.dumps(cli_input, ensure_ascii=False),
            text=True,
            capture_output=True,
            timeout=self.timeout_sec,
            check=False,
            cwd=str(_ENGINE_DIR),
            env=env,
        )
        if proc.returncode != 0:
            raise RuntimeError(
                proc.stderr or proc.stdout or f"battle engine exited {proc.returncode}"
            )
        return json.loads(proc.stdout)
