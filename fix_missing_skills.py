# -*- coding: utf-8 -*-
# 把 data.py 中 skill 找不到对应战法的武将 skill 设为 0
import sys, re
sys.path.insert(0, '.')
for k in list(sys.modules.keys()):
    if 'battle_sim' in k: del sys.modules[k]
from battle_sim.data import HEROS, SKILLS

# 找出缺失的 skill
missing_skills = set()
for h in HEROS.values():
    sid = h.get('skill', 0)
    if sid and sid > 0 and sid not in SKILLS:
        missing_skills.add(sid)

print('缺失战法ID:', sorted(missing_skills))

# 读 data.py，把这些战法ID替换为 0
src = open('battle_sim/data.py', encoding='utf-8').read()
changed = 0
for sid in missing_skills:
    # 匹配 'skill':200xxx 的模式
    pattern = rf"('skill':{sid},)"
    new_src, n = re.subn(pattern, "'skill':0,", src)
    if n:
        src = new_src
        changed += n
        print(f'  替换 skill:{sid} -> 0, {n}处')

open('battle_sim/data.py', 'w', encoding='utf-8').write(src)
print(f'完成，共替换 {changed} 处')

# 验证
for k in list(sys.modules.keys()):
    if 'battle_sim' in k: del sys.modules[k]
from battle_sim.data import HEROS, SKILLS
missing2 = [(h['id'],h['name'],h['skill']) for h in HEROS.values() if h['skill'] and h['skill'] not in SKILLS]
print('还剩缺失:', len(missing2))

