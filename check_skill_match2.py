# -*- coding: utf-8 -*-
# 根据 hero_extra.json 里武将的 uniqueName 精确匹配，避免同名武将干扰
import json, sys
sys.path.insert(0, '.')
from battle_sim.data import HEROS, SKILLS

extra = json.load(open('stzbBattleSimulator-main/cfg/hero_extra.json', encoding='utf-8'))

# 建立 uniqueName -> {mid, mname} 精确映射
extra_by_uname = {}
for e in extra:
    uname = e.get('uniqueName', '')
    mid = e.get('methodId1') or e.get('methodId')
    mname = e.get('methodName1') or e.get('methodName', '')
    if not uname or not mid:
        continue
    try:
        mid = int(float(mid))
    except:
        continue
    if uname not in extra_by_uname:
        extra_by_uname[uname] = {'mid': mid, 'mname': mname}

# hero_extra里用 iconId 或 id 对应我方 hero id
# 我方 HEROS key 是 100xxx，hero_extra 里的 id 也是 100xxx
extra_by_id = {}
for e in extra:
    hid = e.get('id')
    mid = e.get('methodId1') or e.get('methodId')
    mname = e.get('methodName1') or e.get('methodName', '')
    uname = e.get('uniqueName', '')
    if not hid or not mid:
        continue
    try:
        hid = int(float(hid))
        mid = int(float(mid))
    except:
        continue
    if hid not in extra_by_id:
        extra_by_id[hid] = {'mid': mid, 'mname': mname, 'uname': uname}

print('=== 用 hero id 精确匹配 ===')
wrong = []
for h in HEROS.values():
    entry = extra_by_id.get(h['id'])
    if not entry:
        continue
    exp_mid = entry['mid']
    exp_name = entry['mname']
    if h['skill'] != exp_mid and exp_mid in SKILLS:
        cur_sk = SKILLS.get(h['skill'])
        cur_name = cur_sk.name if cur_sk else 'None'
        exp_sk = SKILLS[exp_mid]
        wrong.append((h['id'], h['name'], h['skill'], cur_name, exp_mid, exp_name, entry['uname']))

print('id       name       cur_name         -> exp_name')
print('-'*80)
for hid, name, csid, csname, esid, esname, uname in wrong:
    print(f'{hid} {name:<12} {csname:<20} -> {esname}  [{uname}]')

print(f'\n需修复: {len(wrong)}')

# 生成修复脚本
print('\n--- 修复代码 ---')
for hid, name, csid, csname, esid, esname, uname in wrong:
    print(f'    {hid}: {esid},  # {name} {csname} -> {esname}')

