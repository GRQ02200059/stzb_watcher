# -*- coding: utf-8 -*-
# 批量修复 data.py 里 skill 字段（用 methodId 精确匹配）
import json, sys, re
sys.path.insert(0, '.')
from battle_sim.data import HEROS, SKILLS

extra = json.load(open('stzbBattleSimulator-main/cfg/hero_extra.json', encoding='utf-8'))

extra_by_id = {}
for e in extra:
    hid = e.get('id')
    mid = e.get('methodId')
    if not hid or not mid:
        continue
    try:
        hid = int(float(hid))
        mid = int(float(mid))
    except:
        continue
    extra_by_id[hid] = mid

# 收集需要修复的
fixes = {}
for h in HEROS.values():
    exp_mid = extra_by_id.get(h['id'])
    if exp_mid and exp_mid in SKILLS and h['skill'] != exp_mid:
        fixes[h['id']] = (h['skill'], exp_mid)

print(f'需修复: {len(fixes)}')

with open('battle_sim/data.py', encoding='utf-8') as f:
    src = f.read()

count = 0
for hid, (old_sid, new_sid) in fixes.items():
    pattern = rf"('id':{hid},[^{{}}]*?'skill'):{old_sid}"
    new = rf"\g<1>:{new_sid}"
    new_src, n = re.subn(pattern, new, src)
    if n:
        src = new_src
        count += n
        print(f'OK {hid}: {old_sid} -> {new_sid}')
    else:
        print(f'MISS {hid} old={old_sid} new={new_sid}')

with open('battle_sim/data.py', 'w', encoding='utf-8') as f:
    f.write(src)

print(f'\n修复完成: {count} 处')

