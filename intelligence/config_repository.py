import csv
import json
from pathlib import Path

from .hero_ids import normalize_hero_id


class IntelligenceConfigRepository:
    def __init__(self, root: Path) -> None:
        self.root = Path(root)
        self.load_count = 0
        self._load()

    def _load(self):
        self.load_count += 1
        self.manifest = json.loads(
            (self.root / "manifest.json").read_text(encoding="utf-8")
        )
        self.heroes = self._rows("hero_table.csv", "heroid")
        self.skills = self._rows("skill_table.csv", "skill_id")
        self.details = self._rows("skill_detail_table.csv", "detail_id")
        self.effects = self._rows("skill_effect_table.csv", "effect_id")
        self.hero_by_id = {
            _int(row.get("heroid")): row
            for row in self.heroes
            if _is_real_hero(row)
        }
        self.skill_by_id = {
            _int(row.get("skill_id")): row for row in self.skills
        }
        self.detail_by_id = {
            _int(row.get("detail_id")): row for row in self.details
        }
        self.effect_by_id = {
            _int(row.get("effect_id")): row for row in self.effects
        }

    def _rows(self, name, key):
        with (self.root / name).open(
            "r", encoding="utf-8-sig", newline=""
        ) as file:
            rows = list(csv.DictReader(file))
        rows.sort(key=lambda row: _int(row.get(key)))
        return rows

    @property
    def dataset_version(self):
        return self.manifest["datasetVersion"]

    def search_heroes(self, query="", filters=None, page=1, size=50):
        text = str(query or "").strip().lower()
        filters = filters or {}
        rows = []
        for hero in self.hero_by_id.values():
            if text and text not in (
                f"{hero.get('name','')} {hero.get('heroid','')} "
                f"{hero.get('country_name','')} {hero.get('hero_type','')}"
            ).lower():
                continue
            if filters.get("country") and hero.get("country_name") != filters["country"]:
                continue
            if filters.get("quality") and hero.get("quality_name") != filters["quality"]:
                continue
            rows.append(_hero_projection(hero))
        return _page(rows, page, size, self.dataset_version)

    def hero_detail(self, hero_id):
        hero = self.hero_by_id.get(normalize_hero_id(hero_id))
        if hero is None:
            return None
        skill_id = _int(hero.get("skill_init"))
        skill = self.skill_by_id.get(skill_id)
        return {
            "datasetVersion": self.dataset_version,
            "evidenceClass": "CONFIG_FACT",
            "hero": _hero_projection(hero, detailed=True),
            "initialSkill": _skill_projection(skill) if skill else None,
        }

    def search_skills(self, query="", filters=None, page=1, size=50):
        text = str(query or "").strip().lower()
        filters = filters or {}
        rows = []
        for skill in self.skill_by_id.values():
            if text and text not in (
                f"{skill.get('name','')} {skill.get('skill_id','')} "
                f"{skill.get('description','')} {skill.get('brief_description','')}"
            ).lower():
                continue
            if filters.get("type") and str(skill.get("skill_type")) != str(filters["type"]):
                continue
            rows.append(_skill_projection(skill))
        return _page(rows, page, size, self.dataset_version)

    def skill_detail(self, skill_id):
        skill_id = int(skill_id)
        skill = self.skill_by_id.get(skill_id)
        if skill is None:
            return None
        details = []
        lower = skill_id * 100
        upper = lower + 99
        for detail_id, detail in self.detail_by_id.items():
            if not lower <= detail_id <= upper:
                continue
            effect_id = _int(detail.get("effect_id"))
            effect = self.effect_by_id.get(effect_id)
            details.append(
                {
                    **_numeric_projection(detail),
                    "effect": (
                        _numeric_projection(effect)
                        if effect is not None
                        else {"effect_id": effect_id, "name": "", "missing": True}
                    ),
                }
            )
        return {
            "datasetVersion": self.dataset_version,
            "evidenceClass": "CONFIG_FACT",
            "skill": _skill_projection(skill, detailed=True),
            "details": details,
            "unresolvedDescription": "#" in str(skill.get("description") or ""),
        }


def _is_real_hero(row):
    return (
        str(row.get("name") or "").strip() not in {"", "默认画像"}
        and _int(row.get("attack_base")) > 0
        and _int(row.get("attack_base")) != 900
    )


def _hero_projection(row, detailed=False):
    keys = [
        "heroid", "name", "country", "country_name", "quality", "quality_name",
        "hero_type", "hit_range", "cost", "skill_init", "icon_hero_id",
        "attack_base", "defence_base", "intel_base", "speed_base", "destroy_base",
        "attack_grow", "defence_grow", "intel_grow", "speed_grow", "destroy_grow",
        "season", "book_type", "is_sp_card", "is_memorize_card",
    ]
    result = {key: row.get(key, "") for key in keys}
    return _numeric_projection(result)


def _skill_projection(row, detailed=False):
    if row is None:
        return None
    keys = [
        "skill_id", "name", "skill_quality", "hit_range", "prepare",
        "probability_init", "probability_max", "main_detail", "skill_type",
        "show_skill_type", "description", "target_description",
        "brief_description", "main_effect_id", "main_effect_name",
    ]
    return _numeric_projection({key: row.get(key, "") for key in keys})


def _numeric_projection(row):
    result = {}
    for key, value in row.items():
        if key in {
            "heroid", "quality", "hero_type", "hit_range", "cost", "skill_init",
            "icon_hero_id", "attack_base", "defence_base", "intel_base",
            "speed_base", "destroy_base", "attack_grow", "defence_grow",
            "intel_grow", "speed_grow", "destroy_grow", "season", "book_type",
            "skill_id", "skill_quality", "prepare", "probability_init",
            "probability_max", "main_detail", "skill_type", "show_skill_type",
            "main_effect_id", "detail_id", "effect_id", "constant_param",
            "intel_param", "available_round", "available_hit", "target_type",
            "select_type", "attri_type", "calc_pos", "calc_param",
        }:
            result[key] = _int(value)
        else:
            result[key] = value
    return result


def _page(rows, page, size, dataset_version):
    page = max(1, int(page))
    size = min(100, max(1, int(size)))
    start = (page - 1) * size
    return {
        "datasetVersion": dataset_version,
        "evidenceClass": "CONFIG_FACT",
        "total": len(rows),
        "page": page,
        "size": size,
        "rows": rows[start : start + size],
    }


def _int(value):
    try:
        return int(float(value or 0))
    except (TypeError, ValueError):
        return 0
