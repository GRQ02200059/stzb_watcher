# -*- coding: utf-8 -*-
"""
战报数据深度挖掘统计
提取字段：
- 战斗基本信息：battle_id, result, time, city_type, fight_type
- 攻防双方：名称、联盟、武将组合、等级、HP、兵力
- 技能信息：all_skill_info
- 武将详情：attack_all_hero_info (hero_id, level, max_hp, remain_hp, damage)
- 其他：is_ai, npc, weather, in_night_mode, battle_scenes
"""
import json, os, re, csv
from datetime import datetime
from collections import defaultdict

# 武将ID -> 名称（从网易官网爬取的数据）
try:
    with open('d:/nettest/hero_scraper/output/heroes.json', 'r', encoding='utf-8') as f:
        heroes_raw = json.load(f)
    HERO_NAMES = {str(h['id']): h['name'] for h in heroes_raw if h.get('id') and h.get('name')}
except:
    HERO_NAMES = {}
    print('警告: 未找到武将数据，将使用ID显示')

try:
    with open('d:/nettest/hero_scraper/output/skills_full.json', 'r', encoding='utf-8') as f:
        skills_raw = json.load(f)
    SKILL_NAMES = {str(s['id']): s['name'] for s in skills_raw if s.get('id') and s.get('name')}
except:
    SKILL_NAMES = {}

def hero_name(hid):
    return HERO_NAMES.get(str(hid), f'武将{hid}')

def skill_name(sid):
    return SKILL_NAMES.get(str(sid), f'技能{sid}')

def parse_hero_info(s):
    """解析 attack_all_hero_info: 'hero_id,level,max_hp,remain_hp,damage;...'"""
    heroes = []
    if not s: return heroes
    for part in s.split(';'):
        parts = part.strip().split(',')
        if len(parts) >= 5:
            try:
                hid = int(parts[0])
                if hid > 0:
                    heroes.append({
                        'id': hid,
                        'name': hero_name(hid),
                        'level': int(parts[1]),
                        'max_hp': int(parts[2]),
                        'remain_hp': int(parts[3]),
                        'damage_taken': int(parts[4]),
                    })
            except: pass
    return heroes

def parse_skill_info(s):
    """解析 all_skill_info: 'pos,skill1,lv,skill2,lv,skill3,lv;...'"""
    skills_by_pos = {}
    if not s: return skills_by_pos
    for part in s.split(';'):
        parts = part.strip().split(',')
        if len(parts) >= 3:
            try:
                pos = int(parts[0])
                sks = []
                for i in range(1, len(parts)-1, 2):
                    sid = int(parts[i])
                    if sid > 0:
                        sks.append({'id': sid, 'name': skill_name(sid), 'level': int(parts[i+1])})
                if sks:
                    skills_by_pos[pos] = sks
            except: pass
    return skills_by_pos

# result 含义
RESULT_MAP = {0: '平局/进行中', 1: '攻方胜', 2: '守方胜', 3: '攻方溃败', 4: '守方溃败', 5: '双方溃败', 6: '守方胜(NPC)'}
FIGHT_TYPE_MAP = {1: '普通战', 2: '攻城战', 3: '野战', 4: '埋伏', 5: '援兵战'}
CITY_TYPE_MAP = {0: '野地', 1: '城池', 2: '土地', 3: '关卡', 4: '大营', 5: '皇城'}

# 收集所有战报
all_battles = []
dirs_to_scan = [
    'd:/nettest/decompressed_data_report/0000000a',
    'd:/nettest/capture_new/0000000a',
]

print('扫描战报文件...')
for d in dirs_to_scan:
    if not os.path.exists(d): continue
    for fname in os.listdir(d):
        if not fname.endswith('.json'): continue
        fpath = os.path.join(d, fname)
        try:
            with open(fpath, 'r', encoding='utf-8') as f:
                data = json.load(f)
            battles = []
            if isinstance(data, list) and len(data) >= 2 and isinstance(data[1], list):
                battles = data[1]
            elif isinstance(data, list):
                battles = [b for b in data if isinstance(b, dict) and 'battle_id' in b]
            for b in battles:
                if isinstance(b, dict) and b.get('battle_id'):
                    all_battles.append(b)
        except Exception as e:
            print(f'  读取失败 {fname}: {e}')

print(f'共收集到 {len(all_battles)} 条战报')

if not all_battles:
    print('无战报数据，退出')
    exit()

# ====== 去重 ======
seen = set()
uniq_battles = []
for b in all_battles:
    bid = b['battle_id']
    if bid not in seen:
        seen.add(bid)
        uniq_battles.append(b)
print(f'去重后: {len(uniq_battles)} 条')

# ====== 深度解析 ======
records = []
for b in uniq_battles:
    atk_heroes = parse_hero_info(b.get('attack_all_hero_info', ''))
    def_heroes = parse_hero_info(b.get('defend_all_hero_info', ''))
    skills = parse_skill_info(b.get('all_skill_info', ''))
    
    ts = b.get('time', 0)
    dt_str = datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:%M:%S') if ts else ''
    
    result = b.get('result', -1)
    
    rec = {
        'battle_id': b.get('battle_id'),
        'time': dt_str,
        'timestamp': ts,
        'result': result,
        'result_desc': RESULT_MAP.get(result, f'未知({result})'),
        'atk_win': 1 if result == 1 else 0,
        'def_win': 1 if result in [2, 6] else 0,
        'fight_type': FIGHT_TYPE_MAP.get(b.get('fight_type', 0), str(b.get('fight_type', ''))),
        'city_type': CITY_TYPE_MAP.get(b.get('city_type', 0), str(b.get('city_type', ''))),
        'wid_name': b.get('wid_name', ''),
        'is_ai': b.get('is_ai', 0),
        'is_npc': b.get('npc', 0),
        'weather': b.get('weather', 0),
        'in_night': b.get('in_night_mode', 0),
        'battle_scenes': b.get('battle_scenes', 0),
        # 攻方
        'atk_name': b.get('attack_name', ''),
        'atk_union': b.get('attack_union_name', ''),
        'atk_unionid': b.get('attack_unionid', 0),
        'atk_level': b.get('attack_base_level', 0),
        'atk_hp': b.get('attack_hp', 0),
        'atk_hero1': atk_heroes[0]['name'] if len(atk_heroes) > 0 else '',
        'atk_hero1_id': atk_heroes[0]['id'] if len(atk_heroes) > 0 else 0,
        'atk_hero1_dmg': atk_heroes[0]['damage_taken'] if len(atk_heroes) > 0 else 0,
        'atk_hero2': atk_heroes[1]['name'] if len(atk_heroes) > 1 else '',
        'atk_hero2_id': atk_heroes[1]['id'] if len(atk_heroes) > 1 else 0,
        'atk_hero3': atk_heroes[2]['name'] if len(atk_heroes) > 2 else '',
        'atk_hero3_id': atk_heroes[2]['id'] if len(atk_heroes) > 2 else 0,
        'atk_hero_count': len(atk_heroes),
        'atk_total_dmg': sum(h['damage_taken'] for h in atk_heroes),
        # 守方
        'def_name': b.get('defend_name', ''),
        'def_union': b.get('defend_union_name', ''),
        'def_unionid': b.get('defend_unionid', 0),
        'def_level': b.get('defend_base_level', 0),
        'def_hp': b.get('defend_hp', 0),
        'def_hero1': def_heroes[0]['name'] if len(def_heroes) > 0 else '',
        'def_hero1_id': def_heroes[0]['id'] if len(def_heroes) > 0 else 0,
        'def_hero1_dmg': def_heroes[0]['damage_taken'] if len(def_heroes) > 0 else 0,
        'def_hero2': def_heroes[1]['name'] if len(def_heroes) > 1 else '',
        'def_hero2_id': def_heroes[1]['id'] if len(def_heroes) > 1 else 0,
        'def_hero3': def_heroes[2]['name'] if len(def_heroes) > 2 else '',
        'def_hero3_id': def_heroes[2]['id'] if len(def_heroes) > 2 else 0,
        'def_hero_count': len(def_heroes),
        'def_total_dmg': sum(h['damage_taken'] for h in def_heroes),
        # 技能
        'atk_skills': '|'.join(s['name'] for ss in [skills.get(i,[]) for i in [1,2,3]] for s in ss),
        'def_skills': '|'.join(s['name'] for ss in [skills.get(i,[]) for i in [4,5,6]] for s in ss),
        # 完整武将列表
        'atk_heroes_full': json.dumps([h['name'] for h in atk_heroes], ensure_ascii=False),
        'def_heroes_full': json.dumps([h['name'] for h in def_heroes], ensure_ascii=False),
    }
    records.append(rec)

# ====== 保存详细CSV ======
out_csv = 'd:/nettest/dashboard/battle_detail.csv'
if records:
    with open(out_csv, 'w', encoding='utf-8-sig', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=list(records[0].keys()))
        writer.writeheader()
        writer.writerows(records)
    print(f'详细战报已保存: {out_csv} ({len(records)} 条)')

# ====== 统计分析 ======
print('\n====== 战报统计 ======')
print(f'总战报数: {len(records)}')

# 结果分布
result_cnt = defaultdict(int)
for r in records: result_cnt[r['result_desc']] += 1
print('\n结果分布:')
for k,v in sorted(result_cnt.items(), key=lambda x:-x[1]):
    print(f'  {k}: {v} ({v/len(records)*100:.1f}%)')

# 城池类型
city_cnt = defaultdict(int)
for r in records: city_cnt[r['city_type']] += 1
print('\n城池类型分布:')
for k,v in sorted(city_cnt.items(), key=lambda x:-x[1]):
    print(f'  {k}: {v}')

# 武将出场频次
hero_cnt = defaultdict(int)
for r in records:
    for h in [r['atk_hero1'],r['atk_hero2'],r['atk_hero3'],r['def_hero1'],r['def_hero2'],r['def_hero3']]:
        if h: hero_cnt[h] += 1
print('\n武将出场TOP20:')
for h,c in sorted(hero_cnt.items(), key=lambda x:-x[1])[:20]:
    print(f'  {h}: {c}次')

# 武将组合（攻方）
combo_cnt = defaultdict(int)
for r in records:
    heroes = [r['atk_hero1'],r['atk_hero2'],r['atk_hero3']]
    heroes = [h for h in heroes if h]
    if len(heroes) >= 2:
        combo = '+'.join(sorted(heroes))
        combo_cnt[combo] += 1
print('\n攻方武将组合TOP10:')
for c,n in sorted(combo_cnt.items(), key=lambda x:-x[1])[:10]:
    print(f'  {c}: {n}次')

# 联盟胜率
union_stats = defaultdict(lambda: {'total':0,'wins':0,'name':''})
for r in records:
    if r['atk_union']:
        uid = str(r['atk_unionid'])
        union_stats[uid]['total'] += 1
        union_stats[uid]['wins'] += r['atk_win']
        union_stats[uid]['name'] = r['atk_union']
    if r['def_union']:
        uid = str(r['def_unionid'])+'_def'
        union_stats[uid]['total'] += 1
        union_stats[uid]['wins'] += r['def_win']
        union_stats[uid]['name'] = r['def_union']
print('\n联盟战报TOP10(按总战斗数):')
union_list = [(v['name'], v['total'], v['wins']) for k,v in union_stats.items() if v['total']>=2]
for name,total,wins in sorted(union_list, key=lambda x:-x[1])[:10]:
    wr = wins/total*100 if total else 0
    print(f'  {name}: {total}场 胜率{wr:.0f}%')

# 保存统计JSON
stats_out = {
    'total': len(records),
    'result_dist': dict(result_cnt),
    'city_dist': dict(city_cnt),
    'hero_freq': dict(sorted(hero_cnt.items(), key=lambda x:-x[1])[:50]),
    'atk_combo_top': dict(sorted(combo_cnt.items(), key=lambda x:-x[1])[:20]),
    'union_stats': [
        {'name':v['name'],'total':v['total'],'wins':v['wins'],'win_rate':round(v['wins']/v['total']*100,1) if v['total'] else 0}
        for k,v in union_stats.items() if v['total']>=1
    ],
}
with open('d:/nettest/dashboard/battle_stats.json', 'w', encoding='utf-8') as f:
    json.dump(stats_out, f, ensure_ascii=False, indent=2)
print('\n统计数据已保存: d:/nettest/dashboard/battle_stats.json')

