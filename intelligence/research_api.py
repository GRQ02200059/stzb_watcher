from flask import jsonify, request

from .research_repository import ResearchCatalogRepository


def register_intelligence_research_api(
    app,
    snapshot_root,
    repository=None,
    config_repository=None,
):
    repository = repository or ResearchCatalogRepository(
        snapshot_root,
        config_repository=config_repository,
    )

    @app.route("/api/intelligence/research/summary")
    def intelligence_research_summary():
        return jsonify({"ok": True, **repository.summary()})

    @app.route("/api/intelligence/card-packs")
    def intelligence_card_packs():
        try:
            result = repository.search_card_packs(
                query=request.args.get("q", ""),
                hero_id=request.args.get("heroId"),
                page=request.args.get("page", 1),
                size=request.args.get("size", 50),
            )
        except (TypeError, ValueError):
            return jsonify({"ok": False, "error": "invalid query"}), 400
        return jsonify({"ok": True, **result})

    @app.route("/api/intelligence/card-packs/<int:pack_id>")
    def intelligence_card_pack_detail(pack_id):
        result = repository.card_pack_detail(pack_id)
        if result is None:
            return jsonify({"ok": False, "error": "card pack not found"}), 404
        return jsonify({"ok": True, **result})

    @app.route("/api/intelligence/protocol/commands")
    def intelligence_protocol_commands():
        try:
            result = repository.search_commands(
                query=request.args.get("q", ""),
                version=request.args.get("version", "all"),
                change=request.args.get("change", "all"),
                page=request.args.get("page", 1),
                size=request.args.get("size", 50),
            )
        except (TypeError, ValueError) as error:
            return jsonify({"ok": False, "error": str(error)}), 400
        return jsonify({"ok": True, **result})

    @app.route("/api/intelligence/protocol/commands/<int:command_id>")
    def intelligence_protocol_command_detail(command_id):
        result = repository.command_detail(command_id)
        if result is None:
            return jsonify({"ok": False, "error": "command not found"}), 404
        return jsonify({"ok": True, **result})

    @app.route("/api/intelligence/protocol/schema")
    def intelligence_protocol_schema():
        try:
            result = repository.search_schema(
                query=request.args.get("q", ""),
                page=request.args.get("page", 1),
                size=request.args.get("size", 50),
            )
        except (TypeError, ValueError):
            return jsonify({"ok": False, "error": "invalid query"}), 400
        return jsonify({"ok": True, **result})

    @app.route("/api/intelligence/protocol/schema/<table_name>")
    def intelligence_protocol_schema_detail(table_name):
        result = repository.schema_detail(table_name)
        if result is None:
            return jsonify({"ok": False, "error": "schema table not found"}), 404
        return jsonify({"ok": True, **result})

    return repository
