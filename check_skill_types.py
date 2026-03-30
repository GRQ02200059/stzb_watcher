# -*- coding: utf-8 -*-
from battle_sim.data import SKILLS, HEROS

# 检查几个武将的主战法类型
hero_list = list(HEROS.values())[:8]
for h in hero_list:
    sk = SKILLS.get(h['skill'])
    if sk:
        print(f"{h['name']} skill={h['skill']} name={sk.name} type={sk.skill_type} rate={sk.rate}")
    else:
        print(f"{h['name']} skill={h['skill']} NOT FOUND")

# 统计各类型战法数量
type_count = {1:0, 2:0, 3:0, 4:0}
for sk in SKILLS.values():
    t = getattr(sk, 'skill_type', None)
    if t in type_count:
        type_count[t] += 1
print('\n战法类型统计:', type_count)

# 模拟一场战斗，打印战报
from battle_sim import BattleManager
keys = list(HEROS.keys())
config = {
    'blue': {'morale': 100, 'heros': [
        {'id': keys[0], 'level': 40, 'up': 5, 'equip_skills': [], 'extra_attrs': {}},
        {'id': keys[2], 'level': 40, 'up': 5, 'equip_skills': [], 'extra_attrs': {}},
    ]},
    'red': {'morale': 100, 'heros': [
        {'id': keys[1], 'level': 40, 'up': 5, 'equip_skills': [], 'extra_attrs': {}},
        {'id': keys[3], 'level': 40, 'up': 5, 'equip_skills': [], 'extra_attrs': {}},
    ]}
}
bm = BattleManager(config)
for line in bm.records[:60]:
    print(line)

