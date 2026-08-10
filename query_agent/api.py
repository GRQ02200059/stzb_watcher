from flask import jsonify, request

from .service import QueryAgentService
from .tools import QueryTools


def register_query_agent_api(app, get_connection):
    @app.route("/api/query-agent/messages", methods=["POST"])
    def api_query_agent_messages():
        body = request.get_json(silent=True) or {}
        message = body.get("message", "")
        page_context = body.get("pageContext") or {}
        if not isinstance(message, str) or not message.strip():
            return jsonify({"ok": False, "error": "message is required"}), 400
        try:
            service = QueryAgentService(QueryTools(get_connection))
            return jsonify(service.answer(message, page_context).to_json())
        except ValueError as error:
            return jsonify({"ok": False, "error": str(error)}), 400
