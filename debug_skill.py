# -*- coding: utf-8 -*-
import sys
sys.path.insert(0, '.')
from battle_sim.data import SKILLS, HEROS
from battle_sim.hero import BattleHero
from battle_sim.manager import BattleManager

keys = list(HEROS.keys())

# 手动构造一个武将，检查skills_order和skill_type
class FakeMgr:
    over = False
    round = 0
    records = []
    blue_team = {'heros': []}
    red_team = {'heros': []}
    def log(self, *a): pass
    def log_action(self, *a): pass
    def log_round(self, *a): pass
    def log_hero_turn(self, *a): pass

mgr = FakeMgr()
cfg = {'id': keys[0], 'level': 40, 'up': 5, 'equip_skills': [], 'extra_attrs': {}}
h = BattleHero(cfg, 'blue', mgr, 100)

print(f'武将: {h.name}')
print(f'skill_id: {h.skill_id}')
print(f'skills_order: {h.skills_order}')
print(f'skills keys: {list(h.skills.keys())}')
for sid in h.skills_order:
    sk = h.skills.get(sid)
    if sk:
        print(f'  skill {sid}: name={sk.name} type={sk.skill_type} rate={sk.rate}')
    else:
        print(f'  skill {sid}: NOT FOUND')

# 测试 call_active_skill 会执行什么
print('\n--- 测试主动战法执行 ---')
for sid in h.skills_order:
    sk = h.skills.get(sid)
    if sk and sk.skill_type == 2:
        print(f'找到主动战法: {sk.name} rate={sk.rate}')
        print(f'can_add_ready_skill: {h.can_add_ready_skill(sk)}')
        rr = h.get_real_skill_rate(sk.rate if sk.rate != "--" else 100)
        print(f'real_rate: {rr}')

