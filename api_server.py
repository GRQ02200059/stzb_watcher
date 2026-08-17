# -*- coding: utf-8 -*-
"""
率土之滨 REST API 服务器
提供所有统计数据接口，前端通过 fetch('/api/xxx') 访问
"""
from flask import Flask, jsonify, request, Response, stream_with_context
from flask_cors import CORS
import sqlite3, json, os, time, threading, ast, sys, hmac
from datetime import datetime
from pathlib import Path
from realtime_writer import (start_writer_thread, event_queue, recent_events, _event_lock,
                             subscribe, unsubscribe, push_event,
                             parse_battle_monitor_13a4, build_battle_monitor_payload)
import profile_manager
from battle_engine_adapter import BattleEngineAdapter
from intelligence.config_api import register_intelligence_config_api
from intelligence.config_repository import IntelligenceConfigRepository
from intelligence.live_army_api import register_live_army_api
from intelligence.lineup_api import register_intelligence_lineup_api
from intelligence.research_api import register_intelligence_research_api
from intelligence.world_api import register_world_intelligence_api
from query_agent.api import register_query_agent_api
from score_center.api import register_score_center_api
from score_center.repository import ScoreRepository
from world_scene.api import register_world_scene_api
from world_scene.store import WorldSceneStore

import os, time, threading

APP_DIR      = os.path.dirname(sys.executable) if getattr(sys, 'frozen', False) else os.path.dirname(os.path.abspath(__file__))
RESOURCE_DIR = getattr(sys, '_MEIPASS', APP_DIR)
BASE_DIR     = APP_DIR
DEFAULT_DB   = os.path.join(APP_DIR, 'stzb.db')
PROFILE_FILE = os.path.join(APP_DIR, 'current_profile.json')
REF_SCHEMA_DB = os.path.join(APP_DIR, 'stzb_42.186.96.143.db')
REGION_NAMES = {
    1: '司隶', 2: '雍州', 3: '兖州', 4: '豫州', 5: '冀州', 6: '青州',
    7: '徐州', 8: '凉州', 9: '并州', 10: '扬州', 11: '益州', 12: '幽州', 13: '荆州'
}


def _local_now():
    return datetime.now()


def _local_day_start_timestamp(value=None):
    current = value or _local_now()
    return int(current.replace(hour=0, minute=0, second=0, microsecond=0).timestamp())


def _asset_version(asset_name):
    path = os.path.join(RESOURCE_DIR, 'static', asset_name)
    try:
        return str(int(os.path.getmtime(path)))
    except OSError:
        return '0'

_current_db_path = DEFAULT_DB
_db_lock         = threading.Lock()
_initialized_dbs = set()
_table_info_cache = {}

REQUIRED_BV2_COLUMNS = {
    'wid_name': 'TEXT DEFAULT ""',
    'is_npc': 'INTEGER DEFAULT 0',
    'is_ai': 'INTEGER DEFAULT 0',
    'weather': 'INTEGER DEFAULT 0',
    'in_night': 'INTEGER DEFAULT 0',
    'in_night_mode': 'INTEGER DEFAULT 0',
    'garrison': 'INTEGER DEFAULT 0',
    'atk_unionid': 'INTEGER DEFAULT 0',
    'def_unionid': 'INTEGER DEFAULT 0',
    'atk_hp': 'INTEGER DEFAULT 0',
    'def_hp': 'INTEGER DEFAULT 0',
    'attack_hp': 'INTEGER DEFAULT 0',
    'defend_hp': 'INTEGER DEFAULT 0',
    'all_skill_info': 'TEXT DEFAULT ""',
    'atk_advance': 'TEXT DEFAULT ""',
    'def_advance': 'TEXT DEFAULT ""',
    'attack_advance': 'TEXT DEFAULT ""',
    'defend_advance': 'TEXT DEFAULT ""',
    'atk_gear_info': 'TEXT DEFAULT ""',
    'def_gear_info': 'TEXT DEFAULT ""',
    'attacker_gear_info': 'TEXT DEFAULT ""',
    'defender_gear_info': 'TEXT DEFAULT ""',
    'attack_all_hero_info': 'TEXT DEFAULT ""',
    'defend_all_hero_info': 'TEXT DEFAULT ""',
    'attack_all_sub_hero_info': 'TEXT DEFAULT ""',
    'defend_all_sub_hero_info': 'TEXT DEFAULT ""',
    'atk_hero_type': 'TEXT DEFAULT ""',
    'def_hero_type': 'TEXT DEFAULT ""',
    'attack_hero_type': 'TEXT DEFAULT ""',
    'defend_hero_type': 'TEXT DEFAULT ""',
    'attack_clan_name': 'TEXT DEFAULT ""',
    'defend_clan_name': 'TEXT DEFAULT ""',
    'atk_clan_name': 'TEXT DEFAULT ""',
    'def_clan_name': 'TEXT DEFAULT ""',
    'atk_team_id': 'INTEGER DEFAULT 0',
    'def_team_id': 'INTEGER DEFAULT 0',
    'atk_hero1_id': 'INTEGER DEFAULT 0',
    'atk_hero2_id': 'INTEGER DEFAULT 0',
    'atk_hero3_id': 'INTEGER DEFAULT 0',
    'def_hero1_id': 'INTEGER DEFAULT 0',
    'def_hero2_id': 'INTEGER DEFAULT 0',
    'def_hero3_id': 'INTEGER DEFAULT 0',
    'atk_hero1_level': 'INTEGER DEFAULT 0',
    'atk_hero2_level': 'INTEGER DEFAULT 0',
    'atk_hero3_level': 'INTEGER DEFAULT 0',
    'def_hero1_level': 'INTEGER DEFAULT 0',
    'def_hero2_level': 'INTEGER DEFAULT 0',
    'def_hero3_level': 'INTEGER DEFAULT 0',
    'atk_hero1_star': 'INTEGER DEFAULT 0',
    'atk_hero2_star': 'INTEGER DEFAULT 0',
    'atk_hero3_star': 'INTEGER DEFAULT 0',
    'def_hero1_star': 'INTEGER DEFAULT 0',
    'def_hero2_star': 'INTEGER DEFAULT 0',
    'def_hero3_star': 'INTEGER DEFAULT 0',
}


def _ensure_battles_v2_columns(conn):
    """补齐 battles_v2 常用列，避免新库打开页面时报 no such column。"""
    try:
        existing = {r[1] for r in conn.execute('PRAGMA table_info(battles_v2)').fetchall()}
    except Exception:
        existing = set()
    if not existing:
        return
    changed = False
    for cname, cdef in REQUIRED_BV2_COLUMNS.items():
        if cname in existing:
            continue
        try:
            conn.execute(f'ALTER TABLE battles_v2 ADD COLUMN {cname} {cdef}')
            changed = True
        except Exception as e:
            print(f'[init] add battles_v2.{cname} failed: {e}')
    if changed:
        conn.commit()


def _ensure_task_tables(conn):
    conn.execute('''
        CREATE TABLE IF NOT EXISTS tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            status INTEGER DEFAULT 0,
            name TEXT NOT NULL,
            time INTEGER NOT NULL,
            pos TEXT NOT NULL,
            target_groups TEXT DEFAULT "[]",
            target_user_num INTEGER DEFAULT 0,
            complete_user_num INTEGER DEFAULT 0,
            user_list TEXT DEFAULT "{}",
            profile_id TEXT DEFAULT '',
            created_at TEXT DEFAULT (datetime('now','localtime'))
        )
    ''')
    conn.execute('''
        CREATE TABLE IF NOT EXISTS task_reports (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id INTEGER NOT NULL,
            battle_id INTEGER,
            atk_name TEXT,
            def_name TEXT,
            wid TEXT,
            garrison INTEGER DEFAULT 0,
            atk_base_heroid INTEGER DEFAULT 0,
            time INTEGER,
            raw TEXT,
            UNIQUE(battle_id)
        )
    ''')
    conn.commit()


def _table_exists(conn, table_name):
    row = conn.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
        (table_name,),
    ).fetchone()
    return bool(row)


def _ensure_state_region_tables(conn):
    conn.execute('''
        CREATE TABLE IF NOT EXISTS union_list (
            union_id INTEGER PRIMARY KEY,
            name TEXT,
            level INTEGER DEFAULT 0,
            power INTEGER DEFAULT 0,
            force INTEGER DEFAULT 0,
            total_member INTEGER DEFAULT 0,
            occupy_city_value INTEGER DEFAULT 0,
            total_npc_city INTEGER DEFAULT 0,
            region INTEGER DEFAULT 0,
            area INTEGER DEFAULT 0,
            rank INTEGER DEFAULT 0,
            refresh_time INTEGER DEFAULT 0,
            updated_at TEXT
        )
    ''')
    conn.execute('''
        CREATE TABLE IF NOT EXISTS player_power_rank (
            user_id INTEGER PRIMARY KEY,
            role_id TEXT,
            name TEXT,
            power INTEGER DEFAULT 0,
            force INTEGER DEFAULT 0,
            area INTEGER DEFAULT 0,
            region INTEGER DEFAULT 0,
            land_count INTEGER DEFAULT 0,
            fort_count INTEGER DEFAULT 0,
            branch_city_count INTEGER DEFAULT 0,
            shu_cheng_count INTEGER DEFAULT 0,
            refresh_time INTEGER DEFAULT 0,
            rank INTEGER DEFAULT 0,
            updated_at TEXT
        )
    ''')
    conn.execute('''
        CREATE TABLE IF NOT EXISTS zone_players (
            uid INTEGER PRIMARY KEY,
            role_id TEXT,
            name TEXT,
            power INTEGER DEFAULT 0,
            wid INTEGER DEFAULT 0,
            pos_type INTEGER DEFAULT 0,
            last_active INTEGER DEFAULT 0,
            join_time INTEGER DEFAULT 0,
            union_id INTEGER DEFAULT 0,
            updated_at TEXT
        )
    ''')
    conn.commit()


def _load_profile():
    """读取 current_profile.json，返回当前 db_path"""
    try:
        if os.path.exists(PROFILE_FILE):
            import json as _json
            with open(PROFILE_FILE, 'r', encoding='utf-8') as f:
                p = _json.load(f)
            return p.get('db_path', DEFAULT_DB)
    except:
        pass
    return DEFAULT_DB


def _profile_watcher():
    """后台线程：监控 profile 文件变化，自动切换 DB"""
    global _current_db_path
    last_mtime = 0
    while True:
        try:
            if os.path.exists(PROFILE_FILE):
                mtime = os.path.getmtime(PROFILE_FILE)
                if mtime != last_mtime:
                    last_mtime = mtime
                    new_db = _load_profile()
                    with _db_lock:
                        if new_db != _current_db_path:
                            _current_db_path = new_db
                            print(f'[profile] 切换数据库: {new_db}')
                    try:
                        abs_db_path = os.path.abspath(new_db)
                        init_fn = globals().get('ensure_all_tables')
                        if init_fn and abs_db_path not in _initialized_dbs:
                            init_fn(new_db)
                            _initialized_dbs.add(abs_db_path)
                    except Exception as e:
                        print(f'[profile] 自动建表失败: {e}')
        except:
            pass
        time.sleep(2)


# 初始化当前 DB
_current_db_path = _load_profile()
app = Flask(__name__, static_folder=os.path.join(RESOURCE_DIR, 'static'), static_url_path='/static')
CORS(app)

_PROTECTED_WRITE_ENDPOINTS = {
    'api_switch_profile',
    'api_refresh',
    'api_profile_switch',
    'api_schedule_generate',
    'score_center_create_rule',
    'score_center_activate_rule',
    'score_center_add_adjustment',
    'score_center_delete_adjustment',
    'score_center_recalc',
    'api_task_create',
    'api_task_delete',
    'api_task_statistics',
    'api_task_clear_reports',
}


@app.before_request
def require_optional_api_token():
    expected = os.environ.get('STZB_API_TOKEN', '')
    if not expected or request.endpoint not in _PROTECTED_WRITE_ENDPOINTS:
        return None
    supplied = request.headers.get('X-STZB-Token', '')
    authorization = request.headers.get('Authorization', '')
    if not supplied and authorization.lower().startswith('bearer '):
        supplied = authorization[7:].strip()
    if supplied and hmac.compare_digest(supplied, expected):
        return None
    return jsonify({
        'ok': False,
        'error': 'unauthorized',
        'message': '写操作需要有效的 STZB API Token',
    }), 401


class _IdleWriter:
    def __init__(self):
        self.stats = {
            'battles': 0,
            'db_sync': 0,
            'notifications': 0,
            'errors': 0,
        }


_writer = _IdleWriter()
_watcher_thread = None
_runtime_started = False
_runtime_start_lock = threading.Lock()

def _sync_schema_from_reference(db_path, ref_db_path):
    """把 db_path 的表结构补齐到 ref_db_path（只补表/补列，不删不改已有列）"""
    if not ref_db_path or not os.path.exists(ref_db_path):
        return
    if os.path.abspath(db_path) == os.path.abspath(ref_db_path):
        return

    tgt = sqlite3.connect(db_path)
    ref = sqlite3.connect(ref_db_path)
    try:
        tgt_tables = {r[0] for r in tgt.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")}
        ref_rows = ref.execute("SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").fetchall()

        for tname, create_sql in ref_rows:
            if not tname or not create_sql:
                continue

            # 目标库没有该表：直接按参考库建表
            if tname not in tgt_tables:
                try:
                    tgt.execute(create_sql)
                    tgt_tables.add(tname)
                except Exception as e:
                    print(f'[schema-sync] create table {tname} failed: {e}')
                    continue

            # 目标库有该表：补缺失列
            try:
                tgt_cols = {r[1] for r in tgt.execute(f'PRAGMA table_info({tname})').fetchall()}
                ref_cols = ref.execute(f'PRAGMA table_info({tname})').fetchall()
                for c in ref_cols:
                    # PRAGMA table_info: cid, name, type, notnull, dflt_value, pk
                    cname = c[1]
                    ctype = c[2] or 'TEXT'
                    cnotnull = c[3]
                    cdflt = c[4]
                    if cname in tgt_cols:
                        continue
                    alter = f'ALTER TABLE {tname} ADD COLUMN {cname} {ctype}'
                    if cdflt is not None:
                        alter += f' DEFAULT {cdflt}'
                    elif cnotnull:
                        # SQLite 对已有数据表新增 NOT NULL 列需默认值；无默认时降级为可空
                        pass
                    try:
                        tgt.execute(alter)
                    except Exception as e:
                        print(f'[schema-sync] add column {tname}.{cname} failed: {e}')
            except Exception as e:
                print(f'[schema-sync] sync columns for {tname} failed: {e}')

        tgt.commit()
    finally:
        ref.close()
        tgt.close()


def ensure_all_tables(db_path):
    """对指定数据库执行所有建表操作，幂等安全"""
    abs_db_path = os.path.abspath(db_path)
    conn = sqlite3.connect(db_path)
    conn.execute('PRAGMA journal_mode=WAL')
    try:
        # 1. db_build 基础表
        from db_build import create_tables as _ct
        _ct(conn)
    except Exception as e:
        print(f'[init] db_build create_tables: {e}')
    try:
        # 2. db_schema_v2 扩展表
        import db_schema_v2 as _sv2
        old_db = _sv2.DB_PATH
        _sv2.DB_PATH = db_path
        _sv2.migrate()
        _sv2.DB_PATH = old_db
    except Exception as e:
        print(f'[init] db_schema_v2 migrate: {e}')
    try:
        # 3. team_users 表
        conn.execute('''
            CREATE TABLE IF NOT EXISTS team_users (
                uid INTEGER NOT NULL,
                profile_id TEXT NOT NULL DEFAULT \'\',
                name TEXT,
                contribute_total INTEGER DEFAULT 0,
                contribute_week INTEGER DEFAULT 0,
                pos INTEGER DEFAULT 0,
                power INTEGER DEFAULT 0,
                wuxun INTEGER DEFAULT 0,
                group_name TEXT DEFAULT \'\',
                join_time INTEGER DEFAULT 0,
                wid INTEGER DEFAULT 0,
                hero_config_id INTEGER DEFAULT 0,
                hero_skills TEXT DEFAULT \'\',
                account_id TEXT DEFAULT \'\',
                updated_at TEXT,
                PRIMARY KEY (uid, profile_id)
            )
        ''')
        conn.commit()
    except Exception as e:
        print(f'[init] team_users: {e}')
    try:
        # 4. chat_messages 表
        conn.execute('''
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY,
                sender TEXT, uid TEXT, union_name TEXT,
                text TEXT, time INTEGER, time_str TEXT, source_file TEXT
            )
        ''')
        conn.commit()
    except Exception as e:
        print(f'[init] chat_messages: {e}')
    try:
        # 5. player_self 表
        conn.execute('''
            CREATE TABLE IF NOT EXISTS player_self (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id TEXT,
                uid TEXT, name TEXT, union_name TEXT,
                level INTEGER DEFAULT 0,
                power INTEGER DEFAULT 0,
                wuxun INTEGER DEFAULT 0,
                updated_at TEXT
            )
        ''')
        conn.commit()
    except Exception as e:
        print(f'[init] player_self: {e}')
    try:
        # 6. 州郡/同盟统计依赖表
        _ensure_state_region_tables(conn)
    except Exception as e:
        print(f'[init] state region tables: {e}')
    try:
        # 7. 以 42 库为参考补齐缺失表/列（只补不删，兼容旧库）
        _sync_schema_from_reference(db_path, REF_SCHEMA_DB)
    except Exception as e:
        print(f'[init] schema sync from ref db failed: {e}')
    try:
        # 8. 打包版也能自给自足地补齐 battles_v2 常用列
        _ensure_battles_v2_columns(conn)
    except Exception as e:
        print(f'[init] ensure battles_v2 columns: {e}')
    try:
        # 9. 任务相关表需要对当前库初始化，而不是只初始化默认库
        _ensure_task_tables(conn)
    except Exception as e:
        print(f'[init] ensure task tables: {e}')
    try:
        # 10. 可配置赛季积分中心
        ScoreRepository(conn).ensure_schema()
    except Exception as e:
        print(f'[init] ensure score center schema: {e}')
    _table_info_cache.pop(abs_db_path, None)
    conn.close()
    print(f'[init] 全部表初始化完成: {db_path}')


def start_runtime_services(start_writer=True):
    """显式启动建表、档案监控和实时入库；模块导入阶段不产生后台副作用。"""
    global _runtime_started, _watcher_thread, _writer
    with _runtime_start_lock:
        if _runtime_started:
            return
        try:
            ensure_all_tables(_current_db_path)
            _initialized_dbs.add(os.path.abspath(_current_db_path))
        except Exception as e:
            print(f'[init] startup ensure_all_tables failed: {e}')

        _watcher_thread = threading.Thread(
            target=_profile_watcher,
            daemon=True,
            name='profile-watcher',
        )
        _watcher_thread.start()
        if start_writer:
            _writer = start_writer_thread()
        _runtime_started = True


def get_db():
    with _db_lock:
        db_path = _current_db_path
    abs_db_path = os.path.abspath(db_path)
    if abs_db_path not in _initialized_dbs:
        with _db_lock:
            if abs_db_path not in _initialized_dbs:
                ensure_all_tables(db_path)
                _initialized_dbs.add(abs_db_path)
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    return conn


def _world_scene_connection():
    conn = get_db()
    WorldSceneStore(conn).ensure_schema()
    return conn


register_world_scene_api(app, _world_scene_connection)
register_world_intelligence_api(app, _world_scene_connection)
register_live_army_api(app, _world_scene_connection)
_intelligence_snapshot_root = os.path.join(
    BASE_DIR, 'data', 'intelligence', 'client-9.2.2'
)
_intelligence_config_repository = IntelligenceConfigRepository(
    _intelligence_snapshot_root
)
register_intelligence_config_api(
    app,
    _intelligence_snapshot_root,
    repository=_intelligence_config_repository,
)
_intelligence_research_root = os.path.join(
    _intelligence_snapshot_root, 'research'
)
_intelligence_research_repository = register_intelligence_research_api(
    app,
    _intelligence_research_root,
    config_repository=_intelligence_config_repository,
)
register_intelligence_lineup_api(
    app,
    get_db,
    _intelligence_config_repository,
)
register_query_agent_api(
    app,
    get_db,
    intelligence_root=_intelligence_snapshot_root,
    research_repository=_intelligence_research_repository,
)
register_score_center_api(app, get_db)

def get_bv2_cols(conn):
    """缓存 battles_v2 列信息，避免接口里频繁 PRAGMA table_info。"""
    with _db_lock:
        db_path = _current_db_path
    abs_db_path = os.path.abspath(db_path)
    cols = _table_info_cache.get(abs_db_path)
    if cols is not None:
        return cols
    cols = {r[1] for r in conn.execute("PRAGMA table_info(battles_v2)").fetchall()}
    _table_info_cache[abs_db_path] = cols
    return cols

def get_current_pid() -> str:
    """获取当前激活账号的 profile_id"""
    try:
        return profile_manager.get_current_profile_id()
    except:
        return ''

def rows_to_list(rows):
    return [dict(r) for r in rows]


def _safe_int(v, default=0):
    try:
        return int(v)
    except:
        return default


def _format_wid_xy(wid):
    wid_i = _safe_int(wid, 0)
    if wid_i <= 0:
        return ''
    x = wid_i // 10000
    y = wid_i % 10000
    return f'{x},{y}'


def _parse_13a2_payload(packet_text):
    try:
        raw_text = (packet_text or '').replace('\x00', '').strip()
        raw_text = raw_text.replace(':null', ':None').replace(',null', ',None').replace('[null', '[None').replace(' null', ' None')
        raw_text = raw_text.replace(':true', ':True').replace(',true', ',True').replace('[true', '[True').replace(' true', ' True')
        raw_text = raw_text.replace(':false', ':False').replace(',false', ',False').replace('[false', '[False').replace(' false', ' False')
        data = ast.literal_eval(raw_text)
    except Exception:
        return None
    if not isinstance(data, list) or len(data) < 2:
        return None

    def is_subject_dict(d):
        if not isinstance(d, dict) or not d:
            return False
        vals = list(d.values())[:3]
        return all(isinstance(v, list) and len(v) >= 2 and isinstance(v[0], str) for v in vals)

    def is_team_dict(d):
        if not isinstance(d, dict) or not d:
            return False
        vals = list(d.values())[:3]
        return all(isinstance(v, list) and len(v) >= 6 and _safe_int(v[1], 0) > 0 for v in vals)

    def is_cell_team_map_dict(d):
        if not isinstance(d, dict) or not d:
            return False
        vals = list(d.values())[:3]
        return all(isinstance(v, list) and all(_safe_int(x, 0) > 0 for x in v[:3] if x is not None) for v in vals)

    def is_cell_detail_dict(d):
        if not isinstance(d, dict) or not d:
            return False
        vals = list(d.values())[:3]
        return all(isinstance(v, dict) and 0 in v and isinstance(v.get(0), list) for v in vals)

    subject_power_map = {}
    subjects = {}
    teams_raw = {}
    cell_team_map = {}
    cell_detail_map = {}
    area_range = []
    marker = 0

    for idx, part in enumerate(data):
        if isinstance(part, dict):
            if not subject_power_map and part and all(_safe_int(k, 0) > 0 for k in list(part.keys())[:3]) and all(not isinstance(v, (list, dict)) for v in list(part.values())[:3]):
                subject_power_map = part
                continue
            if not subjects and is_subject_dict(part):
                subjects = part
                continue
            if not teams_raw and is_team_dict(part):
                teams_raw = part
                continue
            if not cell_team_map and is_cell_team_map_dict(part):
                cell_team_map = part
                continue
            if not cell_detail_map and is_cell_detail_dict(part):
                cell_detail_map = part
                continue
        elif isinstance(part, list) and len(part) == 4 and all(isinstance(x, int) for x in part):
            area_range = part
        elif marker == 0 and isinstance(part, int) and part > 1000:
            marker = part

    team_to_cells = {}
    for cell_id, team_ids in cell_team_map.items():
        if not isinstance(team_ids, list):
            continue
        for team_id in team_ids:
            tid = _safe_int(team_id, 0)
            if tid <= 0:
                continue
            team_to_cells.setdefault(tid, []).append(_safe_int(cell_id, 0))

    items = []

    if teams_raw:
        for team_id, arr in teams_raw.items():
            if not isinstance(arr, list):
                continue
            tid = _safe_int(team_id, 0)
            if tid <= 0:
                continue
            move_type = _safe_int(arr[0] if len(arr) > 0 else 0, 0)
            subject_id = _safe_int(arr[1] if len(arr) > 1 else 0, 0)
            from_wid = _safe_int(arr[2] if len(arr) > 2 else 0, 0)
            to_wid = _safe_int(arr[3] if len(arr) > 3 else 0, 0)
            start_time = _safe_int(arr[4] if len(arr) > 4 else 0, 0)
            arrive_time = _safe_int(arr[5] if len(arr) > 5 else 0, 0)
            speed = _safe_int(arr[28] if len(arr) > 28 else 0, 0)
            current_wid = _safe_int(arr[10] if len(arr) > 10 else 0, 0)
            fortress_wid = _safe_int(arr[11] if len(arr) > 11 else 0, 0)
            troop_kind = _safe_int(arr[29] if len(arr) > 29 else 0, 0)
            subject = subjects.get(subject_id, []) if isinstance(subjects, dict) else []
            owner_name = subject[0] if len(subject) > 0 else ''
            owner_uid = _safe_int(subject[1] if len(subject) > 1 else 0, 0)
            union_id = _safe_int(subject[2] if len(subject) > 2 else 0, 0)
            group_info = subject[12] if len(subject) > 12 and isinstance(subject[12], list) else []
            group_id = _safe_int(group_info[0] if len(group_info) > 0 else 0, 0)
            group_name = group_info[2] if len(group_info) > 2 else ''
            power = _safe_int(subject_power_map.get(subject_id, 0), 0)
            cells = sorted(set(team_to_cells.get(tid, [])))
            related_cell_raw = {}
            for cell_id in cells:
                if cell_id in cell_detail_map:
                    related_cell_raw[cell_id] = cell_detail_map[cell_id]
            items.append({
                'team_id': tid,
                'subject_id': subject_id,
                'owner_name': owner_name,
                'owner_uid': owner_uid,
                'union_id': union_id,
                'group_id': group_id,
                'group_name': group_name,
                'move_type': move_type,
                'move_type_text': {1:'驻守',2:'回撤',4:'调动',5:'行军',6:'停留'}.get(move_type, f'类型{move_type}' if move_type else '-'),
                'home_wid': tid // 10,
                'home_xy': _format_wid_xy(tid // 10),
                'from_wid': from_wid,
                'from_xy': _format_wid_xy(from_wid),
                'to_wid': to_wid,
                'to_xy': _format_wid_xy(to_wid),
                'current_wid': current_wid,
                'current_xy': _format_wid_xy(current_wid),
                'fortress_wid': fortress_wid,
                'fortress_xy': _format_wid_xy(fortress_wid),
                'start_time': start_time,
                'arrive_time': arrive_time,
                'speed': speed,
                'troop_kind': troop_kind,
                'power': power,
                'cells': cells,
                'cell_count': len(cells),
                'subject_raw': subject,
                'subject_raw_text': f'{subject_id}:{repr(subject)}' if subject_id else '',
                'cell_raw_map': related_cell_raw,
                'cell_raw_text': '，'.join([f'{cid}:{repr(related_cell_raw[cid])}' for cid in related_cell_raw]),
            })

    elif cell_detail_map:
        pseudo_team_id = 1
        for cell_id, detail in cell_detail_map.items():
            if not isinstance(detail, dict):
                continue
            core = detail.get(0)
            if not isinstance(core, list) or len(core) < 5:
                continue
            move_type = _safe_int(core[0] if len(core) > 0 else 0, 0)
            subject_id = _safe_int(core[2] if len(core) > 2 else 0, 0)
            occur_time = _safe_int(core[4] if len(core) > 4 else 0, 0)
            current_wid = _safe_int(cell_id, 0)
            from_wid = _safe_int(core[7] if len(core) > 7 else 0, 0)
            arrive_time = _safe_int(core[10] if len(core) > 10 else 0, 0)
            fortress_wid = _safe_int(core[11] if len(core) > 11 else 0, 0)
            speed = _safe_int(core[19] if len(core) > 19 else 0, 0)
            subject = subjects.get(subject_id, []) if isinstance(subjects, dict) else []
            owner_name = subject[0] if len(subject) > 0 else ''
            owner_uid = _safe_int(subject[1] if len(subject) > 1 else 0, 0)
            union_id = _safe_int(subject[2] if len(subject) > 2 else 0, 0)
            group_info = subject[12] if len(subject) > 12 and isinstance(subject[12], list) else []
            group_name = group_info[2] if len(group_info) > 2 else ''
            items.append({
                'team_id': pseudo_team_id,
                'subject_id': subject_id,
                'owner_name': owner_name,
                'owner_uid': owner_uid,
                'union_id': union_id,
                'group_id': _safe_int(group_info[0] if len(group_info) > 0 else 0, 0),
                'group_name': group_name,
                'move_type': move_type,
                'move_type_text': {2:'驻留',4:'调动',5:'行军',19:'城池/要塞'}.get(move_type, f'类型{move_type}' if move_type else '-'),
                'home_wid': 0,
                'home_xy': '',
                'from_wid': from_wid,
                'from_xy': _format_wid_xy(from_wid),
                'to_wid': current_wid,
                'to_xy': _format_wid_xy(current_wid),
                'current_wid': current_wid,
                'current_xy': _format_wid_xy(current_wid),
                'fortress_wid': fortress_wid,
                'fortress_xy': _format_wid_xy(fortress_wid),
                'start_time': occur_time,
                'arrive_time': arrive_time,
                'speed': speed,
                'troop_kind': 0,
                'power': _safe_int(subject_power_map.get(subject_id, 0), 0),
                'cells': [current_wid],
                'cell_count': 1,
                'subject_raw': subject,
                'subject_raw_text': f'{subject_id}:{repr(subject)}' if subject_id else '',
                'cell_raw_map': {current_wid: detail},
                'cell_raw_text': f'{current_wid}:{repr(detail)}',
            })
            pseudo_team_id += 1

    items.sort(key=lambda x: ((x['owner_name'] or ''), x['team_id']))

    cell_rows = []
    if cell_team_map:
        for cell_id, team_ids in sorted(cell_team_map.items(), key=lambda kv: _safe_int(kv[0], 0)):
            cid = _safe_int(cell_id, 0)
            tids = [_safe_int(t, 0) for t in team_ids if _safe_int(t, 0) > 0] if isinstance(team_ids, list) else []
            cell_rows.append({
                'cell_id': cid,
                'cell_xy': _format_wid_xy(cid),
                'team_ids': tids,
                'team_count': len(tids),
            })
    elif cell_detail_map:
        for cell_id in sorted(cell_detail_map.keys(), key=lambda x: _safe_int(x, 0)):
            cid = _safe_int(cell_id, 0)
            cell_rows.append({
                'cell_id': cid,
                'cell_xy': _format_wid_xy(cid),
                'team_ids': [],
                'team_count': 1,
            })

    return {
        'marker': marker,
        'area_range': area_range,
        'subjects_count': len(subjects) if isinstance(subjects, dict) else 0,
        'teams_count': len(items),
        'cells_count': len(cell_rows),
        'items': items,
        'cells': cell_rows,
        'raw': data,
    }


# ===== 账号管理 =====
@app.route('/api/profiles')
def api_profiles():
    profiles = profile_manager.get_all_profiles()
    current = profile_manager.load_current_profile()
    cur_id = current.get('profile_id', '')
    return jsonify({'profiles': profiles, 'current_id': cur_id})


@app.route('/api/switch_profile', methods=['POST'])
def api_switch_profile():
    global _current_db_path
    data = request.get_json(silent=True) or {}
    pid = data.get('profile_id', '')
    if not pid:
        return jsonify({'error': 'profile_id 不能为空'}), 400
    try:
        p = profile_manager.switch_profile(pid)
        # 立即更新当前 db 路径，不等 watcher 线程
        new_db = p.get('db_path', '')
        if new_db:
            with _db_lock:
                _current_db_path = new_db
            print(f'[profile] 切换数据库: {new_db}')
            try:
                ensure_all_tables(new_db)
                _initialized_dbs.add(os.path.abspath(new_db))
            except Exception as _te:
                print(f'[profile] 建表失败: {_te}')
        push_event('profile_changed', p)
        return jsonify({'ok': True, 'profile': p})
    except Exception as e:
        return jsonify({'error': str(e)}), 400

# ===== 联盟 =====
@app.route('/api/unions')
def api_unions():
    conn = get_db()
    rows = conn.execute('SELECT * FROM unions ORDER BY power DESC').fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

# ===== 战报 =====
@app.route('/api/battles')
def api_battles():
    page = int(request.args.get('page', 1))
    size = int(request.args.get('size', 30))
    player = request.args.get('player', '')
    union = request.args.get('union', '')
    result = request.args.get('result', '')
    offset = (page - 1) * size
    where = ['1=1']
    params = []
    if player:
        where.append('(atk_name LIKE ? OR def_name LIKE ?)')
        params += [f'%{player}%', f'%{player}%']
    if union:
        where.append('(atk_union LIKE ? OR def_union LIKE ?)')
        params += [f'%{union}%', f'%{union}%']
    if result:
        where.append('result = ?')
        params.append(int(result))
    where_str = ' AND '.join(where)
    conn = get_db()
    total = conn.execute(f'SELECT COUNT(*) FROM battles WHERE {where_str}', params).fetchone()[0]
    rows = conn.execute(f'SELECT * FROM battles WHERE {where_str} ORDER BY time DESC LIMIT ? OFFSET ?', params + [size, offset]).fetchall()
    conn.close()
    return jsonify({'total': total, 'page': page, 'size': size, 'data': rows_to_list(rows)})

@app.route('/api/battles/<int:bid>')
def api_battle_detail(bid):
    conn = get_db()
    battle = conn.execute('SELECT * FROM battles WHERE battle_id=?', (bid,)).fetchone()
    heroes = conn.execute('SELECT * FROM battle_heroes WHERE battle_id=? ORDER BY side,pos', (bid,)).fetchall()
    skills = conn.execute('SELECT * FROM battle_skills WHERE battle_id=? ORDER BY side,pos', (bid,)).fetchall()
    conn.close()
    if not battle:
        return jsonify({'error': 'not found'}), 404
    return jsonify({'battle': dict(battle), 'heroes': rows_to_list(heroes), 'skills': rows_to_list(skills)})

# ===== 战报统计 =====
@app.route('/api/battle_stats')
def api_battle_stats():
    conn = get_db()
    total = conn.execute('SELECT COUNT(*) FROM battles').fetchone()[0]
    result_dist = rows_to_list(conn.execute('SELECT result_desc, COUNT(*) as cnt FROM battles GROUP BY result_desc ORDER BY cnt DESC').fetchall())
    city_dist = rows_to_list(conn.execute('SELECT city_type, COUNT(*) as cnt FROM battles GROUP BY city_type ORDER BY cnt DESC').fetchall())
    hero_freq = rows_to_list(conn.execute('SELECT hero_name, COUNT(*) as cnt FROM battle_heroes WHERE hero_name NOT LIKE \'武将%\' GROUP BY hero_name ORDER BY cnt DESC LIMIT 50').fetchall())
    combo_freq = rows_to_list(conn.execute('''
        SELECT hero_name as combo, COUNT(*) as cnt
        FROM (
            SELECT battle_id, side,
                   (SELECT GROUP_CONCAT(h2.hero_name, '+') FROM
                    (SELECT hero_name FROM battle_heroes WHERE battle_id=bh.battle_id AND side=\'atk\' AND hero_name NOT LIKE \'武将%\' ORDER BY pos) h2
                   ) as hero_name
            FROM battle_heroes bh WHERE side=\'atk\' AND hero_name NOT LIKE \'武将%\'
            GROUP BY battle_id, side HAVING COUNT(*) >= 2
        ) WHERE hero_name IS NOT NULL
        GROUP BY hero_name ORDER BY cnt DESC LIMIT 20
    ''').fetchall())
    union_stats = rows_to_list(conn.execute('''
        SELECT u, uname, total, wins, ROUND(wins*100.0/total,1) as win_rate FROM (
            SELECT atk_unionid as u, atk_union as uname, COUNT(*) as total,
                   SUM(CASE WHEN result=1 THEN 1 ELSE 0 END) as wins
            FROM battles WHERE atk_union != '' GROUP BY atk_unionid
            UNION ALL
            SELECT def_unionid, def_union, COUNT(*),
                   SUM(CASE WHEN result IN (2,6) THEN 1 ELSE 0 END)
            FROM battles WHERE def_union != '' GROUP BY def_unionid
        ) GROUP BY u ORDER BY total DESC LIMIT 20
    ''').fetchall())
    conn.close()
    return jsonify({'total': total, 'result_dist': result_dist, 'city_dist': city_dist,
                    'hero_freq': hero_freq, 'combo_freq': combo_freq, 'union_stats': union_stats})

# ===== 武将统计 =====
@app.route('/api/heroes/freq')
def api_hero_freq():
    conn = get_db()
    rows = conn.execute('''
        SELECT hero_name, hero_id, COUNT(*) as total,
               SUM(CASE WHEN side=\'atk\' THEN 1 ELSE 0 END) as atk_cnt,
               SUM(CASE WHEN side=\'def\' THEN 1 ELSE 0 END) as def_cnt,
               AVG(damage_taken) as avg_dmg
        FROM battle_heroes WHERE hero_name NOT LIKE \'武将%\'
        GROUP BY hero_name ORDER BY total DESC LIMIT 100
    ''').fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

@app.route('/api/heroes/combos')
def api_hero_combos():
    side = request.args.get('side', 'atk')
    conn = get_db()
    win_expr = "bv.result IN (1,7,11)" if side == 'atk' else "bv.result IN (2,6,12)"
    lose_expr = "bv.result IN (2,6,12)" if side == 'atk' else "bv.result IN (1,7,11)"
    # 从 battle_heroes 实时统计每个武将的使用次数、胜率、最新等级，平局按 0.5 胜计入
    rows = conn.execute(f'''
        SELECT bh.hero_name,
               COUNT(*) as cnt,
               SUM(CASE WHEN {win_expr} THEN 1 ELSE 0 END) as wins,
               SUM(CASE WHEN NOT ({win_expr}) AND NOT ({lose_expr}) THEN 1 ELSE 0 END) as draws,
               MAX(bh.level) as max_level,
               ROUND((SUM(CASE WHEN {win_expr} THEN 1 ELSE 0 END) + SUM(CASE WHEN NOT ({win_expr}) AND NOT ({lose_expr}) THEN 1 ELSE 0 END) * 0.5)*100.0/COUNT(*),1) as win_rate
        FROM battle_heroes bh
        JOIN battles_v2 bv ON bv.battle_id=bh.battle_id
        WHERE bh.side=? AND bh.hero_name NOT LIKE \'武将%\' AND bv.fight_type >= 0
        GROUP BY bh.hero_name
        ORDER BY cnt DESC LIMIT 50
    ''', (side,)).fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))


@app.route('/api/heroes/combo_winrate')
def api_hero_combo_winrate():
    """从 battles_v2+battle_heroes 实时计算三人组合胜率"""
    min_count = int(request.args.get('min', 3))
    fight_type = request.args.get('fight_type', '')
    conn = get_db()
    where = "1=1"
    params = []
    if fight_type:
        where += " AND bv.fight_type=?"
        params.append(int(fight_type))
    rows = conn.execute(f'''
        SELECT bv.battle_id, bv.result,
               GROUP_CONCAT(bh.hero_name) as heroes
        FROM battles_v2 bv
        JOIN battle_heroes bh ON bh.battle_id=bv.battle_id AND bh.side=\'atk\'
        WHERE {where} AND bh.hero_name NOT LIKE \'武将%\'
        GROUP BY bv.battle_id
        HAVING COUNT(bh.id) >= 2
    ''', params).fetchall()
    conn.close()
    from collections import defaultdict
    combo_stats = defaultdict(lambda: {'total':0,'win':0,'lose':0,'draw':0})
    for r in rows:
        heroes = sorted(set([h.strip() for h in (r['heroes'] or '').split(',') if h.strip() and not h.startswith('武将')]))
        if len(heroes) < 2: continue
        result = r['result']
        win  = (result in (1,7,11))
        lose = (result in (2,6,12))
        # 三人组合
        for i in range(len(heroes)):
            for j in range(i+1, len(heroes)):
                for k in range(j+1, len(heroes)):
                    key = heroes[i] + '+' + heroes[j] + '+' + heroes[k]
                    combo_stats[key]['total'] += 1
                    if win:  combo_stats[key]['win']  += 1
                    elif lose: combo_stats[key]['lose'] += 1
                    else: combo_stats[key]['draw'] += 1
    result_list = []
    for combo, s in combo_stats.items():
        if s['total'] < min_count: continue
        win_rate = round((s['win'] + s['draw'] * 0.5) * 100.0 / s['total'], 1)
        result_list.append({
            'combo': combo,
            'total': s['total'],
            'win':   s['win'],
            'lose':  s['lose'],
            'draw':  s['draw'],
            'win_rate': win_rate,
        })
    result_list.sort(key=lambda x: (-x['win_rate'], -x['total']))
    return jsonify(result_list)

# ===== 玩家 =====
@app.route('/api/players')
def api_players():
    conn = get_db()
    rows = conn.execute('''
        SELECT player_name, union_name, period, battle_count, atk_count, def_count,
               win_count, wuxun_total, custom_score,
               ROUND(win_count*100.0/MAX(battle_count,1),1) as win_rate
        FROM scores WHERE period=\'all\' ORDER BY battle_count DESC
    ''').fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

@app.route('/api/players/<player_name>')
def api_player_detail(player_name):
    conn = get_db()
    score = conn.execute('SELECT * FROM scores WHERE player_name=? AND period=\'all\'', (player_name,)).fetchone()
    teams = conn.execute('SELECT * FROM player_teams WHERE player_name=? ORDER BY side,used_count DESC', (player_name,)).fetchall()
    battles = conn.execute('SELECT * FROM battles WHERE atk_name=? OR def_name=? ORDER BY time DESC LIMIT 30', (player_name, player_name)).fetchall()
    wx = conn.execute('SELECT SUM(gongxun) as total, COUNT(*) as cnt FROM wuxun WHERE player_name=?', (player_name,)).fetchone()
    conn.close()
    return jsonify({'score': dict(score) if score else {}, 'teams': rows_to_list(teams),
                    'battles': rows_to_list(battles), 'wuxun': dict(wx) if wx else {}})

# ===== 武勋统计 =====
@app.route('/api/wuxun')
def api_wuxun():
    group = request.args.get('group', 'player')  # player / union
    conn = get_db()
    if group == 'union':
        rows = conn.execute('''
            SELECT union_name, SUM(gongxun) as total_wx, COUNT(*) as battles,
                   COUNT(DISTINCT player_name) as players
            FROM wuxun WHERE union_name != '' GROUP BY union_name ORDER BY total_wx DESC
        ''').fetchall()
    else:
        rows = conn.execute('''
            SELECT player_name, union_name, SUM(gongxun) as total_wx,
                   COUNT(*) as battles, AVG(gongxun) as avg_wx
            FROM wuxun WHERE player_name != '' GROUP BY player_name ORDER BY total_wx DESC LIMIT 50
        ''').fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

# ===== 积分排行 =====
@app.route('/api/scores')
def api_scores():
    union = request.args.get('union', '')
    conn = get_db()
    where = '' if not union else f"WHERE union_name LIKE '%{union}%'"
    rows = conn.execute(f'''
        SELECT *, ROUND(win_count*100.0/MAX(battle_count,1),1) as win_rate
        FROM scores {where} ORDER BY custom_score DESC
    ''').fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

# ===== 联盟对抗矩阵 =====
@app.route('/api/union_matrix')
def api_union_matrix():
    conn = get_db()
    rows = conn.execute('''
        SELECT atk_union, def_union,
               COUNT(*) as total,
               SUM(CASE WHEN result=1 THEN 1 ELSE 0 END) as atk_wins
        FROM battles
        WHERE atk_union != '' AND def_union != '' AND atk_union != def_union
        GROUP BY atk_union, def_union
        ORDER BY total DESC LIMIT 200
    ''').fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

# ===== 玩家坐标 =====
@app.route('/api/locations')
def api_locations():
    conn = get_db()
    name = request.args.get('name', '')
    where = "WHERE player_name LIKE ?" if name else ""
    params = [f'%{name}%'] if name else []
    rows = conn.execute(f'SELECT * FROM player_locations {where} ORDER BY power DESC LIMIT 200', params).fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

# ===== 英雄背包 =====
@app.route('/api/hero_bag')
def api_hero_bag():
    conn = get_db()
    rows = conn.execute('''
        SELECT ph.hero_id, ph.level, ph.star, ph.hp, ph.atk, ph.def_val, ph.speed, ph.intel, ph.skill_str,
               ph.captured_at
        FROM player_heroes ph
        ORDER BY ph.level DESC, ph.hero_id
        LIMIT 200
    ''').fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

@app.route('/api/hero_bag/stats')
def api_hero_bag_stats():
    """英雄背包统计：各英雄出现次数、平均等级"""
    conn = get_db()
    rows = conn.execute('''
        SELECT hero_id, COUNT(*) as cnt, AVG(level) as avg_level,
               MAX(level) as max_level, AVG(star) as avg_star
        FROM player_heroes
        GROUP BY hero_id ORDER BY cnt DESC LIMIT 100
    ''').fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

# ===== 联盟城池 =====
@app.route('/api/union_cities')
def api_union_cities():
    conn = get_db()
    union = request.args.get('union', '')
    player = request.args.get('player', '')
    where = []
    params = []
    if union:
        where.append('union_name LIKE ?'); params.append(f'%{union}%')
    if player:
        where.append('player_name LIKE ?'); params.append(f'%{player}%')
    w = ('WHERE ' + ' AND '.join(where)) if where else ''
    rows = conn.execute(f'SELECT * FROM union_cities {w} ORDER BY power DESC LIMIT 300', params).fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

@app.route('/api/union_cities/summary')
def api_union_cities_summary():
    """联盟城池汇总：按联盟统计成员数、总战力"""
    conn = get_db()
    rows = conn.execute('''
        SELECT source_type as msg_type,
               COUNT(*) as city_count,
               COUNT(DISTINCT player_name) as player_count,
               SUM(power) as total_power,
               AVG(power) as avg_power,
               MAX(power) as max_power
        FROM union_cities
        GROUP BY source_type ORDER BY total_power DESC
    ''').fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

# ===== 玩家战绩 =====
@app.route('/api/player_records')
def api_player_records():
    conn = get_db()
    rows = conn.execute('''
        SELECT * FROM player_records ORDER BY wuxun_total DESC
    ''').fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

# ===== 英雄解锁统计 =====
@app.route('/api/hero_unlock')
def api_hero_unlock():
    conn = get_db()
    rows = conn.execute('''
        SELECT hero_id, COUNT(*) as unlock_count,
               MIN(unlock_time) as first_unlock,
               MAX(unlock_time) as last_unlock
        FROM hero_unlock
        GROUP BY hero_id ORDER BY unlock_count DESC LIMIT 100
    ''').fetchall()
    conn.close()
    return jsonify(rows_to_list(rows))

# ===== DB同步记录统计 =====
@app.route('/api/db_sync/tables')
def api_db_sync_tables():
    conn = get_db()
    try:
        rows = conn.execute('''
            SELECT table_name, COUNT(*) as cnt,
                   SUM(CASE WHEN op=1 THEN 1 ELSE 0 END) as inserts,
                   SUM(CASE WHEN op=2 THEN 1 ELSE 0 END) as updates,
                   SUM(CASE WHEN op=3 THEN 1 ELSE 0 END) as deletes
            FROM db_sync GROUP BY table_name ORDER BY cnt DESC
        ''').fetchall()
        result = rows_to_list(rows)
    except:
        result = []
    conn.close()
    return jsonify(result)

# ===== 重新导入数据 =====
@app.route('/api/refresh', methods=['POST'])
def api_refresh():
    import subprocess, sys
    try:
        if getattr(sys, 'frozen', False):
            import db_import
            import db_import_ext
            from db_build import create_tables as _create_tables
            from db_extend import create_ext_tables as _create_ext_tables, get_db as _get_ext_db

            conn = get_db()
            _create_tables(conn)
            db_import.import_battles(conn)
            db_import.import_unions(conn)
            db_import.import_player_teams(conn)
            db_import.calc_scores(conn)
            conn.close()

            ext_conn = _get_ext_db()
            _create_ext_tables(ext_conn)
            db_import_ext.import_player_locations(ext_conn)
            db_import_ext.import_db_sync(ext_conn)
            db_import_ext.import_union_cities(ext_conn)
            db_import_ext.import_player_records(ext_conn)
            db_import_ext.import_hero_unlock(ext_conn)
            ext_conn.close()

            return jsonify({'ok': True, 'output': 'refresh completed in packaged mode', 'err': ''})

        r1 = subprocess.run([sys.executable, os.path.join(BASE_DIR, 'db_import.py')],
                            capture_output=True, text=True, timeout=120, cwd=BASE_DIR)
        r2 = subprocess.run([sys.executable, os.path.join(BASE_DIR, 'db_import_ext.py')],
                            capture_output=True, text=True, timeout=120, cwd=BASE_DIR)
        out = r1.stdout[-1500:] + '\n---ext---\n' + r2.stdout[-1000:]
        return jsonify({'ok': True, 'output': out, 'err': r1.stderr[-300:]+r2.stderr[-300:]})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)})

@app.route('/api/profile')
def api_profile():
    """返回当前绑定的账号/服务器信息"""
    try:
        if os.path.exists(PROFILE_FILE):
            import json as _json
            with open(PROFILE_FILE, 'r', encoding='utf-8') as f:
                p = _json.load(f)
            p['current_db'] = _current_db_path
            return jsonify(p)
    except:
        pass
    return jsonify({'current_db': _current_db_path, 'role_name': '', 'server_name': ''})


@app.route('/api/profile/list')
def api_profile_list():
    """列出所有已有的数据库文件（每个对应一个账号）"""
    dbs = []
    for f in os.listdir(BASE_DIR):
        if f.endswith('.db'):
            fpath = os.path.join(BASE_DIR, f)
            dbs.append({
                'filename': f,
                'path': fpath,
                'size': os.path.getsize(fpath),
                'active': fpath == _current_db_path,
            })
    return jsonify(dbs)


@app.route('/api/profile/switch', methods=['POST'])
def api_profile_switch():
    """手动切换到指定数据库"""
    global _current_db_path
    data = request.json or {}
    db_path = data.get('db_path', '')
    if not db_path or not os.path.exists(db_path):
        return jsonify({'ok': False, 'error': '数据库文件不存在'}), 400
    with _db_lock:
        _current_db_path = db_path
    print(f'[profile] 手动切换数据库: {db_path}')
    return jsonify({'ok': True, 'db_path': db_path})


@app.route('/api/status')
def api_status():
    conn = get_db()
    stats = {}
    for tbl in ['battles','unions','player_teams','wuxun','scores',
               'player_locations','player_heroes','union_cities','hero_unlock','db_sync']:
        try:
            stats[tbl] = conn.execute(f'SELECT COUNT(*) FROM {tbl}').fetchone()[0]
        except:
            stats[tbl] = 0
    try:
        last = conn.execute('SELECT time_str FROM battles ORDER BY time DESC LIMIT 1').fetchone()
        stats['last_battle'] = last[0] if last else ''
    except:
        stats['last_battle'] = ''
    conn.close()
    return jsonify({'ok': True, 'db': _current_db_path, 'stats': stats})


def _cc_scalar(conn, sql, args=(), default=0):
    """Command Center 聚合查询：可选表缺失时返回稳定默认值。"""
    try:
        row = conn.execute(sql, args).fetchone()
        if row is None or row[0] is None:
            return default
        return row[0]
    except sqlite3.Error:
        return default


def _cc_rows(conn, sql, args=()):
    try:
        return rows_to_list(conn.execute(sql, args).fetchall())
    except sqlite3.Error:
        return []


def _command_center_alerts(armies, writer_stats=None, latest_battle_time=0, now=None):
    alerts = []
    now = int(time.time()) if now is None else int(now)
    writer_stats = writer_stats or {}
    writer_errors = _safe_int(writer_stats.get('errors'))
    if writer_errors:
        alerts.append({
            'id': f'writer_error:{writer_errors}',
            'kind': 'writer_error',
            'level': 'danger',
            'title': '实时入库出现异常',
            'message': f'Writer 已累计记录 {writer_errors} 次错误，请检查抓包与数据库链路',
            'entityType': 'system',
            'entityId': 'writer',
            'count': writer_errors,
        })
    if latest_battle_time and now - latest_battle_time > 86400:
        alerts.append({
            'id': f'stale_data:{latest_battle_time}',
            'kind': 'stale_data',
            'level': 'warning',
            'title': '战报数据长时间未更新',
            'message': '最近一条战报已超过 24 小时，请确认抓包目标、账号档案和网络链路',
            'entityType': 'system',
            'entityId': 'capture',
            'lastUpdatedAt': latest_battle_time,
        })
    by_target = {}
    for army in armies:
        target = _safe_int(army.get('wid_to'))
        if target:
            by_target.setdefault(target, []).append(army)
    for target, target_armies in by_target.items():
        if len(target_armies) < 2:
            continue
        target_name = next(
            (row.get('target_name') for row in target_armies if row.get('target_name')),
            '',
        )
        alerts.append({
            'id': f'convergence:{target}',
            'kind': 'convergence',
            'level': 'warning',
            'title': '多支队伍正在集结',
            'message': f'{len(target_armies)} 支队伍正在前往 {target_name or target}',
            'entityType': 'wid',
            'entityId': str(target),
            'count': len(target_armies),
        })
    for army in armies:
        end_time = _safe_int(army.get('end_time'))
        if not end_time or end_time <= now or end_time - now > 300:
            continue
        army_id = _safe_int(army.get('army_id'))
        alerts.append({
            'id': f'arrival:{army_id}',
            'kind': 'arrival',
            'level': 'danger',
            'title': '队伍即将到达',
            'message': (
                f'{army.get("owner_name") or "未知队伍"} 将在 '
                f'{max(1, (end_time - now + 59) // 60)} 分钟内到达 '
                f'{army.get("target_name") or army.get("wid_to") or "目标"}'
            ),
            'entityType': 'army',
            'entityId': str(army_id),
            'arriveAt': end_time,
        })
    return alerts[:20]


@app.route('/api/command-center/overview')
def api_command_center_overview():
    """桌面指挥中心的只读聚合数据；任一可选表缺失都不影响其他模块。"""
    conn = get_db()
    now = int(time.time())
    today_start = _local_day_start_timestamp()
    try:
        metrics = {
            'battlesTotal': _cc_scalar(
                conn, 'SELECT COUNT(*) FROM battles_v2'
            ),
            'battlesToday': _cc_scalar(
                conn, 'SELECT COUNT(*) FROM battles_v2 WHERE time>=?',
                (today_start,),
            ),
            'allianceMembers': _cc_scalar(
                conn, 'SELECT COUNT(*) FROM team_users'
            ),
            'activeArmies': _cc_scalar(
                conn,
                'SELECT COUNT(*) FROM world_armies WHERE deleted_at_seq IS NULL',
            ),
            'knownTiles': _cc_scalar(
                conn, 'SELECT COUNT(*) FROM world_tiles'
            ),
        }
        battles = _cc_rows(
            conn,
            '''
            SELECT battle_id,time,time_str,result,result_desc,atk_name,atk_union,
                   def_name,def_union,wid,atk_gongxun
            FROM battles_v2
            ORDER BY time DESC
            LIMIT 12
            ''',
        )
        armies = _cc_rows(
            conn,
            '''
            SELECT army_id,owner_name,owner_union_name,wid_from,wid_to,
                   target_name,end_time
            FROM world_armies
            WHERE deleted_at_seq IS NULL
            ORDER BY end_time,army_id
            LIMIT 30
            ''',
        )
        profile = profile_manager.load_current_profile() or {}
        latest_battle_time = _safe_int(battles[0].get('time')) if battles else 0
        latest_army_time = max(
            (_safe_int(row.get('end_time')) for row in armies),
            default=0,
        )
        writer_stats = dict(getattr(_writer, 'stats', {}) or {})
        return jsonify({
            'ok': True,
            'profile': {
                'profileId': profile.get('profile_id', ''),
                'roleName': profile.get('role_name', ''),
                'serverName': profile.get('server_name', ''),
                'serverIp': profile.get('server_ip', ''),
            },
            'metrics': metrics,
            'battles': battles,
            'armies': armies,
            'alerts': _command_center_alerts(
                armies,
                writer_stats=writer_stats,
                latest_battle_time=latest_battle_time,
                now=now,
            ),
            'freshness': {
                'generatedAt': now,
                'latestBattleAt': latest_battle_time,
                'latestArmyArrivalAt': latest_army_time,
                'writer': writer_stats,
            },
        })
    finally:
        conn.close()

# ===== 排行榜 v2（玩家/盟/州 × 武勋/出战/势力）=====
@app.route('/api/ranking_v2')
def api_ranking_v2():
    period = request.args.get('period', '24h')   # 24h / week / season
    dim    = request.args.get('dim', 'player')   # player / union / zone
    metric = request.args.get('metric', 'wuxun') # wuxun / battles / power
    conn = get_db()
    now = int(__import__('time').time())
    if period == '24h':    since = now - 86400
    elif period == 'week': since = now - 7*86400
    else: since = 0
    tw = f'AND time >= {since}' if since else ''
    ww = f'WHERE time >= {since}' if since else ''

    FIGHT_TYPE_MAP = {0:'野战',33:'大城',80:'攻城',27:'宝物',1:'援军',2:'援军'}

    if dim == 'player':
        if metric == 'wuxun':
            rows = conn.execute(f'''
                SELECT atk_name as name, atk_union as group_name,
                       SUM(atk_gongxun) as value, COUNT(*) as battles,
                       SUM(CASE WHEN fight_type IN (80,33) THEN 1 ELSE 0 END) as city_cnt,
                       SUM(CASE WHEN result IN (1,7,11) THEN 1 ELSE 0 END) as wins
                FROM battles_v2 WHERE atk_name != '' {tw}
                GROUP BY atk_name ORDER BY value DESC LIMIT 50
            ''').fetchall()
        elif metric == 'battles':
            rows = conn.execute(f'''
                SELECT atk_name as name, atk_union as group_name,
                       COUNT(*) as value, COUNT(*) as battles,
                       SUM(CASE WHEN fight_type IN (80,33) THEN 1 ELSE 0 END) as city_cnt,
                       SUM(CASE WHEN result IN (1,7,11) THEN 1 ELSE 0 END) as wins
                FROM battles_v2 WHERE atk_name != '' {tw}
                GROUP BY atk_name ORDER BY value DESC LIMIT 50
            ''').fetchall()
        else:  # power
            rows = conn.execute(f'''
                SELECT atk_name as name, atk_union as group_name,
                       MAX(atk_power) as value, COUNT(*) as battles,
                       SUM(CASE WHEN fight_type IN (80,33) THEN 1 ELSE 0 END) as city_cnt,
                       SUM(CASE WHEN result IN (1,7,11) THEN 1 ELSE 0 END) as wins
                FROM battles_v2 WHERE atk_name != '' {tw}
                GROUP BY atk_name ORDER BY value DESC LIMIT 50
            ''').fetchall()

    elif dim == 'union':
        # atk_union 在 battles_v2 里为 NULL，改用 wuxun_log（有 atk_union）
        # battles/power 用 def_union 聚合（对方联盟活跃度）
        if metric == 'wuxun':
            rows = conn.execute(f'''
                SELECT atk_union as name, '' as group_name,
                       SUM(gongxun) as value, COUNT(*) as battles,
                       SUM(CASE WHEN fight_type IN (80,33) THEN 1 ELSE 0 END) as city_cnt,
                       SUM(CASE WHEN result IN (1,7,11) THEN 1 ELSE 0 END) as wins
                FROM wuxun_log WHERE atk_union != '' {tw}
                GROUP BY atk_union ORDER BY value DESC LIMIT 50
            ''').fetchall()
        elif metric == 'battles':
            rows = conn.execute(f'''
                SELECT def_union as name, '' as group_name,
                       COUNT(*) as value, COUNT(*) as battles,
                       SUM(CASE WHEN fight_type IN (80,33) THEN 1 ELSE 0 END) as city_cnt,
                       SUM(CASE WHEN result IN (1,7,11) THEN 1 ELSE 0 END) as wins
                FROM battles_v2 WHERE def_union != '' {tw}
                GROUP BY def_union ORDER BY value DESC LIMIT 50
            ''').fetchall()
        else:  # power
            rows = conn.execute(f'''
                SELECT def_union as name, '' as group_name,
                       MAX(atk_power) as value, COUNT(*) as battles,
                       SUM(CASE WHEN fight_type IN (80,33) THEN 1 ELSE 0 END) as city_cnt,
                       SUM(CASE WHEN result IN (1,7,11) THEN 1 ELSE 0 END) as wins
                FROM battles_v2 WHERE def_union != '' {tw}
                GROUP BY def_union ORDER BY value DESC LIMIT 50
            ''').fetchall()

    else:  # zone — wid_code 格式如 "440309"，取前4位作为州
        if metric == 'wuxun':
            rows = conn.execute(f'''
                SELECT SUBSTR(wid_code,1,4) as name, '' as group_name,
                       SUM(atk_gongxun) as value, COUNT(*) as battles,
                       SUM(CASE WHEN fight_type IN (80,33) THEN 1 ELSE 0 END) as city_cnt,
                       SUM(CASE WHEN result IN (1,7,11) THEN 1 ELSE 0 END) as wins
                FROM battles_v2 WHERE wid_code != '' {tw}
                GROUP BY SUBSTR(wid_code,1,4) ORDER BY value DESC LIMIT 50
            ''').fetchall()
        elif metric == 'battles':
            rows = conn.execute(f'''
                SELECT SUBSTR(wid_code,1,4) as name, '' as group_name,
                       COUNT(*) as value, COUNT(*) as battles,
                       SUM(CASE WHEN fight_type IN (80,33) THEN 1 ELSE 0 END) as city_cnt,
                       SUM(CASE WHEN result IN (1,7,11) THEN 1 ELSE 0 END) as wins
                FROM battles_v2 WHERE wid_code != '' {tw}
                GROUP BY SUBSTR(wid_code,1,4) ORDER BY value DESC LIMIT 50
            ''').fetchall()
        else:  # power
            rows = conn.execute(f'''
                SELECT SUBSTR(wid_code,1,4) as name, '' as group_name,
                       MAX(atk_power) as value, COUNT(*) as battles,
                       SUM(CASE WHEN fight_type IN (80,33) THEN 1 ELSE 0 END) as city_cnt,
                       SUM(CASE WHEN result IN (1,7,11) THEN 1 ELSE 0 END) as wins
                FROM battles_v2 WHERE wid_code != '' {tw}
                GROUP BY SUBSTR(wid_code,1,4) ORDER BY value DESC LIMIT 50
            ''').fetchall()

    conn.close()
    data = [dict(r) for r in rows]
    # 最终兜底过滤：避免不同库里 is_npc/isnpc 存储格式不一致导致漏网
    def _is_npc_row(d):
        v1 = d.get('is_npc', 0)
        v2 = d.get('isnpc', 0)
        r  = d.get('result', 0)
        desc = str(d.get('result_desc', '') or '').upper()

        def _truthy(v):
            if isinstance(v, str):
                s = v.strip().lower()
                return s in ('1', 'true', 'yes', 'y', 'npc')
            try:
                return int(v) != 0
            except Exception:
                return bool(v)

        return _truthy(v1) or _truthy(v2) or int(r or 0) == 6 or ('NPC' in desc)

    data = [d for d in data if not _is_npc_row(d)]
    for i, r in enumerate(data):
        r['rank'] = i + 1
        wr = round(r['wins'] / r['battles'] * 100, 1) if r['battles'] else 0
        r['win_rate'] = wr
    return jsonify(data)


# ===== 排行榜 =====
@app.route('/api/ranking')
def api_ranking():
    period = request.args.get('period', '24h')  # 24h / week / season
    scope  = request.args.get('scope', 'player') # player / union
    metric = request.args.get('metric', 'wuxun') # wuxun / power / battles
    conn = get_db()
    now = int(__import__('time').time())
    if period == '24h':   since = now - 86400
    elif period == 'week': since = now - 7*86400
    else: since = 0
    where = f'AND time >= {since}' if since else ''
    if metric == 'wuxun':
        if scope == 'union':
            rows = conn.execute(f'''
                SELECT atk_union as name, SUM(gongxun) as total, COUNT(*) as battles
                FROM wuxun_log WHERE atk_union != '' {where}
                GROUP BY atk_union ORDER BY total DESC LIMIT 50
            ''').fetchall()
        else:
            rows = conn.execute(f'''
                SELECT atk_name as name, SUM(gongxun) as total, COUNT(*) as battles
                FROM wuxun_log WHERE atk_name != '' {where}
                GROUP BY atk_name ORDER BY total DESC LIMIT 50
            ''').fetchall()
    elif metric == 'power':
        if scope == 'union':
            rows = conn.execute(f'''
                SELECT atk_union as name, MAX(power) as total, COUNT(*) as battles
                FROM power_log WHERE atk_union != '' {where}
                GROUP BY atk_union ORDER BY total DESC LIMIT 50
            ''').fetchall()
        else:
            rows = conn.execute(f'''
                SELECT atk_name as name, MAX(power) as total, COUNT(*) as battles
                FROM power_log WHERE atk_name != '' {where}
                GROUP BY atk_name ORDER BY total DESC LIMIT 50
            ''').fetchall()
    else:  # battles
        if scope == 'union':
            rows = conn.execute(f'''
                SELECT def_union as name, COUNT(*) as total,
                       SUM(CASE WHEN result IN (1,11) THEN 1 ELSE 0 END) as battles
                FROM battles_v2 WHERE def_union != '' {where}
                GROUP BY def_union ORDER BY total DESC LIMIT 50
            ''').fetchall()
        else:
            rows = conn.execute(f'''
                SELECT atk_name as name, COUNT(*) as total,
                       SUM(CASE WHEN result IN (1,11) THEN 1 ELSE 0 END) as battles
                FROM battles_v2 WHERE atk_name != '' {where}
                GROUP BY atk_name ORDER BY total DESC LIMIT 50
            ''').fetchall()
    conn.close()
    data = [dict(r) for r in rows]
    for i, r in enumerate(data): r['rank'] = i + 1
    return jsonify(data)


# ===== 武勋统计 =====
@app.route('/api/wuxun_stats')
def api_wuxun_stats():
    period = request.args.get('period', '24h')
    scope  = request.args.get('scope', 'player')
    conn = get_db()
    now = int(__import__('time').time())
    since = now - 86400 if period == '24h' else (now - 7*86400 if period == 'week' else 0)
    where = f'WHERE time >= {since}' if since else ''
    if scope == 'union':
        rows = conn.execute(f'''
            SELECT atk_union as name, SUM(gongxun) as total_wx, COUNT(*) as battles,
                   AVG(gongxun) as avg_wx,
                   SUM(CASE WHEN fight_type=80 THEN 1 ELSE 0 END) as city_battles,
                   SUM(CASE WHEN fight_type=33 THEN 1 ELSE 0 END) as main_city_battles
            FROM wuxun_log {where} GROUP BY atk_union ORDER BY total_wx DESC LIMIT 50
        ''').fetchall()
    else:
        rows = conn.execute(f'''
            SELECT atk_name as name, SUM(gongxun) as total_wx, COUNT(*) as battles,
                   AVG(gongxun) as avg_wx,
                   SUM(CASE WHEN fight_type=80 THEN 1 ELSE 0 END) as city_battles,
                   SUM(CASE WHEN fight_type=33 THEN 1 ELSE 0 END) as main_city_battles
            FROM wuxun_log {where} GROUP BY atk_name ORDER BY total_wx DESC LIMIT 100
        ''').fetchall()
    conn.close()
    return jsonify([dict(r) for r in rows])


# ===== 势力值统计 =====
@app.route('/api/power_stats')
def api_power_stats():
    period = request.args.get('period', '24h')
    scope  = request.args.get('scope', 'player')
    conn = get_db()
    now = int(__import__('time').time())
    since = now - 86400 if period == '24h' else (now - 7*86400 if period == 'week' else 0)
    where = f'WHERE time >= {since}' if since else ''
    if scope == 'union':
        rows = conn.execute(f'''
            SELECT atk_union as name, MAX(power) as max_power, SUM(power) as total_power,
                   COUNT(*) as battles, AVG(power) as avg_power
            FROM power_log {where} GROUP BY atk_union ORDER BY max_power DESC LIMIT 50
        ''').fetchall()
    else:
        rows = conn.execute(f'''
            SELECT atk_name as name, MAX(power) as max_power, SUM(power) as total_power,
                   COUNT(*) as battles, AVG(power) as avg_power
            FROM power_log {where} GROUP BY atk_name ORDER BY max_power DESC LIMIT 100
        ''').fetchall()
    conn.close()
    return jsonify([dict(r) for r in rows])


# ===== 战场分析 =====
@app.route('/api/battle_analysis')
def api_battle_analysis():
    period = request.args.get('period', '24h')
    conn = get_db()
    now = int(__import__('time').time())
    since = now - 86400 if period == '24h' else (now - 7*86400 if period == 'week' else 0)
    where = f'WHERE b.time >= {since}' if since else 'WHERE 1=1'
    # 汇总统计
    summary = conn.execute(f'''
        SELECT COUNT(*) as total,
               SUM(CASE WHEN result IN (1,11) THEN 1 ELSE 0 END) as atk_wins,
               0 as night_cnt,
               COUNT(DISTINCT atk_union) as union_cnt,
               COUNT(DISTINCT atk_name) as player_cnt,
               AVG(atk_gongxun) as avg_wx
        FROM battles_v2 b {where}
    ''').fetchone()
    # 按战斗类型统计
    by_type = conn.execute(f'''
        SELECT fight_type, COUNT(*) as cnt,
               SUM(CASE WHEN result IN (1,11) THEN 1 ELSE 0 END) as atk_wins
        FROM battles_v2 b {where} GROUP BY fight_type ORDER BY cnt DESC
    ''').fetchall()
    # 按小时统计活跃度
    by_hour = conn.execute(f'''
        SELECT strftime('%H', datetime(time,'unixepoch','localtime')) as hour,
               COUNT(*) as cnt
        FROM battles_v2 b {where} GROUP BY hour ORDER BY hour
    ''').fetchall()
    # 夜战 vs 白天（battles_v2无in_night字段，返回空）
    night_day = []
    # 战力段位分布
    try:
        power_dist = conn.execute(f'''
            SELECT
              CASE
                WHEN atk_power >= 10000000 THEN '1000w+'
                WHEN atk_power >= 8000000  THEN '800w+'
                WHEN atk_power >= 6000000  THEN '600w+'
                WHEN atk_power >= 4000000  THEN '400w+'
                WHEN atk_power >= 2000000  THEN '200w+'
                ELSE '200w以下'
              END as tier,
              COUNT(*) as cnt
            FROM battles_v2 b {where} AND atk_power > 0
            GROUP BY tier ORDER BY MIN(atk_power) DESC
        ''').fetchall()
    except:
        power_dist = []
    # 对阵联盟统计
    vs_union = conn.execute(f'''
        SELECT def_union, COUNT(*) as total,
               SUM(CASE WHEN result IN (1,11) THEN 1 ELSE 0 END) as our_wins,
               SUM(CASE WHEN result IN (2,12) THEN 1 ELSE 0 END) as their_wins
        FROM battles_v2 b {where} AND def_union != ''
        GROUP BY def_union ORDER BY total DESC LIMIT 20
    ''').fetchall()
    # 最活跃玩家
    top_players = conn.execute(f'''
        SELECT atk_name, atk_union, COUNT(*) as battles,
               SUM(CASE WHEN result IN (1,11) THEN 1 ELSE 0 END) as wins,
               MAX(atk_power) as max_power
        FROM battles_v2 b {where} AND atk_name != ''
        GROUP BY atk_name ORDER BY battles DESC LIMIT 20
    ''').fetchall()
    conn.close()
    return jsonify({
        'summary': dict(summary) if summary else {},
        'by_type': [dict(r) for r in by_type],
        'by_hour': [dict(r) for r in by_hour],
        'night_day': [dict(r) for r in night_day],
        'power_dist': [dict(r) for r in power_dist],
        'vs_union': [dict(r) for r in vs_union],
        'top_players': [dict(r) for r in top_players],
    })


# ===== 打城考勤 =====
@app.route('/api/attendance')
def api_attendance():
    period = request.args.get('period', '24h')
    union  = request.args.get('union', '')
    pid = get_current_pid()
    conn = get_db()
    now = int(__import__('time').time())
    since = now - 86400 if period == '24h' else (now - 7*86400 if period == 'week' else 0)

    try:
        tbl_exists = conn.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='attendance'").fetchone()
        if not tbl_exists:
            conn.close()
            return jsonify([])

        cols = {r[1] for r in conn.execute("PRAGMA table_info(attendance)").fetchall()}
        where = [f'time >= {since}'] if since else []
        if 'profile_id' in cols and pid:
            where.append(f"profile_id='{pid}'")
        if union:
            where.append(f"union_name LIKE '%{union}%'")
        w = ('WHERE ' + ' AND '.join(where)) if where else ''

        rows = conn.execute(f'''
            SELECT player_name, union_name,
                   COUNT(*) as total_battles,
                   SUM(CASE WHEN fight_type=80 THEN 1 ELSE 0 END) as city_battles,
                   SUM(CASE WHEN fight_type=33 THEN 1 ELSE 0 END) as main_city,
                   SUM(CASE WHEN fight_type=0 THEN 1 ELSE 0 END) as field_battles,
                   SUM(gongxun) as total_wx,
                   SUM(CASE WHEN result IN (1,11) THEN 1 ELSE 0 END) as wins
            FROM attendance {w}
            GROUP BY player_name ORDER BY total_battles DESC LIMIT 100
        ''').fetchall()
        conn.close()
        return jsonify([dict(r) for r in rows])
    except:
        conn.close()
        return jsonify([])


# ===== 打城排表 =====
@app.route('/api/schedule')
def api_schedule():
    conn = get_db()
    rows = conn.execute('SELECT * FROM city_schedule ORDER BY session_id DESC, slot_index LIMIT 100').fetchall()
    conn.close()
    return jsonify([dict(r) for r in rows])


@app.route('/api/schedule/generate', methods=['POST'])
def api_schedule_generate():
    """从最近打城数据自动生成排表"""
    data = request.json or {}
    session_id = data.get('session_id', __import__('datetime').datetime.now().strftime('%Y%m%d_%H%M'))
    interval_mins = int(data.get('interval', 3))  # 默认3分钟一个城
    conn = get_db()
    # 统计最近打过的格子，按频率排序
    wids = conn.execute('''
        SELECT wid, wid_code, COUNT(*) as cnt
        FROM battles_v2 WHERE fight_type IN (80,33) AND wid > 0
        GROUP BY wid ORDER BY cnt DESC LIMIT 30
    ''').fetchall()
    now_str = __import__('datetime').datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    inserted = 0
    for i, w in enumerate(wids):
        conn.execute('''
            INSERT OR IGNORE INTO city_schedule
                (session_id, slot_index, wid, wid_code, scheduled_at, created_at)
            VALUES (?,?,?,?,?,?)
        ''', (session_id, i, w[0], w[1] or '', f'+{i*interval_mins}min', now_str))
        inserted += 1
    conn.commit()
    conn.close()
    return jsonify({'ok': True, 'session_id': session_id, 'slots': inserted})


# ===== 同盟成员 =====
@app.route('/api/team_users')
def api_team_users():
    name = request.args.get('name', '')
    group = request.args.get('group', '')
    pid = get_current_pid()
    conn = get_db()
    where = ['profile_id=?']; params = [pid]
    if name:
        where.append('name LIKE ?'); params.append(f'%{name}%')
    if group:
        where.append('group_name=?'); params.append(group)
    w = ' AND '.join(where)
    rows = conn.execute(
        f'SELECT * FROM team_users WHERE {w} ORDER BY power DESC, wuxun DESC', params
    ).fetchall()
    conn.close()
    return jsonify([dict(r) for r in rows])


@app.route('/api/team_groups')
def api_team_groups():
    pid = get_current_pid()
    conn = get_db()
    rows = conn.execute('SELECT DISTINCT group_name FROM team_users WHERE profile_id=? AND group_name != "" ORDER BY group_name', (pid,)).fetchall()
    conn.close()
    return jsonify([r[0] for r in rows])


@app.route('/api/team_stats')
def api_team_stats():
    pid = get_current_pid()
    conn = get_db()
    total = conn.execute('SELECT COUNT(*) FROM team_users WHERE profile_id=?', (pid,)).fetchone()[0]
    groups = conn.execute('''
        SELECT group_name, COUNT(*) as cnt,
               SUM(power) as total_power, ROUND(AVG(power)) as avg_power,
               SUM(wuxun) as total_wuxun, ROUND(AVG(wuxun)) as avg_wuxun,
               SUM(contribute_week) as total_cw
        FROM team_users WHERE profile_id=? GROUP BY group_name ORDER BY total_power DESC
    ''', (pid,)).fetchall()
    top_power = conn.execute(
        'SELECT uid,name,power,wuxun,group_name FROM team_users WHERE profile_id=? ORDER BY power DESC LIMIT 10', (pid,)
    ).fetchall()
    top_wuxun = conn.execute(
        'SELECT uid,name,power,wuxun,group_name FROM team_users WHERE profile_id=? AND wuxun>0 ORDER BY wuxun DESC LIMIT 10', (pid,)
    ).fetchall()
    conn.close()
    return jsonify({
        'total': total,
        'groups': [dict(r) for r in groups],
        'top_power': [dict(r) for r in top_power],
        'top_wuxun': [dict(r) for r in top_wuxun],
    })


# ===== 玩家战绩统计 =====
@app.route('/api/player_stats')
def api_player_stats():
    name = request.args.get('name', '')
    conn = get_db()
    try:
        where = ['1=1']; params = []
        if name:
            where.append('user_name LIKE ?'); params.append(f'%{name}%')
        w = ' AND '.join(where)
        rows = conn.execute(
            f'SELECT * FROM player_stats WHERE {w} ORDER BY wuxun_total DESC, force_max DESC LIMIT 200', params
        ).fetchall()
        result = [dict(r) for r in rows]
    except:
        result = []
    conn.close()
    return jsonify(result)


# ===== 城池地图统计 =====
@app.route('/api/map_cells')
def api_map_cells():
    cell_type = request.args.get('cell_type', '')
    city_name = request.args.get('city_name', '')
    conn = get_db()
    where = ['1=1']; params = []
    if cell_type:
        where.append('cell_type=?'); params.append(int(cell_type))
    if city_name:
        where.append('city_name LIKE ?'); params.append(f'%{city_name}%')
    w = ' AND '.join(where)
    rows = conn.execute(
        f'SELECT * FROM map_cells WHERE {w} ORDER BY cell_type, wid LIMIT 500', params
    ).fetchall()
    conn.close()
    return jsonify([dict(r) for r in rows])


@app.route('/api/map_stats')
def api_map_stats():
    """城池统计：各类型数量，以及有名字的城池列表"""
    conn = get_db()
    try:
        type_dist = conn.execute('''
            SELECT cell_type, city_name, COUNT(*) as cnt
            FROM map_cells
            WHERE city_name != '' AND city_name IS NOT NULL AND city_name != 'None'
            GROUP BY cell_type, city_name
            ORDER BY cell_type, cnt DESC
        ''').fetchall()
        total = conn.execute('SELECT COUNT(*) FROM map_cells').fetchone()[0]
        named = conn.execute('''
            SELECT wid, x, y, cell_type, city_name, owner_name, building_id, updated_at
            FROM map_cells
            WHERE city_name != '' AND city_name IS NOT NULL AND city_name != 'None'
            ORDER BY cell_type DESC, wid
            LIMIT 500
        ''').fetchall()
        result = {'total_cells': total, 'type_dist': [dict(r) for r in type_dist], 'named_cities': [dict(r) for r in named]}
    except:
        result = {'total_cells': 0, 'type_dist': [], 'named_cities': []}
    conn.close()
    return jsonify(result)


# ===== 战场消息历史 =====
@app.route('/api/msg_history')
def api_msg_history():
    limit = int(request.args.get('limit', 200))
    conn = get_db()
    # 确保表存在
    conn.execute('''
        CREATE TABLE IF NOT EXISTS chat_messages (
            id INTEGER PRIMARY KEY,
            sender TEXT,
            uid TEXT,
            union_name TEXT,
            text TEXT,
            time INTEGER,
            time_str TEXT,
            source_file TEXT
        )
    ''')
    conn.commit()
    rows = conn.execute('''
        SELECT id, sender, uid, union_name, text, time, time_str
        FROM chat_messages
        ORDER BY time DESC
        LIMIT ?
    ''', (limit,)).fetchall()
    conn.close()
    data = []
    for r in rows:
        data.append({
            'kind': 'chat',
            'id': r[0],
            'sender': r[1] or '',
            'uid': r[2] or '',
            'union': r[3] or '',
            'text': r[4] or '',
            'time': r[5] or 0,
            'time_str': r[6] or '',
        })
    return jsonify(data)


# ===== 全部战报列表 =====
@app.route('/api/battles_all', methods=['GET', 'POST'])
def api_battles_all():
    page   = int(request.args.get('page', 1))
    size   = int(request.args.get('size', 50))
    player = request.args.get('player', '')
    union  = request.args.get('union', '')
    result = request.args.get('result', '')
    ftype  = request.args.get('fight_type', '')
    period = request.args.get('period', '')
    wid    = request.args.get('wid', '')
    if request.method == 'POST':
        body = request.get_json(silent=True) or {}
        members = body.get('members', request.args.get('members', ''))
    else:
        members = request.args.get('members', '')
    offset = (page - 1) * size
    conn = get_db()

    where = ['result <= 6', 'result != 6']; params = []
    if player:
        where.append('(atk_name LIKE ? OR def_name LIKE ?)')
        params += [f'%{player}%', f'%{player}%']
    if union:
        where.append('(atk_union LIKE ? OR def_union LIKE ?)')
        params += [f'%{union}%', f'%{union}%']
    if result:
        where.append('result=?'); params.append(int(result))
    if ftype:
        where.append('fight_type=?'); params.append(int(ftype))
    if wid:
        try: where.append('wid=?'); params.append(int(wid))
        except: pass
    if members:
        names = [m.strip() for m in members.split(',') if m.strip()]
        if names:
            phs = ','.join('?' * len(names))
            where.append(f'atk_name IN ({phs})')
            params += names
    if period == '24h':
        where.append(f'time >= {int(__import__("time").time())-86400}')
    elif period == 'week':
        where.append(f'time >= {int(__import__("time").time())-7*86400}')
    w = ' AND '.join(where)
    conn = get_db()
    total = conn.execute(f'SELECT COUNT(*) FROM battles_v2 WHERE {w}', params).fetchone()[0]
    rows  = conn.execute(
        f'''SELECT battle_id, time, time_str, result, fight_type,
                   atk_name, atk_union, def_name, def_union,
                   atk_hero1_id, atk_hero2_id, atk_hero3_id,
                   def_hero1_id, def_hero2_id, def_hero3_id,
                   garrison
            FROM battles_v2
            WHERE {w}
            ORDER BY time DESC
            LIMIT ? OFFSET ?''',
        params + [size, offset]
    ).fetchall()

    data = [dict(r) for r in rows]
    # 兼容：battles_v2 可能没有 atk_hero1_id/def_hero1_id 等列，前端需要显示攻守武将
    try:
        battle_ids = [int(d.get('battle_id')) for d in data if d.get('battle_id') is not None]
        if battle_ids:
            placeholders = ','.join('?' * len(battle_ids))
            hrows = conn.execute(
                f'''SELECT battle_id, side, pos, hero_id
                    FROM battle_heroes
                    WHERE battle_id IN ({placeholders})
                    ORDER BY battle_id, side, pos''',
                battle_ids
            ).fetchall()
            hero_map = {}
            for hr in hrows:
                bid = int(hr[0])
                sd = hr[1] or ''
                hid = int(hr[3]) if hr[3] else 0
                if hid <= 0:
                    continue
                if bid not in hero_map:
                    hero_map[bid] = {'atk': [], 'def': []}
                if sd in ('atk', 'def') and len(hero_map[bid][sd]) < 3:
                    hero_map[bid][sd].append(hid)

            for d in data:
                bid = d.get('battle_id')
                if bid not in hero_map:
                    continue
                atk_ids = hero_map[bid].get('atk', [])
                def_ids = hero_map[bid].get('def', [])
                for i in range(3):
                    ak = f'atk_hero{i+1}_id'
                    dk = f'def_hero{i+1}_id'
                    if not d.get(ak) and i < len(atk_ids):
                        d[ak] = atk_ids[i]
                    if not d.get(dk) and i < len(def_ids):
                        d[dk] = def_ids[i]
    except Exception:
        pass

    conn.close()
    return jsonify({'total': total, 'page': page, 'size': size, 'data': data})


# ===== 玩家队伍统计 =====
@app.route('/api/player_teams_stats')
def api_player_teams_stats():
    player = request.args.get('player', '')
    union  = request.args.get('union', '')
    side   = request.args.get('side', '')   # atk/def/''
    limit  = int(request.args.get('limit', 200))
    conn = get_db()
    # 逻辑：
    # - 攻方玩家(atk_name)用的是 side=atk 的武将
    # - 守方玩家(def_name)用的是 side=def 的武将
    # 所以每个玩家的"自己的队伍"需要分两部分合并
    params_atk = []
    params_def = []
    where_atk = ["bh.side='atk'", "bh.hero_name NOT LIKE '武将%'", "bv.atk_name != ''"]
    where_def = ["bh.side='def'", "bh.hero_name NOT LIKE '武将%'", "bv.def_name != ''"]
    if player:
        where_atk.append('bv.atk_name LIKE ?'); params_atk.append(f'%{player}%')
        where_def.append('bv.def_name LIKE ?'); params_def.append(f'%{player}%')
    if union:
        where_atk.append('bv.atk_union LIKE ?'); params_atk.append(f'%{union}%')
        where_def.append('bv.def_union LIKE ?'); params_def.append(f'%{union}%')
    if side == 'atk':
        where_def = ['1=0']  # 只查攻方
    elif side == 'def':
        where_atk = ['1=0']  # 只查守方
    wa = ' AND '.join(where_atk)
    wd = ' AND '.join(where_def)
    # 攻方：玩家名=atk_name，队伍=side=atk
    raw_atk = conn.execute(f'''
        SELECT bv.atk_name as player_name, bv.atk_union as union_name,
               'atk' as side, bh.battle_id, bh.pos, bh.hero_name, bv.result
        FROM battle_heroes bh
        JOIN battles_v2 bv ON bh.battle_id=bv.battle_id
        WHERE {wa}
        ORDER BY bh.battle_id, bh.pos
    ''', params_atk).fetchall()
    # 守方：玩家名=def_name，队伍=side=def
    raw_def = conn.execute(f'''
        SELECT bv.def_name as player_name, bv.def_union as union_name,
               'def' as side, bh.battle_id, bh.pos, bh.hero_name, bv.result
        FROM battle_heroes bh
        JOIN battles_v2 bv ON bh.battle_id=bv.battle_id
        WHERE {wd}
        ORDER BY bh.battle_id, bh.pos
    ''', params_def).fetchall()
    conn.close()

    from collections import defaultdict
    # 合并处理
    battle_heroes_map = defaultdict(list)  # (player,side,battle_id) -> [(pos,hero)]
    battle_meta = {}  # (player,side,battle_id) -> (union, result)
    for r in list(raw_atk) + list(raw_def):
        pname, union_n, sd, bid, pos, hname, result = r
        if not pname: continue
        k = (pname, sd, bid)
        battle_heroes_map[k].append((pos, hname))
        battle_meta[k] = (union_n or '', result)

    # 聚合队伍
    team_stats = defaultdict(lambda: {'used_count':0,'win_count':0,'draw_count':0,'union':'','side':''})
    for (pname, sd, bid), heroes in battle_heroes_map.items():
        heroes.sort(key=lambda x: x[0])
        heroes_str = ','.join(h[1] for h in heroes if h[1])
        if not heroes_str: continue
        union_n, result = battle_meta[(pname, sd, bid)]
        key = (pname, sd, heroes_str)
        team_stats[key]['used_count'] += 1
        team_stats[key]['union'] = union_n
        team_stats[key]['side'] = sd
        # 攻方胜：result in (1,7,11)；守方胜：result in (2,6,12)；其余按平局算 0.5 胜
        if sd == 'atk' and result in (1,7,11):
            team_stats[key]['win_count'] += 1
        elif sd == 'def' and result in (2,6,12):
            team_stats[key]['win_count'] += 1
        elif result not in (1,2,6,7,11,12):
            team_stats[key]['draw_count'] += 1

    data = []
    for (pname, sd, heroes_str), stat in team_stats.items():
        uc = stat['used_count']
        wc = stat['win_count']
        dc = stat['draw_count']
        data.append({
            'player_name': pname,
            'union_name': stat['union'],
            'side': sd,
            'heroes_str': heroes_str,
            'used_count': uc,
            'win_count': wc,
            'draw_count': dc,
            'win_rate': round((wc + dc * 0.5)/uc*100,1) if uc else 0,
        })
    data.sort(key=lambda x: (-x['used_count'], x['player_name']))
    return jsonify(data[:limit])


# ===== 玩家队伍统计（直接从battles_v2解析红度+技能） =====
@app.route('/api/player_battle_teams')
def api_player_battle_teams():
    """从battles_v2统计每个玩家的队伍组合，含进阶星数、技能、战数、胜场"""
    player  = request.args.get('player', '')   # 模糊匹配玩家名
    union   = request.args.get('union', '')    # 模糊匹配同盟名
    side    = request.args.get('side', '')     # atk/def/''
    debug_mode = request.args.get('debug', '') == '1'

    # 内存缓存：按账号+库+查询参数缓存60秒
    import time as _time
    _cache = api_player_battle_teams.__dict__
    cache_data = _cache.setdefault('_data', {})
    with _db_lock:
        _db_path = _current_db_path
    _pid = get_current_pid()
    cache_key = f'{_pid}|{_db_path}|{player}|{union}|{side}'
    now = _time.time()
    if (not debug_mode) and cache_key in cache_data:
        entry = cache_data[cache_key]
        if now - entry['ts'] < 60:
            return jsonify(entry['result'])

    conn = get_db()
    bv_cols = get_bv2_cols(conn)

    conds = ["1=1"]
    params = []
    if player:
        conds.append("(atk_name LIKE ? OR def_name LIKE ?)")
        params += [f'%{player}%', f'%{player}%']
    if union:
        conds.append("(atk_union LIKE ? OR def_union LIKE ?)")
        params += [f'%{union}%', f'%{union}%']

    # 先按 NPC 过滤查询；若结果为空，再回退为不过滤（用于排查 is_npc/isnpc 字段不一致问题）
    conds_npc = list(conds)
    conds_npc.append('result != 6')
    if 'is_npc' in bv_cols:
        conds_npc.append('COALESCE(is_npc, 0) = 0')
    if 'isnpc' in bv_cols:
        conds_npc.append('COALESCE(isnpc, 0) = 0')

    where = ' AND '.join(conds_npc)
    rows = conn.execute(f'''
        SELECT *
        FROM battles_v2
        WHERE {where}
        ORDER BY battle_id DESC
        LIMIT 50000
    ''', params).fetchall()

    if not rows:
        where_fallback = ' AND '.join(conds)
        rows = conn.execute(f'''
            SELECT *
            FROM battles_v2
            WHERE {where_fallback}
        ORDER BY battle_id DESC
        LIMIT 50000
    ''', params).fetchall()
    conn.close()

    from collections import defaultdict

    def parse_advance_stars(s):
        """atk_advance格式: 星数,x,x,x,x,x;星数,...  返回前3段星数列表 [s1,s2,s3]"""
        if not s: return [0,0,0]
        result = []
        segs = s.split(';')
        for seg in segs[:3]:
            parts = seg.split(',')
            try: result.append(int(parts[0]))
            except: result.append(0)
        while len(result) < 3:
            result.append(0)
        return result

    def parse_hero_info(s):
        if not s: return ''
        heroes = []
        for seg in s.split(';'):
            parts = seg.split(',')
            if parts and parts[0].strip().isdigit():
                heroes.append(parts[0].strip())
        return '+'.join(heroes)

    def parse_skill_info(s, pos_list):
        if not s: return ''
        skills_by_pos = {}
        for seg in s.split(';'):
            parts = seg.split(',')
            if len(parts) >= 2:
                try: p = int(parts[0])
                except: continue
                skill_ids = [parts[i] for i in range(1, len(parts), 2) if i < len(parts)]
                skills_by_pos[p] = skill_ids
        result_skills = []
        for p in pos_list:
            result_skills += skills_by_pos.get(p, [])
        return ','.join(str(x) for x in result_skills if x)

    # key: (player_name, uid, heroes_key) -> stats
    team_map = defaultdict(lambda: {'cnt':0,'wins':0,'draws':0,'union':'','uid':'','hero_stars':[0,0,0],'skills':'','heroes_str':'','clan_name':'','max_troops':0})

    for row in rows:
        r = dict(row)
        atk_name = r.get('atk_name', '')
        atk_uid = r.get('atk_uid', '')
        atk_union = r.get('atk_union', '')
        def_name = r.get('def_name', '')
        def_union = r.get('def_union', '')
        result = r.get('result', 0)
        atk_hero_info = r.get('attack_all_hero_info', '')
        def_hero_info = r.get('defend_all_hero_info', '')
        skill_info = r.get('all_skill_info', '')
        ah1, ah2, ah3 = r.get('atk_hero1_id', 0), r.get('atk_hero2_id', 0), r.get('atk_hero3_id', 0)
        dh1, dh2, dh3 = r.get('def_hero1_id', 0), r.get('def_hero2_id', 0), r.get('def_hero3_id', 0)
        atk_advance = r.get('atk_advance', '')
        def_advance = r.get('def_advance', '')
        atk_clan_name = r.get('attack_clan_name', '')
        def_clan_name = r.get('defend_clan_name', '')
        atk_power = int(r.get('atk_power', 0) or 0)

        # 攻方
        if side in ('', 'atk') and atk_name and atk_name.strip():
            heroes_ids = parse_hero_info(atk_hero_info)
            if not heroes_ids:
                heroes_ids = '+'.join(str(x) for x in [ah1,ah2,ah3] if x)
            if heroes_ids:
                stars = parse_advance_stars(atk_advance)
                skills = parse_skill_info(skill_info, [1,2,3])
                k = (atk_name.strip(), str(atk_uid or ''), heroes_ids)
                d = team_map[k]
                d['cnt'] += 1
                d['union'] = atk_union or ''
                d['uid'] = str(atk_uid or '')
                d['clan_name'] = atk_clan_name or ''
                d['hero_stars'] = [max(d['hero_stars'][i], stars[i]) for i in range(3)]
                d['skills'] = skills
                d['heroes_str'] = heroes_ids
                d['max_troops'] = max(d['max_troops'], atk_power)
                if result in (1,7,11):
                    d['wins'] += 1
                elif result not in (1,2,6,7,11,12):
                    d['draws'] += 1

        # 守方
        if side in ('', 'def') and def_name and def_name.strip():
            heroes_ids = parse_hero_info(def_hero_info)
            if not heroes_ids:
                heroes_ids = '+'.join(str(x) for x in [dh1,dh2,dh3] if x)
            if heroes_ids:
                stars = parse_advance_stars(def_advance)
                skills = parse_skill_info(skill_info, [6,5,4])
                k = (def_name.strip(), '', heroes_ids)
                d = team_map[k]
                d['cnt'] += 1
                d['union'] = def_union or ''
                d['uid'] = ''
                d['clan_name'] = def_clan_name or ''
                d['hero_stars'] = [max(d['hero_stars'][i], stars[i]) for i in range(3)]
                d['skills'] = skills
                d['heroes_str'] = heroes_ids
                if result in (2,6,12):
                    d['wins'] += 1
                elif result not in (1,2,6,7,11,12):
                    d['draws'] += 1

    data = []
    for (pname, uid, heroes_ids), stat in team_map.items():
        cnt = stat['cnt']
        wins = stat['wins']
        draws = stat['draws']
        data.append({
            'player_name': pname,
            'uid': stat['uid'],
            'union': stat['union'],
            'clan_name': stat['clan_name'],
            'heroes_str': stat['heroes_str'],
            'hero_stars': stat['hero_stars'],
            'skills': stat['skills'],
            'max_troops': stat['max_troops'],
            'has_troops': 1 if stat['max_troops'] > 0 else 0,
            'cnt': cnt,
            'wins': wins,
            'draws': draws,
            'win_rate': round((wins + draws * 0.5)/cnt*100,1) if cnt else 0,
        })

    # 回退：若 battles_v2 直解析结果为空，则改用 battle_heroes 聚合，避免因字段为空导致 0 条
    if not data:
        conn = get_db()
        from collections import defaultdict as _dd
        team2 = _dd(lambda: {'cnt':0,'wins':0,'draws':0,'union':'','uid':'','clan_name':'','hero_stars':[0,0,0],'skills':'','max_troops':0})

        where_common = ["b.result != 6"]
        if 'is_npc' in bv_cols:
            where_common.append("COALESCE(b.is_npc,0)=0")
        if 'isnpc' in bv_cols:
            where_common.append("COALESCE(b.isnpc,0)=0")
        if player:
            where_common.append("(b.atk_name LIKE ? OR b.def_name LIKE ?)")
        if union:
            where_common.append("(b.atk_union LIKE ? OR b.def_union LIKE ?)")
        w_common = ' AND '.join(where_common)
        p_common = []
        if player:
            p_common += [f'%{player}%', f'%{player}%']
        if union:
            p_common += [f'%{union}%', f'%{union}%']

        rows2 = []
        if side in ('', 'atk'):
            rows2 += conn.execute(f'''
                SELECT b.battle_id, b.result, b.atk_name as pname, COALESCE(b.atk_uid,'') as uid,
                       COALESCE(b.atk_union,'') as union_name, COALESCE(b.attack_clan_name,'') as clan_name,
                       COALESCE(b.atk_power, 0) as atk_power,
                       bh.pos, bh.hero_id
                FROM battles_v2 b
                JOIN battle_heroes bh ON bh.battle_id=b.battle_id AND bh.side='atk'
                WHERE {w_common} AND COALESCE(b.atk_name,'') != ''
                ORDER BY b.battle_id DESC, bh.pos ASC
            ''', p_common).fetchall()
        if side in ('', 'def'):
            rows2 += conn.execute(f'''
                SELECT b.battle_id, b.result, b.def_name as pname, '' as uid,
                       COALESCE(b.def_union,'') as union_name, COALESCE(b.defend_clan_name,'') as clan_name,
                       0 as atk_power,
                       bh.pos, bh.hero_id
                FROM battles_v2 b
                JOIN battle_heroes bh ON bh.battle_id=b.battle_id AND bh.side='def'
                WHERE {w_common} AND COALESCE(b.def_name,'') != ''
                ORDER BY b.battle_id DESC, bh.pos ASC
            ''', p_common).fetchall()
        conn.close()

        battle_map = _dd(list)
        battle_meta = {}
        for r2 in rows2:
            bid = int(r2[0] or 0)
            result2 = int(r2[1] or 0)
            pname2 = (r2[2] or '').strip()
            uid2 = str(r2[3] or '')
            union2 = r2[4] or ''
            clan2 = r2[5] or ''
            power2 = int(r2[6] or 0)
            pos2 = int(r2[7] or 0)
            hid2 = int(r2[8] or 0)
            if not pname2 or hid2 <= 0:
                continue
            key_b = (pname2, uid2, bid)
            battle_map[key_b].append((pos2, hid2))
            battle_meta[key_b] = (union2, clan2, result2, power2)

        for (pname2, uid2, bid), hs in battle_map.items():
            hs.sort(key=lambda x: x[0])
            heroes_ids2 = '+'.join(str(hid) for _, hid in hs[:3])
            if not heroes_ids2:
                continue
            union2, clan2, result2, power2 = battle_meta[(pname2, uid2, bid)]
            key2 = (pname2, uid2, heroes_ids2)
            d2 = team2[key2]
            d2['cnt'] += 1
            d2['union'] = union2
            d2['uid'] = uid2
            d2['clan_name'] = clan2
            d2['max_troops'] = max(d2['max_troops'], power2)
            if uid2:
                if result2 in (1,7,11):
                    d2['wins'] += 1
                elif result2 not in (1,2,6,7,11,12):
                    d2['draws'] += 1
            else:
                if result2 in (2,6,12):
                    d2['wins'] += 1
                elif result2 not in (1,2,6,7,11,12):
                    d2['draws'] += 1

        data = []
        for (pname2, uid2, heroes_ids2), stat2 in team2.items():
            cnt2 = stat2['cnt']
            wins2 = stat2['wins']
            draws2 = stat2['draws']
            data.append({
                'player_name': pname2,
                'uid': uid2,
                'union': stat2['union'],
                'clan_name': stat2['clan_name'],
                'heroes_str': heroes_ids2,
                'hero_stars': stat2['hero_stars'],
                'skills': stat2['skills'],
                'max_troops': stat2['max_troops'],
                'has_troops': 1 if stat2['max_troops'] > 0 else 0,
                'cnt': cnt2,
                'wins': wins2,
                'draws': draws2,
                'win_rate': round((wins2 + draws2 * 0.5)/cnt2*100,1) if cnt2 else 0,
        })
    # 按玩家名排序，同玩家按出场数降序
    data.sort(key=lambda x: (x['player_name'], -x['cnt']))

    if debug_mode:
        return jsonify({
            'debug_count': {
                'stage1_rows': len(rows),
                'stage1_groups': len(team_map),
                'final_rows': len(data),
                'used_fallback': len(team_map) == 0,
                'cache_hit': False,
            },
            'debug_ctx': {
                'db_path': _db_path,
                'profile_id': _pid,
                'side': side,
                'player': player,
                'union': union,
                'where_stage1': where,
            },
            'data': data,
        })

    # 写入缓存
    cache_data[cache_key] = {'ts': now, 'result': data}
    return jsonify(data)


# ===== 战报v2详情 =====
@app.route('/api/battles_v2/<int:bid>')
def api_battle_v2_detail(bid):
    conn = get_db()
    battle = conn.execute('SELECT * FROM battles_v2 WHERE battle_id=?', (bid,)).fetchone()
    if not battle:
        conn.close()
        return jsonify({'error': 'not found'}), 404
    heroes = conn.execute(
        'SELECT * FROM battle_heroes WHERE battle_id=? ORDER BY side,pos', (bid,)
    ).fetchall()
    b = dict(battle)
    # 直接从数据库字段构建 extra，不再读原始文件
    extra = {
        'all_skill_info': b.get('all_skill_info', ''),
        'atk_advance':    b.get('atk_advance', '') or b.get('attack_advance', ''),
        'def_advance':    b.get('def_advance', '') or b.get('defend_advance', ''),
        'atk_gear_info':  b.get('atk_gear_info', '') or b.get('attacker_gear_info', ''),
        'def_gear_info':  b.get('def_gear_info', '') or b.get('defender_gear_info', ''),
        'wid_name':       b.get('wid_name', ''),
        'is_npc':         b.get('is_npc', 0),
        'is_ai':          b.get('is_ai', 0),
        'weather':        b.get('weather', 0),
        'in_night':       b.get('in_night', 0) or b.get('in_night_mode', 0),
        'atk_hp':         b.get('atk_hp', 0) or b.get('attack_hp', 0),
        'def_hp':         b.get('def_hp', 0) or b.get('defend_hp', 0),
        'atk_hero1_star': b.get('atk_hero1_star', 0),
        'atk_hero2_star': b.get('atk_hero2_star', 0),
        'atk_hero3_star': b.get('atk_hero3_star', 0),
        'def_hero1_star': b.get('def_hero1_star', 0),
        'def_hero2_star': b.get('def_hero2_star', 0),
        'def_hero3_star': b.get('def_hero3_star', 0),
        'atk_hero_type':  b.get('atk_hero_type', '') or b.get('attack_hero_type', ''),
        'def_hero_type':  b.get('def_hero_type', '') or b.get('defend_hero_type', ''),
        'attack_all_sub_hero_info': b.get('attack_all_sub_hero_info', ''),
        'defend_all_sub_hero_info': b.get('defend_all_sub_hero_info', ''),
    }
    conn.close()
    return jsonify({'battle': b, 'heroes': [dict(h) for h in heroes], 'extra': extra})


# ===== 战报v2 =====
@app.route('/api/battles_v2')
def api_battles_v2():
    page   = int(request.args.get('page', 1))
    size   = int(request.args.get('size', 30))
    player = request.args.get('player', '')
    union  = request.args.get('union', '')
    ftype  = request.args.get('fight_type', '')
    period = request.args.get('period', '')
    offset = (page - 1) * size
    where = ['1=1']; params = []
    if player: where.append('atk_name LIKE ?'); params.append(f'%{player}%')
    if union:  where.append('def_union LIKE ?'); params.append(f'%{union}%')
    if ftype:  where.append('fight_type=?'); params.append(int(ftype))
    if period == '24h':
        where.append(f'time >= {int(__import__("time").time())-86400}')
    w = ' AND '.join(where)
    conn = get_db()
    total = conn.execute(f'SELECT COUNT(*) FROM battles_v2 WHERE {w}', params).fetchone()[0]
    rows  = conn.execute(f'SELECT * FROM battles_v2 WHERE {w} ORDER BY time DESC LIMIT ? OFFSET ?',
                         params+[size, offset]).fetchall()
    conn.close()
    return jsonify({'total': total, 'page': page, 'size': size, 'data': [dict(r) for r in rows]})


# ===== SSE 实时推送 =====
@app.route('/api/stream')
def api_stream():
    """SSE 接口：每个连接独立队列，支持多客户端并发"""
    q = subscribe()
    def generate():
        try:
            # 先回放最近事件
            with _event_lock:
                snapshot = list(recent_events)
            for evt in snapshot:
                yield f"data: {json.dumps(evt, ensure_ascii=False)}\n\n"
            # 持续监听自己的队列
            while True:
                try:
                    evt = q.get(timeout=25)
                    yield f"data: {json.dumps(evt, ensure_ascii=False)}\n\n"
                except Exception:
                    # timeout -> 发 ping 保活
                    yield f"data: {json.dumps({'type':'ping','ts':time.strftime('%H:%M:%S')}, ensure_ascii=False)}\n\n"
        finally:
            unsubscribe(q)
    return Response(
        stream_with_context(generate()),
        mimetype='text/event-stream',
        headers={
            'Cache-Control': 'no-cache',
            'X-Accel-Buffering': 'no',
            'Connection': 'keep-alive',
        }
    )


@app.route('/api/recent_events')
def api_recent_events():
    """返回最近100条事件（轮询备用）"""
    with _event_lock:
        evts = list(recent_events)
    return jsonify(evts)


@app.route('/api/battle_monitor_13a2')
def api_battle_monitor_13a2():
    cap_root = os.path.join(BASE_DIR, 'capture_new')
    latest_file = ''
    latest_mtime = 0
    try:
        for root, _, files in os.walk(cap_root):
            if os.path.basename(root) != '000013a2':
                continue
            for name in files:
                if not name.endswith('_000013a2_plain_str.txt'):
                    continue
                fp = os.path.join(root, name)
                try:
                    mt = os.path.getmtime(fp)
                except Exception:
                    continue
                if mt >= latest_mtime:
                    latest_mtime = mt
                    latest_file = fp
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)})

    if not latest_file:
        return jsonify({'ok': False, 'error': '未找到 000013a2 报文'})

    try:
        with open(latest_file, 'r', encoding='utf-8') as f:
            packet_text = f.read().strip()
    except Exception as e:
        return jsonify({'ok': False, 'error': f'读取报文失败: {e}'})

    payload = _parse_13a2_payload(packet_text)
    if not payload:
        return jsonify({'ok': False, 'error': '000013a2 报文解析失败', 'latest_file': latest_file, 'packet_text': packet_text[:1000]})

    conn = get_db()
    try:
        skill_map = {}
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
                if len(segs) < 2 or not segs[0].lstrip('-').isdigit():
                    continue
                pos = int(segs[0])
                skills = []
                for i in range(1, len(segs), 2):
                    sid = segs[i] if i < len(segs) else ''
                    lv = segs[i + 1] if i + 1 < len(segs) else '0'
                    if str(sid).lstrip('-').isdigit() and int(sid) > 0:
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

        for item in payload['items']:
            tid = int(item.get('team_id') or 0)
            if tid <= 0:
                item['lineup'] = {'battle_id': 0, 'side': '', 'heroes': []}
                item['team_stats'] = {'battles': 0, 'wins': 0, 'draws': 0, 'loses': 0, 'win_rate': 0}
                continue
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
            item['team_stats'] = {
                'battles': battles,
                'wins': wins,
                'draws': draws,
                'loses': loses,
                'win_rate': round((wins + draws * 0.5) * 100 / battles, 1) if battles else 0,
            }
            recent_rows = conn.execute(
                '''SELECT battle_id, time, time_str, result,
                          atk_team_id, def_team_id,
                          atk_name, def_name,
                          atk_hero1_id, atk_hero2_id, atk_hero3_id,
                          def_hero1_id, def_hero2_id, def_hero3_id
                   FROM battles_v2
                   WHERE (atk_team_id=? OR def_team_id=?)
                   ORDER BY time DESC, battle_id DESC
                   LIMIT 12''',
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
            for rr in recent_rows:
                rr = dict(rr)
                rr_side = 'atk' if int(rr.get('atk_team_id', 0) or 0) == tid else 'def'
                opp_prefix = 'def' if rr_side == 'atk' else 'atk'
                opp_name = rr.get('def_name' if rr_side == 'atk' else 'atk_name', '') or '未知对手'
                opp_ids = _hero_ids_from_row(rr, opp_prefix)
                outcome = '胜' if _is_team_win(rr_side, rr.get('result', 0)) else ('平' if _is_team_draw(rr.get('result', 0)) else '负')
                recent_battles.append({
                    'battle_id': int(rr.get('battle_id', 0) or 0),
                    'time': int(rr.get('time', 0) or 0),
                    'time_str': str(rr.get('time_str', '') or ''),
                    'result_text': outcome,
                    'opponent_name': opp_name,
                    'opponent_hero_ids': opp_ids,
                    'opponent_heroes_text': _hero_text(opp_ids),
                })
            for rr in matchup_rows:
                rr = dict(rr)
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
            item['team_matchups'] = {
                'recent_battles': recent_battles[:6],
                'favored': win_matchups[:3],
                'countered': lose_matchups[:3],
            }
            row = conn.execute(
                '''SELECT battle_id, time_str, all_skill_info,
                          atk_team_id, def_team_id,
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
                   LIMIT 1''',
                (tid, tid)
            ).fetchone()
            if not row:
                item['lineup'] = {'battle_id': 0, 'side': '', 'heroes': []}
                continue
            row = dict(row)
            side = 'atk' if int(row.get('atk_team_id', 0) or 0) == tid else 'def'
            parsed_skills = _parse_skill_info(row.get('all_skill_info', ''))
            prefix = 'atk' if side == 'atk' else 'def'
            advance_stars = _parse_advance_stars(row.get('atk_advance' if side == 'atk' else 'def_advance', ''))
            heroes = []
            for pos in (1, 2, 3):
                hero_id = int(row.get(f'{prefix}_hero{pos}_id', 0) or 0)
                if hero_id <= 0:
                    continue
                skill_pos = pos if side == 'atk' else pos + 3
                fallback_star = int(row.get(f'{prefix}_hero{pos}_star', 0) or 0)
                advance_star = advance_stars[pos - 1] if pos - 1 < len(advance_stars) else 0
                heroes.append({
                    'pos': pos,
                    'hero_id': hero_id,
                    'level': int(row.get(f'{prefix}_hero{pos}_level', 0) or 0),
                    'star': int(advance_star or fallback_star or 0),
                    'skills': parsed_skills.get(skill_pos, []),
                })
            item['lineup'] = {
                'battle_id': int(row.get('battle_id', 0) or 0),
                'side': side,
                'time_str': str(row.get('time_str', '') or ''),
                'heroes': heroes,
            }
    finally:
        conn.close()

    return jsonify({
        'ok': True,
        'latest_file': latest_file,
        'updated_at': time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(latest_mtime or time.time())),
        'packet_text': packet_text,
        'summary': {
            'teams': payload['teams_count'],
            'subjects': payload['subjects_count'],
            'cells': payload['cells_count'],
            'area_range': payload['area_range'],
        },
        'packet': {
            'marker': payload['marker'],
            'area_range': payload['area_range'],
        },
        'items': payload['items'],
        'cells': payload['cells'],
    })


@app.route('/api/writer_stats')
def api_writer_stats():
    """返回 realtime_writer 统计"""
    return jsonify(_writer.stats)


def _health_component(status, label, detail='', **extra):
    return {
        'status': status,
        'label': label,
        'detail': detail,
        **extra,
    }


@app.route('/api/hud/health', methods=['GET'])
def api_hud_health():
    writer_stats = dict(getattr(_writer, 'stats', {}) or {})
    writer_errors = int(writer_stats.get('errors') or 0)
    engine_path = (
        Path(BASE_DIR)
        / 'battle-engine/build/install/stzb-battle-engine/bin/stzb-battle-engine'
    )
    portrait_manifest = os.path.join(
        RESOURCE_DIR,
        'static',
        'hero-portraits',
        'manifest.json',
    )
    components = {
        'backend': _health_component('live', '后端', 'Flask API 可用'),
        'writer': _health_component(
            'degraded' if writer_errors else 'live',
            '实时入库',
            f'errors={writer_errors}',
            stats=writer_stats,
        ),
        'battleEngine': _health_component(
            'live' if engine_path.is_file() else 'unknown',
            'Kotlin 引擎',
            str(engine_path),
        ),
        'portraits': _health_component(
            'live' if os.path.isfile(portrait_manifest) else 'unknown',
            '画像资源',
            portrait_manifest,
        ),
    }
    statuses = {component['status'] for component in components.values()}
    overall = (
        'degraded'
        if 'degraded' in statuses
        else 'live'
        if statuses == {'live'}
        else 'unknown'
    )
    return jsonify({
        'ok': True,
        'overall': overall,
        'components': components,
    })



# ===== 分组武勋统计 (照搬 stzbHelper GetGroupWu) =====
@app.route('/api/group_wu')
def api_group_wu():
    pid = get_current_pid()
    conn = get_db()
    rows = conn.execute('''
        SELECT
            tu.group_name as `group`,
            COUNT(*) as member_count,
            SUM(tu.wuxun) as total_wu,
            ROUND(AVG(tu.wuxun)) as average_wu,
            SUM(CASE WHEN tu.wuxun = 0 THEN 1 ELSE 0 END) as zero_wu_count
        FROM team_users tu
        WHERE tu.profile_id=? AND tu.group_name != ""
        GROUP BY tu.group_name
        ORDER BY total_wu DESC
    ''', (pid,)).fetchall()
    conn.close()
    return jsonify([dict(r) for r in rows])


# ===== 攻城考勤任务 (照搬 stzbHelper Task) =====
def _init_task_tables():
    conn = get_db()
    _ensure_task_tables(conn)
    conn.close()

@app.route('/api/tasks')
def api_task_list():
    pid = get_current_pid()
    conn = get_db()
    rows = conn.execute('SELECT id,status,name,time,pos,target_groups,target_user_num,complete_user_num,created_at FROM tasks WHERE profile_id=? ORDER BY id DESC', (pid,)).fetchall()
    # 从 battles_v2 反查 wid_name
    wid_names = {}
    try:
        wn_rows = conn.execute("SELECT DISTINCT wid, wid_name FROM battles_v2 WHERE wid_name != '' AND wid_name IS NOT NULL").fetchall()
        for w in wn_rows:
            wid_names[str(w[0])] = w[1]
    except: pass
    conn.close()
    result = []
    for r in rows:
        d = dict(r)
        try: d['target_groups'] = json.loads(d['target_groups'])
        except: d['target_groups'] = []
        # 附加坐标显示和城池名
        pos = str(d.get('pos', ''))
        d['wid_name'] = wid_names.get(pos, '')
        try:
            wid_int = int(pos)
            d['pos_xy'] = f'{wid_int // 10000},{wid_int % 10000}'
        except:
            d['pos_xy'] = pos
        result.append(d)
    return jsonify(result)

@app.route('/api/tasks', methods=['POST'])
def api_task_create():
    data = request.json or {}
    name = data.get('name', '').strip()
    task_time = int(data.get('time', 0))
    pos = str(data.get('pos', '')).strip()
    groups = data.get('groups', [])
    uids   = data.get('uids', None)  # 智能分配指定uid列表
    if not name or not pos:
        return jsonify({'error': '参数错误: 缺少name或pos'}), 400
    # 支持 "X,Y" 格式转为 wid 整数（同 stzbHelper ToTaskPos 逻辑）
    if ',' in pos:
        try:
            parts = pos.split(',')
            x = int(parts[0].strip())
            y = int(parts[1].strip())
            pos = str(x * 10000 + y)
        except:
            return jsonify({'error': 'pos坐标格式错误，请输入WID整数或X,Y格式'}), 400
    else:
        try: int(pos)
        except: return jsonify({'error': 'pos坐标格式错误'}), 400
    conn = get_db()
    pid = get_current_pid()
    # 查询目标分组成员
    if uids:
        placeholders = ','.join('?' * len(uids))
        users = conn.execute(f'SELECT uid,name,group_name,wuxun FROM team_users WHERE profile_id=? AND uid IN ({placeholders})', [pid]+uids).fetchall()
    elif groups:
        placeholders = ','.join('?' * len(groups))
        users = conn.execute(f'SELECT uid,name,group_name,wuxun FROM team_users WHERE profile_id=? AND group_name IN ({placeholders})', [pid]+groups).fetchall()
    else:
        users = conn.execute('SELECT uid,name,group_name,wuxun FROM team_users WHERE profile_id=?', (pid,)).fetchall()
    if not users:
        conn.close()
        return jsonify({'error': '目标人数为0，请先同步成员数据'}), 400
    user_list = {}
    for u in users:
        user_list[str(u['uid'])] = {
            'uid': u['uid'], 'name': u['name'], 'group': u['group_name'],
            'atk_num': 0, 'dis_num': 0, 'atk_team_num': 0, 'dis_team_num': 0
        }
    conn.execute(
        'INSERT INTO tasks (name,time,pos,target_groups,target_user_num,user_list,profile_id) VALUES (?,?,?,?,?,?,?)',
        (name, task_time, pos, json.dumps(groups, ensure_ascii=False),
         len(users), json.dumps(user_list, ensure_ascii=False), get_current_pid())
    )
    conn.commit()
    conn.close()
    return jsonify({'ok': True, 'msg': f'创建成功，目标{len(users)}人'})

@app.route('/api/tasks/<int:tid>')
def api_task_get(tid):
    conn = get_db()
    row = conn.execute('SELECT * FROM tasks WHERE id=?', (tid,)).fetchone()
    conn.close()
    if not row:
        return jsonify({'error': 'not found'}), 404
    d = dict(row)
    try: d['target_groups'] = json.loads(d['target_groups'])
    except: d['target_groups'] = []
    try: d['user_list'] = json.loads(d['user_list'])
    except: d['user_list'] = {}
    return jsonify(d)

@app.route('/api/tasks/<int:tid>', methods=['DELETE'])
def api_task_delete(tid):
    conn = get_db()
    conn.execute('DELETE FROM tasks WHERE id=?', (tid,))
    conn.execute('DELETE FROM task_reports WHERE task_id=?', (tid,))
    conn.commit()
    conn.close()
    return jsonify({'ok': True, 'msg': '删除成功'})

@app.route('/api/tasks/<int:tid>/report_count')
def api_task_report_count(tid):
    conn = get_db()
    task = conn.execute('SELECT pos FROM tasks WHERE id=?', (tid,)).fetchone()
    if not task:
        conn.close()
        return jsonify({'error': 'not found'}), 404
    cnt = conn.execute('SELECT COUNT(*) FROM task_reports WHERE task_id=?', (tid,)).fetchone()[0]
    conn.close()
    return jsonify({'count': cnt})

@app.route('/api/tasks/<int:tid>/statistics', methods=['POST'])
def api_task_statistics(tid):
    """统计考勤：按 battles_v2 中匹配 wid/pos 的战报统计每人出战次数"""
    conn = get_db()
    task = conn.execute('SELECT * FROM tasks WHERE id=?', (tid,)).fetchone()
    if not task:
        conn.close()
        return jsonify({'error': 'not found'}), 404
    d = dict(task)
    pos = d['pos']
    try: pos = int(pos)
    except: return jsonify({'error': 'pos格式错误'}), 400
    try: user_list = json.loads(d['user_list'])
    except: user_list = {}

    # 兼容不同库结构
    bv_cols = {r[1] for r in conn.execute("PRAGMA table_info(battles_v2)").fetchall()}
    has_garrison = 'garrison' in bv_cols
    has_atk_hero1 = 'atk_hero1_id' in bv_cols

    complete = 0
    for uid, u in user_list.items():
        name = u['name']
        if has_garrison:
            # 主力次数（garrison=0，攻方出战）
            atk_num = conn.execute(
                'SELECT COUNT(*) FROM battles_v2 WHERE wid=? AND atk_name=? AND garrison=0',
                (pos, name)
            ).fetchone()[0]
            # 拆迁次数（garrison=1，攻方出战）
            dis_num = conn.execute(
                'SELECT COUNT(*) FROM battles_v2 WHERE wid=? AND atk_name=? AND garrison=1',
                (pos, name)
            ).fetchone()[0]
        else:
            # 老/精简库没有 garrison，统一按总出战统计
            atk_num = conn.execute(
                'SELECT COUNT(*) FROM battles_v2 WHERE wid=? AND atk_name=?',
                (pos, name)
            ).fetchone()[0]
            dis_num = 0

        if has_garrison and has_atk_hero1:
            # 主力队伍数（按主将ID去重，不同主将算不同队伍）
            atk_team_num = conn.execute(
                'SELECT COUNT(DISTINCT atk_hero1_id) FROM battles_v2 WHERE wid=? AND atk_name=? AND garrison=0 AND atk_hero1_id IS NOT NULL AND atk_hero1_id != 0',
                (pos, name)
            ).fetchone()[0]
            # 拆迁队伍数
            dis_team_num = conn.execute(
                'SELECT COUNT(DISTINCT atk_hero1_id) FROM battles_v2 WHERE wid=? AND atk_name=? AND garrison=1 AND atk_hero1_id IS NOT NULL AND atk_hero1_id != 0',
                (pos, name)
            ).fetchone()[0]
        elif has_atk_hero1:
            atk_team_num = conn.execute(
                'SELECT COUNT(DISTINCT atk_hero1_id) FROM battles_v2 WHERE wid=? AND atk_name=? AND atk_hero1_id IS NOT NULL AND atk_hero1_id != 0',
                (pos, name)
            ).fetchone()[0]
            dis_team_num = 0
        else:
            # 没有 atk_hero1_id 时，从 battle_heroes 侧按 pos=0 去重
            atk_team_num = conn.execute(
                '''SELECT COUNT(DISTINCT bh.hero_id)
                   FROM battle_heroes bh
                   JOIN battles_v2 bv ON bv.battle_id = bh.battle_id
                   WHERE bv.wid=? AND bv.atk_name=? AND bh.side='atk' AND bh.pos=0 AND bh.hero_id IS NOT NULL AND bh.hero_id != 0''',
                (pos, name)
            ).fetchone()[0]
            dis_team_num = 0
        user_list[uid]['atk_num'] = atk_num
        user_list[uid]['dis_num'] = dis_num
        user_list[uid]['atk_team_num'] = atk_team_num
        user_list[uid]['dis_team_num'] = dis_team_num
        if atk_num > 0 or dis_num > 0:
            complete += 1

    conn.execute(
        'UPDATE tasks SET user_list=?, complete_user_num=?, status=1 WHERE id=?',
        (json.dumps(user_list, ensure_ascii=False), complete, tid)
    )
    conn.commit()
    conn.close()
    return jsonify({'ok': True, 'msg': f'统计完成，实到{complete}人'})

@app.route('/api/tasks/<int:tid>/clear_reports', methods=['DELETE'])
def api_task_clear_reports(tid):
    conn = get_db()
    conn.execute('DELETE FROM task_reports WHERE task_id=?', (tid,))
    conn.commit()
    conn.close()
    return jsonify({'ok': True, 'msg': '清理战报成功'})


# ===== 按距离自动分配玩家到任务 =====
@app.route('/api/tasks/nearby_players')
def api_tasks_nearby_players():
    """输入城池坐标，返回按距离排序的成员列表"""
    pos_raw = request.args.get('pos', '').strip()
    limit   = int(request.args.get('limit', 20))
    group   = request.args.get('group', '')
    if not pos_raw:
        return jsonify({'error': '缺少pos参数'}), 400
    # 解析目标坐标
    try:
        if ',' in pos_raw:
            parts = pos_raw.split(',')
            tx = int(parts[0].strip())
            ty = int(parts[1].strip())
        else:
            wid = int(pos_raw)
            tx = wid // 10000
            ty = wid % 10000
    except:
        return jsonify({'error': 'pos格式错误'}), 400

    conn = get_db()
    pid = get_current_pid()
    if group:
        rows = conn.execute('SELECT uid, name, group_name, wid, power FROM team_users WHERE profile_id=? AND wid IS NOT NULL AND wid != 0 AND group_name=?', (pid, group,)).fetchall()
    else:
        rows = conn.execute('SELECT uid, name, group_name, wid, power FROM team_users WHERE profile_id=? AND wid IS NOT NULL AND wid != 0', (pid,)).fetchall()
    conn.close()

    import math
    result = []
    for r in rows:
        wid = r['wid']
        px = wid // 10000
        py = wid % 10000
        dist = math.sqrt((px - tx) ** 2 + (py - ty) ** 2)
        result.append({
            'uid': r['uid'],
            'name': r['name'],
            'group': r['group_name'],
            'wid': wid,
            'pos_xy': f'{px},{py}',
            'power': r['power'],
            'dist': round(dist, 1)
        })
    result.sort(key=lambda x: x['dist'])
    return jsonify(result[:limit])


# ===== 队伍查询 (照搬 stzbHelper GetPlayerTeam SQL) =====
@app.route('/api/player_team_query')
def api_player_team_query():
    """按玩家名/同盟名查询去重后的队伍阵容，使用 battles_v2 + battle_heroes"""
    name   = request.args.get('name', '')
    union  = request.args.get('union', '')
    nextid = int(request.args.get('nextid', 0))
    limit  = int(request.args.get('limit', 30))
    conn = get_db()
    where = ['b.fight_type IN (80,33,0)']
    params = []
    if name:  where.append('b.atk_name LIKE ?'); params.append(f'%{name}%')
    if union: where.append('b.def_union LIKE ?'); params.append(f'%{union}%')
    if nextid > 0: where.append('b.battle_id < ?'); params.append(nextid)
    w = ' AND '.join(where)
    rows = conn.execute(f'''
        SELECT
            b.battle_id, b.atk_name as player_name, b.def_union as union_name,
            b.time, b.result, b.fight_type, b.wid_code,
            GROUP_CONCAT(h.hero_name, ',') as heroes_str,
            GROUP_CONCAT(h.hero_id, ',') as hero_ids,
            GROUP_CONCAT(h.level, ',') as hero_levels
        FROM battles_v2 b
        LEFT JOIN battle_heroes h ON h.battle_id=b.battle_id AND h.side='atk'
        WHERE {w}
        GROUP BY b.battle_id
        ORDER BY b.time DESC
        LIMIT ?
    ''', params + [limit]).fetchall()
    conn.close()
    return jsonify([dict(r) for r in rows])


# ===== 5028/000013a4 战场监控 =====
def _safe_read_json(path):
    try:
        with open(path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception:
        return None


def _extract_monitor_from_13a4(data):
    if not isinstance(data, list):
        return None
    team_ids = []
    if len(data) > 7 and isinstance(data[7], list):
        team_ids = [int(x) for x in data[7] if str(x).isdigit() or isinstance(x, int)]
    marker = data[18] if len(data) > 18 else None
    state_info = data[20] if len(data) > 20 else []
    return {
        'team_ids': team_ids,
        'marker': marker if isinstance(marker, int) else 0,
        'state': state_info if isinstance(state_info, list) else [],
        'raw_len': len(data),
    }


@app.route('/api/battle_monitor')
def api_battle_monitor():
    """从最新 000013a4 报文提取队伍ID，并映射同盟成员库实时显示"""
    cap_dir = ''
    try:
        if os.path.exists(PROFILE_FILE):
            with open(PROFILE_FILE, 'r', encoding='utf-8') as f:
                p = json.load(f)
            cap_dir = p.get('cap_dir', '')
    except Exception:
        cap_dir = ''
    if not cap_dir:
        return jsonify({'ok': False, 'error': '未找到抓包目录', 'items': []}), 400

    packet_dir = os.path.join(cap_dir, '000013a4')
    if not os.path.isdir(packet_dir):
        return jsonify({'ok': True, 'items': [], 'latest_file': '', 'updated_at': '', 'summary': {'teams': 0, 'matched_members': 0}})

    txt_files = [os.path.join(packet_dir, x) for x in os.listdir(packet_dir) if x.endswith('_plain_str.txt')]
    json_files = [os.path.join(packet_dir, x) for x in os.listdir(packet_dir) if x.endswith('.json')]
    if not txt_files and not json_files:
        return jsonify({'ok': True, 'items': [], 'latest_file': '', 'updated_at': '', 'summary': {'teams': 0, 'matched_members': 0}})

    plain_text = ''
    plain_txt = ''
    latest = ''

    if txt_files:
        txt_files.sort(key=lambda p: os.path.getmtime(p), reverse=True)
        plain_txt = txt_files[0]
        try:
            with open(plain_txt, 'r', encoding='utf-8', errors='replace') as f:
                plain_text = f.read().strip()
        except Exception:
            plain_text = ''
        if json_files:
            plain_base = os.path.basename(plain_txt).replace('_plain_str.txt', '')
            matched_json = [p for p in json_files if os.path.basename(p).startswith(plain_base)]
            if matched_json:
                matched_json.sort(key=lambda p: os.path.getmtime(p), reverse=True)
                latest = matched_json[0]
        if not latest and json_files:
            json_files.sort(key=lambda p: os.path.getmtime(p), reverse=True)
            latest = json_files[0]
    else:
        json_files.sort(key=lambda p: os.path.getmtime(p), reverse=True)
        latest = json_files[0]
        try:
            plain_txt = latest.replace('_plain.json', '_plain_str.txt')
            if plain_txt == latest or not os.path.exists(plain_txt):
                plain_txt = os.path.splitext(latest)[0] + '_plain_str.txt'
            if os.path.exists(plain_txt):
                with open(plain_txt, 'r', encoding='utf-8', errors='replace') as f:
                    plain_text = f.read().strip()
            else:
                plain_txt = ''
        except Exception:
            plain_text = ''
            plain_txt = ''

    parsed = parse_battle_monitor_13a4(plain_text or _safe_read_json(latest)) or {'team_ids': [], 'marker': 0, 'state': [], 'raw_len': 0}

    display_file = os.path.basename(plain_txt) if plain_txt else os.path.basename(latest)

    conn = get_db()
    payload = build_battle_monitor_payload(conn, parsed, display_file, plain_text)
    conn.close()

    file_mtime = os.path.getmtime(plain_txt or latest) if (plain_txt or latest) else time.time()
    updated_at = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(file_mtime))
    payload.update({
        'ok': True,
        'latest_file': display_file,
        'json_file': os.path.basename(latest) if latest else '',
        'updated_at': updated_at,
    })
    return jsonify(payload)


# ===== 攻城战场态势 =====
@app.route('/api/battle_field')
def api_battle_field():
    """攻城战场实时动态：哪些城池正在被打，以及附近有哪些成员"""
    conn = get_db()
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
    rows = conn.execute('''
        SELECT bf.wid, bf.attacker_uid, bf.nearby_uids, bf.nearby_count,
               bf.cap_time, bf.captured_at,
               tu.name as attacker_name, tu.group_name as attacker_group,
               '' as city_name, NULL as x, NULL as y, NULL as cell_type
        FROM battle_field bf
        LEFT JOIN team_users tu ON tu.uid = bf.attacker_uid AND tu.profile_id=?
        ORDER BY bf.cap_time DESC
        LIMIT 100
    ''', (get_current_pid(),)).fetchall()
    # 补充附近成员名字
    result = []
    for r in rows:
        d = dict(r)
        nearby_uids = [int(x) for x in (d.get('nearby_uids') or '').split(',') if x.strip().isdigit()]
        if nearby_uids:
            placeholders = ','.join('?' * len(nearby_uids))
            members = conn.execute(
                f'SELECT uid, name, group_name FROM team_users WHERE profile_id=? AND uid IN ({placeholders})',
                [get_current_pid()] + nearby_uids
            ).fetchall()
            d['nearby_members'] = [{'uid': m[0], 'name': m[1], 'group': m[2]} for m in members]
        else:
            d['nearby_members'] = []
        d.pop('nearby_uids', None)
        result.append(d)
    conn.close()
    return jsonify(result)


# ===== 攻城队列快照 (000018ae) =====
@app.route('/api/battle_queue')
def api_battle_queue():
    conn = get_db()
    try:
        conn.execute('''
            CREATE TABLE IF NOT EXISTS battle_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uid INTEGER, name TEXT, level INTEGER, queue_slot INTEGER,
                hero_list TEXT, hero_count INTEGER, cur_hero_id INTEGER,
                power INTEGER, flag INTEGER, hero_config_id INTEGER,
                skin_id INTEGER, city_id INTEGER, cap_time INTEGER, captured_at TEXT
            )
        ''')
        # 取最新一批（按最新 cap_time）
        latest = conn.execute('SELECT MAX(cap_time) FROM battle_queue').fetchone()[0]
        if not latest:
            return jsonify([])
        rows = conn.execute('''
            SELECT bq.*, tu.name as member_name, tu.group_name, tu.pos as member_pos
            FROM battle_queue bq
            LEFT JOIN team_users tu ON tu.uid = bq.uid AND tu.profile_id=?
            WHERE bq.cap_time = ?
            ORDER BY bq.power DESC
        ''', (get_current_pid(), latest,)).fetchall()
        return jsonify([dict(r) for r in rows])
    except Exception as e:
        return jsonify({'error': str(e)})
    finally:
        conn.close()


# ===== 联盟列表 (000002bc) =====
@app.route('/api/union_list')
def api_union_list():
    conn = get_db()
    try:
        rows = conn.execute('''
            SELECT * FROM union_list ORDER BY rank ASC, power DESC
        ''').fetchall()
        return jsonify([dict(r) for r in rows])
    except Exception as e:
        return jsonify([])
    finally:
        conn.close()


@app.route('/api/union_power_rank')
def api_union_power_rank():
    conn = get_db()
    try:
        rows = conn.execute('''
            SELECT user_id, role_id, name, power, force, area, region, land_count,
                   fort_count, branch_city_count, shu_cheng_count, refresh_time,
                   rank, updated_at
            FROM player_power_rank
            ORDER BY rank ASC, power DESC, user_id ASC
        ''').fetchall()
        result = [dict(r) for r in rows]
        total_power = sum(int(r.get('power') or 0) for r in result)
        total_land = sum(int(r.get('land_count') or 0) for r in result)
        total_fort = sum(int(r.get('fort_count') or 0) for r in result)
        total_branch_city = sum(int(r.get('branch_city_count') or 0) for r in result)
        return jsonify({
            'summary': {
                'total_players': len(result),
                'total_power': total_power,
                'total_land': total_land,
                'total_fort': total_fort,
                'total_branch_city': total_branch_city,
                'updated_at': result[0]['updated_at'] if result else ''
            },
            'rows': result,
        })
    except Exception as e:
        return jsonify({'error': str(e), 'summary': {}, 'rows': []})
    finally:
        conn.close()


# ===== 游戏公告 (0000030c) =====
@app.route('/api/announcements')
def api_announcements():
    conn = get_db()
    try:
        rows = conn.execute('''
            SELECT * FROM announcements ORDER BY pub_time DESC LIMIT 50
        ''').fetchall()
        return jsonify([dict(r) for r in rows])
    except Exception as e:
        if 'no such table: announcements' in str(e):
            return jsonify([])
        return jsonify({'error': str(e), 'data': []})
    finally:
        conn.close()


# ===== 武将解锁记录 (0000029f) =====
@app.route('/api/hero_unlock_log')
def api_hero_unlock_log():
    conn = get_db()
    try:
        rows = conn.execute('''
            SELECT hero_id, hero_name, MIN(unlock_time) as first_unlock,
                   MAX(unlock_time) as last_unlock, COUNT(*) as unlock_count
            FROM hero_unlock_log
            GROUP BY hero_id
            ORDER BY last_unlock DESC
            LIMIT 200
        ''').fetchall()
        return jsonify([dict(r) for r in rows])
    except Exception as e:
        return jsonify({'error': str(e), 'data': []})
    finally:
        conn.close()


# ===== 玩家自身信息 (00000015) =====
@app.route('/api/player_self')
def api_player_self():
    conn = get_db()
    try:
        row = conn.execute('SELECT * FROM player_self WHERE id=1').fetchone()
        return jsonify(dict(row) if row else {})
    except Exception as e:
        return jsonify({'error': str(e)})
    finally:
        conn.close()


# ===== 战区玩家列表 (00001863) =====
@app.route('/api/zone_players')
def api_zone_players():
    name   = request.args.get('name', '')
    union  = request.args.get('union_id', '')
    limit  = int(request.args.get('limit', 500))
    conn = get_db()
    try:
        where = ['1=1']; params = []
        if name:
            where.append('name LIKE ?'); params.append(f'%{name}%')
        if union:
            where.append('union_id=?'); params.append(int(union))
        w = ' AND '.join(where)
        rows = conn.execute(
            f'SELECT * FROM zone_players WHERE {w} ORDER BY power DESC LIMIT ?',
            params + [limit]
        ).fetchall()
        return jsonify([dict(r) for r in rows])
    except Exception as e:
        return jsonify({'error': str(e), 'data': []})
    finally:
        conn.close()


@app.route('/api/zone_players/stats')
def api_zone_players_stats():
    """战区玩家统计：总数、势力分布、各联盟人数"""
    conn = get_db()
    try:
        total = conn.execute('SELECT COUNT(*) FROM zone_players').fetchone()[0]
        top_unions = conn.execute('''
            SELECT ul.name as union_name, zp.union_id,
                   COUNT(*) as member_count,
                   SUM(zp.power) as total_power,
                   ROUND(AVG(zp.power)) as avg_power,
                   MAX(zp.power) as max_power
            FROM zone_players zp
            LEFT JOIN union_list ul ON ul.union_id = zp.union_id
            WHERE zp.union_id > 0
            GROUP BY zp.union_id
            ORDER BY total_power DESC
            LIMIT 30
        ''').fetchall()
        top_players = conn.execute('''
            SELECT zp.uid, zp.name, zp.power, zp.union_id,
                   ul.name as union_name
            FROM zone_players zp
            LEFT JOIN union_list ul ON ul.union_id = zp.union_id
            ORDER BY zp.power DESC
            LIMIT 50
        ''').fetchall()
        return jsonify({
            'total': total,
            'top_unions': [dict(r) for r in top_unions],
            'top_players': [dict(r) for r in top_players],
        })
    except Exception as e:
        return jsonify({'error': str(e)})
    finally:
        conn.close()


@app.after_request
def add_no_cache_headers(resp):
    resp.headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, max-age=0'
    resp.headers['Pragma'] = 'no-cache'
    resp.headers['Expires'] = '0'
    return resp


# ===== 静态文件 =====
@app.route('/')
def index():
    dashboard_path = os.path.join(RESOURCE_DIR, 'static', 'dashboard.html')
    try:
        with open(dashboard_path, 'r', encoding='utf-8') as dashboard_file:
            html = dashboard_file.read()
        html = __import__('re').sub(
            r'(/static/([^?"\']+\.(?:mjs|js|css)))(?:\?v=[^"\']*)?',
            lambda match: f'{match.group(1)}?v={_asset_version(match.group(2))}',
            html,
        )
        return Response(html, mimetype='text/html')
    except OSError:
        return app.send_static_file('dashboard.html')


# ===== 州郡 / 团统计（基于个人势力排行 + 名字前缀分组） =====
def _name_prefix_group(name):
    import re

    text = str(name or '').strip()
    if not text:
        return '未分组'

    # 先匹配常见的“前缀 + 分隔符 + 正文”格式
    # 支持大量昵称里常见的花式分隔：丨|｜、/／\丶·•･・:：-—_ 空格 灬 乄 の 〆 メ ~ ～ 等
    m = re.match(r'^\s*([\u4e00-\u9fffA-Za-z0-9]{1,6})\s*[丨|｜、/／\\丶·•･・:：\-_ —\s灬乄の〆メ~～]+\s*.+$', text)
    if m:
        prefix = (m.group(1) or '').strip()
        if prefix:
            return prefix

    # 兜底：找最早出现的常见分隔符
    seps = ('丨', '|', '｜', '、', '/', '／', '\\', '丶', '·', '•', '･', '・', ':', '：', '-', '—', '_', ' ', '灬', '乄', 'の', '〆', 'メ', '~', '～')
    split_pos = -1
    for sep in seps:
        pos = text.find(sep)
        if pos > 0 and (split_pos < 0 or pos < split_pos):
            split_pos = pos

    if split_pos > 0:
        prefix = text[:split_pos].strip()
        if prefix:
            return prefix

    return '未分组'


@app.route('/api/state_region_stats')
def api_state_region_stats():
    scope = request.args.get('scope', 'all')
    group = request.args.get('group', '')
    alliance = request.args.get('alliance', '')

    conn = get_db()
    try:
        meta = {
            'has_player_power_rank': _table_exists(conn, 'player_power_rank'),
            'has_zone_players': _table_exists(conn, 'zone_players'),
            'has_union_list': _table_exists(conn, 'union_list'),
        }
        meta['player_power_rank_count'] = conn.execute('SELECT COUNT(*) FROM player_power_rank').fetchone()[0] if meta['has_player_power_rank'] else 0
        meta['zone_players_count'] = conn.execute('SELECT COUNT(*) FROM zone_players').fetchone()[0] if meta['has_zone_players'] else 0
        meta['union_list_count'] = conn.execute('SELECT COUNT(*) FROM union_list').fetchone()[0] if meta['has_union_list'] else 0

        if meta['player_power_rank_count'] <= 0:
            missing_parts = ['个人势力排行']
            if meta['union_list_count'] <= 0:
                missing_parts.append('联盟列表')
            if meta['zone_players_count'] <= 0:
                missing_parts.append('战区玩家')
            meta['ready'] = False
            meta['message'] = '暂无州郡数据：当前库还没抓到' + ' / '.join(missing_parts) + '相关报文，请先在游戏里打开对应页面后再刷新。'
            return jsonify({
                'summary': {
                    'total_players': 0,
                    'total_power': 0,
                    'state_count': 0,
                    'group_count': 0,
                    'alliance_count': 0,
                    'grouped_players': 0,
                    'scope': scope,
                    'selected_group': group,
                    'selected_alliance': alliance,
                },
                'state_rows': [],
                'group_rows': [],
                'alliance_rows': [],
                'groups': [],
                'alliances': [],
                'meta': meta,
            })

        rows = conn.execute('''
            WITH union_sources AS (
                SELECT REPLACE(player_name, ' ', '') AS norm_name,
                       union_name,
                       time AS sort_time
                FROM attendance
                WHERE COALESCE(union_name, '') <> ''

                UNION ALL

                SELECT REPLACE(atk_name, ' ', '') AS norm_name,
                       atk_union AS union_name,
                       time AS sort_time
                FROM battles_v2
                WHERE COALESCE(atk_union, '') <> ''

                UNION ALL

                SELECT REPLACE(def_name, ' ', '') AS norm_name,
                       def_union AS union_name,
                       time AS sort_time
                FROM battles_v2
                WHERE COALESCE(def_union, '') <> ''

                UNION ALL

                SELECT REPLACE(sender, ' ', '') AS norm_name,
                       union_name,
                       time AS sort_time
                FROM chat_messages
                WHERE COALESCE(union_name, '') <> ''
            ),
            union_name_map AS (
                SELECT norm_name, union_name
                FROM (
                    SELECT norm_name,
                           union_name,
                           sort_time,
                           ROW_NUMBER() OVER (
                               PARTITION BY norm_name
                               ORDER BY sort_time DESC, union_name ASC
                           ) AS rn
                    FROM union_sources
                ) t
                WHERE rn = 1
            )
            SELECT
                p.user_id,
                p.name,
                p.power,
                p.region,
                COALESCE(zp.union_id, 0) AS union_id,
                COALESCE(NULLIF(ul.name, ''), NULLIF(um.union_name, ''), '同盟未知') AS union_name
            FROM player_power_rank p
            LEFT JOIN zone_players zp ON CAST(zp.uid AS TEXT) = CAST(p.user_id AS TEXT)
            LEFT JOIN union_list ul ON ul.union_id = zp.union_id
            LEFT JOIN union_name_map um ON um.norm_name = REPLACE(p.name, ' ', '')
            ORDER BY p.rank ASC, p.power DESC
        ''').fetchall()

        data = []
        for row in rows:
            item = dict(row)
            item['group_name'] = _name_prefix_group(item.get('name', ''))
            item['union_name'] = (item.get('union_name') or '同盟未知').strip() or '同盟未知'
            data.append(item)

        if alliance:
            data = [r for r in data if (r.get('union_name') or '同盟未知') == alliance]
        if scope == 'group' and group:
            data = [r for r in data if (r.get('group_name') or '未分组') == group]

        state_map = {}
        group_map = {}
        alliance_map = {}
        all_groups = set()
        all_alliances = set()

        for r in data:
            region = int(r.get('region') or 0)
            state_name = REGION_NAMES.get(region, f'州{region}' if region else '未知')
            group_name = (r.get('group_name') or '未分组').strip() or '未分组'
            union_name = (r.get('union_name') or '同盟未知').strip() or '同盟未知'
            power = int(r.get('power') or 0)
            all_groups.add(group_name)
            all_alliances.add(union_name)

            s = state_map.setdefault(state_name, {
                'state': state_name,
                'region': region,
                'player_count': 0,
                'total_power': 0,
                'max_power': 0,
            })
            s['player_count'] += 1
            s['total_power'] += power
            s['max_power'] = max(s['max_power'], power)

            gk = f'{union_name}__{group_name}'
            g = group_map.setdefault(gk, {
                'alliance_name': union_name,
                'group_name': group_name,
                'player_count': 0,
                'total_power': 0,
                'max_power': 0,
                'states': {},
            })
            g['player_count'] += 1
            g['total_power'] += power
            g['max_power'] = max(g['max_power'], power)
            g['states'][state_name] = g['states'].get(state_name, 0) + 1

            a = alliance_map.setdefault(union_name, {
                'alliance_name': union_name,
                'player_count': 0,
                'total_power': 0,
                'max_power': 0,
                'states': {},
                'groups': {},
            })
            a['player_count'] += 1
            a['total_power'] += power
            a['max_power'] = max(a['max_power'], power)
            a['states'][state_name] = a['states'].get(state_name, 0) + 1
            ag = a['groups'].setdefault(group_name, {
                'alliance_name': union_name,
                'group_name': group_name,
                'player_count': 0,
                'total_power': 0,
                'max_power': 0,
                'states': {},
            })
            ag['player_count'] += 1
            ag['total_power'] += power
            ag['max_power'] = max(ag['max_power'], power)
            ag['states'][state_name] = ag['states'].get(state_name, 0) + 1

        state_rows = []
        for item in state_map.values():
            item['avg_power'] = round(item['total_power'] / item['player_count'], 1) if item['player_count'] else 0
            state_rows.append(item)
        state_rows.sort(key=lambda x: (-x['player_count'], -x['total_power'], x['region']))

        group_rows = []
        for item in group_map.values():
            item['avg_power'] = round(item['total_power'] / item['player_count'], 1) if item['player_count'] else 0
            item['state_summary'] = ' / '.join(
                f'{k}{v}人' for k, v in sorted(item['states'].items(), key=lambda kv: (-kv[1], kv[0]))[:4]
            )
            group_rows.append(item)
        group_rows.sort(key=lambda x: (-x['player_count'], -x['total_power'], x['alliance_name'], x['group_name']))

        alliance_rows = []
        for item in alliance_map.values():
            item['avg_power'] = round(item['total_power'] / item['player_count'], 1) if item['player_count'] else 0
            item['state_summary'] = ' / '.join(
                f'{k}{v}人' for k, v in sorted(item['states'].items(), key=lambda kv: (-kv[1], kv[0]))[:4]
            )
            groups = []
            for g in item['groups'].values():
                g['avg_power'] = round(g['total_power'] / g['player_count'], 1) if g['player_count'] else 0
                g['state_summary'] = ' / '.join(
                    f'{k}{v}人' for k, v in sorted(g['states'].items(), key=lambda kv: (-kv[1], kv[0]))[:4]
                )
                groups.append(g)
            groups.sort(key=lambda x: (-x['player_count'], -x['total_power'], x['group_name']))
            item['groups'] = groups
            alliance_rows.append(item)
        alliance_rows.sort(key=lambda x: (-x['player_count'], -x['total_power'], x['alliance_name']))

        total_players = len(data)
        total_power = sum(int(r.get('power') or 0) for r in data)
        grouped_players = sum(1 for r in data if (r.get('group_name') or '未分组') != '未分组')
        meta['ready'] = True
        meta['message'] = ''

        return jsonify({
            'summary': {
                'total_players': total_players,
                'total_power': total_power,
                'state_count': len(state_rows),
                'group_count': len(group_rows),
                'alliance_count': len(alliance_rows),
                'grouped_players': grouped_players,
                'scope': scope,
                'selected_group': group,
                'selected_alliance': alliance,
            },
            'state_rows': state_rows,
            'group_rows': group_rows,
            'alliance_rows': alliance_rows,
            'groups': sorted(all_groups),
            'alliances': sorted(all_alliances),
            'meta': meta,
        })
    except Exception as e:
        return jsonify({'error': str(e), 'summary': {}, 'state_rows': [], 'group_rows': [], 'alliance_rows': [], 'groups': [], 'alliances': [], 'meta': {'ready': False, 'message': str(e)}})
    finally:
        conn.close()


# ===== 团数据统计 =====
@app.route('/api/team_report')
def api_team_report():
    """团数据：按分组/个人统计战报、胜率、功勋、攻城；包含无战报成员"""
    period  = request.args.get('period', 'all')
    group   = request.args.get('group', '')
    dim     = request.args.get('dim', 'group')
    t_from  = request.args.get('from', 0, type=int)
    t_to    = request.args.get('to', 0, type=int)

    import time as _time
    now = int(_time.time())
    from datetime import datetime, timedelta
    def day_start(d): return int(datetime(d.year,d.month,d.day).timestamp())
    today = datetime.now().date()

    if period == 'today':
        t_from = day_start(today); t_to = now
    elif period == 'yesterday':
        yd = today - timedelta(days=1)
        t_from = day_start(yd); t_to = day_start(today) - 1
    elif period == 'week':
        t_from = day_start(today - timedelta(days=today.weekday())); t_to = now
    elif period == 'lastweek':
        lw = today - timedelta(days=today.weekday()+7)
        t_from = day_start(lw); t_to = day_start(lw + timedelta(days=7)) - 1

    conn = get_db()
    pid = get_current_pid()

    team_where = ['tu.profile_id=?']
    team_params = [pid]
    if group:
        team_where.append("COALESCE(NULLIF(tu.group_name,''), '未分组') = ?")
        team_params.append(group)
    team_w = ' AND '.join(team_where)

    battle_where = ['tu2.profile_id=?']
    battle_params = [pid]
    if t_from:
        battle_where.append('bv.time >= ?')
        battle_params.append(t_from)
    if t_to:
        battle_where.append('bv.time <= ?')
        battle_params.append(t_to)
    if group:
        battle_where.append("COALESCE(NULLIF(tu2.group_name,''), '未分组') = ?")
        battle_params.append(group)
    battle_w = ' AND '.join(battle_where)

    if dim == 'player':
        rows = conn.execute(f'''
            SELECT
                tu.name as name,
                COALESCE(NULLIF(tu.group_name,''), '未分组') as group_name,
                COALESCE(ba.battles, 0) as battles,
                COALESCE(ba.wins, 0) as wins,
                COALESCE(ba.loses, 0) as loses,
                COALESCE(ba.draws, 0) as draws,
                COALESCE(ba.city_battles, 0) as city_battles,
                COALESCE(ba.city_wins, 0) as city_wins,
                COALESCE(tu.wuxun, 0) as total_gongxun,
                COALESCE(tu.power, 0) as power,
                CASE
                    WHEN COALESCE(ba.battles, 0) > 0 THEN ROUND((COALESCE(ba.wins, 0) + COALESCE(ba.draws, 0) * 0.5) * 100.0 / ba.battles, 1)
                    ELSE 0
                END as win_rate
            FROM team_users tu
            LEFT JOIN (
                SELECT
                    bv.atk_name as player_name,
                COUNT(*) as battles,
                    SUM(CASE WHEN bv.result IN (1,7,11) THEN 1 ELSE 0 END) as wins,
                    SUM(CASE WHEN bv.result IN (2,6,12) THEN 1 ELSE 0 END) as loses,
                    SUM(CASE WHEN bv.result NOT IN (1,2,6,7,11,12) THEN 1 ELSE 0 END) as draws,
                SUM(CASE WHEN bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as city_battles,
                    SUM(CASE WHEN bv.result=1 AND bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as city_wins
            FROM battles_v2 bv
                INNER JOIN team_users tu2 ON tu2.name = bv.atk_name
                WHERE {battle_w}
                GROUP BY bv.atk_name
            ) ba ON ba.player_name = tu.name
            WHERE {team_w}
            ORDER BY battles DESC, total_gongxun DESC, power DESC, tu.name ASC
            LIMIT 500
        ''', battle_params + team_params).fetchall()
    else:
        rows = conn.execute(f'''
            SELECT
                COALESCE(NULLIF(tu.group_name,''), '未分组') as name,
                COUNT(*) as player_cnt,
                COALESCE(SUM(ba.battles), 0) as battles,
                COALESCE(SUM(ba.wins), 0) as wins,
                COALESCE(SUM(ba.loses), 0) as loses,
                COALESCE(SUM(ba.draws), 0) as draws,
                COALESCE(SUM(ba.city_battles), 0) as city_battles,
                COALESCE(SUM(ba.city_wins), 0) as city_wins,
                COALESCE(SUM(CAST(COALESCE(NULLIF(tu.wuxun,''), 0) AS REAL)), 0) as total_gongxun,
                ROUND(COALESCE(SUM(CAST(COALESCE(NULLIF(tu.wuxun,''), 0) AS REAL)), 0) * 1.0 / COUNT(*), 1) as avg_gongxun,
                ROUND(COALESCE(SUM(CAST(COALESCE(NULLIF(tu.power,''), 0) AS REAL)), 0) * 1.0 / COUNT(*), 1) as avg_power,
                CASE
                    WHEN COALESCE(SUM(ba.battles), 0) > 0 THEN ROUND((COALESCE(SUM(ba.wins), 0) + COALESCE(SUM(ba.draws), 0) * 0.5) * 100.0 / SUM(ba.battles), 1)
                    ELSE 0
                END as win_rate
            FROM team_users tu
            LEFT JOIN (
                SELECT
                    bv.atk_name as player_name,
                COUNT(*) as battles,
                    SUM(CASE WHEN bv.result IN (1,7,11) THEN 1 ELSE 0 END) as wins,
                    SUM(CASE WHEN bv.result IN (2,6,12) THEN 1 ELSE 0 END) as loses,
                    SUM(CASE WHEN bv.result NOT IN (1,2,6,7,11,12) THEN 1 ELSE 0 END) as draws,
                SUM(CASE WHEN bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as city_battles,
                    SUM(CASE WHEN bv.result=1 AND bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as city_wins
            FROM battles_v2 bv
                INNER JOIN team_users tu2 ON tu2.name = bv.atk_name
                WHERE {battle_w}
                GROUP BY bv.atk_name
            ) ba ON ba.player_name = tu.name
            WHERE {team_w}
            GROUP BY COALESCE(NULLIF(tu.group_name,''), '未分组')
            ORDER BY battles DESC, total_gongxun DESC, player_cnt DESC, name ASC
        ''', battle_params + team_params).fetchall()

    summary_row = conn.execute(f'''
        SELECT
            COUNT(*) as total_players,
            COALESCE(SUM(tu.wuxun), 0) as total_gongxun,
            COALESCE(SUM(ba.battles), 0) as total_battles,
            COALESCE(SUM(ba.wins), 0) as total_wins,
            COALESCE(SUM(ba.draws), 0) as total_draws,
            COALESCE(SUM(ba.city_battles), 0) as total_city
        FROM team_users tu
        LEFT JOIN (
            SELECT
                bv.atk_name as player_name,
                COUNT(*) as battles,
                SUM(CASE WHEN bv.result IN (1,7,11) THEN 1 ELSE 0 END) as wins,
                SUM(CASE WHEN bv.result NOT IN (1,2,6,7,11,12) THEN 1 ELSE 0 END) as draws,
                SUM(CASE WHEN bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as city_battles
            FROM battles_v2 bv
            INNER JOIN team_users tu2 ON tu2.name = bv.atk_name
            WHERE {battle_w}
            GROUP BY bv.atk_name
        ) ba ON ba.player_name = tu.name
        WHERE {team_w}
    ''', battle_params + team_params).fetchone()
    conn.close()

    summary = dict(summary_row) if summary_row else {}
    total_wins = summary.get('total_wins') or 0
    total_draws = summary.get('total_draws') or 0
    total_battles = summary.get('total_battles') or 0
    summary['win_rate'] = round((total_wins + total_draws * 0.5) * 100 / max(total_battles, 1), 1)
    return jsonify({'summary': summary, 'rows': [dict(r) for r in rows]})


# ──────────────────────────────────────────────────────────
# 战斗模拟接口
# ──────────────────────────────────────────────────────────
@app.route('/api/simulate', methods=['POST'])
def api_simulate():
    """
    POST /api/simulate
    body: {
        "blue": {"morale":100, "heros":[{"id":1004,"level":40,"up":0,"equip_skills":[1018],"extra_attrs":{}}]},
        "red":  {"morale":100, "heros":[...]},
        "repeat": 1   // 1=单次详细, N>1=多次统计
    }
    """
    data = None
    try:
        data = request.get_json(force=True)
        result = BattleEngineAdapter().simulate(data)
        return jsonify(result), (200 if result.get('ok') else 500)
    except ValueError as e:
        return jsonify({
            'ok': False,
            'engine': 'stzb-kotlin',
            'error': str(e),
        }), 400
    except Exception as e:
        return jsonify({
            'ok': False,
            'engine': 'stzb-kotlin',
            'error': str(e),
        }), 500


@app.route('/api/simulate/engine', methods=['GET'])
def api_simulate_engine():
    """GET /api/simulate/engine - 返回 Kotlin 引擎来源与能力。"""
    try:
        return jsonify({
            'ok': True,
            **BattleEngineAdapter().engine_metadata(),
        })
    except Exception as e:
        return jsonify({
            'ok': False,
            'engine': 'stzb-kotlin',
            'error': str(e),
        }), 500


@app.route('/api/simulate/heroes', methods=['GET'])
def api_simulate_heroes():
    """GET /api/simulate/heroes - 返回可用武将和战法列表

    数据源为 Kotlin battle-engine 的权威配置表 (hero_table.csv / skill_table.csv)，
    与实际参战引擎的 hero id / skill id 口径完全一致。
    """
    try:
        import sim_data
        return jsonify({
            'ok': True,
            'heroes': sim_data.load_heroes(),
            'skills': sim_data.load_skills(),
        })
    except Exception as e:
        import traceback
        return jsonify({'ok': False, 'error': str(e), 'trace': traceback.format_exc()}), 500


def run_app(open_browser=True, start_sniffer=True, host='127.0.0.1', port=8765):
    """给打包版和开发版共用的正式启动入口。"""
    print(f'启动 API 服务器: http://{host}:{port}')
    start_runtime_services(start_writer=True)

    try:
        os.makedirs(os.path.join(BASE_DIR, 'capture_new'), exist_ok=True)
        os.makedirs(os.path.join(BASE_DIR, 'logs'), exist_ok=True)
    except Exception as e:
        print(f'[startup] 创建运行目录失败: {e}')

    try:
        import glob as _glob
        _dbs_to_init = {_current_db_path}
        for _f in _glob.glob(os.path.join(BASE_DIR, 'stzb*.db')):
            _dbs_to_init.add(_f)
        for _dbp in _dbs_to_init:
            try:
                ensure_all_tables(_dbp)
            except Exception as _te:
                print(f'[startup] 建表失败 {_dbp}: {_te}')
    except Exception as _ie:
        print(f'[startup] 自动建表失败: {_ie}')

    if start_sniffer:
        try:
            import scrapy_v2 as _scrapy
            _sniff_t = threading.Thread(target=_scrapy.run_sniff, daemon=True, name='sniff')
            _sniff_t.start()
            print('[startup] 抓包线程已启动')
        except Exception as _se:
            print(f'[startup] 抓包线程启动失败: {_se}')

    if open_browser:
        try:
            import webbrowser
            threading.Timer(1.5, lambda: webbrowser.open(f'http://{host}:{port}/')).start()
        except Exception as e:
            print(f'[startup] 自动打开浏览器失败: {e}')

    app.run(host=host, port=port, debug=False, use_reloader=False)


if __name__ == '__main__':
    run_app(open_browser=True, start_sniffer=True, host='0.0.0.0', port=8080)
