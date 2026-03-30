import sys, os
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
sys.stderr.reconfigure(encoding='utf-8', errors='replace')
print('START', flush=True)

try:
    sys.path.insert(0, 'd:/nettest')
    from battle_sim import BattleManager
    print('import OK', flush=True)

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
    print('battle done', flush=True)
    for r in bm.records:
        print(r)
    print()
    res = bm.result()
    print('=' * 50)
    print(f'结果: {res["winner"]}')
    print(f'攻方剩余: {res["blue"]["total_arms"]}')
    for h in res['blue']['heros']:
        print(f'  ({h["pos"]})【{h["name"]}】兵力:{h["arms"]} 伤兵:{h["hurt"]}')
    print(f'守方剩余: {res["red"]["total_arms"]}')
    for h in res['red']['heros']:
        print(f'  ({h["pos"]})【{h["name"]}】兵力:{h["arms"]} 伤兵:{h["hurt"]}')
except Exception as e:
    import traceback
    traceback.print_exc()

