# -*- coding: utf-8 -*-
from battle_sim.data import SKILLS
# 查200930 200023
for sid in [200930, 200023, 200649]:
    sk = SKILLS.get(sid)
    if sk:
        print(f'{sid}: {sk.name} type={sk.skill_type} rate={sk.rate}')
    else:
        print(f'{sid}: NOT FOUND')

