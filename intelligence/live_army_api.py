from flask import jsonify, request

from .live_army_service import LiveArmyService


def register_live_army_api(app, get_connection):
    @app.route("/api/intelligence/live-armies")
    def intelligence_live_armies():
        raw = request.args.get("offlineMinutes", "10")
        try:
            offline_minutes = int(raw)
        except (TypeError, ValueError):
            return jsonify(
                {
                    "ok": False,
                    "error": "offlineMinutes must be an integer",
                }
            ), 400
        if not 0 <= offline_minutes <= 60:
            return jsonify(
                {
                    "ok": False,
                    "error": "offlineMinutes must be between 0 and 60",
                }
            ), 400

        connection = get_connection()
        try:
            result = LiveArmyService(connection).snapshot(
                offline_minutes=offline_minutes
            )
            return jsonify(result)
        finally:
            if connection is not None and hasattr(connection, "close"):
                connection.close()
