# -*- coding: utf-8 -*-
import json, sys
sys.path.insert(0, 'D:/nettest')
from battle_sim.skills_s import SKILLS_S

with open('D:/nettest/s_skills.json', encoding='utf-8') as f:
    all_skills = json.load(f)

with open('D:/nettest/s_skills_clean.json', encoding='utf-8') as f:
    clean = json.load(f)

clean_ids = {s['id'] for s in clean}
impl_ids = set(SKILLS_S.keys())

# s_skills.json 里有但未实现的
all_ids = {s['id'] for s in all_skills}
missing = sorted(all_ids - impl_ids)
print(f's_skills.json total: {len(all_ids)}')
print(f'implemented: {len(impl_ids)}')
print(f'missing: {len(missing)}')
for sid in missing:
    sk = next(s for s in all_skills if s['id'] == sid)
    print(f"  {sid} {sk.get('name','?')} [{sk.get('type','?')}] - {sk.get('desc','')[:40]}")

