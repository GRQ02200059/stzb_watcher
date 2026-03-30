# -*- coding: utf-8 -*-
"""
扩展数据导入 - 解析所有已知消息类型
"""
import sqlite3, json, os, glob
from datetime import datetime
from db_extend import get_db, create_ext_tables

DATA_ROOT = 'd:/nettest/decompressed_data_report'

# 加载英雄名称映射
try:
    with open('d:/nettest/hero_scraper/output/heroes.json','r',encoding='utf-8') as f:
        HERO_NAMES = {h['id']: h['name'] for h in json.load(f) if h.get('id')}
except:
    HERO_NAMES = {}

def hero_name(hid):
    return HERO_NAMES.get(int(hid), f'武将{hid}') if hid else ''

def ts_now():
    return datetime.now().strftime('%Y-%m-%d %H:%M:%S')

def all_json_files(msg_types=None):
    """扫描所有匹配类型的json文件"""
    files = []
    # 根目录文件
    for f in glob.glob(f'{DATA_ROOT}/decompressed_*.json'):
        fname = os.path.basename(f)
        parts = fname.replace('.json','').split('_')
        mtype = parts[-1] if len(parts) >= 3 else 'unknown'
        if msg_types is None or mtype in msg_types:
            files.append((f, mtype))
    # 子目录文件
    for subdir in os.listdir(DATA_ROOT):
        sdpath = os.path.join(DATA_ROOT, subdir)
        if not os.path.isdir(sdpath): continue
        if msg_types and subdir not in msg_types: continue
        for fn in os.listdir(sdpath):
            if fn.endswith('.json'):
                files.append((os.path.join(sdpath, fn), subdir))
    # capture_new 目录
    cap_new = 'd:/nettest/capture_new'
    if os.path.exists(cap_new):
        for subdir in os.listdir(cap_new):
            sdpath = os.path.join(cap_new, subdir)
            if not os.path.isdir(sdpath): continue
            if msg_types and subdir not in msg_types: continue
            for fn in os.listdir(sdpath):
                if fn.endswith('.json'):
                    files.append((os.path.join(sdpath, fn), subdir))
    return files

# ============================================================
# 1. 玩家坐标列表 (00000f1d, 000013b9)
# 格式: list of [wid, name, level, power, region, x, 0, 0, 0,
#               union_id, union_name, 0, city_type, city_id, durability, ...]
# ============================================================
def import_player_locations(conn):
    types = {'00000f1d', '000013b9'}
    files = all_json_files(types)
    conn.execute('DELETE FROM player_locations')
    ts = ts_now()
    # 先收集所有数据，按 wid 去重（保留战力最高的记录）
    best = {}  # wid -> row_dict
    for fpath, mtype in files:
        try:
            with open(fpath,'r',encoding='utf-8') as f:
                data = json.load(f)
            if not isinstance(data, list): continue
            for row in data:
                if not isinstance(row, list) or len(row) < 15: continue
                try:
                    wid   = row[9]
                    power = row[14]
                    if wid not in best or power > best[wid]['power']:
                        best[wid] = {
                            'wid':        wid,
                            'player_name':row[1],
                            'level':      row[2],
                            'power':      power,
                            'region':     row[4],
                            'x':          row[5],
                            'union_id':   row[0],
                            'union_name': row[10] if len(row)>10 else '',
                            'city_type':  row[12] if len(row)>12 else 0,
                            'city_id':    wid,
                            'durability': row[13] if len(row)>13 else 0,
                            'score':      row[21] if len(row)>21 else 0,
                            'source_type':mtype,
                        }
                except: pass
        except Exception as e:
            print(f'  ERR {fpath}: {e}')
    count = 0
    for r in best.values():
        try:
            conn.execute(
                'INSERT INTO player_locations'
                '(wid,player_name,level,power,region,x,union_id,union_name,city_type,city_id,durability,score,source_type,captured_at)'
                ' VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
                (r['wid'],r['player_name'],r['level'],r['power'],r['region'],r['x'],
                 r['union_id'],r['union_name'],r['city_type'],r['city_id'],
                 r['durability'],r['score'],r['source_type'],ts)
            )
            count += 1
        except: pass
    conn.commit()
    print(f'玩家坐标: {count} 条（已去重，来自 {len(files)} 文件）')
    return count

# ============================================================
# 2. 数据库同步记录 (00015f95 + 00000ad* + 00000bc* + 00000eb6 等)
# 格式: list of [op, table_name, [row_id, user_id, field_id, value, ...]]
# op: 1=insert 2=update 3=delete
# ============================================================
DB_SYNC_TYPES = {
    '00015f95','00000ad3','00000ad6','00000ad8','00000ad9',
    '00000ada','00000adc','00000add','00000ade','00000adf',
    '00000bc8','00000bc9','00000da9','00000eb6','00000eb5',
    '00000d31','00000f59','00000528','00000411','0000042d',
    '00000497','000004f0'
}

def import_db_sync(conn):
    files = all_json_files(DB_SYNC_TYPES)
    conn.execute('DELETE FROM db_sync')
    # 同时解析 Tb_hero
    conn.execute('DELETE FROM player_heroes')
    ts = ts_now()
    sync_count = hero_count = 0

    for fpath, mtype in files:
        try:
            with open(fpath,'r',encoding='utf-8') as f:
                data = json.load(f)
            if not isinstance(data, list): continue
            for row in data:
                if not isinstance(row, list) or len(row) < 3: continue
                op = row[0]
                tbl = row[1] if isinstance(row[1], str) else ''
                payload = row[2] if len(row) > 2 else []
                raw = json.dumps(row, ensure_ascii=False)[:2000]
                try:
                    row_id = payload[0] if isinstance(payload, list) and payload else 0
                    conn.execute(
                        'INSERT INTO db_sync(op,table_name,row_id,raw_json,captured_at,source_type) VALUES(?,?,?,?,?,?)',
                        (op, tbl, row_id, raw, ts, mtype)
                    )
                    sync_count += 1
                except: pass

                # 专门解析 Tb_hero
                if tbl == 'Tb_hero' and isinstance(payload, list) and len(payload) >= 22:
                    try:
                        # payload: [inst_id, hero_id, user_id, ?, ?, ?, star, hp_max, level,
                        #           time, 0,0,0,0,0,0,0, atk,def,speed,intel,force, skill_str, ...]
                        conn.execute(
                            'INSERT INTO player_heroes(hero_inst_id,hero_id,user_id,level,star,hp,atk,def_val,speed,intel,skill_str,captured_at,source_type) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)',
                            (payload[0], payload[1], payload[2],
                             payload[8] if len(payload)>8 else 0,
                             payload[6] if len(payload)>6 else 0,
                             payload[7] if len(payload)>7 else 0,
                             payload[17] if len(payload)>17 else 0,
                             payload[18] if len(payload)>18 else 0,
                             payload[19] if len(payload)>19 else 0,
                             payload[20] if len(payload)>20 else 0,
                             payload[22] if len(payload)>22 else '',
                             ts, mtype)
                        )
                        hero_count += 1
                    except: pass
        except Exception as e:
            print(f'  ERR {fpath}: {e}')
    conn.commit()
    print(f'DB同步记录: {sync_count} 条, 英雄背包: {hero_count} 条 ({len(files)} 文件)')
    return sync_count

# ============================================================
# 3. 联盟成员城池 (00002624, 0000262a, 000026b6, 00002734, 000146a4)
# 格式: list of [wid, name, player_id, ?, ?, ?, power, hp, region, ?, ?, ?,
#               ?, '', 0, 0, city_type_id, hero_ids_str, ...]
# ============================================================
UNION_CITY_TYPES = {
    '00002624','0000262a','000026b6','00002734','00002851',
    '000146a4'
}

def import_union_cities(conn):
    files = all_json_files(UNION_CITY_TYPES)
    conn.execute('DELETE FROM union_cities')
    ts = ts_now()
    count = 0
    for fpath, mtype in files:
        try:
            with open(fpath,'r',encoding='utf-8') as f:
                data = json.load(f)
            if not isinstance(data, list): continue
            # 000146a4 是嵌套列表 [[wid, [...]]] 结构
            rows = data
            if rows and isinstance(rows[0], list) and len(rows[0]) == 2 and isinstance(rows[0][1], list):
                rows = [r[1] for r in rows if isinstance(r, list) and len(r) > 1]
            for row in rows:
                if not isinstance(row, list) or len(row) < 4: continue
                try:
                    conn.execute(
                        'INSERT INTO union_cities(wid,player_name,player_id,power,hp,city_type,hero_ids,captured_at,source_type) VALUES(?,?,?,?,?,?,?,?,?)',
                        (row[0], row[1] if isinstance(row[1],str) else '',
                         row[2] if len(row)>2 else 0,
                         row[6] if len(row)>6 else 0,
                         row[7] if len(row)>7 else 0,
                         row[16] if len(row)>16 else 0,
                         row[17] if len(row)>17 and isinstance(row[17],str) else '',
                         ts, mtype)
                    )
                    count += 1
                except: pass
        except Exception as e:
            print(f'  ERR {fpath}: {e}')
    conn.commit()
    print(f'联盟城池: {count} 条 ({len(files)} 文件)')
    return count

# ============================================================
# 4. 玩家战绩 (000001fe, 000008a3, 00001105)
# 格式: dict 或 list[dict]
# ============================================================
def import_player_records(conn):
    types = {'000001fe','000008a3','00001105'}
    files = all_json_files(types)
    conn.execute('DELETE FROM player_records')
    ts = ts_now()
    count = 0
    for fpath, mtype in files:
        try:
            with open(fpath,'r',encoding='utf-8') as f:
                data = json.load(f)
            records = [data] if isinstance(data, dict) else data if isinstance(data, list) else []
            for rec in records:
                if not isinstance(rec, dict): continue
                try:
                    conn.execute(
                        'INSERT INTO player_records(wuxun_total,army_kill_max,attack_npc_city,destroy_build,force_max,week_wuxun,raw_json,captured_at,source_type) VALUES(?,?,?,?,?,?,?,?,?)',
                        (rec.get('wuxun_total',0), rec.get('army_kill_enemy_max',0),
                         rec.get('attack_npc_city',0), rec.get('destroy_build',0),
                         rec.get('force_max',0), rec.get('week_wuxun_max',0),
                         json.dumps(rec,ensure_ascii=False)[:3000], ts, mtype)
                    )
                    count += 1
                except: pass
        except Exception as e:
            print(f'  ERR {fpath}: {e}')
    conn.commit()
    print(f'玩家战绩: {count} 条 ({len(files)} 文件)')
    return count

# ============================================================
# 5. 英雄解锁时间串 (0000029f, 00000fd4, 000010e2)
# 格式: "hero_id,timestamp;hero_id,timestamp;..."
# ============================================================
def import_hero_unlock(conn):
    types = {'0000029f','00000fd4','000010e2'}
    files = all_json_files(types)
    conn.execute('DELETE FROM hero_unlock')
    ts = ts_now()
    count = 0
    seen = set()
    for fpath, mtype in files:
        try:
            with open(fpath,'r',encoding='utf-8') as f:
                data = json.load(f)
            if not isinstance(data, str): continue
            for part in data.split(';'):
                part = part.strip()
                if not part: continue
                kv = part.split(',')
                if len(kv) < 2: continue
                try:
                    hid = int(kv[0])
                    ut  = int(kv[1])
                    if (hid, ut) in seen: continue
                    seen.add((hid, ut))
                    conn.execute(
                        'INSERT INTO hero_unlock(hero_id,unlock_time,captured_at) VALUES(?,?,?)',
                        (hid, ut, ts)
                    )
                    count += 1
                except: pass
        except Exception as e:
            print(f'  ERR {fpath}: {e}')
    conn.commit()
    print(f'英雄解锁: {count} 条 ({len(files)} 文件)')
    return count

if __name__ == '__main__':
    conn = get_db()
    create_ext_tables(conn)
    import_player_locations(conn)
    import_db_sync(conn)
    import_union_cities(conn)
    import_player_records(conn)
    import_hero_unlock(conn)
    print('\n=== 扩展数据汇总 ===')
    for tbl in ['player_locations','player_heroes','db_sync','union_cities','player_records','hero_unlock']:
        cnt = conn.execute(f'SELECT COUNT(*) FROM {tbl}').fetchone()[0]
        print(f'  {tbl}: {cnt} 条')
    conn.close()
    print('完成')

