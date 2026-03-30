# -*- coding: utf-8 -*-
import sys, traceback, io
sys.path.insert(0, 'd:/nettest')

out = io.StringIO()

try:
    from battle_sim.manager import BattleManager

    config = {
        'blue': {
            'morale': 100,
            'heros': [
                {'id': 1004, 'level': 40, 'up': 0, 'equip_skills': [1018], 'extra_attrs': {}},
                {'id': 1001, 'level': 40, 'up': 0, 'equip_skills': [1002], 'extra_attrs': {}},
                {'id': 1003, 'level': 40, 'up': 0, 'equip_skills': [],     'extra_attrs': {}},
            ]
        },
        'red': {
            'morale': 100,
            'heros': [
                {'id': 1002, 'level': 40, 'up': 0, 'equip_skills': [],     'extra_attrs': {}},
                {'id': 1006, 'level': 40, 'up': 0, 'equip_skills': [1012], 'extra_attrs': {}},
                {'id': 1012, 'level': 40, 'up': 0, 'equip_skills': [],     'extra_attrs': {}},
            ]
        }
    }

    bm = BattleManager(config)
    for line in bm.records:
        out.write(line + '\n')
    res = bm.result()
    out.write('=' * 50 + '\n')
    out.write(f'winner: {res["winner"]}\n')
    out.write(f'blue arms: {res["blue"]["total_arms"]}\n')
    out.write(f'red  arms: {res["red"]["total_arms"]}\n')
except Exception:
    out.write(traceback.format_exc())

with open('d:/nettest/test_output.txt', 'w', encoding='utf-8') as f:
    f.write(out.getvalue())


