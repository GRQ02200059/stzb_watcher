# -*- coding: utf-8 -*-
"""
率土之滨 REST API 服务器
提供所有统计数据接口，前端通过 fetch('/api/xxx') 访问
"""
from flask import Flask, jsonify, request, Response, stream_with_context
from flask_cors import CORS
import sqlite3, json, os, time, threading
from realtime_writer import (start_writer_thread, event_queue, recent_events, _event_lock,
                             subscribe, unsubscribe, push_event)
import profile_manager

import os, time, threading

BASE_DIR     = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DB   = os.path.join(BASE_DIR, 'stzb.db')
PROFILE_FILE = os.path.join(BASE_DIR, 'current_profile.json')

_current_db_path = DEFAULT_DB
_db_lock         = threading.Lock()


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
        except:
            pass
        time.sleep(2)


# 启动 profile 监控线程
_watcher_thread = threading.Thread(target=_profile_watcher, daemon=True, name='profile-watcher')
_watcher_thread.start()

# 初始化当前 DB
_current_db_path = _load_profile()
app = Flask(__name__, static_folder='static', static_url_path='/static')
CORS(app)

# 启动实时写库线程
_writer = start_writer_thread()

def ensure_all_tables(db_path):
    """对指定数据库执行所有建表操作，幂等安全"""
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
        # 6. zone_players 表
        conn.execute('''
            CREATE TABLE IF NOT EXISTS zone_players (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uid TEXT, name TEXT, union_name TEXT,
                level INTEGER DEFAULT 0,
                power INTEGER DEFAULT 0,
                updated_at TEXT
            )
        ''')
        conn.commit()
    except Exception as e:
        print(f'[init] zone_players: {e}')
    conn.close()
    print(f'[init] 全部表初始化完成: {db_path}')


def get_db():
    with _db_lock:
        db_path = _current_db_path
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    return conn

def get_current_pid() -> str:
    """获取当前激活账号的 profile_id"""
    try:
        return profile_manager.get_current_profile_id()
    except:
        return ''

def rows_to_list(rows):
    return [dict(r) for r in rows]


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
    # 从 battle_heroes 实时统计每个武将的使用次数、胜率、最新等级
    rows = conn.execute(f'''
        SELECT bh.hero_name,
               COUNT(*) as cnt,
               SUM(CASE WHEN bv.result=1 THEN 1 ELSE 0 END) as wins,
               MAX(bh.level) as max_level,
               ROUND(SUM(CASE WHEN bv.result=1 THEN 1 ELSE 0 END)*100.0/COUNT(*),1) as win_rate
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
        win  = (result == 1)
        lose = (result == 0)
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
        win_rate = round(s['win'] * 100.0 / s['total'], 1)
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
        r1 = subprocess.run([sys.executable, 'd:/nettest/db_import.py'],
                            capture_output=True, text=True, timeout=120)
        r2 = subprocess.run([sys.executable, 'd:/nettest/db_import_ext.py'],
                            capture_output=True, text=True, timeout=120)
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


# ===== 自定义积分 =====
@app.route('/api/custom_scores')
def api_custom_scores():
    season = request.args.get('season', 'current')
    union  = request.args.get('union', '')
    conn = get_db()
    where = [f"season_id='{season}'"]
    if union: where.append(f"union_name LIKE '%{union}%'")
    w = 'WHERE ' + ' AND '.join(where)
    rows = conn.execute(f'SELECT * FROM custom_scores {w} ORDER BY score DESC').fetchall()
    conn.close()
    return jsonify([dict(r) for r in rows])


@app.route('/api/custom_scores/recalc', methods=['POST'])
def api_custom_scores_recalc():
    """重新计算自定义积分（出战1分+武勋权重）"""
    import time as _time
    season = (request.json or {}).get('season', 'current')
    conn = get_db()
    # 从 battles_v2 聚合
    rows = conn.execute('''
        SELECT atk_name, atk_uid, def_union as union_name,
               COUNT(*) as battles,
               SUM(CASE WHEN result IN (1,11) THEN 1 ELSE 0 END) as wins,
               SUM(atk_gongxun) as gongxun_total,
               MAX(atk_power) as power_total,
               SUM(CASE WHEN fight_type IN (80,33) THEN 1 ELSE 0 END) as main_city_cnt
        FROM battles_v2 WHERE atk_name != ''
        GROUP BY atk_name
    ''').fetchall()
    now_str = __import__('datetime').datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    for r in rows:
        # 积分公式：出战*1 + 武勋/1000 + 胜率加成
        score = r[1] + r[5]/1000.0 + r[2]*0.5  # battles + wx_bonus + win_bonus
        conn.execute('''
            INSERT INTO custom_scores
                (season_id, player_name, player_uid, union_name, battles, wins,
                 gongxun_total, power_total, main_city_cnt, score, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(season_id, player_name) DO UPDATE SET
                battles=excluded.battles, wins=excluded.wins,
                gongxun_total=excluded.gongxun_total, power_total=excluded.power_total,
                main_city_cnt=excluded.main_city_cnt, score=excluded.score,
                updated_at=excluded.updated_at
        ''', (season, r[0], r[1], r[2], r[3], r[4], r[5] or 0, r[6] or 0, r[7], score, now_str))
    conn.commit()
    conn.close()
    return jsonify({'ok': True, 'updated': len(rows)})


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
    where = ['result <= 6']; params = []
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
        f'SELECT * FROM battles_v2 WHERE {w} ORDER BY time DESC LIMIT ? OFFSET ?',
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
    team_stats = defaultdict(lambda: {'used_count':0,'win_count':0,'union':'','side':''})
    for (pname, sd, bid), heroes in battle_heroes_map.items():
        heroes.sort(key=lambda x: x[0])
        heroes_str = ','.join(h[1] for h in heroes if h[1])
        if not heroes_str: continue
        union_n, result = battle_meta[(pname, sd, bid)]
        key = (pname, sd, heroes_str)
        team_stats[key]['used_count'] += 1
        team_stats[key]['union'] = union_n
        team_stats[key]['side'] = sd
        # 攻方胜：result in (1,7,11)；守方胜：result in (2,6,12)
        if sd == 'atk' and result in (1,7,11):
            team_stats[key]['win_count'] += 1
        elif sd == 'def' and result in (2,6,12):
            team_stats[key]['win_count'] += 1

    data = []
    for (pname, sd, heroes_str), stat in team_stats.items():
        uc = stat['used_count']
        wc = stat['win_count']
        data.append({
            'player_name': pname,
            'union_name': stat['union'],
            'side': sd,
            'heroes_str': heroes_str,
            'used_count': uc,
            'win_count': wc,
            'win_rate': round(wc/uc*100,1) if uc else 0,
        })
    data.sort(key=lambda x: (-x['used_count'], x['player_name']))
    return jsonify(data[:limit])


# ===== 玩家队伍统计（直接从battles_v2解析红度+技能） =====
@app.route('/api/player_battle_teams')
def api_player_battle_teams():
    """从battles_v2统计每个玩家的队伍组合，含进阶星数、技能、战数、胜场"""
    player  = request.args.get('player', '')   # 模糊匹配玩家名
    side    = request.args.get('side', '')     # atk/def/''

    # 内存缓存：无过滤条件时缓存60秒
    import time as _time
    _cache = api_player_battle_teams.__dict__
    cache_key = f'{player}|{side}'
    now = _time.time()
    if cache_key in _cache.get('_data', {}):
        entry = _cache['_data'][cache_key]
        if now - entry['ts'] < 60:
            return jsonify(entry['result'])

    conn = get_db()

    conds = ["1=1"]
    params = []
    if player:
        conds.append("(atk_name LIKE ? OR def_name LIKE ?)")
        params += [f'%{player}%', f'%{player}%']

    where = ' AND '.join(conds)
    rows = conn.execute(f'''
        SELECT atk_name, atk_uid, atk_union,
               def_name, def_union,
               result,
               '' as attack_all_hero_info, '' as defend_all_hero_info,
               '' as all_skill_info,
               0 as atk_hero1_id, 0 as atk_hero2_id, 0 as atk_hero3_id,
               0 as def_hero1_id, 0 as def_hero2_id, 0 as def_hero3_id,
               '' as atk_advance, '' as def_advance,
               '' as attack_clan_name, '' as defend_clan_name
        FROM battles_v2
        WHERE {where}
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
    team_map = defaultdict(lambda: {'cnt':0,'wins':0,'union':'','uid':'','hero_stars':[0,0,0],'skills':'','heroes_str':'','clan_name':''})

    for row in rows:
        atk_name, atk_uid, atk_union, def_name, def_union, result, \
        atk_hero_info, def_hero_info, skill_info, \
        ah1, ah2, ah3, dh1, dh2, dh3, \
        atk_advance, def_advance, atk_clan_name, def_clan_name = row

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
                if result in (1,7,11):
                    d['wins'] += 1

        # 守方
        if side in ('', 'def') and def_name and def_name.strip():
            heroes_ids = parse_hero_info(def_hero_info)
            if not heroes_ids:
                heroes_ids = '+'.join(str(x) for x in [dh1,dh2,dh3] if x)
            if heroes_ids:
                stars = parse_advance_stars(def_advance)
                skills = parse_skill_info(skill_info, [4,5,6])
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

    data = []
    for (pname, uid, heroes_ids), stat in team_map.items():
        cnt = stat['cnt']
        wins = stat['wins']
        data.append({
            'player_name': pname,
            'uid': stat['uid'],
            'union': stat['union'],
            'clan_name': stat['clan_name'],
            'heroes_str': stat['heroes_str'],
            'hero_stars': stat['hero_stars'],
            'skills': stat['skills'],
            'cnt': cnt,
            'wins': wins,
            'win_rate': round(wins/cnt*100,1) if cnt else 0,
        })
    # 按玩家名排序，同玩家按出场数降序
    data.sort(key=lambda x: (x['player_name'], -x['cnt']))
    # 写入缓存
    if '_data' not in api_player_battle_teams.__dict__:
        api_player_battle_teams._data = {}
    api_player_battle_teams._data[cache_key] = {'ts': now, 'result': data}
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


@app.route('/api/writer_stats')
def api_writer_stats():
    """返回 realtime_writer 统计"""
    return jsonify(_writer.stats)



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
    conn.close()

_init_task_tables()

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
        return jsonify({'error': str(e), 'data': []})
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


# ===== 静态文件 =====
@app.route('/')
def index():
    return app.send_static_file('dashboard.html')


# ===== 团数据统计 =====
@app.route('/api/team_report')
def api_team_report():
    """团数据：按分组/个人统计战报、胜率、功勋、攻城"""
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

    where = ['1=1']; params = []
    if t_from: where.append('bv.time >= ?'); params.append(t_from)
    if t_to:   where.append('bv.time <= ?'); params.append(t_to)
    if group:  where.append('tu.group_name = ?'); params.append(group)
    w = ' AND '.join(where)
    conn = get_db()
    pid = get_current_pid()
    if dim == 'player':
        rows = conn.execute(f'''
            SELECT bv.atk_name as name,
                COALESCE(tu.group_name,\'\') as group_name,
                COUNT(*) as battles,
                SUM(CASE WHEN bv.result=1 THEN 1 ELSE 0 END) as wins,
                SUM(CASE WHEN bv.result=0 THEN 1 ELSE 0 END) as loses,
                SUM(CASE WHEN bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as city_battles,
                SUM(CASE WHEN bv.result=1 AND bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as city_wins,
                COALESCE(MAX(tu.wuxun), 0) as total_gongxun,
                0 as max_power,
                ROUND(SUM(CASE WHEN bv.result=1 THEN 1 ELSE 0 END)*100.0/COUNT(*),1) as win_rate
            FROM battles_v2 bv
            INNER JOIN team_users tu ON tu.name = bv.atk_name AND tu.profile_id=?
            WHERE {w} GROUP BY bv.atk_name ORDER BY battles DESC LIMIT 200
        ''', [pid]+params).fetchall()
    else:
        rows = conn.execute(f'''
            SELECT COALESCE(tu.group_name,\'未知\') as name,
                COUNT(DISTINCT bv.atk_name) as player_cnt,
                COUNT(*) as battles,
                SUM(CASE WHEN bv.result=1 THEN 1 ELSE 0 END) as wins,
                SUM(CASE WHEN bv.result=0 THEN 1 ELSE 0 END) as loses,
                SUM(CASE WHEN bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as city_battles,
                SUM(CASE WHEN bv.result=1 AND bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as city_wins,
                COALESCE((SELECT SUM(wuxun) FROM team_users WHERE profile_id=? AND group_name=COALESCE(tu.group_name,\'未知\')),0) as total_gongxun,
                0 as max_power,
                ROUND(SUM(CASE WHEN bv.result=1 THEN 1 ELSE 0 END)*100.0/COUNT(*),1) as win_rate
            FROM battles_v2 bv
            INNER JOIN team_users tu ON tu.name = bv.atk_name AND tu.profile_id=?
            WHERE {w} GROUP BY tu.group_name ORDER BY battles DESC
        ''', [pid, pid]+params).fetchall()
    summary_row = conn.execute(f'''
        SELECT COUNT(*) as total_battles, COUNT(DISTINCT bv.atk_name) as total_players,
            SUM(CASE WHEN bv.result=1 THEN 1 ELSE 0 END) as total_wins,
            SUM(CASE WHEN bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as total_city,
            (SELECT SUM(wuxun) FROM team_users WHERE profile_id=?) as total_gongxun
        FROM battles_v2 bv LEFT JOIN team_users tu ON tu.name = bv.atk_name AND tu.profile_id=? WHERE {w}
    ''', [pid, pid]+params).fetchone()
    conn.close()
    summary = dict(summary_row) if summary_row else {}
    total_wins = summary.get('total_wins') or 0
    total_battles = summary.get('total_battles') or 0
    summary['win_rate'] = round(total_wins * 100 / max(total_battles, 1), 1)
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
    try:
        import sys, importlib
        sys.path.insert(0, BASE_DIR)
        # 强制清除缓存，确保加载最新战法
        for mod_name in list(sys.modules.keys()):
            if mod_name.startswith('battle_sim'):
                del sys.modules[mod_name]
        from battle_sim import BattleManager
        data   = request.get_json(force=True)
        repeat = int(data.get('repeat', 1))
        config = {'blue': data['blue'], 'red': data['red']}

        if repeat == 1:
            bm  = BattleManager(config)
            res = bm.result()
            return jsonify({'ok': True, 'result': res})
        else:
            blue_wins = red_wins = draws = 0
            for _ in range(repeat):
                bm  = BattleManager(config)
                res = bm.result()
                w   = res['winner']
                if '攻方' in w:  blue_wins += 1
                elif '守方' in w: red_wins  += 1
                else:            draws     += 1
            return jsonify({
                'ok': True,
                'repeat': repeat,
                'blue_wins': blue_wins,
                'red_wins':  red_wins,
                'draws':     draws,
                'blue_rate': round(blue_wins / repeat * 100, 1),
                'red_rate':  round(red_wins  / repeat * 100, 1),
                'draw_rate': round(draws      / repeat * 100, 1),
            })
    except Exception as e:
        import traceback
        return jsonify({'ok': False, 'error': str(e), 'trace': traceback.format_exc()}), 500


@app.route('/api/simulate/heroes', methods=['GET'])
def api_simulate_heroes():
    """GET /api/simulate/heroes - 返回可用武将和战法列表"""
    try:
        import sys, importlib
        sys.path.insert(0, BASE_DIR)
        # 强制清除缓存，确保每次加载最新战法
        for mod_name in list(sys.modules.keys()):
            if mod_name.startswith('battle_sim'):
                del sys.modules[mod_name]
        from battle_sim.data import HEROS, SKILLS
        heroes = [
            {
                'id':   h['id'],
                'name': h['name'],
                'camp': h['camp'],
                'army': h['army'],
                'limit': h['limit'],
                'skill': h['skill'],
            }
            for h in HEROS.values()
        ]
        skills = [
            {
                'id':         sk.id,
                'name':       sk.name,
                'desc':       sk.desc,
                'skill_type': sk.skill_type,
                'rate':       sk.rate,
                'study':      sk.study,
            }
            for sk in SKILLS.values()
        ]
        return jsonify({'ok': True, 'heroes': heroes, 'skills': skills})
    except Exception as e:
        import traceback
        return jsonify({'ok': False, 'error': str(e), 'trace': traceback.format_exc()}), 500


if __name__ == '__main__':
    print('启动 API 服务器: http://127.0.0.1:8080')
    print('接口列表:')
    print('  GET  /api/status')
    print('  GET  /api/unions')
    print('  GET  /api/battles?page=1&size=30&player=&union=&result=')
    print('  GET  /api/battles/{id}')
    print('  GET  /api/battle_stats')
    print('  GET  /api/heroes/freq')
    print('  GET  /api/heroes/combos?side=atk')
    print('  GET  /api/players')
    print('  GET  /api/players/{name}')
    print('  POST /api/simulate')
    print('  GET  /api/simulate/heroes')
    print('  GET  /api/wuxun?group=player|union')
    print('  GET  /api/scores?union=')
    print('  GET  /api/union_matrix')
    print('  POST /api/refresh')

    # 自动建表：对当前激活的数据库（及所有已存在的 stzb_*.db）执行建表
    try:
        import glob as _glob
        _dbs_to_init = set()
        _dbs_to_init.add(_current_db_path)
        for _f in _glob.glob(os.path.join(BASE_DIR, 'stzb*.db')):
            _dbs_to_init.add(_f)
        for _dbp in _dbs_to_init:
            try:
                ensure_all_tables(_dbp)
            except Exception as _te:
                print(f'[!] 建表失败 {_dbp}: {_te}')
    except Exception as _ie:
        print(f'[!] 自动建表失败: {_ie}')

    # 启动抓包线程（集成 scrapy_v2）
    try:
        import scrapy_v2 as _scrapy
        _sniff_t = threading.Thread(target=_scrapy.run_sniff, daemon=True, name='sniff')
        _sniff_t.start()
        print('[*] 抓包线程已启动')
    except Exception as _se:
        print(f'[!] 抓包线程启动失败: {_se}')

    app.run(host='0.0.0.0', port=8080, debug=False)

