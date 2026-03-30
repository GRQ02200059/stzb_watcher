# -*- coding: utf-8 -*-
from battle_sim.data import HEROS, SKILLS

# 原项目默认阵容武将名
names = ['张辽', '刘备', '太史慈', '马超', '魏延', '曹操']
for name in names:
    matches = [(h['id'], h['name'], h.get('camp'), h.get('skill')) for h in HEROS.values() if h['name'] == name]
    for m in matches:
        sk = SKILLS.get(m[3])
        skname = sk.name if sk else '?'
        print(f"{m[1]} id={m[0]} camp={m[2]} skill={m[3]}({skname})")
    print()

