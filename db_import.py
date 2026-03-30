# -*- coding: utf-8 -*-
# db_import.py - 数据导入部分（追加到 db_build.py 后运行）
import sqlite3, json, os, re
from datetime import datetime

DB_PATH = 'd:/nettest/stzb.db'
DATA_ROOT = 'd:/nettest'

try:
    with open(f'{DATA_ROOT}/hero_scraper/output/heroes.json', 'r', encoding='utf-8') as f:
        HERO_NAMES = {str(h['id']): h['name'] for h in json.load(f) if h.get('id') and h.get('name')}
except: HERO_NAMES = {}
try:
    with open(f'{DATA_ROOT}/hero_scraper/output/skills_full.json', 'r', encoding='utf-8') as f:
        SKILL_NAMES = {str(s['id']): s['name'] for s in json.load(f) if s.get('id') and s.get('name')}
except: SKILL_NAMES = {}

def hero_name(hid): return HERO_NAMES.get(str(hid), f'武将{hid}' if hid else '')
def skill_name(sid): return SKILL_NAMES.get(str(sid), f'技能{sid}' if sid else '')

RESULT_MAP = {0:'平局',1:'攻方胜',2:'守方胜',3:'攻方溃',4:'守方溃',5:'双溃',6:'守方胜(NPC)'}

def parse_hero_info(s):
    heroes = []
    if not s: return heroes
    for part in s.split(';'):
        p = part.strip().split(',')
        if len(p) >= 5:
            try:
                hid = int(p[0])
                if hid > 0:
                    heroes.append({'id':hid,'name':hero_name(hid),'level':int(p[1]),'max_hp':int(p[2]),'remain_hp':int(p[3]),'damage_taken':int(p[4])})
            except: pass
    return heroes

def parse_skill_info(s):
    skills = []
    if not s: return skills
    for part in s.split(';'):
        p = part.strip().split(',')
        if len(p) >= 3:
            try:
                pos = int(p[0])
                side = 'atk' if pos <= 3 else 'def'
                for i in range(1, len(p)-1, 2):
                    sid = int(p[i])
                    if sid > 0:
                        skills.append({'pos':pos,'side':side,'skill_id':sid,'skill_name':skill_name(sid),'skill_level':int(p[i+1])})
            except: pass
    return skills

def import_battles(conn):
    dirs = [
        f'{DATA_ROOT}/decompressed_data_report/0000000a',
        f'{DATA_ROOT}/capture_new/0000000a',
    ]
    # 也扫根目录
    import glob
    root_files = glob.glob(f'{DATA_ROOT}/decompressed_data_report/decompressed_*_0000000a.json')
    root_files += glob.glob(f'{DATA_ROOT}/capture_new/decompressed_*_0000000a.json')

    all_files = root_files[:]
    for d in dirs:
        if os.path.exists(d):
            for f in os.listdir(d):
                if f.endswith('.json'):
                    all_files.append(os.path.join(d, f))

    # 已导入的
    existing = set(r[0] for r in conn.execute('SELECT battle_id FROM battles').fetchall())
    print(f'已有 {len(existing)} 条战报，扫描 {len(all_files)} 个文件...')

    new_battles = added = 0
    for fpath in all_files:
        try:
            with open(fpath, 'r', encoding='utf-8') as f:
                data = json.load(f)
            battles = []
            if isinstance(data, list) and len(data) >= 2 and isinstance(data[1], list):
                battles = data[1]
            elif isinstance(data, list):
                battles = [b for b in data if isinstance(b, dict) and 'battle_id' in b]
            for b in battles:
                if not isinstance(b, dict) or not b.get('battle_id'): continue
                bid = b['battle_id']
                if bid in existing: continue
                existing.add(bid)
                new_battles += 1

                ts = b.get('time', 0)
                ts_str = datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:%M:%S') if ts else ''
                result = b.get('result', -1)

                atk_heroes = parse_hero_info(b.get('attack_all_hero_info', ''))
                def_heroes = parse_hero_info(b.get('defend_all_hero_info', ''))
                skills = parse_skill_info(b.get('all_skill_info', ''))

                # 插入战报
                conn.execute('''
                    INSERT OR IGNORE INTO battles VALUES
                    (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ''', (
                    bid, ts, ts_str, result, RESULT_MAP.get(result, str(result)),
                    b.get('fight_type',0), b.get('city_type',0), b.get('wid_name',''),
                    b.get('is_ai',0), b.get('npc',0), b.get('weather',0), b.get('in_night_mode',0),
                    b.get('battle_scenes',0),
                    b.get('attack_name',''), b.get('attack_union_name',''), b.get('attack_unionid',0),
                    b.get('attack_base_level',0), b.get('attack_hp',0),
                    len(atk_heroes), sum(h['damage_taken'] for h in atk_heroes),
                    b.get('attacker_gongxun',0),
                    b.get('defend_name',''), b.get('defend_union_name',''), b.get('defend_unionid',0),
                    b.get('defend_base_level',0), b.get('defend_hp',0),
                    len(def_heroes), sum(h['damage_taken'] for h in def_heroes),
                    b.get('defender_gongxun',0),
                    os.path.basename(fpath)
                ))

                # 插入武将
                for pos, h in enumerate(atk_heroes, 1):
                    conn.execute('INSERT INTO battle_heroes(battle_id,side,pos,hero_id,hero_name,level,max_hp,remain_hp,damage_taken) VALUES(?,?,?,?,?,?,?,?,?)',
                        (bid,'atk',pos,h['id'],h['name'],h['level'],h['max_hp'],h['remain_hp'],h['damage_taken']))
                for pos, h in enumerate(def_heroes, 1):
                    conn.execute('INSERT INTO battle_heroes(battle_id,side,pos,hero_id,hero_name,level,max_hp,remain_hp,damage_taken) VALUES(?,?,?,?,?,?,?,?,?)',
                        (bid,'def',pos,h['id'],h['name'],h['level'],h['max_hp'],h['remain_hp'],h['damage_taken']))

                # 插入技能
                for sk in skills:
                    conn.execute('INSERT INTO battle_skills(battle_id,side,pos,skill_id,skill_name,skill_level) VALUES(?,?,?,?,?,?)',
                        (bid,sk['side'],sk['pos'],sk['skill_id'],sk['skill_name'],sk['skill_level']))

                # 插入武勋
                if b.get('attack_name') and not b.get('is_ai'):
                    conn.execute('INSERT INTO wuxun(battle_id,time,player_name,union_name,side,gongxun,fight_type,city_type,result) VALUES(?,?,?,?,?,?,?,?,?)',
                        (bid,ts,b.get('attack_name',''),b.get('attack_union_name',''),'atk',b.get('attacker_gongxun',0),b.get('fight_type',0),b.get('city_type',0),result))
                if b.get('defend_name') and not b.get('is_ai'):
                    conn.execute('INSERT INTO wuxun(battle_id,time,player_name,union_name,side,gongxun,fight_type,city_type,result) VALUES(?,?,?,?,?,?,?,?,?)',
                        (bid,ts,b.get('defend_name',''),b.get('defend_union_name',''),'def',b.get('defender_gongxun',0),b.get('fight_type',0),b.get('city_type',0),result))

                added += 1
                if added % 100 == 0:
                    conn.commit()
                    print(f'  已导入 {added} 条新战报...')
        except Exception as e:
            print(f'  ERR {fpath}: {e}')

    conn.commit()
    print(f'战报导入完成: 新增 {new_battles} 条')
    return new_battles

def import_unions(conn):
    import glob
    dirs = [
        f'{DATA_ROOT}/decompressed_data_report/000002bc',
        f'{DATA_ROOT}/capture_new/000002bc',
    ]
    all_files = glob.glob(f'{DATA_ROOT}/decompressed_data_report/decompressed_*_000002bc.json')
    all_files += glob.glob(f'{DATA_ROOT}/capture_new/decompressed_*_000002bc.json')
    for d in dirs:
        if os.path.exists(d):
            for fn in os.listdir(d):
                if fn.endswith('.json'):
                    all_files.append(os.path.join(d, fn))

    # 合并所有文件的联盟数据（不同文件可能是不同页/不同时间，用 union_id 去重取最新）
    all_unions = {}  # union_id -> union_dict
    ts_str = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    for fpath in all_files:
        try:
            with open(fpath, 'r', encoding='utf-8') as f:
                data = json.load(f)
            if not isinstance(data, list) or len(data) <= 4: continue
            arr = data[4]
            if not isinstance(arr, list): continue
            for item in arr:
                if not isinstance(item, list) or len(item) < 2: continue
                u = item[1]
                if not isinstance(u, dict) or not u.get('union_id'): continue
                uid = u['union_id']
                # 取 power 最大的版本（最新数据）
                if uid not in all_unions or u.get('power', 0) > all_unions[uid].get('power', 0):
                    all_unions[uid] = u
        except Exception as e:
            print(f'  联盟文件ERR {fpath}: {e}')

    if not all_unions:
        print('未找到联盟数据'); return 0

    for u in all_unions.values():
        conn.execute('INSERT OR REPLACE INTO unions VALUES(?,?,?,?,?,?,?)',
            (u['union_id'], u.get('name',''), u.get('level',0),
             u.get('power',0), u.get('total_member',0), u.get('region',0), ts_str))
    conn.commit()
    print(f'联盟导入完成: {len(all_unions)} 个联盟（合并自 {len(all_files)} 个文件）')
    return len(all_unions)

def import_player_teams(conn):
    fpath = f'{DATA_ROOT}/player_team_details_dedup.json'
    if not os.path.exists(fpath):
        print('未找到 player_team_details_dedup.json'); return 0
    with open(fpath, 'r', encoding='utf-8') as f:
        data = json.load(f)
    conn.execute('DELETE FROM player_teams')
    count = 0
    for player in data:
        pname = player.get('玩家名称', '')
        for side_key, side in [('攻击方队伍详情','atk'),('防守方队伍详情','def')]:
            for team in player.get(side_key, []):
                heroes = [h.strip() for h in team.get('队伍配置','').split('+') if h.strip()]
                h1 = heroes[0] if len(heroes) > 0 else ''
                h2 = heroes[1] if len(heroes) > 1 else ''
                h3 = heroes[2] if len(heroes) > 2 else ''
                conn.execute('INSERT INTO player_teams(player_name,side,hero1,hero2,hero3,heroes_str,used_count,win_count,win_rate) VALUES(?,?,?,?,?,?,?,?,?)',
                    (pname, side, h1, h2, h3, team.get('队伍配置',''),
                     team.get('使用次数',0), team.get('胜利次数',0), team.get('胜率(%)',0)))
                count += 1
    conn.commit()
    print(f'玩家队伍导入完成: {count} 条记录')
    return count

def calc_scores(conn):
    conn.execute('DELETE FROM scores')
    rows = conn.execute('''
        SELECT
            COALESCE(NULLIF(b.atk_name,''),b.def_name) as pname,
            COALESCE(NULLIF(b.atk_union,''),b.def_union) as uname,
            COUNT(*) as battles,
            SUM(CASE WHEN b.atk_name != '' AND b.atk_name IS NOT NULL THEN 1 ELSE 0 END) as atk_cnt,
            SUM(CASE WHEN b.def_name != '' AND b.def_name IS NOT NULL THEN 1 ELSE 0 END) as def_cnt,
            SUM(CASE WHEN (b.result=1 AND b.atk_name!='') OR (b.result IN (2,6) AND b.def_name!='') THEN 1 ELSE 0 END) as wins,
            SUM(b.atk_gongxun + b.def_gongxun) as wx
        FROM battles b
        WHERE b.is_ai=0 AND b.is_npc=0
        GROUP BY pname
        HAVING pname IS NOT NULL AND pname != ''
    ''').fetchall()
    for r in rows:
        score = r['battles']*2 + r['wins']*3 + r['wx']//1000
        conn.execute('INSERT OR REPLACE INTO scores(player_name,union_name,period,battle_count,atk_count,def_count,win_count,wuxun_total,custom_score) VALUES(?,?,?,?,?,?,?,?,?)',
            (r['pname'],r['uname'],'all',r['battles'],r['atk_cnt'],r['def_cnt'],r['wins'],r['wx'],score))
    conn.commit()
    print(f'积分计算完成: {len(rows)} 名玩家')

if __name__ == '__main__':
    from db_build import create_tables, get_db
    conn = get_db()
    create_tables(conn)
    import_battles(conn)
    import_unions(conn)
    import_player_teams(conn)
    calc_scores(conn)
    # 统计
    for tbl in ['battles','battle_heroes','battle_skills','unions','player_teams','wuxun','scores']:
        cnt = conn.execute(f'SELECT COUNT(*) FROM {tbl}').fetchone()[0]
        print(f'  {tbl}: {cnt} 条')
    conn.close()
    print('数据库构建完成:', DB_PATH)

