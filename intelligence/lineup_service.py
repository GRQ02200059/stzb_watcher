from collections import defaultdict
from typing import Any, Dict, Iterable, List, Optional, Sequence


ATTACK_WINS = {1, 7, 11}
DEFENCE_WINS = {2, 6, 12}
MINIMUM_RECOMMENDED_SAMPLE = 10


def canonical_lineup_key(hero_ids: Sequence[int]) -> str:
    normalized = [int(hero_id) for hero_id in hero_ids]
    if (
        len(normalized) != 3
        or len(set(normalized)) != 3
        or any(hero_id <= 0 for hero_id in normalized)
    ):
        raise ValueError("lineup requires three unique positive hero ids")
    return ".".join(str(hero_id) for hero_id in normalized)


class LineupStatisticsService:
    def __init__(self, get_connection, config_repository=None) -> None:
        self.get_connection = get_connection
        self.config_repository = config_repository

    def list_lineups(
        self,
        hero_id: Optional[int] = None,
        minimum_sample: int = 1,
        page: int = 1,
        size: int = 50,
    ) -> Dict[str, Any]:
        aggregates = self._aggregate()
        rows = []
        for key, aggregate in aggregates.items():
            if hero_id is not None and int(hero_id) not in aggregate["heroIds"]:
                continue
            if aggregate["sampleSize"] < max(1, int(minimum_sample)):
                continue
            rows.append(self._summary(key, aggregate))
        rows.sort(
            key=lambda row: (
                -row["battleStats"]["sampleSize"],
                -row["battleStats"]["winRate"],
                row["key"],
            )
        )
        page = max(1, int(page))
        size = min(100, max(1, int(size)))
        start = (page - 1) * size
        return {
            "datasetVersion": self._dataset_version(),
            "evidenceClass": "BATTLE_STAT",
            "total": len(rows),
            "page": page,
            "size": size,
            "rows": rows[start : start + size],
        }

    def get_lineup(self, key: str) -> Optional[Dict[str, Any]]:
        try:
            normalized_key = canonical_lineup_key(
                [int(part) for part in str(key).split(".")]
            )
        except (TypeError, ValueError):
            return None
        aggregate = self._aggregate().get(normalized_key)
        if aggregate is None:
            return None
        return self._detail(normalized_key, aggregate)

    def get_matchup(
        self, left_key: str, right_key: str
    ) -> Optional[Dict[str, Any]]:
        try:
            left = canonical_lineup_key(
                [int(part) for part in str(left_key).split(".")]
            )
            right = canonical_lineup_key(
                [int(part) for part in str(right_key).split(".")]
            )
        except (TypeError, ValueError):
            return None
        stats = {
            "sampleSize": 0,
            "wins": 0,
            "draws": 0,
            "losses": 0,
            "latestBattleTime": 0,
        }
        for battle in self._battle_lineups():
            atk = battle.get("atk")
            defence = battle.get("def")
            if not atk or not defence:
                continue
            if atk["key"] == left and defence["key"] == right:
                side = "atk"
            elif atk["key"] == right and defence["key"] == left:
                side = "def"
            else:
                continue
            outcome = _outcome(side, battle["result"])
            stats["sampleSize"] += 1
            stats[outcome] += 1
            stats["latestBattleTime"] = max(
                stats["latestBattleTime"], battle["time"]
            )
        return {
            "leftKey": left,
            "rightKey": right,
            "battleStats": {
                "evidenceClass": "BATTLE_STAT",
                **stats,
                "winRate": _win_rate(stats),
            },
            "confidence": _confidence(stats["sampleSize"]),
        }

    def _battle_lineups(self) -> List[Dict[str, Any]]:
        connection = self.get_connection()
        rows = connection.execute(
            """
            SELECT
                bv.battle_id,
                COALESCE(bv.time, 0) AS battle_time,
                COALESCE(bv.result, 0) AS result,
                bh.side,
                bh.pos,
                bh.hero_id,
                bh.hero_name,
                COALESCE(bh.level, 0) AS level
            FROM battles_v2 bv
            JOIN battle_heroes bh ON bh.battle_id = bv.battle_id
            WHERE bh.side IN ('atk', 'def') AND COALESCE(bh.hero_id, 0) > 0
            ORDER BY bv.battle_id, bh.side, bh.pos, bh.id
            """
        ).fetchall()
        battles: Dict[int, Dict[str, Any]] = {}
        for row in rows:
            item = dict(row)
            battle_id = int(item["battle_id"])
            battle = battles.setdefault(
                battle_id,
                {
                    "battleId": battle_id,
                    "result": int(item["result"] or 0),
                    "time": int(item["battle_time"] or 0),
                    "sides": defaultdict(list),
                },
            )
            battle["sides"][item["side"]].append(item)

        normalized_battles = []
        for battle in battles.values():
            normalized_battle = {
                "battleId": battle["battleId"],
                "time": battle["time"],
                "result": battle["result"],
                "atk": None,
                "def": None,
            }
            for side in ("atk", "def"):
                heroes = _normalize_side(battle["sides"].get(side, []))
                if heroes is None:
                    continue
                normalized_battle[side] = {
                    "key": canonical_lineup_key(
                        [int(hero["hero_id"]) for hero in heroes]
                    ),
                    "heroes": heroes,
                }
            normalized_battles.append(normalized_battle)
        return normalized_battles

    def _aggregate(self) -> Dict[str, Dict[str, Any]]:
        aggregates: Dict[str, Dict[str, Any]] = {}
        for battle in self._battle_lineups():
            for side in ("atk", "def"):
                lineup = battle.get(side)
                if lineup is None:
                    continue
                key = lineup["key"]
                heroes = lineup["heroes"]
                aggregate = aggregates.setdefault(
                    key,
                    {
                        "heroIds": [int(hero["hero_id"]) for hero in heroes],
                        "heroes": heroes,
                        "sampleSize": 0,
                        "wins": 0,
                        "draws": 0,
                        "losses": 0,
                        "latestBattleTime": 0,
                        "opponents": defaultdict(
                            lambda: {
                                "sampleSize": 0,
                                "wins": 0,
                                "draws": 0,
                                "losses": 0,
                            }
                        ),
                    },
                )
                outcome = _outcome(side, battle["result"])
                aggregate["sampleSize"] += 1
                aggregate[outcome] += 1
                aggregate["latestBattleTime"] = max(
                    aggregate["latestBattleTime"], battle["time"]
                )
                opponent_side = "def" if side == "atk" else "atk"
                opponent = battle.get(opponent_side)
                if opponent is not None:
                    opponent_key = opponent["key"]
                    opponent_stats = aggregate["opponents"][opponent_key]
                    opponent_stats["sampleSize"] += 1
                    opponent_stats[outcome] += 1
        return aggregates

    def _summary(self, key: str, aggregate: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "key": key,
            "configFacts": self._config_facts(
                aggregate["heroIds"], aggregate["heroes"]
            ),
            "battleStats": self._battle_stats(aggregate, include_opponents=False),
            "confidence": _confidence(aggregate["sampleSize"]),
        }

    def _detail(self, key: str, aggregate: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "key": key,
            "datasetVersion": self._dataset_version(),
            "configFacts": self._config_facts(
                aggregate["heroIds"], aggregate["heroes"]
            ),
            "battleStats": self._battle_stats(aggregate, include_opponents=True),
            "simulationLink": {
                "evidenceClass": "SIMULATION",
                "hasResult": False,
                "notice": "尚未运行模拟；模拟结果不等同于真实历史胜率。",
                "lineup": {
                    "heroes": [
                        {
                            "id": hero_id,
                            "level": int(hero.get("level") or 40),
                            "up": 5,
                            "equip_skills": [],
                        }
                        for hero_id, hero in zip(
                            aggregate["heroIds"], aggregate["heroes"]
                        )
                    ],
                    "morale": 100,
                },
            },
            "confidence": _confidence(aggregate["sampleSize"]),
        }

    def _battle_stats(
        self, aggregate: Dict[str, Any], include_opponents: bool
    ) -> Dict[str, Any]:
        result = {
            "evidenceClass": "BATTLE_STAT",
            "sampleSize": aggregate["sampleSize"],
            "wins": aggregate["wins"],
            "draws": aggregate["draws"],
            "losses": aggregate["losses"],
            "winRate": _win_rate(aggregate),
            "latestBattleTime": aggregate["latestBattleTime"],
        }
        if include_opponents:
            opponents = []
            for key, stats in aggregate["opponents"].items():
                opponents.append(
                    {
                        "key": key,
                        "sampleSize": stats["sampleSize"],
                        "wins": stats["wins"],
                        "draws": stats["draws"],
                        "losses": stats["losses"],
                        "winRate": _win_rate(stats),
                    }
                )
            opponents.sort(
                key=lambda row: (-row["sampleSize"], -row["winRate"], row["key"])
            )
            result["commonOpponents"] = opponents[:10]
        return result

    def _config_facts(
        self, hero_ids: Iterable[int], observed_heroes: List[Dict[str, Any]]
    ) -> Dict[str, Any]:
        observed_by_id = {
            int(hero["hero_id"]): hero for hero in observed_heroes
        }
        heroes = []
        for position, hero_id in enumerate(hero_ids):
            config = None
            if self.config_repository is not None:
                config = self.config_repository.hero_by_id.get(int(hero_id))
            observed = observed_by_id.get(int(hero_id), {})
            heroes.append(
                {
                    "position": position,
                    "heroId": int(hero_id),
                    "name": (
                        (config or {}).get("name")
                        or observed.get("hero_name")
                        or str(hero_id)
                    ),
                    "level": int(observed.get("level") or 0),
                    "resolved": config is not None,
                }
            )
        return {
            "evidenceClass": "CONFIG_FACT",
            "datasetVersion": self._dataset_version(),
            "heroes": heroes,
        }

    def _dataset_version(self) -> str:
        if self.config_repository is None:
            return ""
        return str(self.config_repository.dataset_version)


def _normalize_side(rows: List[Dict[str, Any]]) -> Optional[List[Dict[str, Any]]]:
    by_position = {}
    for row in rows:
        position = int(row["pos"])
        if position not in by_position:
            by_position[position] = row
    heroes = [by_position[position] for position in sorted(by_position)]
    if len(heroes) != 3:
        return None
    hero_ids = [int(hero["hero_id"]) for hero in heroes]
    if len(set(hero_ids)) != 3:
        return None
    return heroes


def _outcome(side: str, result: int) -> str:
    result = int(result)
    if (side == "atk" and result in ATTACK_WINS) or (
        side == "def" and result in DEFENCE_WINS
    ):
        return "wins"
    if result in ATTACK_WINS or result in DEFENCE_WINS:
        return "losses"
    return "draws"


def _win_rate(stats: Dict[str, Any]) -> float:
    sample_size = int(stats["sampleSize"])
    if sample_size <= 0:
        return 0.0
    return round(
        (int(stats["wins"]) + int(stats["draws"]) * 0.5)
        * 100.0
        / sample_size,
        1,
    )


def _confidence(sample_size: int) -> Dict[str, Any]:
    sample_size = int(sample_size)
    if sample_size < MINIMUM_RECOMMENDED_SAMPLE:
        label = "low"
        notice = "样本不足，仅用于观察趋势，不能视为确定性克制关系。"
    elif sample_size < 30:
        label = "medium"
        notice = "样本达到基础参考线，仍需结合对手与战法配置判断。"
    else:
        label = "high"
        notice = "样本相对充足，但历史统计仍不代表确定性结果。"
    return {
        "label": label,
        "sampleSize": sample_size,
        "minimumRecommendedSample": MINIMUM_RECOMMENDED_SAMPLE,
        "notice": notice,
    }
