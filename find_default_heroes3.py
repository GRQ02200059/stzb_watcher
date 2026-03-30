# -*- coding: utf-8 -*-
from battle_sim.data import HEROS, SKILLS

# 直接打印魏延和曹操的原始数据
for h in HEROS.values():
    if h['name'] in ('魏延', '曹操'):
        print(h)

# 另外看原项目技能1010 1028对应什么
# 原项目skills.js里1010=侵掠如火, 1028=魏武之世
# 找我方对应战法
for sk in SKILLS.values():
    if sk.name in ('侵掠如火', '魏武之世', '神速奔袭', '魏武之泽'):
        print(f'skill id={sk.id} name={sk.name} type={sk.skill_type}')

