# -*- coding: utf-8 -*-
import json, sys
sys.path.insert(0, '.')
from battle_sim.data import HEROS, SKILLS

extra = json.load(open('stzbBattleSimulator-main/cfg/hero_extra.json', encoding='utf-8'))

extra_by_name = {}
for e in extra:
    mid = e.get('methodId1') or e.get('methodId')
    mname = e.get('methodName1') or e.get('methodName', '')
    name = e.get('name', '')
    if not mid or not name:
        continue
    try:
        mid = int(float(mid))
    except:
        continue
    if name not in extra_by_name:
        extra_by_name[name] = []
    entry = {'mid': mid, 'mname': mname}
    if entry not in extra_by_name[name]:
        extra_by_name[name].append(entry)

wrong = []
for h in HEROS.values():
    name = h['name']
    cur_sk = SKILLS.get(h['skill'])
    cur_name = cur_sk.name if cur_sk else 'None'
    entries = extra_by_name.get(name, [])
    if not entries:
        continue
    exp_mid = entries[0]['mid']
    exp_name = entries[0]['mname']
    if h['skill'] != exp_mid:
        wrong.append((h['id'], name, h['skill'], cur_name, exp_mid, exp_name))

print('id       name     cur_id  cur_name         exp_id  exp_name')
print('-'*80)
for hid, name, csid, csname, esid, esname in wrong:
    print(f'{hid}  {name:<10} {csid:<8} {csname:<16} {esid:<8} {esname}')

print(f'\n불일치: {len(wrong)} 个武将')

# 检查哪些 exp_id 在 SKILLS 里存在
fixable = [(hid, name, csid, csname, esid, esname) for hid,name,csid,csname,esid,esname in wrong if esid in SKILLS]
print(f'可修复(exp_id在SKILLS里): {len(fixable)}')
for hid, name, csid, csname, esid, esname in fixable:
    sk = SKILLS[esid]
    print(f'  {hid} {name}: {csname} -> {sk.name}')
