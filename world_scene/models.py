from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple


@dataclass(frozen=True)
class WorldMapUser:
    user_id: int
    name: str
    role_id: int
    union_id: int
    union_name: str
    raw: List[Any]


@dataclass(frozen=True)
class WorldTile:
    wid: int
    row: int
    col: int
    city_type: int
    city_param: int
    user_id: int
    union_id: int
    protect_end_time: int
    name: str
    belong_city: int
    world_city_state: int
    guard_end_time: int
    force: int
    state_id: Optional[int]
    view_range_add: int
    raw_world_city: List[Any]


@dataclass(frozen=True)
class WorldArmy:
    army_id: int
    state: int
    user_id: int
    wid_from: int
    wid_to: int
    begin_time: int
    end_time: int
    target_type: int
    reside_wid: int
    stay_wid: int
    army_hero_type: str
    morale: int
    real_march_id: int
    buff_ids: str
    obstacle_wid: int
    battle_show: str
    state_id: Optional[int]
    raw: List[Any]


@dataclass(frozen=True)
class WorldRealMarch:
    real_march_id: int
    last_wid: int
    current_wid: int
    current_arrive_time: int
    next_wid: int
    next_begin_time: int
    next_need_time: int
    next_spend_time: int
    path_id: int
    unit_time_cost: int
    march_type: int
    belong_id: int
    morale: int
    morale_stay_last_calc_time: int
    morale_hungry_last_calc_time: int
    raw: List[Any]

    @property
    def start_time(self) -> int:
        return self.next_begin_time

    @property
    def next_time(self) -> int:
        return self.next_begin_time + self.next_need_time

    @property
    def end_time(self) -> int:
        return self.next_begin_time + self.next_spend_time


@dataclass(frozen=True)
class ObservedArea:
    row_up: int
    row_down: int
    col_left: int
    col_right: int


@dataclass(frozen=True)
class WorldSceneEntity:
    category: str
    entity_id: int
    raw: Any
    deleted: bool = False


@dataclass(frozen=True)
class WorldScenePacket:
    cmd_id: int
    source: str
    observed_at_ms: int
    server_order_id: int
    payload_len: int
    visual_field_raw: Any
    users: Dict[int, WorldMapUser] = field(default_factory=dict)
    unions: Dict[int, Tuple[int, int, str]] = field(default_factory=dict)
    armies: Dict[int, WorldArmy] = field(default_factory=dict)
    direct_deleted_army_ids: Tuple[int, ...] = ()
    block_deleted_army_ids: Tuple[int, ...] = ()
    deleted_ship_ids: Tuple[int, ...] = ()
    deleted_assist_army_ids: Tuple[int, ...] = ()
    block_info: Optional[Tuple[int, int]] = None
    block_armies: Dict[int, Tuple[int, ...]] = field(default_factory=dict)
    block_ships: Dict[int, Tuple[int, ...]] = field(default_factory=dict)
    block_assist_armies: Dict[int, Tuple[int, ...]] = field(default_factory=dict)
    tile_chunks: Dict[int, Dict[str, Any]] = field(default_factory=dict)
    tiles: Dict[int, WorldTile] = field(default_factory=dict)
    clear_chunks: Dict[int, Tuple[str, ...]] = field(default_factory=dict)
    real_marches: Dict[int, WorldRealMarch] = field(default_factory=dict)
    entities: Dict[str, Dict[int, WorldSceneEntity]] = field(default_factory=dict)
    observed_area: Optional[ObservedArea] = None
    raw_payload: str = ""


@dataclass(frozen=True)
class WorldSceneApplyResult:
    accepted: bool
    snapshot_complete: bool
    reason: str = ""
    packet: Optional[WorldScenePacket] = None
