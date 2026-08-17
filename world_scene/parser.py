import ast
import json
import re
from dataclasses import replace
from typing import Any, Iterable, Tuple

from .models import (
    ObservedArea,
    WorldArmy,
    WorldMapUser,
    WorldRealMarch,
    WorldSceneEntity,
    WorldSceneApplyResult,
    WorldScenePacket,
    WorldTile,
)


SPECIAL_ORDER_ID = -999999999


def _load_payload(decoded_text: str) -> list:
    text = decoded_text.strip().rstrip("\x00").strip()
    text = re.sub(r"(?<=[{,])\s*(\d+)\s*(?=:)", r'"\1"', text)
    try:
        value = json.loads(text)
    except json.JSONDecodeError:
        value = ast.literal_eval(text)
    if not isinstance(value, list) or len(value) != 31:
        raise ValueError("world scene payload must be a 31-slot array")
    return value


def _as_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except Exception:
        return default


def _entries(value: Any) -> Iterable[Tuple[int, Any]]:
    if not isinstance(value, dict):
        return
    for key, item in value.items():
        ident = _as_int(key)
        if ident > 0:
            yield ident, item


def _string(value: Any) -> str:
    return "" if value is None else str(value)


def _visual_field(value: Any) -> Any:
    if isinstance(value, (dict, list)):
        return value
    return {} if value in (None, "") else value


def _generic_entities(category: str, value: Any) -> dict[int, WorldSceneEntity]:
    entities = {}
    if isinstance(value, dict):
        for entity_id, raw in _entries(value):
            deleted = isinstance(raw, list) and bool(raw) and _as_int(raw[0], -1) == 0
            entities[entity_id] = WorldSceneEntity(category, entity_id, raw, deleted)
    elif isinstance(value, list):
        for idx, raw in enumerate(value):
            if raw in ({}, [], None, ""):
                continue
            # list 型槽位没有稳定实体 id 时，用 1-based index 保留顺序。
            entity_id = idx + 1
            entities[entity_id] = WorldSceneEntity(category, entity_id, raw, False)
    return entities


def parse_world_scene_packet(
    cmd_id: int,
    decoded_text: str,
    source: str,
    observed_at_ms: int,
) -> WorldScenePacket:
    payload = _load_payload(decoded_text)
    users = {}
    for user_id, raw in _entries(payload[1]):
        if isinstance(raw, list) and len(raw) >= 25:
            extra = raw[12] if len(raw) > 12 and isinstance(raw[12], list) else []
            users[user_id] = WorldMapUser(
                user_id=user_id,
                name=_string(raw[0]),
                role_id=_as_int(raw[1]),
                union_id=_as_int(raw[2]),
                union_name=_string(extra[2] if len(extra) > 2 else ""),
                raw=raw,
            )

    unions = {
        union_id: (_as_int(raw[0]), _as_int(raw[1]), _string(raw[2] if len(raw) > 2 else ""))
        for union_id, raw in _entries(payload[3])
        if isinstance(raw, list) and len(raw) >= 3
    }

    armies = {}
    direct_deleted = []
    for army_id, raw in _entries(payload[6]):
        if not isinstance(raw, list) or not raw:
            continue
        if _as_int(raw[0], -1) == 0:
            direct_deleted.append(army_id)
            continue
        if len(raw) < 32:
            continue
        armies[army_id] = WorldArmy(
            army_id=army_id,
            state=_as_int(raw[0]),
            user_id=_as_int(raw[1]),
            wid_from=_as_int(raw[2]),
            wid_to=_as_int(raw[3]),
            begin_time=_as_int(raw[4]),
            end_time=_as_int(raw[5]),
            target_type=_as_int(raw[9]),
            reside_wid=_as_int(raw[10]),
            stay_wid=_as_int(raw[11]),
            army_hero_type=_string(raw[16]),
            morale=_as_int(raw[27]),
            real_march_id=_as_int(raw[28]),
            buff_ids=_string(raw[29]),
            obstacle_wid=_as_int(raw[30]),
            battle_show=_string(raw[31]),
            state_id=None if len(raw) <= 32 or raw[32] is None else _as_int(raw[32]),
            raw=raw,
        )

    tile_chunks = {}
    tiles = {}
    for wid, chunk_map in _entries(payload[14]):
        if not isinstance(chunk_map, dict):
            continue
        tile_chunks[wid] = {str(key): value for key, value in chunk_map.items()}
        raw = chunk_map.get("0") or chunk_map.get(0)
        if isinstance(raw, list) and len(raw) >= 21:
            tiles[wid] = WorldTile(
                wid=wid,
                row=wid // 10000,
                col=wid % 10000,
                city_type=_as_int(raw[0]),
                city_param=_as_int(raw[1]),
                user_id=_as_int(raw[2]),
                union_id=_as_int(raw[3]),
                protect_end_time=_as_int(raw[4]),
                name=_string(raw[6]),
                belong_city=_as_int(raw[7]),
                world_city_state=_as_int(raw[8]),
                guard_end_time=_as_int(raw[9]),
                force=_as_int(raw[12]),
                state_id=None if raw[19] is None else _as_int(raw[19]),
                view_range_add=_as_int(raw[20]),
                raw_world_city=raw,
            )

    real_marches = {}
    for real_id, raw in _entries(payload[29]):
        if isinstance(raw, list) and len(raw) >= 14:
            real_marches[real_id] = WorldRealMarch(
                real_march_id=real_id,
                last_wid=_as_int(raw[0]),
                current_wid=_as_int(raw[1]),
                current_arrive_time=_as_int(raw[2]),
                next_wid=_as_int(raw[3]),
                next_begin_time=_as_int(raw[4]),
                next_need_time=_as_int(raw[5]),
                next_spend_time=_as_int(raw[6]),
                path_id=_as_int(raw[7]),
                unit_time_cost=_as_int(raw[8]),
                march_type=_as_int(raw[9]),
                belong_id=_as_int(raw[10]),
                morale=_as_int(raw[11]),
                morale_stay_last_calc_time=_as_int(raw[12]),
                morale_hungry_last_calc_time=_as_int(raw[13]),
                raw=raw,
            )

    observed_area = None
    if isinstance(payload[17], list) and len(payload[17]) == 4:
        observed_area = ObservedArea(
            row_up=_as_int(payload[17][0]),
            row_down=_as_int(payload[17][1]),
            col_left=_as_int(payload[17][2]),
            col_right=_as_int(payload[17][3]),
        )

    block_info = None
    if isinstance(payload[20], list) and len(payload[20]) >= 2:
        block_info = (_as_int(payload[20][0]), _as_int(payload[20][1]))

    entities = {
        "war_ship": _generic_entities("war_ship", payload[8]),
        "assist_army": _generic_entities("assist_army", payload[10]),
        "army_group": _generic_entities("army_group", payload[12]),
        "short_message": _generic_entities("short_message", payload[13]),
        "block_ship": _generic_entities("block_ship", payload[22]),
        "block_assist_army": _generic_entities("block_assist_army", payload[23]),
    }

    return WorldScenePacket(
        cmd_id=cmd_id,
        source=source,
        observed_at_ms=observed_at_ms,
        server_order_id=_as_int(payload[18]),
        payload_len=len(payload),
        visual_field_raw=_visual_field(payload[0]),
        users=users,
        unions=unions,
        armies=armies,
        direct_deleted_army_ids=tuple(direct_deleted),
        block_deleted_army_ids=tuple(
            _as_int(value)
            for value in payload[7]
            if isinstance(payload[7], list) and _as_int(value) > 0
        ),
        deleted_ship_ids=tuple(
            _as_int(value)
            for value in payload[9]
            if isinstance(payload[9], list) and _as_int(value) > 0
        ),
        deleted_assist_army_ids=tuple(
            _as_int(value)
            for value in payload[11]
            if isinstance(payload[11], list) and _as_int(value) > 0
        ),
        block_info=block_info,
        block_armies={
            block: tuple(_as_int(value) for value in ids if _as_int(value) > 0)
            for block, ids in _entries(payload[21])
            if isinstance(ids, list)
        },
        block_ships={
            block: tuple(_as_int(value) for value in ids if _as_int(value) > 0)
            for block, ids in _entries(payload[22])
            if isinstance(ids, list)
        },
        block_assist_armies={
            block: tuple(_as_int(value) for value in ids if _as_int(value) > 0)
            for block, ids in _entries(payload[23])
            if isinstance(ids, list)
        },
        tile_chunks=tile_chunks,
        tiles=tiles,
        clear_chunks={
            wid: tuple(str(value) for value in values)
            for wid, values in _entries(payload[15])
            if isinstance(values, list)
        },
        real_marches=real_marches,
        entities=entities,
        observed_area=observed_area,
        raw_payload=decoded_text,
    )


class WorldSceneAssembler:
    def __init__(self) -> None:
        self.last_completed_server_order_id = -1
        self._pending_5026: list[WorldScenePacket] = []

    def apply(self, packet: WorldScenePacket) -> WorldSceneApplyResult:
        if packet.cmd_id == 5026:
            self._pending_5026.append(packet)
            if packet.server_order_id > 0:
                merged = _merge_5026_packets(self._pending_5026)
                self._pending_5026 = []
                self.last_completed_server_order_id = merged.server_order_id
                return WorldSceneApplyResult(True, True, packet=merged)
            return WorldSceneApplyResult(True, False, packet=packet)
        if packet.cmd_id == 5028 and packet.server_order_id != SPECIAL_ORDER_ID:
            if packet.server_order_id <= self.last_completed_server_order_id:
                return WorldSceneApplyResult(False, False, "STALE_5028", packet)
        return WorldSceneApplyResult(True, False, packet=packet)


def _merge_dicts(packets, field_name):
    merged = {}
    for packet in packets:
        merged.update(getattr(packet, field_name))
    return merged


def _merge_5026_packets(packets: list[WorldScenePacket]) -> WorldScenePacket:
    final = packets[-1]
    visual = {}
    for packet in packets:
        if isinstance(packet.visual_field_raw, dict):
            visual.update(packet.visual_field_raw)
    return replace(
        final,
        source="|".join(packet.source for packet in packets),
        observed_at_ms=max(packet.observed_at_ms for packet in packets),
        visual_field_raw=visual or final.visual_field_raw,
        users=_merge_dicts(packets, "users"),
        unions=_merge_dicts(packets, "unions"),
        armies=_merge_dicts(packets, "armies"),
        direct_deleted_army_ids=tuple(
            value for packet in packets for value in packet.direct_deleted_army_ids
        ),
        block_deleted_army_ids=tuple(
            value for packet in packets for value in packet.block_deleted_army_ids
        ),
        deleted_ship_ids=tuple(
            value for packet in packets for value in packet.deleted_ship_ids
        ),
        deleted_assist_army_ids=tuple(
            value for packet in packets for value in packet.deleted_assist_army_ids
        ),
        block_armies=_merge_dicts(packets, "block_armies"),
        block_ships=_merge_dicts(packets, "block_ships"),
        block_assist_armies=_merge_dicts(packets, "block_assist_armies"),
        tile_chunks=_merge_nested_dicts(packets, "tile_chunks"),
        tiles=_merge_dicts(packets, "tiles"),
        clear_chunks=_merge_dicts(packets, "clear_chunks"),
        real_marches=_merge_dicts(packets, "real_marches"),
        entities={
            category: {
                key: value
                for packet in packets
                for key, value in packet.entities.get(category, {}).items()
            }
            for category in {
                category
                for packet in packets
                for category in packet.entities
            }
        },
        observed_area=next(
            (
                packet.observed_area
                for packet in reversed(packets)
                if packet.observed_area is not None
            ),
            None,
        ),
        raw_payload="\n".join(packet.raw_payload for packet in packets),
    )


def _merge_nested_dicts(packets, field_name):
    merged = {}
    for packet in packets:
        for key, values in getattr(packet, field_name).items():
            merged.setdefault(key, {}).update(values)
    return merged
