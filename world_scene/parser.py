import ast
import json
import re
from typing import Any, Iterable, Tuple

from .models import (
    WorldArmy,
    WorldMapUser,
    WorldRealMarch,
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

    tiles = {}
    for wid, chunk_map in _entries(payload[14]):
        if not isinstance(chunk_map, dict):
            continue
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
                next_wid=_as_int(raw[2]),
                start_time=_as_int(raw[3]),
                next_time=_as_int(raw[4]),
                end_time=_as_int(raw[5]),
                path_id=_as_int(raw[6]),
                unit_time_cost=_as_int(raw[7]),
                march_type=_as_int(raw[8]),
                belong_id=_as_int(raw[9]),
                raw=raw,
            )

    block_info = None
    if isinstance(payload[20], list) and len(payload[20]) >= 2:
        block_info = (_as_int(payload[20][0]), _as_int(payload[20][1]))

    return WorldScenePacket(
        cmd_id=cmd_id,
        source=source,
        observed_at_ms=observed_at_ms,
        server_order_id=_as_int(payload[18]),
        payload_len=len(payload),
        visual_field_raw=payload[0] if isinstance(payload[0], dict) else {},
        users=users,
        unions=unions,
        armies=armies,
        direct_deleted_army_ids=tuple(direct_deleted),
        block_deleted_army_ids=tuple(
            _as_int(value)
            for value in payload[7]
            if isinstance(payload[7], list) and _as_int(value) > 0
        ),
        block_info=block_info,
        block_armies={
            block: tuple(_as_int(value) for value in ids if _as_int(value) > 0)
            for block, ids in _entries(payload[21])
            if isinstance(ids, list)
        },
        tiles=tiles,
        clear_chunks={
            wid: tuple(str(value) for value in values)
            for wid, values in _entries(payload[15])
            if isinstance(values, list)
        },
        real_marches=real_marches,
        raw_payload=decoded_text,
    )


class WorldSceneAssembler:
    def __init__(self) -> None:
        self.last_completed_server_order_id = -1
        self._assembling_5026 = False

    def apply(self, packet: WorldScenePacket) -> WorldSceneApplyResult:
        if packet.cmd_id == 5026:
            self._assembling_5026 = packet.server_order_id <= 0
            if packet.server_order_id > 0:
                self.last_completed_server_order_id = packet.server_order_id
                return WorldSceneApplyResult(True, True, packet=packet)
            return WorldSceneApplyResult(True, False, packet=packet)
        if packet.cmd_id == 5028 and packet.server_order_id != SPECIAL_ORDER_ID:
            if packet.server_order_id <= self.last_completed_server_order_id:
                return WorldSceneApplyResult(False, False, "STALE_5028", packet)
        return WorldSceneApplyResult(True, False, packet=packet)
