# -*- coding: utf-8 -*-
# 正确：武将技能用 methodId（不是 methodId1）
import json, sys
sys.path.insert(0, '.')
from battle_sim.data import HEROS, SKILLS

extra = json.load(open('stzbBattleSimulator-main/cfg/hero_extra.json', encoding='utf-8'))

# 建立 hero_id -> methodId 映射
extra_by_id = {}
for e in extra:
    hid = e.get('id')
    mid = e.get('methodId')   # 武将对应的技能
    mname = e.get('methodName', '')
    if not hid or not mid:
        continue
    try:
        hid = int(float(hid))
        mid = int(float(mid))
    except:
        continue
    extra_by_id[hid] = {'mid': mid, 'mname': mname}

print(f'hero_extra 条目数: {len(extra_by_id)}')

# 对比 HEROS 里的 skill 和 hero_extra 里的 methodId
wrong = []
for h in HEROS.values():
    entry = extra_by_id.get(h['id'])
    if not entry:
        continue
    exp_mid = entry['mid']
    if h['skill'] != exp_mid:
        cur_sk = SKILLS.get(h['skill'])
        exp_sk = SKILLS.get(exp_mid)
        wrong.append({
            'hid': h['id'],
            'name': h['name'],
            'cur_sid': h['skill'],
            'cur_name': cur_sk.name if cur_sk else '?',
            'exp_sid': exp_mid,
            'exp_name': entry['mname'],
            'exp_in_skills': exp_mid in SKILLS,
        })

print(f'不一致: {len(wrong)}')
fixable = [w for w in wrong if w['exp_in_skills']]
missing_sk = [w for w in wrong if not w['exp_in_skills']]
print(f'可修复(exp在SKILLS里): {len(fixable)}')
print(f'需实现(exp不在SKILLS里): {len(missing_sk)}')

print('\n=== 可修复 ===')
for w in fixable:
    print(f"  {w['hid']} {w['name']}: {w['cur_sid']}({w['cur_name']}) -> {w['exp_sid']}({w['exp_name']})")

print('\n=== 需实现的战法 ===')
seen = set()
for w in missing_sk:
    print(f"  {w['hid']} {w['name']}: cur={w['cur_name']} -> {w['exp_sid']}({w['exp_name']}) [MISSING]")
    if w['exp_sid'] not in seen:
        seen.add(w['exp_sid'])
print(f'\n共 {len(seen)} 个需实现的战法id: {sorted(seen)}')

