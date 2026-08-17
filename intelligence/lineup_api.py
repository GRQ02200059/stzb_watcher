from flask import jsonify, request

from .lineup_service import LineupStatisticsService


def register_intelligence_lineup_api(
    app,
    get_connection,
    config_repository=None,
):
    service = LineupStatisticsService(get_connection, config_repository)

    @app.route("/api/intelligence/lineups")
    def intelligence_lineups():
        hero_id = request.args.get("heroId")
        try:
            result = service.list_lineups(
                hero_id=int(hero_id) if hero_id else None,
                minimum_sample=request.args.get("minSample", 1),
                page=request.args.get("page", 1),
                size=request.args.get("size", 50),
            )
        except (TypeError, ValueError):
            return jsonify({"ok": False, "error": "invalid query"}), 400
        return jsonify({"ok": True, **result})

    @app.route(
        "/api/intelligence/lineups/<left_key>/matchup/<right_key>"
    )
    def intelligence_lineup_matchup(left_key, right_key):
        result = service.get_matchup(left_key, right_key)
        if result is None:
            return jsonify({"ok": False, "error": "lineup not found"}), 404
        return jsonify({"ok": True, **result})

    @app.route("/api/intelligence/lineups/<key>")
    def intelligence_lineup_detail(key):
        result = service.get_lineup(key)
        if result is None:
            return jsonify({"ok": False, "error": "lineup not found"}), 404
        return jsonify({"ok": True, **result})

    return service
