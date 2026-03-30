# -*- coding: utf-8 -*-
"""
战斗模拟入口
用法:
    python -m battle_sim.run_sim
    python battle_sim/run_sim.py
"""
import sys, json
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

from battle_sim import BattleManager

def run(config, verbose=True, repeat=1):
    """
    config 格式:
    {
        'blue': {
            'morale': 100,
            'heros': [
                {'id':1004, 'level':40, 'up':0, 'equip_skills':[1018], 'extra_attrs':{}},
                {'id':1001, 'level':40, 'up':0, 'equip_skills':[], 'extra_attrs':{}},
                {'id':1003, 'level':40, 'up':0, 'equip_skills':[], 'extra_attrs':{}},
            ]
        },
        'red': {
            'morale': 100,
            'heros': [
                {'id':1002, 'level':40, 'up':0, 'equip_skills':[], 'extra_attrs':{}},
                {'id':1006, 'level':40, 'up':0, 'equip_skills':[], 'extra_attrs':{}},
                {'id':1012, 'level':40, 'up':0, 'equip_skills':[], 'extra_attrs':{}},
            ]
        }
    }
    """
    if repeat == 1:
        bm = BattleManager(config)
        if verbose:
            bm.print_records()
            print()
        res = bm.result()
        print('=' * 50)
        print(f'结果: {res["winner"]}')
        print(f'攻方剩余兵力: {res["blue"]["total_arms"]}')
        for h in res['blue']['heros']:
            print(f'  ({h["pos"]})【{h["name"]}】兵力:{h["arms"]} 伤兵:{h["hurt"]}')
        print(f'守方剩余兵力: {res["red"]["total_arms"]}')
        for h in res['red']['heros']:
            print(f'  ({h["pos"]})【{h["name"]}】兵力:{h["arms"]} 伤兵:{h["hurt"]}')
        return res
    else:
        # 多次模拟统计胜率
        blue_wins = 0
        red_wins  = 0
        draws     = 0
        for _ in range(repeat):
            bm = BattleManager(config)
            res = bm.result()
            if '攻方' in res['winner']:
                blue_wins += 1
            elif '守方' in res['winner']:
                red_wins += 1
            else:
                draws += 1
        print(f'模拟{repeat}次结果:')
        print(f'  攻方胜: {blue_wins} ({blue_wins/repeat*100:.1f}%)')
        print(f'  守方胜: {red_wins}  ({red_wins/repeat*100:.1f}%)')
        print(f'  平局:   {draws}   ({draws/repeat*100:.1f}%)')
        return {'blue_wins': blue_wins, 'red_wins': red_wins, 'draws': draws}


if __name__ == '__main__':
    # 示例：张辽(其疾如风) + 太史慈(方阵突击) + 刘备(皇裔流离)
    # vs 马超(血践黄砂) + 魏延 + 曹操
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

    import sys
    if '--multi' in sys.argv:
        run(config, verbose=False, repeat=1000)
    else:
        run(config, verbose=True, repeat=1)

