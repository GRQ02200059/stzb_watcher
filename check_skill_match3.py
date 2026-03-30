# -*- coding: utf-8 -*-
# 正确理解 hero_extra.json 结构：
# id = 武将id (对应 HEROS 的 key)
# methodId = 武将固有战法（自带，不可学习）
# methodId1 = 武将专属学习战法（这才是 data.py skill 字段应该用的）
import json, sys
sys.path.insert(0, '.')
from battle_sim.data import HEROS, SKILLS

extra = json.load(open('stzbBattleSimulator-main/cfg/hero_extra.json', encoding='utf-8'))

# 打印几条样本确认结构
print('=== 样本数据 ===')
for e in extra[:3]:
    print(f"id={e.get('id')} name={e.get('name')} methodId={e.get('methodId')} methodName={e.get('methodName')} methodId1={e.get('methodId1')} methodName1={e.get('methodName1')}")
    print()

# 建立 hero_id -> (methodId, methodId1) 映射
extra_by_id = {}
for e in extra:
    hid = e.get('id')
    mid = e.get('methodId')   # 固有战法
    mid1 = e.get('methodId1') # 专属学习战法
    name = e.get('name', '')
    mname = e.get('methodName', '')
    mname1 = e.get('methodName1', '')
    if not hid:
        continue
    try:
        hid = int(float(hid))
        mid = int(float(mid)) if mid else 0
        mid1 = int(float(mid1)) if mid1 else 0
    except:
        continue
    extra_by_id[hid] = {'mid': mid, 'mname': mname, 'mid1': mid1, 'mname1': mname1, 'name': name}

print(f'hero_extra 条目数: {len(extra_by_id)}')

# 对比 HEROS 里的 skill 和 hero_extra 里的 methodId1
print('\n=== skill 与 methodId1 不一致的武将 ===')
wrong = []
for h in HEROS.values():
    entry = extra_by_id.get(h['id'])
    if not entry:
        continue
    exp_mid1 = entry['mid1']
    if exp_mid1 == 0:
        continue
    if h['skill'] != exp_mid1:
        cur_sk = SKILLS.get(h['skill'])
        exp_sk = SKILLS.get(exp_mid1)
        wrong.append({
            'hid': h['id'],
            'name': h['name'],
            'cur_sid': h['skill'],
            'cur_name': cur_sk.name if cur_sk else '?',
            'exp_sid': exp_mid1,
            'exp_name': entry['mname1'],
            'exp_in_skills': exp_mid1 in SKILLS,
        })
        print(f"{h['id']} {h['name']}: cur={h['skill']}({cur_sk.name if cur_sk else '?'}) exp={exp_mid1}({entry['mname1']}) {'[IN SKILLS]' if exp_mid1 in SKILLS else '[MISSING]'}")

print(f'\n不一致: {len(wrong)}')
fixable = [w for w in wrong if w['exp_in_skills']]
print(f'可修复(exp在SKILLS里): {len(fixable)}')
missing = [w for w in wrong if not w['exp_in_skills']]
print(f'需实现(exp不在SKILLS里): {len(missing)}')
if missing:
    print('\n=== 需要实现的战法 ===')
    seen = set()
    for w in missing:
        if w['exp_sid'] not in seen:
            seen.add(w['exp_sid'])
            print(f"  {w['exp_sid']} {w['exp_name']}  (武将: {w['name']})")

