# -*- coding: utf-8 -*-
from battle_sim.data import HEROS, SKILLS

# 找所有魏延
for h in HEROS.values():
    if h['name'] == '魏延':
        sk = SKILLS.get(h['skill'])
        print(f"id={h['id']} camp={h['camp']} skill={h['skill']} skname={sk.name if sk else '?'}")

# 找奇兵拒北战法
for sk in SKILLS.values():
    if '拒北' in sk.name or '奇兵' in sk.name:
        print(f'skill id={sk.id} name={sk.name} type={sk.skill_type}')

