from flask import jsonify, request

from .store import WorldSceneStore


def _int_arg(name, default=None):
    raw = request.args.get(name)
    if raw is None:
        if default is None:
            raise ValueError(f"{name} is required")
        return default
    value = int(raw)
    if value < 0:
        raise ValueError(f"{name} must be non-negative")
    return value


def register_world_scene_api(app, get_connection):
    @app.route("/api/world/viewport")
    def api_world_viewport():
        try:
            row_up = _int_arg("rowUp")
            row_down = _int_arg("rowDown")
            col_left = _int_arg("colLeft")
            col_right = _int_arg("colRight")
        except (TypeError, ValueError) as error:
            return jsonify({"ok": False, "error": str(error)}), 400
        store = WorldSceneStore(get_connection())
        return jsonify(
            {
                "ok": True,
                **store.viewport(row_up, row_down, col_left, col_right),
            }
        )

    @app.route("/api/world/armies")
    def api_world_armies():
        store = WorldSceneStore(get_connection())
        return jsonify({"ok": True, "armies": store.active_armies()})

    @app.route("/api/world/marches")
    def api_world_marches():
        store = WorldSceneStore(get_connection())
        return jsonify({"ok": True, "marches": store.active_marches()})

    @app.route("/api/world/entities")
    def api_world_entities():
        category = request.args.get("category") or None
        store = WorldSceneStore(get_connection())
        return jsonify({"ok": True, "entities": store.active_entities(category)})
