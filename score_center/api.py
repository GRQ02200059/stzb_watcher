from flask import jsonify, request

from .calculator import PRESETS
from .repository import ScoreRepository
from .service import ScoreCenterService


def register_score_center_api(app, get_connection, get_profile_id=None):
    service = ScoreCenterService(get_connection, get_profile_id=get_profile_id)

    @app.route("/api/custom_scores")
    def score_center_list():
        try:
            result = service.list_scores(
                request.args.get("season", "current"),
                board=request.args.get("board", "overall"),
                union_filter=request.args.get("union", ""),
                group_filter=request.args.get("group", ""),
            )
            return jsonify(result)
        except ValueError as error:
            return jsonify({"ok": False, "error": str(error)}), 400

    @app.route("/api/custom_scores/player/<path:player_name>")
    def score_center_player(player_name):
        result = service.player_detail(
            request.args.get("season", "current"), player_name
        )
        return (
            jsonify({"ok": True, **result})
            if result
            else (jsonify({"ok": False, "error": "player not found"}), 404)
        )

    @app.route("/api/custom_scores/rules")
    def score_center_rules():
        connection = get_connection()
        repository = ScoreRepository(connection)
        repository.ensure_schema()
        season = request.args.get("season", "current")
        return jsonify(
            {
                "ok": True,
                "seasonId": season,
                "presets": PRESETS,
                "activeRule": repository.active_rule(season),
                "rules": repository.list_rules(season),
            }
        )

    @app.route("/api/custom_scores/rules", methods=["POST"])
    def score_center_create_rule():
        body = request.get_json(silent=True) or {}
        try:
            connection = get_connection()
            repository = ScoreRepository(connection)
            repository.ensure_schema()
            rule = repository.create_rule(
                body.get("season", "current"),
                body.get("name", "新积分规则"),
                body.get("presetKey", "custom"),
                body.get("config") or {},
            )
            return jsonify({"ok": True, "rule": rule})
        except ValueError as error:
            return jsonify({"ok": False, "error": str(error)}), 400

    @app.route(
        "/api/custom_scores/rules/<int:rule_id>/activate",
        methods=["POST"],
    )
    def score_center_activate_rule(rule_id):
        try:
            connection = get_connection()
            repository = ScoreRepository(connection)
            repository.ensure_schema()
            return jsonify(
                {"ok": True, "rule": repository.activate_rule(rule_id)}
            )
        except ValueError as error:
            return jsonify({"ok": False, "error": str(error)}), 400

    @app.route("/api/custom_scores/adjustments")
    def score_center_adjustments():
        connection = get_connection()
        repository = ScoreRepository(connection)
        repository.ensure_schema()
        return jsonify(
            {
                "ok": True,
                "rows": repository.list_adjustments(
                    request.args.get("season", "current"),
                    request.args.get("player") or None,
                ),
            }
        )

    @app.route("/api/custom_scores/adjustments", methods=["POST"])
    def score_center_add_adjustment():
        body = request.get_json(silent=True) or {}
        try:
            connection = get_connection()
            repository = ScoreRepository(connection)
            repository.ensure_schema()
            row = repository.add_adjustment(
                body.get("season", "current"),
                body.get("playerName"),
                body.get("playerUid", ""),
                body.get("points"),
                body.get("reason"),
                body.get("createdBy", ""),
            )
            return jsonify({"ok": True, "adjustment": row})
        except (TypeError, ValueError) as error:
            return jsonify({"ok": False, "error": str(error)}), 400

    @app.route(
        "/api/custom_scores/adjustments/<int:adjustment_id>",
        methods=["DELETE"],
    )
    def score_center_delete_adjustment(adjustment_id):
        body = request.get_json(silent=True) or {}
        try:
            connection = get_connection()
            repository = ScoreRepository(connection)
            repository.ensure_schema()
            repository.delete_adjustment(
                adjustment_id, body.get("season", "current")
            )
            return jsonify({"ok": True})
        except ValueError as error:
            return jsonify({"ok": False, "error": str(error)}), 400

    @app.route("/api/custom_scores/preview", methods=["POST"])
    def score_center_preview():
        try:
            return jsonify(
                {"ok": True, **service.preview(request.get_json(silent=True))}
            )
        except ValueError as error:
            return jsonify({"ok": False, "error": str(error)}), 400

    @app.route("/api/custom_scores/recalc", methods=["POST"])
    def score_center_recalc():
        body = request.get_json(silent=True) or {}
        try:
            result = service.recalculate(body.get("previewToken"), body)
            return jsonify(result)
        except ValueError as error:
            return jsonify({"ok": False, "error": str(error)}), 400

    return service
