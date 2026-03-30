import sys, os, io, traceback

out = io.StringIO()
sys.stdout = out
sys.stderr = out

try:
    print('START')
    sys.path.insert(0, 'd:/nettest')
    from battle_sim import BattleManager
    print('import OK')

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
    print('battle done')
    for r in bm.records:
        print(r)
    res = bm.result()
    print('=' * 50)
    print(f'结果: {res["winner"]}')
    print(f'攻方剩余: {res["blue"]["total_arms"]}')
    for h in res['blue']['heros']:
        print(f'  ({h["pos"]})【{h["name"]}】兵力:{h["arms"]} 伤兵:{h["hurt"]}')
    print(f'守方剩余: {res["red"]["total_arms"]}')
    for h in res['red']['heros']:
        print(f'  ({h["pos"]})【{h["name"]}】兵力:{h["arms"]} 伤兵:{h["hurt"]}')
except Exception:
    traceback.print_exc()

result = out.getvalue()
sys.stdout = sys.__stdout__
sys.stderr = sys.__stderr__

with open('d:/nettest/_sim_result.txt', 'w', encoding='utf-8') as f:
    f.write(result)

