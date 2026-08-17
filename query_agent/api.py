from flask import jsonify, request
from pathlib import Path

from intelligence.config_repository import IntelligenceConfigRepository
from intelligence.lineup_service import LineupStatisticsService
from intelligence.research_repository import ResearchCatalogRepository
from intelligence.world_service import WorldIntelligenceService
from .llm import build_llm_client_from_env
from .service import QueryAgentService
from .tools import QueryTools


def register_query_agent_api(
    app,
    get_connection,
    llm_client_factory=None,
    intelligence_root=None,
    research_repository=None,
):
    create_llm_client = llm_client_factory or build_llm_client_from_env
    config_repository = (
        IntelligenceConfigRepository(Path(intelligence_root))
        if intelligence_root
        else None
    )
    if research_repository is None and intelligence_root:
        research_repository = ResearchCatalogRepository(
            Path(intelligence_root) / "research",
            config_repository=config_repository,
        )
    lineup_service = LineupStatisticsService(
        get_connection,
        config_repository=config_repository,
    )

    @app.route("/api/query-agent/messages", methods=["POST"])
    def api_query_agent_messages():
        body = request.get_json(silent=True) or {}
        message = body.get("message", "")
        page_context = body.get("pageContext") or {}
        if not isinstance(message, str) or not message.strip():
            return jsonify({"ok": False, "error": "message is required"}), 400
        try:
            service = QueryAgentService(
                QueryTools(
                    get_connection,
                    config_repository=config_repository,
                    lineup_service=lineup_service,
                    world_service_factory=lambda: WorldIntelligenceService(
                        get_connection()
                    ),
                    research_repository=research_repository,
                ),
                llm_client=create_llm_client(),
            )
            return jsonify(service.answer(message, page_context).to_json())
        except ValueError as error:
            return jsonify({"ok": False, "error": str(error)}), 400
