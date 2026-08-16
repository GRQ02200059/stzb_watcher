"""武将 / 战法数据源。

旧的 ``battle_sim`` Python 包已在 v4 提交中删除，`/api/simulate/heroes`
因此崩溃。这里改为直接读取 Kotlin ``battle-engine`` 使用的权威配置表
(hero_table.csv / skill_table.csv)，保证武将 id、战法 id 与引擎口径完全一致，
并把字段映射成前端 ``sim.js`` 约定的形状。
"""

import csv
import json
import os
from functools import lru_cache

_BASE_DIR = os.path.dirname(os.path.abspath(__file__))
_CONFIG_DIR = os.path.join(
    _BASE_DIR, "battle-engine", "src", "main", "resources", "battle-config"
)
_PORTRAIT_ROOT = os.path.join(_BASE_DIR, "static", "hero-portraits")
_PORTRAIT_MANIFEST = os.path.join(_PORTRAIT_ROOT, "manifest.json")
_PORTRAIT_PLACEHOLDER = "/static/hero-portraits/placeholder.svg"
_PORTRAIT_CDN = (
    "https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/"
    "cut/card_medium_{icon_id}.jpg?gameid=g10"
)

# CSV country 编码 -> 前端 sim.js SIM_CAMP_NAME=['','蜀','魏','吴','汉','群','晋'] 的下标
# CSV: 1汉 2魏 3蜀 4吴 5群 6晋
_COUNTRY_TO_CAMP = {1: 4, 2: 2, 3: 1, 4: 3, 5: 5, 6: 6}

# 引擎原始 skill_type -> 前端 SIM_SK_TYPE=['','指挥','主动','追击','被动']
# 引擎 fromRawType: 2=指挥 3=主动 4=追击 1/12/13=被动
_SKILL_TYPE_TO_FRONTEND = {2: 1, 3: 2, 4: 3, 1: 4, 12: 4, 13: 4}


def _config_path(name):
    return os.path.join(_CONFIG_DIR, name)


def _to_int(value, default=0):
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return default


@lru_cache(maxsize=1)
def load_portrait_manifest():
    try:
        with open(_PORTRAIT_MANIFEST, encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError):
        return {"heroes": {}}


def _portrait_fields(hero_id, icon_id, manifest=None):
    manifest = (
        load_portrait_manifest()
        if manifest is None
        else manifest
    )
    row = manifest.get("heroes", {}).get(str(hero_id), {})
    resolved_icon_id = int(row.get("iconId") or icon_id or hero_id)
    local = bool(row.get("local"))
    return {
        "iconId": resolved_icon_id,
        "portraitUrl": (
            "/static/hero-portraits/cards/%s.webp"
            % resolved_icon_id
            if local
            else _PORTRAIT_PLACEHOLDER
        ),
        "portraitFallbackUrl": _PORTRAIT_CDN.format(
            icon_id=resolved_icon_id
        ),
        "portraitLocal": local,
    }


@lru_cache(maxsize=1)
def load_heroes():
    """返回可用武将列表，字段与前端 sim.js 一致：id/name/camp/army/quality。"""
    heroes = []
    with open(_config_path("hero_table.csv"), encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            if row.get("is_release") != "1":
                continue
            hero_id = _to_int(row.get("heroid"))
            if hero_id <= 0:
                continue
            country = _to_int(row.get("country"))
            hero = {
                "id": hero_id,
                "name": (row.get("name") or "").strip(),
                "camp": _COUNTRY_TO_CAMP.get(country, 0),
                "army": _to_int(row.get("hero_type")),
                "quality": _to_int(row.get("quality")),
            }
            hero.update(
                _portrait_fields(
                    hero_id,
                    _to_int(row.get("icon_hero_id")) or hero_id,
                )
            )
            heroes.append(hero)
    heroes.sort(key=lambda h: h["id"])
    return heroes


@lru_cache(maxsize=1)
def load_skills():
    """返回可学习战法列表，字段与前端 sim.js 一致：id/name/desc/skill_type/level/study。"""
    skills = []
    with open(_config_path("skill_table.csv"), encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            skill_id = _to_int(row.get("skill_id"))
            if skill_id < 200000:
                continue
            raw_type = _to_int(row.get("skill_type"))
            frontend_type = _SKILL_TYPE_TO_FRONTEND.get(raw_type)
            if frontend_type is None:
                continue
            name = (row.get("name") or "").strip()
            if not name:
                continue
            skills.append(
                {
                    "id": skill_id,
                    "name": name,
                    "desc": (
                        row.get("brief_description") or row.get("description") or ""
                    ).strip(),
                    "skill_type": frontend_type,
                    "level": (row.get("skill_quality_level") or "").strip(),
                    "study": True,
                }
            )
    skills.sort(key=lambda s: s["id"])
    return skills


def hero_index():
    """{hero_id: hero_dict}，供 adapter 把 heroId 补全成 name/camp/army。"""
    return {h["id"]: h for h in load_heroes()}


def skill_index():
    """{skill_id: skill_dict}，供 adapter 把 skillId 补全成战法名称。"""
    return {s["id"]: s for s in load_skills()}
