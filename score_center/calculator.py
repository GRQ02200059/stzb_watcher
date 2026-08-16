from .models import ScoreBreakdown, ScoreMetrics, ScoreRule


PRESETS = {
    "alliance_contribution": {
        "battleWeight": 1.0,
        "winWeight": 2.0,
        "drawWeight": 0.5,
        "gongxunDivisor": 1000.0,
        "mainCityWeight": 5.0,
        "tearWeight": 3.0,
        "attendanceWeight": 1.0,
    },
    "season_reward": {
        "battleWeight": 1.5,
        "winWeight": 2.5,
        "drawWeight": 0.5,
        "gongxunDivisor": 800.0,
        "mainCityWeight": 8.0,
        "tearWeight": 4.0,
        "attendanceWeight": 2.0,
    },
    "siege_priority": {
        "battleWeight": 0.5,
        "winWeight": 1.0,
        "drawWeight": 0.25,
        "gongxunDivisor": 2000.0,
        "mainCityWeight": 12.0,
        "tearWeight": 7.0,
        "attendanceWeight": 4.0,
    },
}


def calculate_score(metrics, rule, adjustment=0):
    if not isinstance(metrics, ScoreMetrics) or not isinstance(rule, ScoreRule):
        raise TypeError("metrics and rule must be typed score objects")
    adjustment = float(adjustment)
    components = {
        "battles": round(metrics.battles * rule.battle_weight, 2),
        "wins": round(metrics.wins * rule.win_weight, 2),
        "draws": round(metrics.draws * rule.draw_weight, 2),
        "gongxun": round(metrics.gongxun_total / rule.gongxun_divisor, 2),
        "mainCity": round(metrics.main_city_cnt * rule.main_city_weight, 2),
        "tear": round(metrics.tear_cnt * rule.tear_weight, 2),
        "attendance": round(
            metrics.attendance_cnt * rule.attendance_weight, 2
        ),
    }
    battle_score = round(
        components["battles"]
        + components["wins"]
        + components["draws"]
        + components["gongxun"],
        2,
    )
    siege_score = round(
        components["mainCity"]
        + components["tear"]
        + components["attendance"],
        2,
    )
    adjustment_score = round(adjustment, 2)
    return ScoreBreakdown(
        metrics=metrics,
        rule=rule,
        components=components,
        battle_score=battle_score,
        siege_score=siege_score,
        adjustment_score=adjustment_score,
        score=round(battle_score + siege_score + adjustment_score, 2),
    )
