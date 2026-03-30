# -*- coding: utf-8 -*-
import sys
sys.path.insert(0, '.')

# 检查各模块的 SKILLS 内容
from battle_sim.skills import SKILLS as SK_BASE
print('skills.py SKILLS count:', len(SK_BASE))
print('sample keys:', list(SK_BASE.keys())[:5])

from battle_sim.data import SKILLS as SK_DATA, HEROS
print('data.py SKILLS count:', len(SK_DATA))
print('200235 in data SKILLS:', 200235 in SK_DATA)
print('200235 skill:', SK_DATA.get(200235))

# hero._init_skills 里用的是哪个
from battle_sim import hero as hero_mod
import inspect
print('\n_init_skills source:')
print(inspect.getsource(hero_mod.BattleHero._init_skills))

