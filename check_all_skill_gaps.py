# -*- coding: utf-8 -*-
from battle_sim.data import HEROS, SKILLS

# 找所有 skill=0 或 skill不在SKILLS里的武将
print('=== skill=0 的武将 ===')
for h in HEROS.values():
    if h['skill'] == 0:
        print(f"  id={h['id']} name={h['name']} camp={h['camp']}")

print('\n=== skill不在SKILLS里的武将 ===')
for h in HEROS.values():
    if h['skill'] != 0 and h['skill'] not in SKILLS:
        print(f"  id={h['id']} name={h['name']} skill={h['skill']}")

print(f'\n总武将数: {len(HEROS)}')
print(f'总战法数: {len(SKILLS)}')

