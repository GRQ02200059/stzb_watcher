import hashlib
import json
import secrets
import time
from datetime import datetime, timedelta

from .aggregation import ScoreAggregator
from .calculator import PRESETS, calculate_score
from .models import ScoreRule
from .repository import ScoreRepository


class ScoreCenterService:
    def __init__(
        self,
        get_connection,
        preview_ttl_seconds=900,
        preview_limit=100,
        now=None,
    ):
        self.get_connection = get_connection
        self.preview_ttl_seconds = int(preview_ttl_seconds)
        self.preview_limit = int(preview_limit)
        self.now = now or time.time
        self._previews = {}

    def preview(self, request):
        normalized = normalize_score_request(request)
        connection = self.get_connection()
        repository = ScoreRepository(connection)
        repository.ensure_schema()
        rule_row = self._resolve_rule(repository, normalized)
        rule = ScoreRule.from_mapping(rule_row["config"])
        aggregated = ScoreAggregator(connection, repository).aggregate(
            normalized["season"],
            start_time=normalized["startTime"],
            end_time_exclusive=normalized["endTimeExclusive"],
            union_filter=normalized["union"],
            group_filter=normalized["group"],
        )
        old_rows = {
            row["player_name"]: row
            for row in repository.list_scores(normalized["season"])
        }
        old_rank = {
            row["player_name"]: index + 1
            for index, row in enumerate(repository.list_scores(normalized["season"]))
        }
        rows = []
        for player in aggregated:
            breakdown = calculate_score(
                player.metrics,
                rule,
                adjustment=player.adjustment_score,
            )
            payload = {
                "playerName": player.player_name,
                "playerUid": player.player_uid,
                "unionName": player.union_name,
                "groupName": player.group_name,
                **breakdown.to_json(),
                "dataCompleteness": player.data_completeness,
                "missingSources": list(player.missing_sources),
                "sampleSize": player.metrics.battles,
            }
            previous = old_rows.get(player.player_name, {})
            payload["oldScore"] = round(float(previous.get("score") or 0), 2)
            payload["scoreDelta"] = round(
                payload["score"] - payload["oldScore"], 2
            )
            payload["oldRank"] = old_rank.get(player.player_name)
            payload["breakdown"] = {
                **breakdown.to_json(),
                "dataCompleteness": player.data_completeness,
                "missingSources": list(player.missing_sources),
            }
            rows.append(payload)
        rows.sort(
            key=lambda row: (
                -row["score"],
                -row["battleScore"],
                -row["siegeScore"],
                row["playerName"],
            )
        )
        for index, row in enumerate(rows):
            row["newRank"] = index + 1
            row["rankDelta"] = (
                0 if row["oldRank"] is None else row["oldRank"] - row["newRank"]
            )
        token = secrets.token_urlsafe(24)
        fingerprint = _database_fingerprint(connection)
        preview = {
            "token": token,
            "expiresAt": self.now() + self.preview_ttl_seconds,
            "request": normalized,
            "rule": rule_row,
            "fingerprint": fingerprint,
            "rows": rows,
        }
        self._prune_previews()
        self._previews[token] = preview
        missing_sources = sorted(
            {
                source
                for row in rows
                for source in row.get("missingSources", [])
            }
        )
        return {
            "previewToken": token,
            "expiresAt": preview["expiresAt"],
            "seasonId": normalized["season"],
            "dateRange": {
                "startDate": normalized["startDate"],
                "endDate": normalized["endDate"],
            },
            "ruleVersion": rule_row["version"],
            "rule": rule_row,
            "summary": _summary(rows, missing_sources),
            "rows": rows,
        }

    def recalculate(self, preview_token, request):
        normalized = normalize_score_request(request)
        preview = self._previews.get(str(preview_token or ""))
        if preview is None:
            raise ValueError("preview token is required or invalid")
        if self.now() > preview["expiresAt"]:
            self._previews.pop(preview["token"], None)
            raise ValueError("preview token expired")
        if normalized != preview["request"]:
            raise ValueError("preview request does not match recalculation")
        connection = self.get_connection()
        if _database_fingerprint(connection) != preview["fingerprint"]:
            raise ValueError("score source data changed; preview again")
        repository = ScoreRepository(connection)
        repository.ensure_schema()
        active = repository.active_rule(normalized["season"])
        if active is None or active["id"] != preview["rule"]["id"]:
            raise ValueError("active score rule changed; preview again")
        repository.replace_scores(
            normalized["season"],
            active["id"],
            preview["rows"],
        )
        self._previews.pop(preview["token"], None)
        return {
            "ok": True,
            "seasonId": normalized["season"],
            "ruleVersion": active["version"],
            "updated": len(preview["rows"]),
        }

    def list_scores(
        self,
        season_id,
        board="overall",
        union_filter="",
        group_filter="",
    ):
        if board not in {"overall", "battle", "siege"}:
            raise ValueError("invalid score board")
        connection = self.get_connection()
        repository = ScoreRepository(connection)
        repository.ensure_schema()
        rule = repository.active_rule(season_id)
        rows = repository.list_scores(season_id)
        filtered = []
        for item in rows:
            breakdown = item.get("breakdown") or {}
            if union_filter and union_filter not in str(item.get("union_name") or ""):
                continue
            if group_filter and group_filter not in str(
                breakdown.get("groupName") or ""
            ):
                continue
            filtered.append(_score_projection(item))
        key = {
            "overall": "score",
            "battle": "battleScore",
            "siege": "siegeScore",
        }[board]
        filtered.sort(
            key=lambda item: (
                -item[key],
                -item["score"],
                item["playerName"],
            )
        )
        for index, item in enumerate(filtered):
            item["rank"] = index + 1
        missing_sources = sorted(
            {
                source
                for item in filtered
                for source in item.get("missingSources", [])
            }
        )
        return {
            "ok": True,
            "seasonId": season_id,
            "board": board,
            "ruleVersion": rule["version"] if rule else 0,
            "rule": rule,
            "dataCompleteness": "partial" if missing_sources else "complete",
            "missingSources": missing_sources,
            "summary": _summary(filtered, missing_sources),
            "rows": filtered,
        }

    def player_detail(self, season_id, player_name):
        connection = self.get_connection()
        repository = ScoreRepository(connection)
        repository.ensure_schema()
        item = repository.score_detail(season_id, player_name)
        if item is None:
            return None
        detail = _score_projection(item)
        detail["adjustments"] = repository.list_adjustments(
            season_id, player_name
        )
        detail["rule"] = (
            repository.get_rule(item["rule_version_id"])
            if item.get("rule_version_id")
            else None
        )
        return detail

    def _resolve_rule(self, repository, request):
        rule_id = request.get("ruleVersionId")
        if rule_id:
            rule = repository.get_rule(rule_id)
        else:
            rule = repository.active_rule(request["season"])
        if rule is None:
            rule = repository.create_rule(
                request["season"],
                "同盟综合贡献",
                "alliance_contribution",
                PRESETS["alliance_contribution"],
            )
            repository.activate_rule(rule["id"])
            rule = repository.get_rule(rule["id"])
        return rule

    def _prune_previews(self):
        now = self.now()
        self._previews = {
            token: preview
            for token, preview in self._previews.items()
            if preview["expiresAt"] >= now
        }
        if len(self._previews) >= self.preview_limit:
            oldest = sorted(
                self._previews.items(),
                key=lambda item: item[1]["expiresAt"],
            )
            for token, _ in oldest[
                : len(self._previews) - self.preview_limit + 1
            ]:
                self._previews.pop(token, None)


def normalize_score_request(request):
    request = request or {}
    start_date = _parse_date(request.get("startDate"), "startDate")
    end_date = _parse_date(request.get("endDate"), "endDate")
    if start_date and end_date and start_date > end_date:
        raise ValueError("startDate must not exceed endDate")
    start_time = (
        int(start_date.timestamp())
        if start_date
        else _optional_int(request.get("startTime"))
    )
    end_exclusive = (
        int((end_date + timedelta(days=1)).timestamp())
        if end_date
        else _optional_int(request.get("endTimeExclusive"))
    )
    if end_exclusive is None and request.get("endTime") not in (None, ""):
        end_exclusive = int(request["endTime"]) + 1
    return {
        "season": str(request.get("season") or "current"),
        "startDate": start_date.strftime("%Y-%m-%d") if start_date else "",
        "endDate": end_date.strftime("%Y-%m-%d") if end_date else "",
        "startTime": start_time,
        "endTimeExclusive": end_exclusive,
        "union": str(request.get("union") or ""),
        "group": str(request.get("group") or ""),
        "ruleVersionId": _optional_int(request.get("ruleVersionId")),
    }


def _parse_date(value, field):
    if value in (None, ""):
        return None
    try:
        return datetime.strptime(str(value), "%Y-%m-%d")
    except ValueError as error:
        raise ValueError(f"{field} must use YYYY-MM-DD") from error


def _optional_int(value):
    return None if value in (None, "") else int(value)


def _database_fingerprint(connection):
    values = []
    for table in ("battles_v2", "attendance", "score_adjustments"):
        try:
            row = connection.execute(
                f"SELECT COUNT(*) AS count,COALESCE(MAX(rowid),0) AS maximum FROM {table}"
            ).fetchone()
            values.append((table, int(row["count"]), int(row["maximum"])))
        except Exception:
            values.append((table, -1, -1))
    return hashlib.sha256(
        json.dumps(values, sort_keys=True).encode("utf-8")
    ).hexdigest()


def _score_projection(item):
    breakdown = item.get("breakdown") or {}
    metrics = breakdown.get("metrics") or {
        "battles": item.get("battles", 0),
        "wins": item.get("wins", 0),
        "draws": item.get("draws", 0),
        "gongxunTotal": item.get("gongxun_total", 0),
        "mainCityCnt": item.get("main_city_cnt", 0),
        "tearCnt": item.get("tear_cnt", 0),
        "attendanceCnt": item.get("attendance_cnt", 0),
    }
    return {
        "playerName": item["player_name"],
        "playerUid": item.get("player_uid") or "",
        "unionName": item.get("union_name") or "",
        "groupName": breakdown.get("groupName") or "",
        "metrics": metrics,
        "battleScore": round(float(item.get("battle_score") or 0), 2),
        "siegeScore": round(float(item.get("siege_score") or 0), 2),
        "adjustmentScore": round(
            float(item.get("adjustment_score") or 0), 2
        ),
        "score": round(float(item.get("score") or 0), 2),
        "breakdown": breakdown,
        "dataCompleteness": breakdown.get("dataCompleteness", "legacy"),
        "missingSources": breakdown.get("missingSources", []),
        "calculatedAt": item.get("calculated_at") or item.get("updated_at"),
    }


def _summary(rows, missing_sources):
    return {
        "players": len(rows),
        "scoreTotal": round(sum(float(row.get("score") or 0) for row in rows), 2),
        "battleTotal": round(
            sum(float(row.get("battleScore") or 0) for row in rows), 2
        ),
        "siegeTotal": round(
            sum(float(row.get("siegeScore") or 0) for row in rows), 2
        ),
        "adjustmentTotal": round(
            sum(float(row.get("adjustmentScore") or 0) for row in rows), 2
        ),
        "dataCompleteness": "partial" if missing_sources else "complete",
        "missingSources": missing_sources,
    }
