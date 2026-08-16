from copy import deepcopy


_LEVEL_COLORS = {
    "0": "#18232d",
    "1": "#263746",
    "2": "#24536a",
    "3": "#167a78",
    "4": "#199f69",
    "5": "#75b83b",
    "6": "#d1b52c",
    "7": "#e87e25",
    "8": "#ed4936",
    "9": "#ff174f",
}


def world_scene_schema() -> dict:
    slots = {
        str(index): {"5026": f"reserved{index}", "5028": f"reserved{index}"}
        for index in range(31)
    }
    mappings = {
        0: ("visualField", "visualFieldChanges", "object"),
        1: ("mapUsers", "mapUserChanges", "object"),
        3: ("unions", "unionChanges", "object"),
        4: ("strategies", "strategyChanges", "object"),
        5: ("nationStrategies", "nationStrategyChanges", "object"),
        6: ("armies", "armyChanges", "object"),
        7: ("reserved7", "deletedArmies", "array"),
        8: ("warShips", "warShipChanges", "object"),
        9: ("reserved9", "deletedShips", "array"),
        10: ("assistArmies", "assistArmyChanges", "object"),
        11: ("reserved11", "deletedAssistArmies", "array"),
        12: ("armyGroups", "armyGroups", "object"),
        13: ("shortMessages", "shortMessages", "object"),
        14: ("worldChunks", "worldChunkChanges", "object"),
        15: ("reserved15", "clearChunks", "object"),
        16: ("extGarrison", "reserved16", "object"),
        17: ("observedMapArea", "reserved17", "array|null"),
        18: ("serverOrderId", "serverOrderId", "int"),
        19: ("manorFamily", "reserved19", "object"),
        21: ("blockArmies", "reserved21", "object"),
        22: ("blockShips", "reserved22", "object"),
        23: ("blockAssistArmies", "reserved23", "object"),
        24: ("careerSupportAdd", "careerSupportAdd", "object"),
        25: ("careerSupportRemove", "careerSupportRemove", "array"),
        26: ("reserved26", "clearHunter", "array"),
        27: ("reserved27", "clearStrategy", "array"),
        29: ("realMarch", "realMarch", "object"),
    }
    for index, (baseline, delta, kind) in mappings.items():
        slots[str(index)] = {"5026": baseline, "5028": delta, "type": kind}
    slots["20"] = {
        "5026": "blockInfo",
        "5028": "blockInfo",
        "5026Type": "null",
        "5028Type": "array[2]",
    }
    return {
        "schemaVersion": 1,
        "payloadLength": 31,
        "baselineCommand": 5026,
        "deltaCommand": 5028,
        "specialOrderBypass": -999999999,
        "slots": slots,
        "tupleLengths": {
            "mapUser": 25,
            "mapArmy": 33,
            "worldCity": 21,
            "realMarch": 14,
        },
        "int64Fields": [
            "visualField.*",
            "worldCity.protectEndTime",
            "worldCity.guardEndTime",
            "worldCity.worldCityBeginTime",
            "worldCity.worldCityEndTime",
        ],
        "evidence": [
            "/Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md"
        ],
    }


def land_intelligence_rules() -> dict:
    return deepcopy(
        {
            "rulesVersion": 1,
            "levelColors": _LEVEL_COLORS,
            "freshness": {"freshSeconds": 120, "staleSeconds": 600},
            "newResLv": {"levelDigit": "tens", "resourceDigit": "ones"},
            "ownershipColors": {
                "self": "#34d399",
                "ally": "#54a6ff",
                "enemy": "#f05267",
                "unknown": "#7183a7",
            },
            "riskWeights": {
                "landLevel": 25,
                "enemyOwnership": 15,
                "incomingArmyCount": 20,
                "earliestArrival": 15,
                "estimatedTroops": 10,
                "protectionGuard": 5,
                "staleIntel": 10,
            },
            "evidence": [
                "/Users/bytedance/stzb/tools/monitor-agent/web/farming/map_codec.py",
                "/Users/bytedance/stzb/tools/monitor-agent/docs/superpowers/specs/2026-08-02-farming-map-risk-heatmap-design.md",
            ],
        }
    )
