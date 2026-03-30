# -*- coding: utf-8 -*-
from battle_sim.data import HEROS, SKILLS

# 找所有同名武将，展示详细信息
names = ['张辽', '刘备', '太史慈', '马超', '魏延', '曹操']
for name in names:
    matches = [h for h in HEROS.values() if h['name'] == name]
    print(f'=== {name} ===')
    for h in matches:
        sk = SKILLS.get(h['skill'])
        skname = sk.name if sk else '无战法'
        sktype = sk.skill_type if sk else '-'
        print(f"  id={h['id']} camp={h['camp']} army={h['army']} skill={h['skill']}({skname} type={sktype})")
    print()

