# -*- coding: utf-8 -*-
# 只保留 quality=4-SR 的武将
import json, re

data = json.load(open('stzbBattleSimulator-main/cfg/hero_extra.json', encoding='utf-8'))

# 只取 4-SR
sr_data = [h for h in data if str(h.get('quality','')).upper() in ('4-SR','4SR','SR','4-SR ')]
print('4-SR武将数量:', len(sr_data))

country_map = {'蜀':1,'魏':2,'吴':3,'汉':4,'群':5,'晋':6}
type_map = {'弓':1,'步':2,'骑':3}

heros_list = []
for h in sr_data:
    try:
        hid = int(h.get('id', 0))
        if hid <= 0: continue
        name = h.get('name', '')
        if not name: continue
        country = h.get('country', '')
        camp = country_map.get(country, 5)
        army_str = h.get('type', '')
        army = 1
        for k, v in type_map.items():
            if k in army_str:
                army = v
                break
        limit = int(float(h.get('distance', 3) or 3))
        star = 5  # 4-SR 都是5星
        skill = int(float(h.get('methodId1') or h.get('methodId') or 0))
        atk = round(float(h.get('attack', 80) or 80), 2)
        def_ = round(float(h.get('def', 80) or 80), 2)
        int_ = round(float(h.get('ruse', 60) or 60), 2)
        spd = round(float(h.get('speed', 60) or 60), 2)
        des = round(float(h.get('siege', 5) or 5), 2)
        atk_g = round(float(h.get('attGrow', 1.5) or 1.5), 2)
        def_g = round(float(h.get('defGrow', 1.5) or 1.5), 2)
        int_g = round(float(h.get('ruseGrow', 1.0) or 1.0), 2)
        spd_g = round(float(h.get('speedGrow', 0.8) or 0.8), 2)
        des_g = round(float(h.get('siegeGrow', 0.3) or 0.3), 2)
        heros_list.append({
            'id': hid, 'name': name, 'camp': camp, 'army': army,
            'limit': limit, 'skill': skill, 'star': star,
            'attrs': {'atk':atk,'def':def_,'int':int_,'spd':spd,'des':des},
            'attrs_grow': {'atk':atk_g,'def':def_g,'int':int_g,'spd':spd_g,'des':des_g}
        })
    except Exception as e:
        print('skip', h.get('name','?'), e)

# 去重
heros_dict = {h['id']:h for h in heros_list}
heros_list = sorted(heros_dict.values(), key=lambda x: x['id'])
print('最终武将数量:', len(heros_list))

# 生成代码
lines = ['_HEROS_LIST = [']
for h in heros_list:
    a = h['attrs']
    g = h['attrs_grow']
    lines.append(f"    {{'id':{h['id']},'name':{repr(h['name'])},'camp':{h['camp']},'army':{h['army']},'limit':{h['limit']},'skill':{h['skill']},")
    lines.append(f"     'attrs':{{'atk':{a['atk']},'def':{a['def']},'int':{a['int']},'spd':{a['spd']},'des':{a['des']}}},")
    lines.append(f"     'attrs_grow':{{'atk':{g['atk']},'def':{g['def']},'int':{g['int']},'spd':{g['spd']},'des':{g['des']}}}}},")
lines.append(']')
lines.append('')
lines.append("HEROS = {h['id']: h for h in _HEROS_LIST}")
new_code = '\n'.join(lines)

# 替换 data.py
src = open('battle_sim/data.py', encoding='utf-8').read()
if '_HEROS_LIST = [' in src:
    start = src.index('_HEROS_LIST = [')
    m = re.search(r"HEROS\s*=\s*\{h\['id'\][^\n]+\n", src[start:])
    end = start + m.end() if m else len(src)
    new_src = src[:start] + new_code + '\n' + src[end:]
else:
    m = re.search(r'HEROS\s*=\s*\{[^}]+\}', src)
    new_src = src[:m.start()] + new_code + src[m.end():] if m else src + '\n' + new_code

open('battle_sim/data.py', 'w', encoding='utf-8').write(new_src)
print('data.py 写入完成')

# 验证
import sys
for k in list(sys.modules.keys()):
    if 'battle_sim' in k: del sys.modules[k]
sys.path.insert(0, '.')
from battle_sim.data import HEROS
print('验证武将数量:', len(HEROS))
for h in list(HEROS.values())[:5]:
    print(' ', h['id'], h['name'], h['camp'], h['army'], h['limit'])

