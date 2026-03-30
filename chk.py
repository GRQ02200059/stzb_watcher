import sys, traceback
sys.path.insert(0, 'd:/nettest')
err = ''
try:
    from battle_sim.skills import SKILLS
    result = f'OK: {len(SKILLS)} skills'
except Exception as e:
    result = 'FAIL'
    err = traceback.format_exc()
try:
    from battle_sim.data import HEROS
    result2 = f'OK: {len(HEROS)} heros'
except Exception as e:
    result2 = 'FAIL'
    err += traceback.format_exc()
try:
    from battle_sim.manager import BattleManager
    result3 = 'BattleManager OK'
except Exception as e:
    result3 = 'FAIL'
    err += traceback.format_exc()

with open('d:/nettest/err.txt','w',encoding='utf-8') as f:
    f.write(result+'\n'+result2+'\n'+result3+'\n')
    if err: f.write(err)


