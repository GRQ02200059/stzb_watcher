# -*- coding: utf-8 -*-
"""
realtime_writer.py
实时监控 capture_new 目录，解析新 JSON 文件写入 stzb.db
同时维护内存事件队列，供 SSE 推送
"""
import sqlite3, json, os, time, glob, threading, queue, sys
from datetime import datetime
from collections import deque
from world_scene.parser import WorldSceneAssembler, parse_world_scene_packet
from world_scene.state_store import WorldStateStore
from world_scene.store import WorldSceneStore

APP_DIR      = os.path.dirname(sys.executable) if getattr(sys, 'frozen', False) else os.path.dirname(os.path.abspath(__file__))
RESOURCE_DIR = getattr(sys, '_MEIPASS', APP_DIR)
BASE_DIR     = APP_DIR
DB_PATH      = os.path.join(APP_DIR, 'stzb.db')   # 默认，会被 profile 覆盖
PROFILE_FILE = os.path.join(APP_DIR, 'current_profile.json')
CAP_DIR      = os.path.join(APP_DIR, 'capture_new')  # 默认，会被 profile 覆盖
POLL_SECS    = 1.5

_writer_db_lock = threading.Lock()
_writer_db_path = DB_PATH
_writer_cap_dir = CAP_DIR
_cap_dir_changed = False  # 切换账号时置 True，让 writer 重置 seen_files
_default_world_scene_assembler = WorldSceneAssembler()


def _get_writer_db_path():
    """读取当前应使用的 db 路径和报文目录，切换时清空事件缓存"""
    global _writer_db_path, _writer_cap_dir
    try:
        if os.path.exists(PROFILE_FILE):
            with open(PROFILE_FILE, 'r', encoding='utf-8') as f:
                p = json.load(f)
            new_path = p.get('db_path', DB_PATH)
            new_cap  = p.get('cap_dir', CAP_DIR)
            with _writer_db_lock:
                if new_path != _writer_db_path:
                    global _cap_dir_changed
                    print(f'[writer] 切换数据库: {new_path}')
                    print(f'[writer] 切换报文目录: {new_cap}')
                    _writer_db_path = new_path
                    _writer_cap_dir = new_cap
                    _cap_dir_changed = True
                    # 清空 SSE 回放缓存，避免新连接收到旧账号数据
                    with _event_lock:
                        recent_events.clear()
                    # 推送 profile_changed 事件，前端收到后自动刷新
                    push_event('profile_changed', {
                        'db_path':     new_path,
                        'cap_dir':     new_cap,
                        'role_name':   p.get('role_name', ''),
                        'server_name': p.get('server_name', ''),
                        'bound_at':    p.get('bound_at', ''),
                    })
    except:
        pass
    with _writer_db_lock:
        return _writer_db_path

# ==== 全局事件队列（SSE用）====
# 多客户端订阅者模式：每个连接有自己的队列
_subscribers  = []           # list of queue.Queue
_sub_lock     = threading.Lock()
# 最近100条事件（新建连接时回放）
recent_events = deque(maxlen=100)
_event_lock   = threading.Lock()
# 兼容旧代码
event_queue   = queue.Queue(maxsize=500)

def subscribe():
    """新建一个订阅队列，返回给 SSE 连接使用"""
    q = queue.Queue(maxsize=200)
    with _sub_lock:
        _subscribers.append(q)
    return q

def unsubscribe(q):
    """SSE 连接断开时移除订阅队列"""
    with _sub_lock:
        try:
            _subscribers.remove(q)
        except ValueError:
            pass

try:
    with open(os.path.join(RESOURCE_DIR, 'hero_scraper', 'output', 'heroes.json'), 'r', encoding='utf-8') as f:
        HERO_NAMES = {str(h['id']): h['name'] for h in json.load(f) if h.get('id')}
except:
    HERO_NAMES = {}

def normalize_hero_id(hid):
    """将赛季武将ID转换为基础ID（13xxxx -> 10xxxx, 14xxxx -> 10xxxx）"""
    if not hid:
        return hid
    hid_int = int(hid)
    # 赛季武将ID：13xxxx - 30000 = 10xxxx, 14xxxx - 40000 = 10xxxx
    if 130000 <= hid_int < 140000:
        return hid_int - 30000
    elif 140000 <= hid_int < 150000:
        return hid_int - 40000
    return hid_int

def hero_name(hid):
    if not hid:
        return ''
    # 先尝试原始ID
    hid_str = str(hid)
    if hid_str in HERO_NAMES:
        return HERO_NAMES[hid_str]
    # 尝试转换后的ID
    normalized_id = normalize_hero_id(hid)
    normalized_str = str(normalized_id)
    return HERO_NAMES.get(normalized_str, f'武将{hid}')

RESULT_MAP = {
    0:'平局', 1:'攻方胜', 2:'守方胜', 3:'攻方溃', 4:'守方溃',
    5:'双溃', 6:'守方胜(NPC)', 7:'攻方胜', 8:'攻方溃',
    9:'守方溃', 10:'平局', 11:'攻方胜', 12:'守方胜', 13:'攻方溃',
    14:'守方溃', 15:'双溃',
}
FIGHT_TYPE_MAP = {0:'野战', 33:'大城', 80:'攻城', 27:'宝物', 1:'援军', 2:'援军'}


def get_db():
    conn = sqlite3.connect(_get_writer_db_path(), timeout=10)
    conn.execute('PRAGMA journal_mode=WAL')
    conn.execute('PRAGMA synchronous=NORMAL')
    return conn


def extract_team_id(idu):
    """从 attack_idu / defend_idu 取首个 team_id"""
    raw = str(idu or '').strip()
    if not raw:
        return 0
    first = raw.split(',', 1)[0].strip()
    return int(first) if first.isdigit() else 0


def ensure_battles_v2_team_columns(conn):
    """确保 battles_v2 存在攻守双方队伍id字段"""
    cols = {r[1] for r in conn.execute('PRAGMA table_info(battles_v2)').fetchall()}
    changed = False
    if 'atk_team_id' not in cols:
        try:
            conn.execute('ALTER TABLE battles_v2 ADD COLUMN atk_team_id INTEGER DEFAULT 0')
            changed = True
        except sqlite3.OperationalError:
            pass
    if 'def_team_id' not in cols:
        try:
            conn.execute('ALTER TABLE battles_v2 ADD COLUMN def_team_id INTEGER DEFAULT 0')
            changed = True
        except sqlite3.OperationalError:
            pass
    if changed:
        conn.commit()


def backfill_team_users_team_id(conn, player_name, team_id):
    """按玩家名回填 team_users.team_id（当前主队伍）"""
    if not player_name or not team_id:
        return
    cols = {r[1] for r in conn.execute('PRAGMA table_info(team_users)').fetchall()}
    if 'team_id' not in cols:
        try:
            conn.execute('ALTER TABLE team_users ADD COLUMN team_id INTEGER DEFAULT 0')
            conn.commit()
        except sqlite3.OperationalError:
            pass
    conn.execute('UPDATE team_users SET team_id=?, updated_at=? WHERE name=?', (
        team_id,
        datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
        player_name,
    ))


def push_event(evt_type, data):
    """向所有订阅者推送事件，并记录到 recent_events"""
    now = time.time()
    evt = {
        'type': evt_type,
        'data': data,
        'timestamp': now,
        'ts': datetime.fromtimestamp(now).strftime('%H:%M:%S'),
    }
    with _event_lock:
        recent_events.append(evt)
    # 广播给所有 SSE 连接
    with _sub_lock:
        dead = []
        for q in _subscribers:
            try:
                q.put_nowait(evt)
            except queue.Full:
                dead.append(q)
        for q in dead:
            try: _subscribers.remove(q)
            except: pass
    # 兼容旧 event_queue
    try:
        event_queue.put_nowait(evt)
    except queue.Full:
        pass


def persist_world_scene_packet(conn, msg_type, data, fpath, assembler=None):
    """Persist canonical 5026/5028 world-scene packets into normalized read tables."""
    cmd_id = {
        '000013a2': 5026,
        '000013a4': 5028,
        '5026': 5026,
        '5028': 5028,
    }.get(str(msg_type))
    if cmd_id is None:
        return None
    text = data if isinstance(data, str) else json.dumps(data, ensure_ascii=False)
    packet = parse_world_scene_packet(
        cmd_id=cmd_id,
        decoded_text=text,
        source=os.path.basename(str(fpath or '')) or f'live:{msg_type}',
        observed_at_ms=int(time.time() * 1000),
    )
    gate = (assembler or _default_world_scene_assembler).apply(packet)
    if not gate.accepted:
        return None
    if packet.cmd_id == 5026 and not gate.snapshot_complete:
        return None
    applied_packet = gate.packet or packet
    store = WorldStateStore(conn)
    store.ensure_schema()
    if applied_packet.cmd_id == 5026:
        change_set = store.apply_baseline(applied_packet)
    else:
        change_set = store.apply_delta(applied_packet)
    seq = change_set.packet_seq
    entity_count = sum(len(items) for items in applied_packet.entities.values())
    return {
        'seq': seq,
        'state_version': change_set.state_version,
        'completeness': change_set.completeness,
        'cmd_id': applied_packet.cmd_id,
        'server_order_id': applied_packet.server_order_id,
        'snapshot_complete': bool(gate.snapshot_complete),
        'source': applied_packet.source,
        'counts': {
            'tiles': len(applied_packet.tiles),
            'armies': len(applied_packet.armies),
            'deleted_armies': len(applied_packet.direct_deleted_army_ids) + len(applied_packet.block_deleted_army_ids),
            'marches': len(applied_packet.real_marches),
            'visual_fields': 0 if applied_packet.visual_field_raw in ({}, [], None, "") else 1,
            'entities': entity_count,
            'clear_chunks': len(applied_packet.clear_chunks),
        },
    }


def parse_battle_monitor_13a4(data):
    """解析 000013a4 / 5028 报文：优先按 plain_str.txt 中的主体缓存 + 队伍行军数组解析"""
    import re as _re

    def _to_int(v, default=0):
        try:
            return int(v)
        except Exception:
            return default

    def _load_plain_str(raw):
        if not isinstance(raw, str):
            return raw
        txt = raw.strip().rstrip('\x00').strip()
        if not txt:
            return None
        txt = _re.sub(r'(?<=[{,])\s*(\d+)\s*(?=:)', r'"\1"', txt)
        try:
            return json.loads(txt)
        except Exception:
            return None

    data = _load_plain_str(data)
    if not isinstance(data, list):
        return None

    subject_map = {}
    map_state_map = {}
    team_moves = []
    team_ids = []

    for block_idx, block in enumerate(data):
        if not isinstance(block, dict) or not block:
            continue
        for k, v in block.items():
            obj_id = _to_int(k, 0)
            if obj_id <= 0:
                continue
            # 主体缓存：{11607:["被卡刘备",8710837,1003,...,[1003,0,"看风花雪月"],...]}
            if isinstance(v, list) and v and isinstance(v[0], str):
                extra_info = v[12] if len(v) > 12 and isinstance(v[12], list) else []
                subject_map[obj_id] = {
                    'id': obj_id,
                    'name': str(v[0] or ''),
                    'display_id': _to_int(v[1], 0),
                    'force_id': _to_int(v[2], 0),
                    'extra_force_id': _to_int(extra_info[0], 0) if len(extra_info) > 0 else 0,
                    'extra_flag': _to_int(extra_info[1], 0) if len(extra_info) > 1 else 0,
                    'group_name': str(extra_info[2] or '') if len(extra_info) > 2 else '',
                    'wid_text': str(v[21] or '') if len(v) > 21 and v[21] is not None else '',
                    'union_name': str(extra_info[2] or '') if len(extra_info) > 2 else '',
                    'raw': v,
                    'block_index': block_idx,
                }
            # 地图点状态：{10190817:{2:[1,3,0,3,0]}}
            elif isinstance(v, dict):
                state_items = {}
                for sub_key, sub_val in v.items():
                    if not isinstance(sub_val, list):
                        continue
                    state_items[str(sub_key)] = {
                        'state_type': _to_int(sub_key, 0),
                        'params': [_to_int(x, 0) for x in sub_val],
                        'raw': sub_val,
                    }
                if state_items:
                    map_state_map[obj_id] = {
                        'wid': obj_id,
                        'states': state_items,
                        'block_index': block_idx,
                    }
            # txt 中的队伍 / 行军数组：{102008461:[5,11059,9920834,9920834,1776811474,...]}
            elif isinstance(v, list) and len(v) >= 6 and isinstance(v[0], (int, float)):
                subject_id = _to_int(v[1], 0)
                subject = subject_map.get(subject_id, {})
                from_wid = _to_int(v[2], 0)
                to_wid_candidate = _to_int(v[3], 0)
                current_wid = _to_int(v[10], 0) if len(v) > 10 else 0
                to_wid = to_wid_candidate if to_wid_candidate > 10000 else (current_wid if current_wid > 10000 else from_wid)
                team_moves.append({
                    'team_id': obj_id,
                    'move_type': _to_int(v[0], 0),
                    'subject_id': subject_id,
                    'owner_uid': _to_int(subject.get('display_id', 0), 0),
                    'owner_name': str(subject.get('name', '') or ''),
                    'owner_union': str(subject.get('union_name', '') or subject.get('group_name', '') or ''),
                    'from_wid': from_wid,
                    'to_wid': to_wid,
                    'current_wid': current_wid,
                    'start_time': _to_int(v[4], 0),
                    'arrive_time': _to_int(v[5], 0),
                    'path_points': str(v[15] or '') if len(v) > 15 and v[15] is not None else '',
                    'path_dirs': str(v[16] or '') if len(v) > 16 and v[16] is not None else '',
                    'speed': _to_int(v[29], 0) if len(v) > 29 else 0,
                    'raw': v,
                    'block_index': block_idx,
                })
                team_ids.append(obj_id)

    marker = _to_int(data[18], 0) if len(data) > 18 else 0
    pair = data[20] if len(data) > 20 and isinstance(data[20], list) else []

    # 兜底：部分 13a4 只有地图状态对象，没有显式行军数组
    # 这类报文里对象 key 本身就是队伍/目标 id，也要参与队伍匹配
    if not team_ids and map_state_map:
        team_ids = [int(k) for k in map_state_map.keys() if int(k) > 0]

    return {
        'team_ids': team_ids,
        'team_moves': team_moves,
        'user_map': subject_map,
        'event_map': map_state_map,
        'subjects': list(subject_map.values()),
        'map_states': list(map_state_map.values()),
        'events': team_moves or list(map_state_map.values()),
        'marker': marker,
        'state': [],
        'context': pair,
        'raw_len': len(data),
    }


def build_battle_monitor_payload(conn, parsed, source_file='', plain_text=''):
    """把 13a4 解析结果映射到战报队伍关系，生成前端实时展示 payload"""
    parsed = parsed or {'team_ids': [], 'team_moves': [], 'user_map': {}, 'event_map': {}, 'events': [], 'subjects': [], 'map_states': [], 'marker': 0, 'state': [], 'context': [], 'raw_len': 0}
    items = []
    matched_battles = 0

    def _wid_xy(wid):
        wid = int(wid or 0)
        if wid <= 0:
            return ''
        return f'{wid // 10000},{wid % 10000}'

    def _parse_skill_info(raw):
        res = {}
        txt = str(raw or '').strip()
        if not txt:
            return res
        for part in txt.split(';'):
            part = part.strip()
            if not part:
                continue
            segs = [x.strip() for x in part.split(',')]
            if len(segs) < 2 or not segs[0].isdigit():
                continue
            pos = int(segs[0])
            skills = []
            for i in range(1, len(segs), 2):
                sid = segs[i] if i < len(segs) else ''
                lv = segs[i + 1] if i + 1 < len(segs) else '0'
                if sid.isdigit() and int(sid) > 0:
                    skills.append({'skill_id': int(sid), 'level': int(lv) if str(lv).isdigit() else 0})
            if skills:
                res[pos] = skills
        return res

    def _parse_advance_stars(raw):
        txt = str(raw or '').strip()
        if not txt:
            return [0, 0, 0]
        vals = [0, 0, 0]
        segs = [seg.strip() for seg in txt.split(';') if str(seg).strip()]
        for i, seg in enumerate(segs):
            if i == 0:
                continue
            hero_idx = i - 1
            if hero_idx > 2:
                break
            parts = [x.strip() for x in str(seg).split(',')]
            try:
                vals[hero_idx] = int(parts[0]) if parts and parts[0] else 0
            except Exception:
                vals[hero_idx] = 0
        return vals

    def _is_team_win(side, result):
        r = int(result or 0)
        return (side == 'atk' and r in (1, 7, 11)) or (side == 'def' and r in (2, 6, 12))

    def _is_team_draw(result):
        return int(result or 0) not in (1, 2, 6, 7, 11, 12)

    def _hero_ids_from_row(row_dict, prefix):
        ids = []
        for pos in (1, 2, 3):
            hid = int(row_dict.get(f'{prefix}_hero{pos}_id', 0) or 0)
            if hid > 0:
                ids.append(hid)
        return ids

    def _hero_text(ids):
        return ' / '.join(str(x) for x in ids if int(x or 0) > 0) or '-'

    ensure_battles_v2_team_columns(conn)
    move_map = {int(m['team_id']): m for m in parsed.get('team_moves', []) if m.get('team_id')}
    user_map = parsed.get('user_map', {}) or {}

    for tid in parsed.get('team_ids', []):
        tid = int(tid or 0)
        stat_row = conn.execute(
            '''SELECT
                   COUNT(*) AS battles,
                   SUM(CASE
                         WHEN atk_team_id=? AND result IN (1,7,11) THEN 1
                         WHEN def_team_id=? AND result IN (2,6,12) THEN 1
                         ELSE 0
                       END) AS wins,
                   SUM(CASE WHEN result NOT IN (1,2,6,7,11,12) THEN 1 ELSE 0 END) AS draws
               FROM battles_v2
               WHERE (atk_team_id=? OR def_team_id=?)
                 AND COALESCE(is_npc, 0)=0''',
            (tid, tid, tid, tid)
        ).fetchone()
        battles = int((stat_row['battles'] if stat_row else 0) or 0)
        wins = int((stat_row['wins'] if stat_row else 0) or 0)
        draws = int((stat_row['draws'] if stat_row else 0) or 0)
        loses = max(0, battles - wins - draws)
        team_stats = {
            'battles': battles,
            'wins': wins,
            'draws': draws,
            'loses': loses,
            'win_rate': round((wins + draws * 0.5) * 100 / battles, 1) if battles else 0,
        }
        recent_rows = conn.execute(
            '''SELECT battle_id, time, time_str, result, result_desc, fight_type, wid, wid_code,
                      atk_name, atk_uid, atk_union, atk_team_id,
                      def_name, def_union, def_team_id, def_level,
                      atk_gongxun, atk_power, all_skill_info,
                      atk_advance, def_advance,
                      atk_hero1_id, atk_hero1_level, atk_hero1_star,
                      atk_hero2_id, atk_hero2_level, atk_hero2_star,
                      atk_hero3_id, atk_hero3_level, atk_hero3_star,
                      def_hero1_id, def_hero1_level, def_hero1_star,
                      def_hero2_id, def_hero2_level, def_hero2_star,
                      def_hero3_id, def_hero3_level, def_hero3_star
               FROM battles_v2
               WHERE atk_team_id=? OR def_team_id=?
               ORDER BY time DESC, battle_id DESC
               LIMIT 8''',
            (tid, tid)
        ).fetchall()
        matchup_rows = conn.execute(
            '''SELECT battle_id, time, time_str, result,
                      atk_team_id, def_team_id,
                      atk_name, def_name,
                      atk_hero1_id, atk_hero2_id, atk_hero3_id,
                      def_hero1_id, def_hero2_id, def_hero3_id
               FROM battles_v2
               WHERE (atk_team_id=? OR def_team_id=?)
                 AND COALESCE(is_npc, 0)=0
               ORDER BY time DESC, battle_id DESC''',
            (tid, tid)
        ).fetchall()
        recent_battles = []
        matchup_stats = {}
        lineup = {'battle_id': 0, 'side': '', 'heroes': []}
        for idx, r in enumerate(recent_rows):
            row = dict(r) if isinstance(r, sqlite3.Row) else {
                'battle_id': r[0], 'time': r[1], 'time_str': r[2], 'result': r[3], 'result_desc': r[4],
                'fight_type': r[5], 'wid': r[6], 'wid_code': r[7], 'atk_name': r[8], 'atk_uid': r[9],
                'atk_union': r[10], 'atk_team_id': r[11], 'def_name': r[12], 'def_union': r[13],
                'def_team_id': r[14], 'def_level': r[15], 'atk_gongxun': r[16], 'atk_power': r[17],
                'all_skill_info': r[18], 'atk_advance': r[19], 'def_advance': r[20],
                'atk_hero1_id': r[21], 'atk_hero1_level': r[22], 'atk_hero1_star': r[23],
                'atk_hero2_id': r[24], 'atk_hero2_level': r[25], 'atk_hero2_star': r[26],
                'atk_hero3_id': r[27], 'atk_hero3_level': r[28], 'atk_hero3_star': r[29],
                'def_hero1_id': r[30], 'def_hero1_level': r[31], 'def_hero1_star': r[32],
                'def_hero2_id': r[33], 'def_hero2_level': r[34], 'def_hero2_star': r[35],
                'def_hero3_id': r[36], 'def_hero3_level': r[37], 'def_hero3_star': r[38],
            }
            side = 'atk' if int(row.get('atk_team_id', 0) or 0) == tid else 'def'
            opponent_name = row.get('def_name', '') if side == 'atk' else row.get('atk_name', '')
            opponent_union = row.get('def_union', '') if side == 'atk' else row.get('atk_union', '')
            opp_prefix = 'def' if side == 'atk' else 'atk'
            opp_ids = _hero_ids_from_row(row, opp_prefix)
            outcome = '胜' if _is_team_win(side, row.get('result', 0)) else ('平' if _is_team_draw(row.get('result', 0)) else '负')
            recent_battles.append({
                'battle_id': int(row.get('battle_id', 0) or 0),
                'time': int(row.get('time', 0) or 0),
                'time_str': str(row.get('time_str', '') or ''),
                'result': int(row.get('result', 0) or 0),
                'result_desc': str(row.get('result_desc', '') or ''),
                'result_text': outcome,
                'fight_type': int(row.get('fight_type', 0) or 0),
                'wid': int(row.get('wid', 0) or 0),
                'wid_code': str(row.get('wid_code', '') or ''),
                'side': side,
                'self_name': row.get('atk_name', '') if side == 'atk' else row.get('def_name', ''),
                'self_union': row.get('atk_union', '') if side == 'atk' else row.get('def_union', ''),
                'opponent_name': opponent_name,
                'opponent_union': opponent_union,
                'opponent_hero_ids': opp_ids,
                'opponent_heroes_text': _hero_text(opp_ids),
            })
            if idx == 0:
                skill_map = _parse_skill_info(row.get('all_skill_info', ''))
                heroes = []
                prefix = 'atk' if side == 'atk' else 'def'
                advance_stars = _parse_advance_stars(row.get('atk_advance' if side == 'atk' else 'def_advance', ''))
                for pos in (1, 2, 3):
                    hero_id = int(row.get(f'{prefix}_hero{pos}_id', 0) or 0)
                    if hero_id <= 0:
                        continue
                    fallback_star = int(row.get(f'{prefix}_hero{pos}_star', 0) or 0)
                    advance_star = advance_stars[pos - 1] if pos - 1 < len(advance_stars) else 0
                    heroes.append({
                        'pos': pos,
                        'hero_id': hero_id,
                        'level': int(row.get(f'{prefix}_hero{pos}_level', 0) or 0),
                        'star': int(advance_star or fallback_star or 0),
                        'skills': skill_map.get(pos if side == 'atk' else pos + 3, []),
                    })
                lineup = {
                    'battle_id': int(row.get('battle_id', 0) or 0),
                    'side': side,
                    'time_str': str(row.get('time_str', '') or ''),
                    'heroes': heroes,
                }
        for rr in matchup_rows:
            rr = dict(rr) if isinstance(rr, sqlite3.Row) else dict(rr)
            rr_side = 'atk' if int(rr.get('atk_team_id', 0) or 0) == tid else 'def'
            opp_prefix = 'def' if rr_side == 'atk' else 'atk'
            opp_ids = _hero_ids_from_row(rr, opp_prefix)
            outcome = '胜' if _is_team_win(rr_side, rr.get('result', 0)) else ('平' if _is_team_draw(rr.get('result', 0)) else '负')
            mk = ','.join(str(x) for x in opp_ids) or '-'
            if mk not in matchup_stats:
                matchup_stats[mk] = {
                    'opponent_hero_ids': opp_ids,
                    'opponent_heroes_text': _hero_text(opp_ids),
                    'wins': 0,
                    'draws': 0,
                    'loses': 0,
                }
            if outcome == '胜':
                matchup_stats[mk]['wins'] += 1
            elif outcome == '平':
                matchup_stats[mk]['draws'] += 1
            else:
                matchup_stats[mk]['loses'] += 1
        win_matchups = []
        lose_matchups = []
        for ms in matchup_stats.values():
            total_vs = ms['wins'] + ms['draws'] + ms['loses']
            ms['total'] = total_vs
            ms['win_rate'] = round((ms['wins'] + ms['draws'] * 0.5) * 100 / total_vs, 1) if total_vs else 0
            if ms['wins'] > 0:
                win_matchups.append(ms)
            if ms['loses'] > 0:
                lose_matchups.append(ms)
        win_matchups.sort(key=lambda x: (-x['wins'], -x['win_rate'], -x['total']))
        lose_matchups.sort(key=lambda x: (-x['loses'], x['win_rate'], -x['total']))
        team_matchups = {
            'recent_battles': recent_battles[:6],
            'favored': win_matchups[:3],
            'countered': lose_matchups[:3],
        }
        matched_battles += len(recent_battles)

        move = move_map.get(tid, {})
        owner_uid = int(move.get('owner_uid', 0) or 0)
        owner_info = user_map.get(owner_uid, {}) if owner_uid else {}
        owner_name = move.get('owner_name', '') or owner_info.get('name', '')
        owner_union = move.get('owner_union', '') or owner_info.get('union_name', '')
        if not owner_name and recent_battles:
            owner_name = recent_battles[0].get('self_name', '')
        if not owner_union and recent_battles:
            owner_union = recent_battles[0].get('self_union', '')
        items.append({
            'team_id': tid,
            'subject_id': move.get('subject_id', 0),
            'member_count': 0,
            'battle_count': len(recent_battles),
            'owner_uid': owner_uid,
            'owner_name': owner_name,
            'owner_union': owner_union,
            'move_type': move.get('move_type', 0),
            'from_wid': move.get('from_wid', 0),
            'to_wid': move.get('to_wid', 0),
            'current_wid': move.get('current_wid', 0),
            'from_xy': _wid_xy(move.get('from_wid', 0)),
            'to_xy': _wid_xy(move.get('to_wid', 0)),
            'current_xy': _wid_xy(move.get('current_wid', 0)),
            'start_time': move.get('start_time', 0),
            'arrive_time': move.get('arrive_time', 0),
            'speed': move.get('speed', 0),
            'path_points': move.get('path_points', ''),
            'path_dirs': move.get('path_dirs', ''),
            'members': [],
            'recent_battles': recent_battles,
            'team_stats': team_stats,
            'team_matchups': team_matchups,
            'lineup': lineup,
        })

    return {
        'ok': True,
        'source_file': source_file,
        'plain_text': plain_text,
        'packet': parsed,
        'subjects': parsed.get('subjects', []),
        'map_states': parsed.get('map_states', []),
        'events': parsed.get('events', []),
        'summary': {
            'teams': len(items),
            'matched_members': matched_battles,
            'matched_battles': matched_battles,
            'events': len(parsed.get('map_states', []) or []),
            'users': len(parsed.get('subjects', []) or []),
        },
        'items': items,
    }


# ============================================================
# 解析 00000834 战报
# 格式: [battle_id, ?, fight_type, wid, atk_name, atk_hero_str,
#        time, result, def_union, def_level, [[hero_data...]], ...]
# ============================================================
def parse_battle_834(data, fpath):
    """解析 00000834 消息，返回 (msg_kind, parsed)
       msg_kind: 'chat' | 'battle_notice'
    """
    if not isinstance(data, list) or len(data) < 6:
        return None, None
    msg_kind_raw = int(data[1]) if len(data) > 1 else -1
    ts = int(data[6]) if len(data) > 6 else 0
    time_str = datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:%M:%S') if ts > 0 else ''
    sender_raw = str(data[45]) if len(data) > 45 else ''
    sender = sender_raw.rsplit('#', 1)[0] if '#' in sender_raw else sender_raw
    uid = sender_raw.rsplit('#', 1)[1] if '#' in sender_raw else ''
    union_name = str(data[8]) if len(data) > 8 else ''

    # [1]==9 聊天消息
    if msg_kind_raw == 9:
        text = str(data[5]) if len(data) > 5 else ''
        return 'chat', {
            'id': int(data[0]),
            'sender': sender,
            'uid': uid,
            'union': union_name,
            'text': text,
            'time': ts,
            'time_str': time_str,
            'source_file': os.path.basename(fpath),
        }

    # [1]==0/1 战斗通知
    if msg_kind_raw not in (0, 1):
        return None, None
    try:
        battle_id  = int(data[0])
        fight_type = int(data[2]) if len(data) > 2 else 0
        wid        = int(data[3]) if len(data) > 3 else 0
        atk_name_raw = str(data[4]) if len(data) > 4 else ''
        def_sign   = str(data[5]) if len(data) > 5 else ''  # 守方个性签名（非名字）
        ts         = int(data[6]) if len(data) > 6 else 0
        result     = int(data[7]) if len(data) > 7 else 0
        def_union  = str(data[8]) if len(data) > 8 else ''
        def_level  = int(data[9]) if len(data) > 9 else 0
        heroes_raw = data[10] if len(data) > 10 else []
        atk_gongxun_17 = 0
        def_gongxun_32 = 0
        # [1]=0表示攻方视角，[1]=1表示守方视角
        # 武勋取两者最大值（有时一方为0）
        atk_gongxun = 0
        wid_code   = str(data[37]) if len(data) > 37 else ''
        def_gongxun = 0
        atk_power  = 0
        # [45] 格式: "名字#uid" 或 "名字"
        full_name  = str(data[45]) if len(data) > 45 else atk_name_raw
        if '#' in full_name:
            atk_name, atk_uid = full_name.rsplit('#', 1)
        else:
            atk_name, atk_uid = atk_name_raw, ''

        # 解析武将列表
        heroes = []
        if isinstance(heroes_raw, list):
            for h in heroes_raw:
                if isinstance(h, list) and len(h) >= 5:
                    try:
                        hid = int(h[0])
                        if hid > 0:
                            heroes.append({
                                'hero_id': hid, 'hero_name': hero_name(hid),
                                'level': int(h[1]) if len(h) > 1 else 0,
                                'max_hp': int(h[2]) if len(h) > 2 else 0,
                                'remain_hp': int(h[3]) if len(h) > 3 else 0,
                                'damage_taken': int(h[4]) if len(h) > 4 else 0,
                            })
                    except: pass

        time_str = datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:%M:%S') if ts > 0 else ''
        return 'battle_notice', {
            'battle_id': battle_id, 'time': ts, 'time_str': time_str,
            'result': result, 'result_desc': RESULT_MAP.get(result, str(result)),
            'fight_type': fight_type, 'wid': wid, 'wid_code': wid_code,
            'atk_name': atk_name, 'atk_uid': atk_uid,
            'atk_gongxun': atk_gongxun, 'atk_power': atk_power,
            'def_name': '', 'def_union': def_union, 'def_level': def_level,
            'def_gongxun': def_gongxun,
            'heroes': heroes, 'source_file': os.path.basename(fpath),
        }
    except Exception as e:
        return None, None


def upsert_battle_834(conn, b):
    """将战报写入 battles_v2 / wuxun_log / power_log"""
    exists = conn.execute('SELECT 1 FROM battles_v2 WHERE battle_id=?', (b['battle_id'],)).fetchone()
    if exists:
        return False
    # battles_v2
    conn.execute('''
        INSERT OR IGNORE INTO battles_v2
            (battle_id, time, time_str, result, result_desc, fight_type, wid, wid_code,
             atk_name, atk_uid, atk_gongxun, atk_power,
             def_name, def_union, def_level, def_gongxun, source_file, is_npc)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    ''', (b['battle_id'], b['time'], b['time_str'], b['result'], b['result_desc'],
          b['fight_type'], b['wid'], b['wid_code'],
          b['atk_name'], b['atk_uid'], b['atk_gongxun'], b['atk_power'],
          b['def_name'], b['def_union'], b['def_level'], b['def_gongxun'],
          b['source_file'], 1))
    # wuxun_log
    if b['atk_gongxun'] > 0:
        conn.execute('''
            INSERT INTO wuxun_log (battle_id, time, atk_name, atk_union, atk_level,
                                   gongxun, fight_type, result, wid)
            VALUES (?,?,?,?,?,?,?,?,?)
        ''', (b['battle_id'], b['time'], b['atk_name'], b['def_union'], b['def_level'],
              b['atk_gongxun'], b['fight_type'], b['result'], b['wid']))
    # power_log
    if b['atk_power'] > 0:
        conn.execute('''
            INSERT INTO power_log (battle_id, time, atk_name, atk_union, atk_level,
                                   power, fight_type, result, wid)
            VALUES (?,?,?,?,?,?,?,?,?)
        ''', (b['battle_id'], b['time'], b['atk_name'], b['def_union'], b['def_level'],
              b['atk_power'], b['fight_type'], b['result'], b['wid']))
    # attendance
    try:
        import profile_manager as _pm_att
        _att_pid = _pm_att.get_current_profile_id()
    except:
        _att_pid = ''
    try:
        conn.execute('''
            INSERT INTO attendance (battle_id, time, player_name, player_uid, union_name,
                                    fight_type, wid, gongxun, result, profile_id)
            VALUES (?,?,?,?,?,?,?,?,?,?)
        ''', (b['battle_id'], b['time'], b['atk_name'], b['atk_uid'], b['def_union'],
              b['fight_type'], b['wid'], b['atk_gongxun'], b['result'], _att_pid))
    except sqlite3.OperationalError as _e:
        if 'profile_id' in str(_e):
            conn.execute('''
                INSERT INTO attendance (battle_id, time, player_name, player_uid, union_name,
                                        fight_type, wid, gongxun, result)
                VALUES (?,?,?,?,?,?,?,?,?)
            ''', (b['battle_id'], b['time'], b['atk_name'], b['atk_uid'], b['def_union'],
                  b['fight_type'], b['wid'], b['atk_gongxun'], b['result']))
        else:
            raise
    # battle_heroes
    for i, h in enumerate(b['heroes']):
        conn.execute('''
            INSERT INTO battle_heroes (battle_id, side, pos, hero_id, hero_name,
                                       level, max_hp, remain_hp, damage_taken)
            VALUES (?,?,?,?,?,?,?,?,?)
        ''', (b['battle_id'], 'atk', i, h['hero_id'], h['hero_name'],
              h['level'], h['max_hp'], h['remain_hp'], h['damage_taken']))
    conn.commit()
    return True


# ============================================================
# 解析 00015f95 数据表推送 (db_sync)
# 格式: [[op, table_name, [row_id, uid, field_id, value...]], ...]
# ============================================================
def parse_db_sync(data, fpath):
    """解析 Tb_xxx 数据表推送"""
    events = []
    if not isinstance(data, list):
        return events
    for item in data:
        if not isinstance(item, list) or len(item) < 3:
            continue
        try:
            op         = int(item[0])   # 1=upsert 2=update 3=delete
            table_name = str(item[1])
            row_data   = item[2] if len(item) > 2 else []
            row_id     = int(row_data[0]) if isinstance(row_data, list) and row_data else 0
            events.append({
                'op': op,
                'table_name': table_name,
                'row_id': row_id,
                'raw_json': json.dumps(item, ensure_ascii=False),
                'source_file': os.path.basename(fpath),
            })
        except: pass
    return events


def upsert_db_sync(conn, evts, src_type):
    ts = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    for e in evts:
        conn.execute('''
            INSERT INTO db_sync (op, table_name, row_id, raw_json, captured_at, source_type)
            VALUES (?,?,?,?,?,?)
        ''', (e['op'], e['table_name'], e['row_id'], e['raw_json'], ts, src_type))
    if evts:
        conn.commit()


# ============================================================
# 解析 0000000a 完整战报（字典格式，攻守双方完整信息）
# 格式: [wid, [battle_dict, ...]]
# ============================================================
# ============================================================
# 字段映射表：(数据库列名, dict key)
# 照搬 stzbHelper Report struct 全字段，动态构建 INSERT
# ============================================================
_BATTLE_0A_FIELDS = [
    ('battle_id',                  'battle_id'),
    ('time',                       'time'),
    ('time_str',                   'time_str'),
    ('result',                     'result'),
    ('result_desc',                'result_desc'),
    ('source_file',                'source_file'),
    ('fight_type',                 'fight_type'),
    ('wid',                        'wid'),
    ('wid_code',                   'wid_code'),
    ('wid_name',                   'wid_name'),
    ('aiid',                       'aiid'),
    ('all_exp',                    'all_exp'),
    ('all_skill_info',             'all_skill_info'),
    ('ambush',                     'ambush'),
    ('atk_advance',                'atk_advance'),
    ('attack_advance',             'atk_advance'),
    ('attack_all_hero_info',       'attack_all_hero_info'),
    ('attack_all_surface',         'attack_all_surface'),
    ('attack_army_group',          'attack_army_group'),
    ('attack_base_heroid',         'attack_base_heroid'),
    ('attack_clan_name',           'attack_clan_name'),
    ('attack_clanid',              'attack_clanid'),
    ('attack_fight_event_count',   'attack_fight_event_count'),
    ('attack_fight_event_facade',  'attack_fight_event_facade'),
    ('attack_help_id',             'attack_help_id'),
    ('attack_hero_policy',         'attack_hero_policy'),
    ('atk_hero_type',              'atk_hero_type'),
    ('attack_hero_type',           'atk_hero_type'),
    ('attack_hero_type_advance',   'attack_hero_type_advance'),
    ('atk_hp',                     'atk_hp'),
    ('attack_hp',                  'atk_hp'),
    ('atk_idu',                    'atk_idu'),
    ('atk_name',                   'atk_name'),
    ('attack_name',                'atk_name'),
    ('attack_role_id',             'attack_role_id'),
    ('atk_uid',                    'atk_uid'),
    ('attack_ship_type',           'attack_ship_type'),
    ('atk_union',                  'atk_union'),
    ('attack_union_name',          'atk_union'),
    ('attack_union_official',      'attack_union_official'),
    ('atk_unionid',                'atk_unionid'),
    ('attack_unionid',             'atk_unionid'),
    ('attacker_army_effect',       'attacker_army_effect'),
    ('attacker_base_hero_detail',  'attacker_base_hero_detail'),
    ('atk_power',                  'atk_power'),
    ('attacker_force',             'atk_power'),
    ('atk_gear_info',              'atk_gear_info'),
    ('attacker_gear_info',         'atk_gear_info'),
    ('atk_gongxun',                'atk_gongxun'),
    ('attacker_gongxun',           'atk_gongxun'),
    ('attacker_life_end_time',     'attacker_life_end_time'),
    ('attacker_machine_stat_info', 'attacker_machine_stat_info'),
    ('attacker_surface',           'attacker_surface'),
    ('attacker_xwc',               'attacker_xwc'),
    ('bandit',                     'bandit'),
    ('battle_scenes',              'battle_scenes'),
    ('block_id',                   'block_id'),
    ('borrow_land',                'borrow_land'),
    ('city_type',                  'city_type'),
    ('def_advance',                'def_advance'),
    ('defend_advance',             'def_advance'),
    ('defend_all_hero_info',       'defend_all_hero_info'),
    ('defend_all_surface',         'defend_all_surface'),
    ('defend_army_group',          'defend_army_group'),
    ('defend_base_heroid',         'defend_base_heroid'),
    ('def_level',                  'def_level'),
    ('defend_base_level',          'def_level'),
    ('defend_clan_name',           'defend_clan_name'),
    ('defend_clanid',              'defend_clanid'),
    ('defend_fight_event_count',   'defend_fight_event_count'),
    ('defend_fight_event_facade',  'defend_fight_event_facade'),
    ('defend_help_id',             'defend_help_id'),
    ('defend_hero_policy',         'defend_hero_policy'),
    ('def_hero_type',              'def_hero_type'),
    ('defend_hero_type',           'def_hero_type'),
    ('defend_hero_type_advance',   'defend_hero_type_advance'),
    ('def_hp',                     'def_hp'),
    ('defend_hp',                  'def_hp'),
    ('def_idu',                    'def_idu'),
    ('def_name',                   'def_name'),
    ('defend_name',                'def_name'),
    ('defend_role_id',             'defend_role_id'),
    ('defend_ship_type',           'defend_ship_type'),
    ('def_union',                  'def_union'),
    ('defend_union_name',          'def_union'),
    ('defend_union_official',      'defend_union_official'),
    ('def_unionid',                'def_unionid'),
    ('defend_unionid',             'def_unionid'),
    ('defender_army_effect',       'defender_army_effect'),
    ('defender_base_hero_detail',  'defender_base_hero_detail'),
    ('def_gongxun',                'def_gongxun'),
    ('defender_gongxun',           'def_gongxun'),
    ('def_gear_info',              'def_gear_info'),
    ('defender_gear_info',         'def_gear_info'),
    ('defender_life_end_time',     'defender_life_end_time'),
    ('defender_machine_stat_info', 'defender_machine_stat_info'),
    ('defender_surface',           'defender_surface'),
    ('defender_xwc',               'defender_xwc'),
    ('extra_result',               'extra_result'),
    ('first_occupy_lvn_land',      'first_occupy_lvn_land'),
    ('garrison',                   'garrison'),
    ('huangjin_convert',           'huangjin_convert'),
    ('in_night',                   'in_night'),
    ('in_night_mode',              'in_night'),
    ('is_ai',                      'is_ai'),
    ('is_npc',                     'is_npc'),
    ('is_shared',                  'is_shared'),
    ('is_support',                 'is_support'),
    ('lose_tips',                  'lose_tips'),
    ('machine_effect',             'machine_effect'),
    ('military',                   'military'),
    ('military_effect',            'military_effect'),
    ('mvp_svp_pos',                'mvp_svp_pos'),
    ('nation_member_union_info',   'nation_member_union_info'),
    ('no_owner_res',               'no_owner_res'),
    ('press_attack',               'press_attack'),
    ('reference_count',            'reference_count'),
    ('res_type',                   'res_type'),
    ('rob',                        'rob'),
    ('sand_extra_info',            'sand_extra_info'),
    ('ship_effect',                'ship_effect'),
    ('tech_jian_jun_effect',       'tech_jian_jun_effect'),
    ('tech_quan_xiang_effect',     'tech_quan_xiang_effect'),
    ('weather',                    'weather'),
    ('world_npc_army',             'world_npc_army'),
    ('yi_ling_press_attack',       'yi_ling_press_attack'),
    ('attack_all_sub_hero_info',   'attack_all_sub_hero_info'),
    ('defend_all_sub_hero_info',   'defend_all_sub_hero_info'),
    ('attack_support_user_info',   'attack_support_user_info'),
    ('defend_support_user_info',   'defend_support_user_info'),
    ('atk_hero1_id',               'atk_hero1_id'),
    ('atk_hero2_id',               'atk_hero2_id'),
    ('atk_hero3_id',               'atk_hero3_id'),
    ('atk_hero1_level',            'atk_hero1_level'),
    ('atk_hero2_level',            'atk_hero2_level'),
    ('atk_hero3_level',            'atk_hero3_level'),
    ('atk_hero1_star',             'atk_hero1_star'),
    ('atk_hero2_star',             'atk_hero2_star'),
    ('atk_hero3_star',             'atk_hero3_star'),
    ('atk_total_star',             'atk_total_star'),
    ('def_hero1_id',               'def_hero1_id'),
    ('def_hero2_id',               'def_hero2_id'),
    ('def_hero3_id',               'def_hero3_id'),
    ('def_hero1_level',            'def_hero1_level'),
    ('def_hero2_level',            'def_hero2_level'),
    ('def_hero3_level',            'def_hero3_level'),
    ('def_hero1_star',             'def_hero1_star'),
    ('def_hero2_star',             'def_hero2_star'),
    ('def_hero3_star',             'def_hero3_star'),
    ('def_total_star',             'def_total_star'),
]


def _parse_hero_info_0a(all_hero_info, advance_str, hero_type_str):
    """
    照搬 stzbHelper parseHeroInfo 逻辑：
    - all_hero_info: "hero_id,level,max_hp,remain_hp,dmg;..."  (最多3段)
    - advance_str:   4段，格式 "x,...;x,...;x,...;x,...;"
                    第0段跳过，第1/2/3段对应武将1/2/3
                    每段第一个值=1 表示有红度（照搬 stzbHelper）
    返回: (heroes列表, hero1_id, hero2_id, hero3_id,
           hero1_level, hero2_level, hero3_level,
           hero1_star, hero2_star, hero3_star, total_star)
    """
    heroes = []
    ids    = [0, 0, 0]
    levels = [0, 0, 0]
    stars  = [0, 0, 0]

    # 解析武将基础信息（最多3段）
    segs = [s.strip() for s in all_hero_info.split(';') if s.strip()]
    for i, seg in enumerate(segs[:3]):
        parts = seg.split(',')
        if not parts or not parts[0]:
            continue
        try:
            hid = int(parts[0])
        except:
            continue
        if hid <= 0:
            continue
        lv  = int(parts[1]) if len(parts) > 1 and parts[1] else 0
        mhp = int(parts[2]) if len(parts) > 2 and parts[2] else 0
        rhp = int(parts[3]) if len(parts) > 3 and parts[3] else 0
        dmg = int(parts[4]) if len(parts) > 4 and parts[4] else 0
        ids[i]    = hid
        levels[i] = lv
        heroes.append({
            'hero_id': hid, 'hero_name': hero_name(hid),
            'level': lv, 'max_hp': mhp, 'remain_hp': rhp, 'damage_taken': dmg,
            'star': 0,  # 后面填充
        })

    # 解析红度 —— 照搬 stzbHelper parseHeroInfo：
    # advance_str 共4段，跳过第0段，第1/2/3段对应武将1/2/3
    # 每段第一个逗号分隔值=1 表示该武将有红度
    adv_segs = [s.strip() for s in advance_str.split(';') if s.strip()]
    for i, seg in enumerate(adv_segs):
        if i == 0:  # 照搬 stzbHelper: if i == 0 { continue }
            continue
        hero_idx = i - 1  # 第1段→武将0，第2段→武将1，第3段→武将2
        if hero_idx > 2:
            break
        parts = seg.split(',')
        if parts and parts[0] == '1':
            stars[hero_idx] = 1

    # 将红度填入 heroes
    for i, h in enumerate(heroes):
        h['star'] = stars[i]

    total_star = sum(stars)
    return heroes, ids[0], ids[1], ids[2], levels[0], levels[1], levels[2], \
           stars[0], stars[1], stars[2], total_star


def parse_battle_0a(data, fpath):
    """解析 0000000a 战报列表，返回 battle 列表（照搬 stzbHelper Report struct 全字段）"""
    results = []
    if not isinstance(data, list) or len(data) < 2:
        return results
    battles = data[1] if isinstance(data[1], list) else []
    for b in battles:
        if not isinstance(b, dict):
            continue
        try:
            battle_id = int(b.get('battle_id', 0))
            if not battle_id:
                continue

            ts       = int(b.get('time', 0))
            time_str = datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:%M:%S') if ts > 0 else ''
            result   = int(b.get('result', 0))

            # 照搬 stzbHelper parseHeroInfo：解析攻守双方武将+红度
            atk_heroes, ah1, ah2, ah3, al1, al2, al3, as1, as2, as3, atk_total_star = \
                _parse_hero_info_0a(
                    str(b.get('attack_all_hero_info', '')),
                    str(b.get('attack_advance', '')),
                    str(b.get('attack_hero_type', '')))
            def_heroes, dh1, dh2, dh3, dl1, dl2, dl3, ds1, ds2, ds3, def_total_star = \
                _parse_hero_info_0a(
                    str(b.get('defend_all_hero_info', '')),
                    str(b.get('defend_advance', '')),
                    str(b.get('defend_hero_type', '')))

            results.append({
                # 核心字段
                'battle_id':   battle_id,
                'time':        ts,
                'time_str':    time_str,
                'result':      result,
                'result_desc': RESULT_MAP.get(result, str(result)),
                'source_file': os.path.basename(fpath),

                # 照搬 stzbHelper Report struct 全字段（直接 json key 映射）
                'aiid':                       str(b.get('aiid', '')),
                'all_exp':                    str(b.get('all_exp', '')),
                'all_skill_info':             str(b.get('all_skill_info', '')),
                'ambush':                     int(b.get('ambush', 0) or 0),
                'atk_advance':                str(b.get('attack_advance', '')),
                'attack_all_hero_info':       str(b.get('attack_all_hero_info', '')),
                'attack_all_surface':         str(b.get('attack_all_surface', '')),
                'attack_army_group':          str(b.get('attack_army_group', '')),
                'attack_base_heroid':         int(b.get('attack_base_heroid', 0) or 0),
                'attack_clan_name':           str(b.get('attack_clan_name', '')),
                'attack_clanid':              int(b.get('attack_clanid', 0) or 0),
                'attack_fight_event_count':   int(b.get('attack_fight_event_count', 0) or 0),
                'attack_fight_event_facade':  int(b.get('attack_fight_event_facade', 0) or 0),
                'attack_help_id':             str(b.get('attack_help_id', '')),
                'attack_hero_policy':         str(b.get('attack_hero_policy', '')),
                'atk_hero_type':              str(b.get('attack_hero_type', '')),
                'attack_hero_type_advance':   str(b.get('attack_hero_type_advance', '')),
                'atk_hp':                     int(b.get('attack_hp', 0) or 0),
                'atk_idu':                    str(b.get('attack_idu', '')),
                'atk_team_id':                extract_team_id(b.get('attack_idu', '')),
                'atk_name':                   str(b.get('attack_name', '')),
                'attack_role_id':             str(b.get('attack_role_id', '')),
                'atk_uid':                    str(b.get('attack_role_id', '')),
                'attack_ship_type':           int(b.get('attack_ship_type', 0) or 0),
                'atk_union':                  str(b.get('attack_union_name', '')),
                'attack_union_official':      int(b.get('attack_union_official', 0) or 0),
                'atk_unionid':                int(b.get('attack_unionid', 0) or 0),
                'attacker_army_effect':       str(b.get('attacker_army_effect', '')),
                'attacker_base_hero_detail':  str(b.get('attacker_base_hero_detail', '')),
                'atk_power': 0,
                'atk_gear_info':              str(b.get('attacker_gear_info', '')),
                'atk_gongxun': 0,
                'attacker_life_end_time':     str(b.get('attacker_life_end_time', '')),
                'attacker_machine_stat_info': str(b.get('attacker_machine_stat_info', '')),
                'attacker_surface':           str(b.get('attacker_surface', '')),
                'attacker_xwc':               int(b.get('attacker_xwc', 0) or 0),
                'bandit':                     int(b.get('bandit', 0) or 0),
                'battle_scenes':              int(b.get('battle_scenes', 0) or 0),
                'block_id':                   int(b.get('block_id', 0) or 0),
                'borrow_land':                int(b.get('borrow_land', 0) or 0),
                'city_type':                  int(b.get('city_type', 0) or 0),
                'def_advance':                str(b.get('defend_advance', '')),
                'defend_all_hero_info':       str(b.get('defend_all_hero_info', '')),
                'defend_all_surface':         str(b.get('defend_all_surface', '')),
                'defend_army_group':          str(b.get('defend_army_group', '')),
                'defend_base_heroid':         int(b.get('defend_base_heroid', 0) or 0),
                'def_level':                  int(b.get('defend_base_level', 0) or 0),
                'defend_clan_name':           str(b.get('defend_clan_name', '')),
                'defend_clanid':              int(b.get('defend_clanid', 0) or 0),
                'defend_fight_event_count':   int(b.get('defend_fight_event_count', 0) or 0),
                'defend_fight_event_facade':  int(b.get('defend_fight_event_facade', 0) or 0),
                'defend_help_id':             str(b.get('defend_help_id', '')),
                'defend_hero_policy':         str(b.get('defend_hero_policy', '')),
                'def_hero_type':              str(b.get('defend_hero_type', '')),
                'defend_hero_type_advance':   str(b.get('defend_hero_type_advance', '')),
                'def_hp':                     int(b.get('defend_hp', 0) or 0),
                'def_idu':                    str(b.get('defend_idu', '')),
                'def_team_id':                extract_team_id(b.get('defend_idu', '')),
                'def_name':                   str(b.get('defend_name', '')),
                'defend_role_id':             str(b.get('defend_role_id', '')),
                'defend_ship_type':           int(b.get('defend_ship_type', 0) or 0),
                'def_union':                  str(b.get('defend_union_name', '')),
                'defend_union_official':      int(b.get('defend_union_official', 0) or 0),
                'def_unionid':                int(b.get('defend_unionid', 0) or 0),
                'defender_army_effect':       str(b.get('defender_army_effect', '')),
                'defender_base_hero_detail':  str(b.get('defender_base_hero_detail', '')),
                'def_gongxun': 0,
                'def_gear_info':              str(b.get('defender_gear_info', '')),
                'defender_life_end_time':     str(b.get('defender_life_end_time', '')),
                'defender_machine_stat_info': str(b.get('defender_machine_stat_info', '')),
                'defender_surface':           str(b.get('defender_surface', '')),
                'defender_xwc':               int(b.get('defender_xwc', 0) or 0),
                'extra_result':               int(b.get('extra_result', 0) or 0),
                'fight_type':                 int(b.get('fight_type', 0) or 0),
                'first_occupy_lvn_land':      int(b.get('first_occupy_lvn_land', 0) or 0),
                'garrison':                   int(b.get('garrison', 0) or 0),
                'huangjin_convert':           int(b.get('huangjin_convert', 0) or 0),
                'in_night':                   int(b.get('in_night_mode', 0) or 0),
                'is_ai':                      int(b.get('is_ai', 0) or 0),
                'is_npc':                     int(b.get('npc', 0) or 0),
                'is_shared':                  int(b.get('is_shared', 0) or 0),
                'is_support':                 int(b.get('is_support', 0) or 0),
                'lose_tips':                  str(b.get('lose_tips', '')),
                'machine_effect':             str(b.get('machine_effect', '')),
                'military':                   int(b.get('military', 0) or 0),
                'military_effect':            int(b.get('military_effect', 0) or 0),
                'mvp_svp_pos':                str(b.get('mvp_svp_pos', '')),
                'nation_member_union_info':   str(b.get('nation_member_union_info', '')),
                'no_owner_res':               int(b.get('no_owner_res', 0) or 0),
                'press_attack':               int(b.get('press_attack', 0) or 0),
                'reference_count':            int(b.get('reference_count', 0) or 0),
                'res_type':                   int(b.get('res_type', 0) or 0),
                'rob':                        int(b.get('rob', 0) or 0),
                'sand_extra_info':            str(b.get('sand_extra_info', '')),
                'ship_effect':                int(b.get('ship_effect', 0) or 0),
                'tech_jian_jun_effect':       int(b.get('tech_jian_jun_effect', 0) or 0),
                'tech_quan_xiang_effect':     int(b.get('tech_quan_xiang_effect', 0) or 0),
                'weather':                    int(b.get('weather', 0) or 0),
                'wid':                        int(b.get('wid', 0) or 0),
                'wid_name':                   str(b.get('wid_name', '')),
                'wid_code':                   '',
                'world_npc_army':             str(b.get('world_npc_army', '')),
                'yi_ling_press_attack':       int(b.get('yi_ling_press_attack', 0) or 0),
                'attack_all_sub_hero_info':   str(b.get('attack_all_sub_hero_info', '')),
                'defend_all_sub_hero_info':   str(b.get('defend_all_sub_hero_info', '')),
                'attack_support_user_info':   str(b.get('attack_support_user_info', '')),
                'defend_support_user_info':   str(b.get('defend_support_user_info', '')),

                # 解析出的独立武将字段（照搬 stzbHelper BattleReport）
                'atk_hero1_id': ah1, 'atk_hero2_id': ah2, 'atk_hero3_id': ah3,
                'atk_hero1_level': al1, 'atk_hero2_level': al2, 'atk_hero3_level': al3,
                'atk_hero1_star': as1, 'atk_hero2_star': as2, 'atk_hero3_star': as3,
                'atk_total_star': atk_total_star,
                'def_hero1_id': dh1, 'def_hero2_id': dh2, 'def_hero3_id': dh3,
                'def_hero1_level': dl1, 'def_hero2_level': dl2, 'def_hero3_level': dl3,
                'def_hero1_star': ds1, 'def_hero2_star': ds2, 'def_hero3_star': ds3,
                'def_total_star': def_total_star,
                'atk_heroes': atk_heroes,
                'def_heroes': def_heroes,
            })
        except Exception as e:
            continue
    return results


def upsert_battle_0a(conn, b):
    """将 0000000a 战报写入 battles_v2 及相关表（照搬 stzbHelper Report struct 全字段）"""
    ensure_battles_v2_team_columns(conn)
    exists = conn.execute('SELECT 1 FROM battles_v2 WHERE battle_id=?', (b['battle_id'],)).fetchone()
    if exists:
        return False
    # 用字段映射表动态构建 INSERT，彻底避免列名/key 不一致
    db_cols = {r[1] for r in conn.execute('PRAGMA table_info(battles_v2)').fetchall()}
    seen = set()
    cols, vals = [], []
    for col, key in _BATTLE_0A_FIELDS:
        if col in db_cols and key in b and col not in seen:
            seen.add(col)
            cols.append(col)
            vals.append(b[key])
    if 'atk_team_id' in db_cols and 'atk_team_id' not in seen:
        cols.append('atk_team_id')
        vals.append(int(b.get('atk_team_id', extract_team_id(b.get('atk_idu', ''))) or 0))
    if 'def_team_id' in db_cols and 'def_team_id' not in seen:
        cols.append('def_team_id')
        vals.append(int(b.get('def_team_id', extract_team_id(b.get('def_idu', ''))) or 0))
    placeholders = ','.join('?' * len(cols))
    col_list = ','.join(cols)
    conn.execute(f'INSERT OR IGNORE INTO battles_v2 ({col_list}) VALUES ({placeholders})', vals)

    # 不再把 team_id 回填到 team_users；队伍id属于战报队伍实体，不属于玩家主档
    # wuxun_log
    if b['atk_gongxun'] > 0:
        conn.execute('''
            INSERT INTO wuxun_log (battle_id, time, atk_name, atk_union, atk_level,
                                   gongxun, fight_type, result, wid)
            VALUES (?,?,?,?,?,?,?,?,?)
        ''', (b['battle_id'], b['time'], b['atk_name'], b['atk_union'], b['def_level'],
              b['atk_gongxun'], b['fight_type'], b['result'], b['wid']))
    # power_log
    if b['atk_power'] > 0:
        conn.execute('''
            INSERT INTO power_log (battle_id, time, atk_name, atk_union, atk_level,
                                   power, fight_type, result, wid)
            VALUES (?,?,?,?,?,?,?,?,?)
        ''', (b['battle_id'], b['time'], b['atk_name'], b['atk_union'], b['def_level'],
              b['atk_power'], b['fight_type'], b['result'], b['wid']))
    # attendance
    try:
        import profile_manager as _pm_att
        _att_pid = _pm_att.get_current_profile_id()
    except:
        _att_pid = ''
    try:
        conn.execute('''
            INSERT INTO attendance (battle_id, time, player_name, player_uid, union_name,
                                    fight_type, wid, gongxun, result, profile_id)
            VALUES (?,?,?,?,?,?,?,?,?,?)
        ''', (b['battle_id'], b['time'], b['atk_name'], b['atk_uid'], b['atk_union'],
              b['fight_type'], b['wid'], b['atk_gongxun'], b['result'], _att_pid))
    except sqlite3.OperationalError as _e:
        if 'profile_id' in str(_e):
            conn.execute('''
                INSERT INTO attendance (battle_id, time, player_name, player_uid, union_name,
                                        fight_type, wid, gongxun, result)
                VALUES (?,?,?,?,?,?,?,?,?)
            ''', (b['battle_id'], b['time'], b['atk_name'], b['atk_uid'], b['atk_union'],
                  b['fight_type'], b['wid'], b['atk_gongxun'], b['result']))
        else:
            raise
    # battle_heroes（兼容旧库无 star 列）
    bh_cols = {r[1] for r in conn.execute('PRAGMA table_info(battle_heroes)').fetchall()}
    has_star = 'star' in bh_cols

    # battle_heroes 攻方
    for i, h in enumerate(b['atk_heroes']):
        if has_star:
            conn.execute('''
                INSERT INTO battle_heroes (battle_id, side, pos, hero_id, hero_name,
                                           level, max_hp, remain_hp, damage_taken, star)
                VALUES (?,?,?,?,?,?,?,?,?,?)
            ''', (b['battle_id'], 'atk', i, h['hero_id'], h['hero_name'],
                  h['level'], h['max_hp'], h['remain_hp'], h['damage_taken'], h.get('star', 0)))
        else:
            conn.execute('''
                INSERT INTO battle_heroes (battle_id, side, pos, hero_id, hero_name,
                                           level, max_hp, remain_hp, damage_taken)
                VALUES (?,?,?,?,?,?,?,?,?)
            ''', (b['battle_id'], 'atk', i, h['hero_id'], h['hero_name'],
                  h['level'], h['max_hp'], h['remain_hp'], h['damage_taken']))

    # battle_heroes 守方
    for i, h in enumerate(b['def_heroes']):
        if has_star:
            conn.execute('''
                INSERT INTO battle_heroes (battle_id, side, pos, hero_id, hero_name,
                                           level, max_hp, remain_hp, damage_taken, star)
                VALUES (?,?,?,?,?,?,?,?,?,?)
            ''', (b['battle_id'], 'def', i, h['hero_id'], h['hero_name'],
                  h['level'], h['max_hp'], h['remain_hp'], h['damage_taken'], h.get('star', 0)))
        else:
            conn.execute('''
                INSERT INTO battle_heroes (battle_id, side, pos, hero_id, hero_name,
                                           level, max_hp, remain_hp, damage_taken)
                VALUES (?,?,?,?,?,?,?,?,?)
            ''', (b['battle_id'], 'def', i, h['hero_id'], h['hero_name'],
                  h['level'], h['max_hp'], h['remain_hp'], h['damage_taken']))
    conn.commit()
    return True


# ============================================================
# 解析 00000067 同盟成员数据
# 索引: [0]=uid [1]=名字 [2]=总贡献 [3]=职位 [6]=wid [7]=本周贡献
#       [8]=势力 [10]=武勋 [13]=分组 [16]=武将配置ID [17]=技能 [30]=加入时间
# ============================================================
def parse_team_users_67(fpath, data=None):
    import json as _json
    try:
        if data is None:
            with open(fpath, 'r', encoding='utf-8', errors='replace') as f:
                data = _json.load(f)
        if not isinstance(data, list) or len(data) == 0:
            return []
        users = []
        for item in data:
            if not isinstance(item, list) or len(item) < 31:
                continue
            try:
                uid   = int(item[0])
                name  = str(item[1])
                ct    = int(item[2]) if item[2] else 0
                pos   = int(item[3]) if item[3] else 0
                wid   = int(item[6]) if item[6] else 0
                cw    = int(item[7]) if item[7] else 0
                power = int(item[8]) if item[8] else 0
                wu    = int(item[10]) if item[10] else 0
                grp   = str(item[13]) if item[13] else ''
                hero_cfg = int(item[16]) if item[16] else 0
                skills   = str(item[17]) if item[17] else ''
                jt    = int(item[30]) if item[30] else 0
                users.append({
                    'uid': uid, 'name': name,
                    'contribute_total': ct, 'contribute_week': cw,
                    'pos': pos, 'wid': wid, 'power': power, 'wuxun': wu,
                    'group_name': grp, 'hero_config_id': hero_cfg,
                    'team_id': 0,
                    'hero_skills': skills, 'join_time': jt,
                })
            except:
                continue
        return users
    except:
        return []


def upsert_team_users(conn, users, profile_id=''):
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    cols = {r[1] for r in conn.execute('PRAGMA table_info(team_users)').fetchall()}
    if 'team_id' not in cols:
        try:
            conn.execute('ALTER TABLE team_users ADD COLUMN team_id INTEGER DEFAULT 0')
            conn.commit()
        except sqlite3.OperationalError:
            pass
    for u in users:
        conn.execute('''
            INSERT INTO team_users (uid,profile_id,name,contribute_total,contribute_week,pos,wid,
                power,wuxun,group_name,hero_config_id,team_id,hero_skills,join_time,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(uid,profile_id) DO UPDATE SET
                name=excluded.name, contribute_total=excluded.contribute_total,
                contribute_week=excluded.contribute_week, pos=excluded.pos,
                wid=excluded.wid, power=excluded.power, wuxun=excluded.wuxun,
                group_name=excluded.group_name, hero_config_id=excluded.hero_config_id,
                team_id=excluded.team_id,
                hero_skills=excluded.hero_skills, join_time=excluded.join_time,
                updated_at=excluded.updated_at
        ''', (u['uid'], profile_id, u['name'], u['contribute_total'], u['contribute_week'],
              u['pos'], u['wid'], u['power'], u['wuxun'], u['group_name'],
              u['hero_config_id'], u.get('team_id', 0), u['hero_skills'], u['join_time'], now))
    if users:
        conn.commit()


# ============================================================
# 解析 000001fe 玩家战绩统计
# ============================================================
def parse_player_stats_1fe(fpath, data=None):
    """解析 000001fe 玩家战绩，返回 dict 或 None"""
    import json as _json
    try:
        if data is None:
            with open(fpath, 'r', encoding='utf-8', errors='replace') as f:
                data = _json.load(f)
        if not isinstance(data, dict) or 'userid' not in data:
            return None
        return data
    except:
        return None


def upsert_player_stats(conn, d):
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    import json as _json
    conn.execute('''
        INSERT INTO player_stats (
            userid, user_name, city_count, land_count, force_max, power_max,
            season, wuxun_total, wuxun_cur_week, wuxun_last_week,
            kill_enemy_count, kill_enemy_cur_week, kill_ai_total,
            destroy_build, grab_land_count, npc_city_destroy, npc_city_kill,
            cfg_db_id, raw_json, updated_at
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT(userid) DO UPDATE SET
            user_name=excluded.user_name,
            city_count=excluded.city_count,
            land_count=excluded.land_count,
            force_max=MAX(force_max, excluded.force_max),
            power_max=MAX(power_max, excluded.power_max),
            season=excluded.season,
            wuxun_total=excluded.wuxun_total,
            wuxun_cur_week=excluded.wuxun_cur_week,
            wuxun_last_week=excluded.wuxun_last_week,
            kill_enemy_count=excluded.kill_enemy_count,
            kill_enemy_cur_week=excluded.kill_enemy_cur_week,
            kill_ai_total=excluded.kill_ai_total,
            destroy_build=excluded.destroy_build,
            grab_land_count=excluded.grab_land_count,
            npc_city_destroy=excluded.npc_city_destroy,
            npc_city_kill=excluded.npc_city_kill,
            cfg_db_id=excluded.cfg_db_id,
            raw_json=excluded.raw_json,
            updated_at=excluded.updated_at
    ''', (
        d.get('userid'), d.get('user_name',''),
        d.get('city_count',0), d.get('land_count',0),
        d.get('force_max',0), d.get('power_max',0),
        d.get('season',0),
        d.get('wuxun_total',0), d.get('wuxun_cur_week',0), d.get('wuxun_last_week',0),
        d.get('kill_enemy_count',0), d.get('kill_enemy_count_cur_week',0),
        d.get('kill_ai_total',0),
        d.get('destroy_build',0), d.get('grab_land_count',0),
        d.get('npc_city_destroy',0), d.get('npc_city_kill',0),
        d.get('cfg_db_id',0),
        _json.dumps(d, ensure_ascii=False), now
    ))
    conn.commit()


# ============================================================
# 解析 000013a2 地图格子数据
# 格式: [{wid:0,...}, ...empty..., {wid:{0:[type,bid,0,0,0,owner,city_name,parent_wid,...]},...}, ...]
# ============================================================
CELL_TYPE_MAP = {
    0:'空地', 1:'土地L1', 2:'土地L2', 3:'土地L3', 4:'土地L4', 5:'土地L5',
    7:'附属格', 8:'攻城营垒', 9:'土地', 10:'矿山', 11:'斥候营地',
    12:'大型要塞', 13:'关卡', 14:'皇城', 15:'资源点',
    16:'行军营', 17:'联盟城池', 18:'联盟资源',
    20:'战场', 21:'战场附属', 30:'皇城附属', 31:'皇城附属',
    70:'铁矿场', 71:'铜矿场', 72:'银矿场', 73:'金矿场', 74:'玉矿场',
    75:'石矿场', 76:'采矿场', 77:'采矿场', 78:'采矿场', 79:'采矿场',
    80:'采矿场', 90:'资源区', 100:'特殊格',
}

def parse_map_13a2(txt, fpath):
    """解析 000013a2 地图格子，返回 cells 列表"""
    import re as _re
    cells = []
    try:
        txt = txt.strip().rstrip('\x00').strip()
        # JS对象key无引号，转为JSON格式
        # 匹配 {数字key: 或 ,数字key:
        txt_json = _re.sub(r'(?<=[{,])(\d+)(?=:)', r'"\1"', txt)
        import json as _json
        data = _json.loads(txt_json)
    except Exception as e:
        return cells
    if not isinstance(data, list) or len(data) < 14:
        return cells
    # 找包含格子数据的字典（通常在index 13或14）
    cell_details = {}
    for idx in [13, 14, 12]:
        if idx < len(data) and isinstance(data[idx], dict) and data[idx]:
            cell_details = data[idx]
            break
    if not cell_details:
        return cells
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    for wid_str, info in cell_details.items():
        try:
            wid = int(wid_str)
            x = wid % 10000
            y = wid // 10000
            arr = info.get('0') or info.get(0)
            if not arr or not isinstance(arr, list): continue
            cell_type   = int(arr[0]) if len(arr) > 0 else 0
            building_id = int(arr[1]) if len(arr) > 1 else 0
            owner_name  = str(arr[5]) if len(arr) > 5 else ''
            city_name   = str(arr[6]) if len(arr) > 6 else ''
            parent_wid  = int(arr[7]) if len(arr) > 7 and arr[7] else 0
            type_name   = CELL_TYPE_MAP.get(cell_type, f'type{cell_type}')
            cells.append({
                'wid': wid, 'x': x, 'y': y,
                'cell_type': cell_type, 'type_name': type_name,
                'building_id': building_id,
                'owner_name': owner_name,
                'city_name': city_name,
                'parent_wid': parent_wid,
                'updated_at': now,
            })
        except: continue
    return cells


def upsert_map_cells(conn, cells):
    for c in cells:
        conn.execute('''
            INSERT INTO map_cells (wid, cell_type, building_id, owner_name, city_name,
                                   parent_wid, x, y, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            ON CONFLICT(wid) DO UPDATE SET
                cell_type=excluded.cell_type, building_id=excluded.building_id,
                owner_name=excluded.owner_name, city_name=excluded.city_name,
                parent_wid=excluded.parent_wid, updated_at=excluded.updated_at
        ''', (c['wid'], c['cell_type'], c['building_id'], c['owner_name'],
              c['city_name'], c['parent_wid'], c['x'], c['y'], c['updated_at']))
    if cells:
        conn.commit()


# ============================================================
# 解析 0000005c 同盟战报 (cmdId=92)
# 照搬 stzbHelper parseReport：格式 [[battle_obj, extra...], ...]
# 每个元素是数组，v[0] 才是战报 dict（与 0000000a 的 dict 格式相同）
# ============================================================
def parse_battle_5c(data, fpath):
    """解析 0000005c 同盟战报，返回 battle 列表"""
    results = []
    if not isinstance(data, list):
        return results
    for item in data:
        # 照搬 stzbHelper: for _, v := range jsondata { reportJSON, _ := json.Marshal(v[0]) }
        if not isinstance(item, list) or len(item) == 0:
            continue
        b = item[0]
        if not isinstance(b, dict):
            continue
        try:
            battle_id = int(b.get('battle_id', 0))
            if not battle_id:
                continue
            ts       = int(b.get('time', 0))
            time_str = datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:%M:%S') if ts > 0 else ''
            result   = int(b.get('result', 0))

            atk_heroes, ah1, ah2, ah3, al1, al2, al3, as1, as2, as3, atk_total_star = \
                _parse_hero_info_0a(
                    str(b.get('attack_all_hero_info', '')),
                    str(b.get('attack_advance', '')),
                    str(b.get('attack_hero_type', '')))
            def_heroes, dh1, dh2, dh3, dl1, dl2, dl3, ds1, ds2, ds3, def_total_star = \
                _parse_hero_info_0a(
                    str(b.get('defend_all_hero_info', '')),
                    str(b.get('defend_advance', '')),
                    str(b.get('defend_hero_type', '')))

            rec = {
                'battle_id':   battle_id,
                'time':        ts,
                'time_str':    time_str,
                'result':      result,
                'result_desc': RESULT_MAP.get(result, str(result)),
                'source_file': os.path.basename(fpath),
                'wid_code':    '',
            }
            # 直接把 b 里所有字段映射进去（照搬 stzbHelper json.Unmarshal to Report struct）
            for col, key in _BATTLE_0A_FIELDS:
                if key not in rec:
                    raw = b.get(key) or b.get(col)
                    if raw is not None:
                        rec[key] = raw
            # 补充解析字段
            rec.update({
                'atk_advance': str(b.get('attack_advance', '')),
                'def_advance': str(b.get('defend_advance', '')),
                'atk_name':    str(b.get('attack_name', '')),
                'atk_uid':     str(b.get('attack_role_id', '')),
                'atk_union':   str(b.get('attack_union_name', '')),
                'atk_unionid': int(b.get('attack_unionid', 0) or 0),
                'atk_gongxun': 0,
                'atk_power': 0,
                'atk_hp':      int(b.get('attack_hp', 0) or 0),
                'atk_idu':     str(b.get('attack_idu', '')),
                'atk_hero_type': str(b.get('attack_hero_type', '')),
                'atk_gear_info': str(b.get('attacker_gear_info', '')),
                'def_name':    str(b.get('defend_name', '')),
                'def_union':   str(b.get('defend_union_name', '')),
                'def_unionid': int(b.get('defend_unionid', 0) or 0),
                'def_gongxun': 0,
                'def_level':   int(b.get('defend_base_level', 0) or 0),
                'def_hp':      int(b.get('defend_hp', 0) or 0),
                'def_idu':     str(b.get('defend_idu', '')),
                'def_hero_type': str(b.get('defend_hero_type', '')),
                'def_gear_info': str(b.get('defender_gear_info', '')),
                'wid':         int(b.get('wid', 0) or 0),
                'wid_name':    str(b.get('wid_name', '')),
                'fight_type':  int(b.get('fight_type', 0) or 0),
                'is_npc':      int(b.get('npc', 0) or 0),
                'is_ai':       int(b.get('is_ai', 0) or 0),
                'weather':     int(b.get('weather', 0) or 0),
                'in_night':    int(b.get('in_night_mode', 0) or 0),
                'all_skill_info': str(b.get('all_skill_info', '')),
                'attack_all_hero_info': str(b.get('attack_all_hero_info', '')),
                'defend_all_hero_info': str(b.get('defend_all_hero_info', '')),
                'attack_all_sub_hero_info': str(b.get('attack_all_sub_hero_info', '')),
                'defend_all_sub_hero_info': str(b.get('defend_all_sub_hero_info', '')),
                'atk_hero1_id': ah1, 'atk_hero2_id': ah2, 'atk_hero3_id': ah3,
                'atk_hero1_level': al1, 'atk_hero2_level': al2, 'atk_hero3_level': al3,
                'atk_hero1_star': as1, 'atk_hero2_star': as2, 'atk_hero3_star': as3,
                'atk_total_star': atk_total_star,
                'def_hero1_id': dh1, 'def_hero2_id': dh2, 'def_hero3_id': dh3,
                'def_hero1_level': dl1, 'def_hero2_level': dl2, 'def_hero3_level': dl3,
                'def_hero1_star': ds1, 'def_hero2_star': ds2, 'def_hero3_star': ds3,
                'def_total_star': def_total_star,
                'atk_heroes': atk_heroes,
                'def_heroes': def_heroes,
            })
            results.append(rec)
        except Exception:
            continue
    return results


# ============================================================
# 解析 00000898 战报通知
# 格式: [type, name1, name2?, extra...]
# ============================================================
def parse_battle_898(data, fpath):
    if not isinstance(data, list) or len(data) < 2:
        return None
    typ   = int(data[0]) if data else 0
    name1 = str(data[1]) if len(data) > 1 else ''
    name2 = str(data[2]) if len(data) > 2 else ''
    return {'type': typ, 'name1': name1, 'name2': name2, 'source': os.path.basename(fpath)}


# ============================================================
# 解析 000018aa 攻城战场实时动态
# 格式: [[wid, attacker_uid, "nearby_uid1,uid2,..."], ...]
# wid=正在被攻打的城池, attacker_uid=攻击者uid, nearby=附近成员uid
# ============================================================
def parse_battle_field_18aa(data, fpath):
    """解析攻城战场动态，返回 events 列表"""
    events = []
    if not isinstance(data, list):
        return events
    ts = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    fname = os.path.basename(fpath)
    # 从文件名提取时间戳
    try:
        cap_ts = int(fname.split('_')[1]) // 1000  # 毫秒转秒
    except:
        cap_ts = int(time.time())
    for item in data:
        if not isinstance(item, list) or len(item) < 2:
            continue
        try:
            wid = int(item[0])
            attacker_uid = int(item[1])
            nearby_str = str(item[2]) if len(item) > 2 else ''
            nearby_uids = [int(x) for x in nearby_str.split(',') if x.strip().isdigit()]
            events.append({
                'wid': wid,
                'attacker_uid': attacker_uid,
                'nearby_uids': nearby_uids,
                'nearby_count': len(nearby_uids),
                'cap_time': cap_ts,
                'captured_at': ts,
            })
        except:
            continue
    return events


def upsert_battle_field(conn, events):
    """写入攻城战场动态表"""
    for e in events:
        conn.execute('''
            INSERT INTO battle_field
                (wid, attacker_uid, nearby_uids, nearby_count, cap_time, captured_at)
            VALUES (?,?,?,?,?,?)
            ON CONFLICT(wid) DO UPDATE SET
                attacker_uid=excluded.attacker_uid,
                nearby_uids=excluded.nearby_uids,
                nearby_count=excluded.nearby_count,
                cap_time=excluded.cap_time,
                captured_at=excluded.captured_at
        ''', (
            e['wid'], e['attacker_uid'],
            ','.join(str(u) for u in e['nearby_uids']),
            e['nearby_count'], e['cap_time'], e['captured_at']
        ))
    if events:
        conn.commit()


# ============================================================
# 解析 000018ae 攻城队列快照
# 格式: [[成员列表], city_id]
# 每条成员: [name, uid, level, pos, hero_list_str, hero_count_x120,
#            cur_hero_id, power, flag, hero_config_id, skin_id]
# ============================================================
def parse_battle_queue_18ae(data, fpath):
    """解析攻城队列快照，返回 (city_id, members) """
    if not isinstance(data, list) or len(data) < 2:
        return None, []
    members_raw = data[0]
    city_id = int(data[1]) if isinstance(data[1], int) else 0
    if not isinstance(members_raw, list):
        return city_id, []
    fname = os.path.basename(fpath)
    try:
        cap_ts = int(fname.split('_')[1]) // 1000
    except:
        cap_ts = int(time.time())
    ts = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    members = []
    for row in members_raw:
        if not isinstance(row, list) or len(row) < 11:
            continue
        try:
            members.append({
                'name':          str(row[0]),
                'uid':           int(row[1]),
                'level':         int(row[2]) if row[2] else 0,
                'queue_slot':    int(row[3]) if row[3] else 0,
                'hero_list':     str(row[4]) if isinstance(row[4], str) else '',
                'hero_count':    int(row[5]) // 120 if row[5] else 0,
                'cur_hero_id':   int(row[6]) if row[6] else 0,
                'power':         int(row[7]) if row[7] else 0,
                'flag':          int(row[8]) if row[8] else 0,
                'hero_config_id':int(row[9]) if row[9] else 0,
                'skin_id':       int(row[10]) if row[10] else 0,
                'city_id':       city_id,
                'cap_time':      cap_ts,
                'captured_at':   ts,
            })
        except:
            continue
    return city_id, members


def upsert_battle_queue(conn, city_id, members):
    """写入攻城队列快照表"""
    conn.execute('''
        CREATE TABLE IF NOT EXISTS battle_queue (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            uid          INTEGER,
            name         TEXT,
            level        INTEGER,
            queue_slot   INTEGER,
            hero_list    TEXT,
            hero_count   INTEGER,
            cur_hero_id  INTEGER,
            power        INTEGER,
            flag         INTEGER,
            hero_config_id INTEGER,
            skin_id      INTEGER,
            city_id      INTEGER,
            cap_time     INTEGER,
            captured_at  TEXT
        )
    ''')
    for m in members:
        conn.execute('''
            INSERT INTO battle_queue
                (uid, name, level, queue_slot, hero_list, hero_count,
                 cur_hero_id, power, flag, hero_config_id, skin_id,
                 city_id, cap_time, captured_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        ''', (m['uid'], m['name'], m['level'], m['queue_slot'],
              m['hero_list'], m['hero_count'], m['cur_hero_id'],
              m['power'], m['flag'], m['hero_config_id'], m['skin_id'],
              m['city_id'], m['cap_time'], m['captured_at']))
    if members:
        conn.commit()


# ============================================================
# 解析 0000012d 玩家行军/移动数据
# 格式: [wid, [[battle_id, hero_id, speed, 0, 0, 0]], 0, dist, 0]
# ============================================================
def parse_march_12d(data, fpath):
    """解析玩家行军数据"""
    if not isinstance(data, list) or len(data) < 2:
        return None
    try:
        wid = int(data[0])
        troops = data[1] if isinstance(data[1], list) else []
        dist = int(data[3]) if len(data) > 3 else 0
        troop_list = []
        for t in troops:
            if isinstance(t, list) and len(t) >= 2:
                troop_list.append({
                    'battle_id': int(t[0]) if t[0] else 0,
                    'hero_id': int(t[1]) if t[1] else 0,
                    'speed': int(t[2]) if len(t) > 2 and t[2] else 0,
                })
        return {'wid': wid, 'dist': dist, 'troops': troop_list}
    except:
        return None


# ============================================================
# 解析 000002bc 联盟列表
# 格式: [page, total, null, page_size, [[rank, {union_obj}], ...]]
# union_obj: {union_id, name, level, power, total_member, occupy_city_value, ...}
# ============================================================
def parse_union_list_2bc(data, fpath):
    """解析 000002bc 中的联盟排行报文"""
    results = []
    if not isinstance(data, list) or len(data) < 5:
        return results
    union_rows = data[4] if isinstance(data[4], list) else []
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    for row in union_rows:
        if not isinstance(row, list) or len(row) < 2:
            continue
        rank = int(row[0]) if row[0] is not None else 0
        obj  = row[1] if isinstance(row[1], dict) else {}
        if not obj:
            continue
        if 'union_id' not in obj or 'name' not in obj:
            continue
        # 个人势力排行也会带 power/name，但不会带这些联盟字段
        if not any(k in obj for k in ('user_count', 'city_count', 'total_member', 'total_npc_city', 'boss', 'state')):
            continue
        try:
            total_member = obj.get('total_member', obj.get('user_count', 0))
            total_npc_city = obj.get('total_npc_city', obj.get('city_count', 0))
            occupy_city_value = obj.get('occupy_city_value', obj.get('branch_city_count', 0))
            results.append({
                'union_id':         int(obj.get('union_id', 0)),
                'name':             str(obj.get('name', '')),
                'level':            int(obj.get('level', 0)),
                'power':            int(obj.get('power', 0)),
                'force':            int(obj.get('force', 0)),
                'total_member':     int(total_member or 0),
                'occupy_city_value':int(occupy_city_value or 0),
                'total_npc_city':   int(total_npc_city or 0),
                'region':           int(obj.get('region', 0)),
                'area':             int(obj.get('area', 0)),
                'rank':             rank,
                'refresh_time':     int(obj.get('refresh_time', 0)),
                'updated_at':       now,
            })
        except Exception:
            continue
    return results


def parse_player_power_rank_2bc(data, fpath):
    """解析 000002bc 中的个人势力排行报文"""
    results = []
    if not isinstance(data, list) or len(data) < 5:
        return results
    rows = data[4] if isinstance(data[4], list) else []
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    for row in rows:
        if not isinstance(row, list) or len(row) < 2:
            continue
        rank = int(row[0]) if row[0] is not None else 0
        obj = row[1] if isinstance(row[1], dict) else {}
        if not obj:
            continue
        if 'user_id' not in obj and 'role_id' not in obj:
            continue
        try:
            results.append({
                'user_id': int(obj.get('user_id', 0) or 0),
                'role_id': str(obj.get('role_id', '')),
                'name': str(obj.get('name', '')),
                'power': int(obj.get('power', 0) or 0),
                'force': int(obj.get('force', 0) or 0),
                'area': int(obj.get('area', 0) or 0),
                'region': int(obj.get('region', 0) or 0),
                'land_count': int(obj.get('land_count', 0) or 0),
                'fort_count': int(obj.get('fort_count', 0) or 0),
                'branch_city_count': int(obj.get('branch_city_count', 0) or 0),
                'shu_cheng_count': int(obj.get('shu_cheng_count', 0) or 0),
                'refresh_time': int(obj.get('refresh_time', 0) or 0),
                'rank': rank,
                'updated_at': now,
            })
        except Exception:
            continue
    return results


def upsert_union_list(conn, unions):
    conn.execute('''
        CREATE TABLE IF NOT EXISTS union_list (
            union_id        INTEGER PRIMARY KEY,
            name            TEXT,
            level           INTEGER DEFAULT 0,
            power           INTEGER DEFAULT 0,
            force           INTEGER DEFAULT 0,
            total_member    INTEGER DEFAULT 0,
            occupy_city_value INTEGER DEFAULT 0,
            total_npc_city  INTEGER DEFAULT 0,
            region          INTEGER DEFAULT 0,
            area            INTEGER DEFAULT 0,
            rank            INTEGER DEFAULT 0,
            refresh_time    INTEGER DEFAULT 0,
            updated_at      TEXT
        )
    ''')
    for u in unions:
        conn.execute('''
            INSERT INTO union_list
                (union_id,name,level,power,force,total_member,occupy_city_value,
                 total_npc_city,region,area,rank,refresh_time,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(union_id) DO UPDATE SET
                name=excluded.name, level=excluded.level, power=excluded.power,
                force=excluded.force, total_member=excluded.total_member,
                occupy_city_value=excluded.occupy_city_value,
                total_npc_city=excluded.total_npc_city,
                region=excluded.region, area=excluded.area,
                rank=excluded.rank, refresh_time=excluded.refresh_time,
                updated_at=excluded.updated_at
        ''', (u['union_id'], u['name'], u['level'], u['power'], u['force'],
              u['total_member'], u['occupy_city_value'], u['total_npc_city'],
              u['region'], u['area'], u['rank'], u['refresh_time'], u['updated_at']))
    if unions:
        conn.commit()


def upsert_player_power_rank(conn, rows):
    conn.execute('''
        CREATE TABLE IF NOT EXISTS player_power_rank (
            user_id            INTEGER PRIMARY KEY,
            role_id            TEXT,
            name               TEXT,
            power              INTEGER DEFAULT 0,
            force              INTEGER DEFAULT 0,
            area               INTEGER DEFAULT 0,
            region             INTEGER DEFAULT 0,
            land_count         INTEGER DEFAULT 0,
            fort_count         INTEGER DEFAULT 0,
            branch_city_count  INTEGER DEFAULT 0,
            shu_cheng_count    INTEGER DEFAULT 0,
            refresh_time       INTEGER DEFAULT 0,
            rank               INTEGER DEFAULT 0,
            updated_at         TEXT
        )
    ''')
    for r in rows:
        conn.execute('''
            INSERT INTO player_power_rank
                (user_id, role_id, name, power, force, area, region, land_count,
                 fort_count, branch_city_count, shu_cheng_count, refresh_time, rank, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(user_id) DO UPDATE SET
                role_id=excluded.role_id,
                name=excluded.name,
                power=excluded.power,
                force=excluded.force,
                area=excluded.area,
                region=excluded.region,
                land_count=excluded.land_count,
                fort_count=excluded.fort_count,
                branch_city_count=excluded.branch_city_count,
                shu_cheng_count=excluded.shu_cheng_count,
                refresh_time=excluded.refresh_time,
                rank=excluded.rank,
                updated_at=excluded.updated_at
        ''', (r['user_id'], r['role_id'], r['name'], r['power'], r['force'], r['area'], r['region'],
              r['land_count'], r['fort_count'], r['branch_city_count'], r['shu_cheng_count'],
              r['refresh_time'], r['rank'], r['updated_at']))
    if rows:
        conn.commit()


# ============================================================
# 解析 0000030c 游戏公告/系统通知
# 格式: [[title, content, time, type, id], ...]
# ============================================================
def parse_announcements_30c(data, fpath):
    """解析游戏公告列表"""
    results = []
    if not isinstance(data, list):
        return results
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    for item in data:
        if not isinstance(item, list) or len(item) < 3:
            continue
        try:
            title    = str(item[0]) if item[0] else ''
            content  = str(item[1]) if item[1] else ''
            pub_time = int(item[2]) if item[2] else 0
            ann_type = int(item[3]) if len(item) > 3 and item[3] else 0
            ann_id   = int(item[4]) if len(item) > 4 and item[4] else 0
            if not title and not content:
                continue
            time_str = datetime.fromtimestamp(pub_time).strftime('%Y-%m-%d %H:%M:%S') if pub_time > 0 else ''
            results.append({
                'ann_id':   ann_id,
                'title':    title,
                'content':  content,
                'pub_time': pub_time,
                'time_str': time_str,
                'ann_type': ann_type,
                'captured_at': now,
            })
        except Exception:
            continue
    return results


def upsert_announcements(conn, notices):
    conn.execute('''
        CREATE TABLE IF NOT EXISTS announcements (
            ann_id      INTEGER PRIMARY KEY,
            title       TEXT,
            content     TEXT,
            pub_time    INTEGER DEFAULT 0,
            time_str    TEXT,
            ann_type    INTEGER DEFAULT 0,
            captured_at TEXT
        )
    ''')
    for n in notices:
        conn.execute('''
            INSERT INTO announcements (ann_id,title,content,pub_time,time_str,ann_type,captured_at)
            VALUES (?,?,?,?,?,?,?)
            ON CONFLICT(ann_id) DO UPDATE SET
                title=excluded.title, content=excluded.content,
                pub_time=excluded.pub_time, time_str=excluded.time_str,
                ann_type=excluded.ann_type, captured_at=excluded.captured_at
        ''', (n['ann_id'], n['title'], n['content'], n['pub_time'],
              n['time_str'], n['ann_type'], n['captured_at']))
    if notices:
        conn.commit()


# ============================================================
# 解析 0000029f 武将解锁记录
# 格式: "hero_id,timestamp;hero_id,timestamp;..."
# ============================================================
def parse_hero_unlock_29f(data, fpath):
    """解析武将解锁记录，返回 [{hero_id, unlock_time}, ...]"""
    results = []
    raw = data if isinstance(data, str) else ''
    if not raw:
        return results
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    for part in raw.split(';'):
        part = part.strip()
        if not part:
            continue
        segs = part.split(',')
        if len(segs) < 2:
            continue
        try:
            hero_id     = int(segs[0])
            unlock_time = int(segs[1])
            if hero_id <= 0:
                continue
            results.append({
                'hero_id':     hero_id,
                'hero_name':   hero_name(hero_id),
                'unlock_time': unlock_time,
                'time_str':    datetime.fromtimestamp(unlock_time).strftime('%Y-%m-%d %H:%M:%S') if unlock_time > 0 else '',
                'captured_at': now,
            })
        except Exception:
            continue
    return results


def upsert_hero_unlock_log(conn, unlocks):
    conn.execute('''
        CREATE TABLE IF NOT EXISTS hero_unlock_log (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            hero_id     INTEGER,
            hero_name   TEXT,
            unlock_time INTEGER,
            time_str    TEXT,
            captured_at TEXT,
            UNIQUE(hero_id, unlock_time)
        )
    ''')
    for u in unlocks:
        conn.execute('''
            INSERT OR IGNORE INTO hero_unlock_log
                (hero_id, hero_name, unlock_time, time_str, captured_at)
            VALUES (?,?,?,?,?)
        ''', (u['hero_id'], u['hero_name'], u['unlock_time'], u['time_str'], u['captured_at']))
    if unlocks:
        conn.commit()


# ============================================================
# 解析 00000015 玩家自身信息包
# 索引: [0]=名字 [4]=最大兵力 [5]=当前兵力 [6]=粮食 [7]=木材
#       [10]=移速 [11]=行军上限 ... (大量字段)
# ============================================================
def parse_player_self_15(data, fpath):
    """解析自身玩家信息，返回 dict"""
    if not isinstance(data, list) or len(data) < 10:
        return None
    try:
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        return {
            'name':        str(data[0]) if data[0] else '',
            'force':       int(data[4]) if len(data) > 4 and data[4] else 0,
            'force_cur':   int(data[5]) if len(data) > 5 and data[5] else 0,
            'food':        int(data[6]) if len(data) > 6 and data[6] else 0,
            'wood':        int(data[7]) if len(data) > 7 and data[7] else 0,
            'speed':       int(data[10]) if len(data) > 10 and data[10] else 0,
            'march_max':   int(data[11]) if len(data) > 11 and data[11] else 0,
            'updated_at':  now,
        }
    except Exception:
        return None


def upsert_player_self(conn, ps):
    conn.execute('''
        CREATE TABLE IF NOT EXISTS player_self (
            id          INTEGER PRIMARY KEY CHECK(id=1),
            name        TEXT,
            force       INTEGER DEFAULT 0,
            force_cur   INTEGER DEFAULT 0,
            food        INTEGER DEFAULT 0,
            wood        INTEGER DEFAULT 0,
            speed       INTEGER DEFAULT 0,
            march_max   INTEGER DEFAULT 0,
            updated_at  TEXT
        )
    ''')
    conn.execute('''
        INSERT INTO player_self (id,name,force,force_cur,food,wood,speed,march_max,updated_at)
        VALUES (1,?,?,?,?,?,?,?,?)
        ON CONFLICT(id) DO UPDATE SET
            name=excluded.name, force=excluded.force, force_cur=excluded.force_cur,
            food=excluded.food, wood=excluded.wood, speed=excluded.speed,
            march_max=excluded.march_max, updated_at=excluded.updated_at
    ''', (ps['name'], ps['force'], ps['force_cur'], ps['food'],
          ps['wood'], ps['speed'], ps['march_max'], ps['updated_at']))
    conn.commit()


# ============================================================
# 解析 00001863 战区玩家列表
# 格式: [[uid, role_id, name, power, state1, state2, wid, pos_type,
#          region1, region2, last_active, last_active2, join_time, [], union_id], ...]
# ============================================================
def parse_zone_players_1863(data, fpath):
    """解析战区内所有玩家列表"""
    results = []
    # 数据可能是 [[player...], [player...]] 或 [[[player...], ...]]
    rows = []
    if isinstance(data, list):
        if data and isinstance(data[0], list) and data[0] and isinstance(data[0][0], list):
            rows = data[0]
        elif data and isinstance(data[0], list) and len(data[0]) > 3 and not isinstance(data[0][0], list):
            rows = data
        else:
            for sub in data:
                if isinstance(sub, list):
                    rows.extend(sub)
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    for item in rows:
        if not isinstance(item, list) or len(item) < 3:
            continue
        try:
            uid         = int(item[0]) if item[0] else 0
            role_id     = str(item[1]) if len(item) > 1 and item[1] else ''
            name        = str(item[2]) if len(item) > 2 and item[2] else ''
            power       = int(item[3]) if len(item) > 3 and item[3] else 0
            wid         = int(item[6]) if len(item) > 6 and item[6] else 0
            pos_type    = int(item[7]) if len(item) > 7 and item[7] is not None else 0
            last_active = int(item[10]) if len(item) > 10 and item[10] else 0
            join_time   = int(item[12]) if len(item) > 12 and item[12] else 0
            union_id    = int(item[14]) if len(item) > 14 and item[14] else 0
            if uid <= 0 and not name:
                continue
            results.append({
                'uid':         uid,
                'role_id':     role_id,
                'name':        name,
                'power':       power,
                'wid':         wid,
                'pos_type':    pos_type,
                'last_active': last_active,
                'join_time':   join_time,
                'union_id':    union_id,
                'updated_at':  now,
            })
        except Exception:
            continue
    return results


def upsert_zone_players(conn, players):
    conn.execute('''
        CREATE TABLE IF NOT EXISTS zone_players (
            uid         INTEGER PRIMARY KEY,
            role_id     TEXT,
            name        TEXT,
            power       INTEGER DEFAULT 0,
            wid         INTEGER DEFAULT 0,
            pos_type    INTEGER DEFAULT 0,
            last_active INTEGER DEFAULT 0,
            join_time   INTEGER DEFAULT 0,
            union_id    INTEGER DEFAULT 0,
            updated_at  TEXT
        )
    ''')
    for p in players:
        conn.execute('''
            INSERT INTO zone_players
                (uid,role_id,name,power,wid,pos_type,last_active,join_time,union_id,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(uid) DO UPDATE SET
                role_id=excluded.role_id, name=excluded.name, power=excluded.power,
                wid=excluded.wid, pos_type=excluded.pos_type,
                last_active=excluded.last_active, join_time=excluded.join_time,
                union_id=excluded.union_id, updated_at=excluded.updated_at
        ''', (p['uid'], p['role_id'], p['name'], p['power'], p['wid'],
              p['pos_type'], p['last_active'], p['join_time'], p['union_id'], p['updated_at']))
    if players:
        conn.commit()


# ============================================================
# 文件监控主循环
# ============================================================
class RealtimeWriter:
    def __init__(self):
        self.seen_files = set()   # 已处理文件路径
        self.stats = {'battles': 0, 'db_sync': 0, 'notifications': 0, 'errors': 0}
        self.world_scene_assembler = WorldSceneAssembler()
        _get_writer_db_path()  # 先更新 _writer_cap_dir 为当前账号目录
        self._load_seen_from_db()

    def _load_seen_from_db(self):
        """从 db 里读已导入的 source_file 名，避免重启后重复导入"""
        try:
            conn = get_db()
            rows = conn.execute('SELECT DISTINCT source_file FROM battles_v2 WHERE source_file IS NOT NULL').fetchall()
            for r in rows:
                if r[0]:
                    self.seen_files.add(r[0])
            conn.close()
        except: pass
        # 把 capture_new 里所有现存文件也标记为已处理（非 battles_v2 类型）
        # 避免重启时重复推送大量历史 battle_field/team_users 等事件
        try:
            scan_dirs = set([CAP_DIR, _writer_cap_dir])
            for scan_dir in scan_dirs:
                for dirpath, dirnames, filenames in os.walk(scan_dir):
                    for fn in filenames:
                        if fn.endswith('.json'):
                            self.seen_files.add(fn)
        except: pass
        print(f'[writer] 已知文件 {len(self.seen_files)} 个')

    def process_file(self, fpath, msg_type):
        basename = os.path.basename(fpath)
        if basename in self.seen_files:
            return
        self.seen_files.add(basename)

        try:
            with open(fpath, 'r', encoding='utf-8') as f:
                data = json.load(f)
        except Exception as e:
            self.stats['errors'] += 1
            return

        conn = get_db()
        try:
            if msg_type == '0000000a':
                battles = parse_battle_0a(data, fpath)
                for b in battles:
                    if upsert_battle_0a(conn, b):
                        self.stats['battles'] += 1
                        push_event('battle', {
                            'battle_id': b['battle_id'],
                            'atk_name': b['atk_name'],
                            'atk_uid': b['atk_uid'],
                            'result': b['result'],
                            'result_desc': b['result_desc'],
                            'time_str': b['time_str'],
                            'fight_type': b['fight_type'],
                            'fight_type_name': FIGHT_TYPE_MAP.get(b['fight_type'], str(b['fight_type'])),
                            'wid': b['wid'],
                            'wid_code': b['wid_code'],
                            'def_name': b['def_name'],
                            'def_union': b['def_union'],
                            'def_level': b['def_level'],
                            'atk_union': b['atk_union'],
                            'atk_gongxun': b['atk_gongxun'],
                            'atk_power': b['atk_power'],
                            'is_npc': b.get('is_npc', 0),
                            'heroes': [h['hero_name'] for h in b['atk_heroes'][:3]],
                        })
                        print(f'[battle-0a] {b["atk_name"]} {b["result_desc"]} wx={b["atk_gongxun"]} vs {b["def_union"]} (id={b["battle_id"]})')

            elif msg_type == '00000834':
                msg_kind, b = parse_battle_834(data, fpath)
                if msg_kind == 'chat':
                    push_event('chat_834', b)
                    print(f'[chat-834] {b["sender"]}({b["union"]}): {b["text"][:40]}')
                    # 写入数据库
                    try:
                        conn.execute('''
                            INSERT OR IGNORE INTO chat_messages(id,sender,uid,union_name,text,time,time_str,source_file)
                            VALUES(?,?,?,?,?,?,?,?)
                        ''', (b.get('id',0), b.get('sender',''), b.get('uid',''),
                              b.get('union',''), b.get('text',''), b.get('time',0),
                              b.get('time_str',''), b.get('source_file','')))
                        conn.commit()
                    except Exception as _ce:
                        pass
                elif msg_kind == 'battle_notice' and b:
                    # 834 战报通知仅推送事件，不写入战报数据库
                    push_event('battle_notice', {
                        'battle_id': b['battle_id'],
                        'atk_name': b['atk_name'],
                        'atk_uid': b.get('atk_uid', ''),
                        'result': b['result'],
                        'result_desc': b['result_desc'],
                        'time_str': b['time_str'],
                        'fight_type': b['fight_type'],
                        'fight_type_name': FIGHT_TYPE_MAP.get(b['fight_type'], str(b['fight_type'])),
                        'wid': b['wid'],
                        'wid_code': b.get('wid_code', ''),
                        'def_union': b.get('def_union', ''),
                        'atk_gongxun': b.get('atk_gongxun', 0),
                        'atk_power': b.get('atk_power', 0),
                        'heroes': [h['hero_name'] for h in b.get('heroes', [])[:3]],
                    })
                    print(f'[battle-notice-834] {b["atk_name"]} {b["result_desc"]} wx={b.get("atk_gongxun",0)} vs {b.get("def_union","")} (id={b["battle_id"]})')

            elif msg_type == '00000067':
                pass  # 只走实时路径(_dispatch)，避免文件重复处理导致 profile_id 错误

            elif msg_type == '000001fe':
                try:
                    d = parse_player_stats_1fe(fpath)
                    if d:
                        upsert_player_stats(conn, d)
                        print(f'[player_stats] {d.get("user_name","?")} uid={d.get("userid")} city={d.get("city_count")} land={d.get("land_count")}')
                except Exception as e:
                    pass

            elif msg_type == '000013a2':
                try:
                    with open(fpath, 'r', encoding='utf-8', errors='replace') as _f:
                        txt = _f.read()
                    cells = parse_map_13a2(txt, fpath)
                    if cells:
                        upsert_map_cells(conn, cells)
                        city_cells = [c for c in cells if c['cell_type'] in (8,11,12,13,14,17)]
                        if city_cells:
                            print(f'[map] {len(cells)}格, {len(city_cells)}城池/要塞')
                except Exception as e:
                    pass

            elif msg_type == '000013a4':
                try:
                    plain_text = ''
                    try:
                        plain_txt_path = fpath.replace('_plain.json', '_plain_str.txt')
                        if plain_txt_path == fpath or not os.path.exists(plain_txt_path):
                            plain_txt_path = os.path.splitext(fpath)[0] + '_plain_str.txt'
                        if os.path.exists(plain_txt_path):
                            with open(plain_txt_path, 'r', encoding='utf-8', errors='replace') as _tf:
                                plain_text = _tf.read().strip()
                    except Exception:
                        plain_text = ''
                    parsed = parse_battle_monitor_13a4(plain_text or data)
                    if parsed and parsed.get('events'):
                        payload = build_battle_monitor_payload(conn, parsed, os.path.basename(fpath), plain_text)
                        push_event('battle_monitor_13a4', payload)
                        push_event('notification', {
                            'message': plain_text or json.dumps(data, ensure_ascii=False),
                            'source': '13a4',
                            'source_file': os.path.basename(fpath),
                        })
                        print(f'[battle_monitor_13a4] teams={len(parsed.get("team_ids", []))} marker={parsed.get("marker", 0)}')
                except Exception as e:
                    print(f'[battle_monitor_13a4 ERR] {e}')

            elif msg_type == '00015f95':
                evts = parse_db_sync(data, fpath)
                if evts:
                    upsert_db_sync(conn, evts, msg_type)
                    self.stats['db_sync'] += len(evts)
                    tables = list({e['table_name'] for e in evts})
                    push_event('db_sync', {'tables': tables, 'count': len(evts)})
                    print(f'[db_sync] {len(evts)} ops: {tables}')

            elif msg_type == '000018aa':
                try:
                    events = parse_battle_field_18aa(data, fpath)
                    if events:
                        # 确保表存在
                        conn.execute('''
                            CREATE TABLE IF NOT EXISTS battle_field (
                                wid INTEGER PRIMARY KEY,
                                attacker_uid INTEGER,
                                nearby_uids TEXT,
                                nearby_count INTEGER DEFAULT 0,
                                cap_time INTEGER,
                                captured_at TEXT
                            )
                        ''')
                        upsert_battle_field(conn, events)
                        # 关联同盟成员名字，推送事件
                        uids = set()
                        for e in events:
                            uids.add(e['attacker_uid'])
                            uids.update(e['nearby_uids'])
                        name_map = {}
                        if uids:
                            placeholders = ','.join('?' * len(uids))
                            rows = conn.execute(
                                f'SELECT uid,name FROM team_users WHERE uid IN ({placeholders})',
                                list(uids)
                            ).fetchall()
                            name_map = {r[0]: r[1] for r in rows}
                        for e in events:
                            atk_name = name_map.get(e['attacker_uid'], f'uid:{e["attacker_uid"]}')
                            nearby_names = [name_map.get(u, str(u)) for u in e['nearby_uids'][:5]]
                            push_event('battle_field', {
                                'wid': e['wid'],
                                'attacker_uid': e['attacker_uid'],
                                'attacker_name': atk_name,
                                'nearby_count': e['nearby_count'],
                                'nearby_names': nearby_names,
                            })
                        print(f'[battle_field] {len(events)}个城池有战场动态')
                except Exception as e:
                    print(f'[battle_field ERR] {e}')

            elif msg_type == '000018ae':
                try:
                    city_id, members = parse_battle_queue_18ae(data, fpath)
                    if members:
                        upsert_battle_queue(conn, city_id, members)
                        push_event('battle_queue', {
                            'city_id': city_id,
                            'count': len(members),
                        })
                        print(f'[battle_queue] city={city_id} 共{len(members)}条队列记录')
                except Exception as e:
                    print(f'[battle_queue ERR] {e}')

            elif msg_type == '0000012d':
                try:
                    march = parse_march_12d(data, fpath)
                    if march and march['troops']:
                        print(f'[march] wid={march["wid"]} dist={march["dist"]} troops={len(march["troops"])}')
                except Exception as e:
                    pass

            elif msg_type == '000002bc':
                try:
                    unions = parse_union_list_2bc(data, fpath)
                    if unions:
                        upsert_union_list(conn, unions)
                        push_event('union_list', {'count': len(unions), 'sample': [u['name'] for u in unions[:5]]})
                        print(f'[union_list] {len(unions)}个联盟')
                    players = parse_player_power_rank_2bc(data, fpath)
                    if players:
                        upsert_player_power_rank(conn, players)
                        push_event('player_power_rank', {'count': len(players), 'sample': [p['name'] for p in players[:5]]})
                        print(f'[player_power_rank] {len(players)}名玩家')
                except Exception as e:
                    print(f'[000002bc ERR] {e}')

            elif msg_type == '0000030c':
                try:
                    notices = parse_announcements_30c(data, fpath)
                    if notices:
                        upsert_announcements(conn, notices)
                        push_event('announcement', {'count': len(notices), 'title': notices[0].get('title','')})
                        print(f'[announcement] {len(notices)}条公告')
                except Exception as e:
                    print(f'[announcement ERR] {e}')

            elif msg_type == '0000029f':
                try:
                    unlocks = parse_hero_unlock_29f(data, fpath)
                    if unlocks:
                        upsert_hero_unlock_log(conn, unlocks)
                        print(f'[hero_unlock] {len(unlocks)}条武将解锁记录')
                except Exception as e:
                    print(f'[hero_unlock ERR] {e}')

            elif msg_type == '00000015':
                try:
                    ps = parse_player_self_15(data, fpath)
                    if ps:
                        upsert_player_self(conn, ps)
                        push_event('player_self', {'name': ps.get('name',''), 'force': ps.get('force',0)})
                        print(f'[player_self] name={ps.get("name","?")} force={ps.get("force",0)}')
                except Exception as e:
                    print(f'[player_self ERR] {e}')

            elif msg_type == '00001863':
                try:
                    players = parse_zone_players_1863(data, fpath)
                    if players:
                        upsert_zone_players(conn, players)
                        push_event('zone_players', {'count': len(players)})
                        print(f'[zone_players] {len(players)}名战区玩家')
                except Exception as e:
                    print(f'[zone_players ERR] {e}')

            elif msg_type == '0000005c':
                # 照搬 stzbHelper parseReport：格式 [[battle_obj,...], ...]
                # 每个元素是数组，v[0] 才是战报 dict
                try:
                    battles = parse_battle_5c(data, fpath)
                    for b in battles:
                        if upsert_battle_0a(conn, b):
                            self.stats['battles'] += 1
                            push_event('battle', {
                                'battle_id': b['battle_id'],
                                'atk_name': b['atk_name'],
                                'atk_uid': b['atk_uid'],
                                'result': b['result'],
                                'result_desc': b['result_desc'],
                                'time_str': b['time_str'],
                                'fight_type': b['fight_type'],
                                'fight_type_name': FIGHT_TYPE_MAP.get(b['fight_type'], str(b['fight_type'])),
                                'wid': b['wid'],
                                'wid_code': b['wid_code'],
                                'def_name': b['def_name'],
                                'def_union': b['def_union'],
                                'def_level': b['def_level'],
                                'atk_union': b['atk_union'],
                                'atk_gongxun': b['atk_gongxun'],
                                'atk_power': b['atk_power'],
                                'heroes': [h['hero_name'] for h in b['atk_heroes'][:3]],
                            })
                            print(f'[battle-5c] {b["atk_name"]} {b["result_desc"]} wx={b["atk_gongxun"]} vs {b["def_union"]} (id={b["battle_id"]})')
                except Exception as e:
                    print(f'[battle-5c ERR] {e}')

        except Exception as e:
            self.stats['errors'] += 1
            print(f'[ERR] {fpath}: {e}')
        finally:
            conn.close()

    def process_data(self, msg_type, data, fpath=''):
        """直接处理已解析的数据，供 scrapy_v2 实时调用"""
        conn = get_db()
        try:
            self._dispatch(conn, msg_type, data, fpath or f'live:{msg_type}')
        except Exception as e:
            self.stats['errors'] += 1
            print(f'[ERR live] {msg_type}: {e}')
        finally:
            conn.close()

    def _dispatch(self, conn, msg_type, data, fpath):
        if msg_type in {'000013a2', '000013a4', '5026', '5028'}:
            try:
                world_event = persist_world_scene_packet(
                    conn, msg_type, data, fpath, self.world_scene_assembler
                )
                if world_event:
                    push_event(
                        'world_snapshot_complete'
                        if world_event.get('snapshot_complete')
                        else 'world_scene_delta',
                        world_event,
                    )
            except Exception as e:
                print(f'[world_scene ERR] {msg_type}: {e}')

        if msg_type == '0000000a':
            battles = parse_battle_0a(data, fpath)
            for b in battles:
                if upsert_battle_0a(conn, b):
                    self.stats['battles'] += 1
                    push_event('battle', {
                        'battle_id': b['battle_id'], 'atk_name': b['atk_name'],
                        'atk_uid': b['atk_uid'], 'result': b['result'],
                        'result_desc': b['result_desc'], 'time_str': b['time_str'],
                        'fight_type': b['fight_type'],
                        'fight_type_name': FIGHT_TYPE_MAP.get(b['fight_type'], str(b['fight_type'])),
                        'wid': b['wid'], 'wid_code': b['wid_code'],
                        'def_name': b['def_name'], 'def_union': b['def_union'],
                        'def_level': b['def_level'], 'atk_union': b['atk_union'],
                        'atk_gongxun': b['atk_gongxun'], 'atk_power': b['atk_power'],
                        'is_npc': b.get('is_npc', 0),
                        'heroes': [h['hero_name'] for h in b['atk_heroes'][:3]],
                    })
                    print(f'[battle-0a] {b["atk_name"]} {b["result_desc"]} wx={b["atk_gongxun"]} vs {b["def_union"]} (id={b["battle_id"]})')

        elif msg_type == '0000005c':
            try:
                battles = parse_battle_5c(data, fpath)
                for b in battles:
                    if upsert_battle_0a(conn, b):
                        self.stats['battles'] += 1
                        push_event('battle', {
                            'battle_id': b['battle_id'],
                            'atk_name': b['atk_name'],
                            'atk_uid': b['atk_uid'],
                            'result': b['result'],
                            'result_desc': b['result_desc'],
                            'time_str': b['time_str'],
                            'fight_type': b['fight_type'],
                            'fight_type_name': FIGHT_TYPE_MAP.get(b['fight_type'], str(b['fight_type'])),
                            'wid': b['wid'],
                            'wid_code': b['wid_code'],
                            'def_name': b['def_name'],
                            'def_union': b['def_union'],
                            'def_level': b['def_level'],
                            'atk_union': b['atk_union'],
                            'atk_gongxun': b['atk_gongxun'],
                            'atk_power': b['atk_power'],
                            'heroes': [h['hero_name'] for h in b['atk_heroes'][:3]],
                        })
                        print(f'[battle-5c] {b["atk_name"]} {b["result_desc"]} wx={b["atk_gongxun"]} vs {b["def_union"]} (id={b["battle_id"]})')
            except Exception as e:
                print(f'[battle-5c ERR] {e}')

        elif msg_type == '00000067':
            try:
                users = parse_team_users_67(fpath, data=data)
                if users:
                    try:
                        import profile_manager as _pm
                        _pid = _pm.get_current_profile_id()
                    except:
                        _pid = ''
                    upsert_team_users(conn, users, profile_id=_pid)
                    print(f'[team_users] {len(users)}人同盟成员已更新 profile={_pid}')
            except Exception as e:
                pass

        elif msg_type == '000001fe':
            try:
                d = parse_player_stats_1fe(fpath, data=data)
                if d:
                    upsert_player_stats(conn, d)
                    print(f'[player_stats] {d.get("user_name","?")} uid={d.get("userid")}')
            except Exception:
                pass

        elif msg_type == '000013a2':
            try:
                if isinstance(data, str):
                    txt = data
                else:
                    txt = json.dumps(data, ensure_ascii=False)
                cells = parse_map_13a2(txt, fpath)
                if cells:
                    upsert_map_cells(conn, cells)
                    city_cells = [c for c in cells if c['cell_type'] in (8,11,12,13,14,17)]
                    if city_cells:
                        print(f'[map] {len(cells)}格, {len(city_cells)}城池/要塞')
            except Exception:
                pass

        elif msg_type == '000013a4':
            try:
                plain_text = data if isinstance(data, str) else json.dumps(data, ensure_ascii=False)
                parsed = parse_battle_monitor_13a4(plain_text or data)
                if parsed and parsed.get('events'):
                    payload = build_battle_monitor_payload(conn, parsed, os.path.basename(fpath), plain_text)
                    push_event('battle_monitor_13a4', payload)
                    push_event('notification', {
                        'message': plain_text or json.dumps(data, ensure_ascii=False),
                        'source': '13a4',
                        'source_file': os.path.basename(fpath),
                    })
                    print(f'[battle_monitor_13a4] teams={len(parsed.get("team_ids", []))} marker={parsed.get("marker", 0)}')
            except Exception as e:
                print(f'[battle_monitor_13a4 ERR] {e}')

    def scan_once(self):
        # 如果禁用了文件扫描（scrapy_v2 实时推入模式），直接跳过
        if getattr(self, '_scan_disabled', False):
            return
        # 每次 scan 前检查是否切换了账号/目录
        _get_writer_db_path()
        global _cap_dir_changed
        if _cap_dir_changed:
            _cap_dir_changed = False
            # 切换账号时，把新目录下已有文件全部标记为已处理，只处理之后的新文件
            try:
                new_cap_dir = _writer_cap_dir
                for dirpath, dirnames, filenames in os.walk(new_cap_dir):
                    for fn in filenames:
                        if fn.endswith('.json'):
                            self.seen_files.add(fn)
                print(f'[writer] 账号切换，已知文件 {len(self.seen_files)} 个，只处理新增文件')
            except:
                pass
        cap_dir = _writer_cap_dir
        watched = {
            '0000000a': os.path.join(cap_dir, '0000000a'),
            '0000005c': os.path.join(cap_dir, '0000005c'),  # 同盟战报 cmdId=92
            '00000834': os.path.join(cap_dir, '00000834'),
            '00000067': os.path.join(cap_dir, '00000067'),
            '000001fe': os.path.join(cap_dir, '000001fe'),
            '000013a2': os.path.join(cap_dir, '000013a2'),
            '00015f95': os.path.join(cap_dir, '00015f95'),
            # '00000898': 已废弃，notify 数据无用
            '0000030c': os.path.join(cap_dir, '0000030c'),
            '000013a4': os.path.join(cap_dir, '000013a4'),
            '000018aa': os.path.join(cap_dir, '000018aa'),
            '000018ae': os.path.join(cap_dir, '000018ae'),
            '0000012d': os.path.join(cap_dir, '0000012d'),
            '000002bc': os.path.join(cap_dir, '000002bc'),
            '0000029f': os.path.join(cap_dir, '0000029f'),
            '00000015': os.path.join(cap_dir, '00000015'),
            '00001863': os.path.join(cap_dir, '00001863'),
        }
        for msg_type, dirpath in watched.items():
            if not os.path.isdir(dirpath):
                continue
            for fn in os.listdir(dirpath):
                if not fn.endswith('.json'):
                    continue
                fpath = os.path.join(dirpath, fn)
                self.process_file(fpath, msg_type)

    def run(self):
        print(f'[writer] 启动，监控目录: {CAP_DIR}')
        while True:
            try:
                self.scan_once()
            except Exception as e:
                print(f'[writer ERR] {e}')
            time.sleep(POLL_SECS)


def start_writer_thread():
    """在后台线程启动 writer"""
    w = RealtimeWriter()
    t = threading.Thread(target=w.run, daemon=True, name='realtime-writer')
    t.start()
    return w


_global_writer = None

def get_writer_instance():
    """返回全局 writer 实例，供 scrapy_v2 直接调用"""
    global _global_writer
    if _global_writer is None:
        _global_writer = RealtimeWriter()
    return _global_writer


if __name__ == '__main__':
    w = RealtimeWriter()
    w.run()
