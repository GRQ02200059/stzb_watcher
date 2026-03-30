# -*- coding: utf-8 -*-
import sys
sys.path.insert(0, 'd:/nettest')
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

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

try:
    bm = BattleManager(config)
    for line in bm.records:
        print(line)
    res = bm.result()
    print('='*50)
    print('winner:', res['winner'])
    print('blue arms:', res['blue']['total_arms'])
    print('red  arms:', res['red']['total_arms'])
except Exception as e:
    import traceback
    traceback.print_exc()


