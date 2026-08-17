import json
from pathlib import Path

from flask import jsonify, request

from .config_repository import IntelligenceConfigRepository


def register_intelligence_config_api(
    app,
    snapshot_root: Path,
    repository=None,
):
    repository = repository or IntelligenceConfigRepository(snapshot_root)

    @app.route("/api/intelligence/config/manifest")
    def intelligence_config_manifest():
        manifest = json.loads(
            (Path(snapshot_root) / "manifest.json").read_text(encoding="utf-8")
        )
        return jsonify({"ok": True, **manifest})

    @app.route("/api/intelligence/heroes")
    def intelligence_heroes():
        result = repository.search_heroes(
            request.args.get("q", ""),
            {
                "country": request.args.get("country", ""),
                "quality": request.args.get("quality", ""),
            },
            request.args.get("page", 1),
            request.args.get("size", 50),
        )
        return jsonify({"ok": True, **result})

    @app.route("/api/intelligence/heroes/<int:hero_id>")
    def intelligence_hero_detail(hero_id):
        result = repository.hero_detail(hero_id)
        return (
            jsonify({"ok": True, **result})
            if result is not None
            else (jsonify({"ok": False, "error": "hero not found"}), 404)
        )

    @app.route("/api/intelligence/skills")
    def intelligence_skills():
        result = repository.search_skills(
            request.args.get("q", ""),
            {"type": request.args.get("type", "")},
            request.args.get("page", 1),
            request.args.get("size", 50),
        )
        return jsonify({"ok": True, **result})

    @app.route("/api/intelligence/skills/<int:skill_id>")
    def intelligence_skill_detail(skill_id):
        result = repository.skill_detail(skill_id)
        return (
            jsonify({"ok": True, **result})
            if result is not None
            else (jsonify({"ok": False, "error": "skill not found"}), 404)
        )

    return repository
