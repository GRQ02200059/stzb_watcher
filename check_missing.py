# -*- coding: utf-8 -*-
import json, sys
sys.path.insert(0, 'D:/nettest')
from battle_sim.skills_s import SKILLS_S

with open('D:/nettest/s_skills_clean.json', encoding='utf-8') as f:
    clean = json.load(f)

all_ids = {s['id'] for s in clean}
impl_ids = set(SKILLS_S.keys())
missing = sorted(all_ids - impl_ids)
print('s_skills_clean total:', len(all_ids))
print('implemented:', len(impl_ids))
print('missing:', len(missing))
for sid in missing:
    sk = next(s for s in clean if s['id'] == sid)
    print(f"  {sid} {sk['name']} [{sk['type']}]")

