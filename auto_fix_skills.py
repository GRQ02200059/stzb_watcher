# -*- coding: utf-8 -*-
import json, sys
sys.path.insert(0, '.')
from battle_sim.data import HEROS, SKILLS

# 读取原始hero_extra数据
extra = json.load(open('stzbBattleSimulator-main/cfg/hero_extra.json', encoding='utf-8'))

# 建立 uniqueName -> methodId1 映射
# hero_extra里每条记录有 uniqueName, methodId1 等
extra_map = {}  # name -> list of methodId
for e in extra:
    mid = e.get('methodId1') or e.get('methodId')
    if mid:
        try:
            mid = int(float(mid))
        except:
            continue
        name = e.get('name', '')
        if name not in extra_map:
            extra_map[name] = []
        if mid not in extra_map[name]:
            extra_map[name].append(mid)

# 找skill=0的武将，尝试匹配
zero_heroes = [h for h in HEROS.values() if h['skill'] == 0]
print(f'需要修复: {len(zero_heroes)} 个武将\n')

fixes = {}  # id -> skill_id
for h in zero_heroes:
    name = h['name']
    mids = extra_map.get(name, [])
    # 找在SKILLS里存在的
    valid = [m for m in mids if m in SKILLS]
    if len(valid) == 1:
        fixes[h['id']] = valid[0]
        sk = SKILLS[valid[0]]
        print(f"OK  {h['id']} {name} -> {valid[0]}({sk.name})")
    elif len(valid) > 1:
        sk = SKILLS[valid[0]]
        fixes[h['id']] = valid[0]
        print(f"MULTI {h['id']} {name} -> {valid} (用第一个:{sk.name})")
    else:
        print(f"MISS  {h['id']} {name} mids={mids}")

print(f'\n可自动修复: {len(fixes)}/{len(zero_heroes)}')

# 输出修复命令
print('\n--- 修复列表 ---')
for hid, sid in fixes.items():
    h = HEROS[hid]
    sk = SKILLS[sid]
    print(f"{hid}({h['name']}) -> {sid}({sk.name})")

