# -*- coding: utf-8 -*-
# 验证导入和基本战斗流程
import sys, traceback, io
sys.path.insert(0, 'd:/nettest')
out = io.StringIO()
try:
    from battle_sim.skills import SKILLS
    out.write(f'SKILLS 共 {len(SKILLS)} 个战法\n')
    for sid, sk in sorted(SKILLS.items()):
        out.write(f'  {sid}: {sk.name} (type={sk.skill_type}, rate={sk.rate})\n')
except Exception:
    out.write('导入 SKILLS 失败:\n')
    out.write(traceback.format_exc())

try:
    from battle_sim.data import HEROS
    out.write(f'HEROS 共 {len(HEROS)} 个武将\n')
except Exception:
    out.write('导入 HEROS 失败:\n')
    out.write(traceback.format_exc())

try:
    from battle_sim.manager import BattleManager
    config = {
        'blue': {'morale':100,'heros':[
            {'id':1004,'level':40,'up':0,'equip_skills':[1018],'extra_attrs':{}},
            {'id':1001,'level':40,'up':0,'equip_skills':[1002],'extra_attrs':{}},
            {'id':1003,'level':40,'up':0,'equip_skills':[],'extra_attrs':{}},
        ]},
        'red':  {'morale':100,'heros':[
            {'id':1002,'level':40,'up':0,'equip_skills':[],'extra_attrs':{}},
            {'id':1006,'level':40,'up':0,'equip_skills':[1012],'extra_attrs':{}},
            {'id':1012,'level':40,'up':0,'equip_skills':[],'extra_attrs':{}},
        ]}
    }
    bm = BattleManager(config)
    res = bm.result()
    out.write('\n战斗模拟成功!\n')
    out.write(f'胜者: {res["winner"]}\n')
    out.write(f'攻方兵力: {res["blue"]["total_arms"]}\n')
    out.write(f'守方兵力: {res["red"]["total_arms"]}\n')
    out.write(f'回合数: {res["rounds_played"]}\n')
    out.write('\n战斗日志 (前30行):\n')
    for line in res['records'][:30]:
        out.write(line + '\n')
except Exception:
    out.write('战斗模拟失败:\n')
    out.write(traceback.format_exc())

with open('d:/nettest/test_result.txt', 'w', encoding='utf-8') as f:
    f.write(out.getvalue())
print('done')


