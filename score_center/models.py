from dataclasses import asdict, dataclass
import math


RULE_FIELDS = (
    "battleWeight",
    "winWeight",
    "drawWeight",
    "gongxunDivisor",
    "mainCityWeight",
    "tearWeight",
    "attendanceWeight",
)


@dataclass(frozen=True)
class ScoreRule:
    battle_weight: float
    win_weight: float
    draw_weight: float
    gongxun_divisor: float
    main_city_weight: float
    tear_weight: float
    attendance_weight: float

    @classmethod
    def from_mapping(cls, value):
        if not isinstance(value, dict) or set(value) != set(RULE_FIELDS):
            raise ValueError("score rule fields are invalid")
        numbers = {}
        for field in RULE_FIELDS:
            item = value[field]
            if isinstance(item, bool) or not isinstance(item, (int, float)):
                raise ValueError(f"{field} must be numeric")
            number = float(item)
            if not math.isfinite(number):
                raise ValueError(f"{field} must be finite")
            if field == "gongxunDivisor":
                if number <= 0 or number > 1_000_000:
                    raise ValueError("gongxunDivisor must be in (0, 1000000]")
            elif number < -1000 or number > 1000:
                raise ValueError(f"{field} is out of range")
            numbers[field] = number
        return cls(
            battle_weight=numbers["battleWeight"],
            win_weight=numbers["winWeight"],
            draw_weight=numbers["drawWeight"],
            gongxun_divisor=numbers["gongxunDivisor"],
            main_city_weight=numbers["mainCityWeight"],
            tear_weight=numbers["tearWeight"],
            attendance_weight=numbers["attendanceWeight"],
        )

    def to_json(self):
        return {
            "battleWeight": self.battle_weight,
            "winWeight": self.win_weight,
            "drawWeight": self.draw_weight,
            "gongxunDivisor": self.gongxun_divisor,
            "mainCityWeight": self.main_city_weight,
            "tearWeight": self.tear_weight,
            "attendanceWeight": self.attendance_weight,
        }


@dataclass(frozen=True)
class ScoreMetrics:
    battles: int = 0
    wins: int = 0
    draws: int = 0
    gongxun_total: int = 0
    main_city_cnt: int = 0
    tear_cnt: int = 0
    attendance_cnt: int = 0

    def to_json(self):
        return {
            "battles": int(self.battles),
            "wins": int(self.wins),
            "draws": int(self.draws),
            "gongxunTotal": int(self.gongxun_total),
            "mainCityCnt": int(self.main_city_cnt),
            "tearCnt": int(self.tear_cnt),
            "attendanceCnt": int(self.attendance_cnt),
        }


@dataclass(frozen=True)
class ScoreBreakdown:
    metrics: ScoreMetrics
    rule: ScoreRule
    components: dict
    battle_score: float
    siege_score: float
    adjustment_score: float
    score: float

    def to_json(self):
        return {
            "metrics": self.metrics.to_json(),
            "rule": self.rule.to_json(),
            "components": dict(self.components),
            "battleScore": self.battle_score,
            "siegeScore": self.siege_score,
            "adjustmentScore": self.adjustment_score,
            "score": self.score,
        }
