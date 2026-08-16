from flask import jsonify, request

from .world_service import WorldIntelligenceService


def _bounds():
    values = {}
    for key in ("rowUp", "rowDown", "colLeft", "colRight"):
        raw = request.args.get(key)
        if raw is None:
            raise ValueError(f"{key} is required")
        values[key] = int(raw)
        if values[key] < 0:
            raise ValueError(f"{key} must be non-negative")
    if values["rowUp"] > values["rowDown"]:
        raise ValueError("rowUp must not exceed rowDown")
    if values["colLeft"] > values["colRight"]:
        raise ValueError("colLeft must not exceed colRight")
    return values


def register_world_intelligence_api(app, get_connection, now_ms=None):
    def service():
        return WorldIntelligenceService(get_connection(), now_ms=now_ms)

    @app.route("/api/intelligence/world/summary")
    def intelligence_world_summary():
        return jsonify({"ok": True, **service().summary()})

    @app.route("/api/intelligence/world/viewport")
    def intelligence_world_viewport():
        try:
            bounds = _bounds()
        except (TypeError, ValueError) as error:
            return jsonify({"ok": False, "error": str(error)}), 400
        result = service().viewport(
            bounds["rowUp"],
            bounds["rowDown"],
            bounds["colLeft"],
            bounds["colRight"],
        )
        return jsonify({"ok": True, **result})

    @app.route("/api/intelligence/world/overview")
    def intelligence_world_overview():
        try:
            bounds = _bounds()
            result = service().overview(
                bounds["rowUp"],
                bounds["rowDown"],
                bounds["colLeft"],
                bounds["colRight"],
                int(request.args.get("bucketRows", 20)),
                int(request.args.get("bucketCols", 20)),
            )
        except (TypeError, ValueError) as error:
            return jsonify({"ok": False, "error": str(error)}), 400
        return jsonify({"ok": True, **result})

    @app.route("/api/intelligence/world/tile/<int:wid>")
    def intelligence_world_tile(wid):
        result = service().tile_detail(wid)
        status = 200 if result["tile"] is not None else 404
        return jsonify({"ok": result["tile"] is not None, **result}), status

    @app.route("/api/intelligence/world/events")
    def intelligence_world_events():
        result = service()
        events = result.events(
            since_version=request.args.get("sinceVersion", 0),
            event_type=request.args.get("type") or None,
            entity_id=request.args.get("entityId") or None,
            limit=request.args.get("limit", 100),
        )
        return jsonify({"ok": True, **result.envelope(), "events": events})

    @app.route("/api/intelligence/world/risks")
    def intelligence_world_risks():
        try:
            bounds = _bounds()
        except (TypeError, ValueError) as error:
            return jsonify({"ok": False, "error": str(error)}), 400
        result = service()
        risks = result.risks(
            bounds["rowUp"],
            bounds["rowDown"],
            bounds["colLeft"],
            bounds["colRight"],
        )
        return jsonify({"ok": True, **result.envelope(), "risks": risks})
