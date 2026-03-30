# -*- coding: utf-8 -*-
# 从 stzbBattleSimulator-main/cfg/hero_extra.json 生成完整武将数据
import json, re

data = json.load(open('stzbBattleSimulator-main/cfg/hero_extra.json', encoding='utf-8'))
print('hero_extra.json 武将数量:', len(data))

# 阵营映射
country_map = {'蜀':1,'魏':2,'吴':3,'汉':4,'群':5,'晋':6}
# 兵种映射
type_map = {'弓':1,'步':2,'骑':3}

def quality_to_star(q):
    q = str(q).upper()
    if 'SP' in q: return 5
    if '5' in q: return 5
    if '4' in q: return 5
    if '3' in q: return 4
    if '2' in q: return 3
    return 4

heros_list = []
for h in data:
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
        cost_raw = h.get('cost', '2.5')
        cost = float(cost_raw) * 10 if float(cost_raw or 2.5) < 10 else float(cost_raw)
        star = quality_to_star(h.get('quality', '4'))
        # 技能ID
        skill = int(float(h.get('methodId1') or h.get('methodId') or 0))
        # 属性
        atk = float(h.get('attack', 80) or 80)
        def_ = float(h.get('def', 80) or 80)
        int_ = float(h.get('ruse', 60) or 60)
        spd = float(h.get('speed', 60) or 60)
        des = float(h.get('siege', 5) or 5)
        # 成长
        atk_g = float(h.get('attGrow', 1.5) or 1.5)
        def_g = float(h.get('defGrow', 1.5) or 1.5)
        int_g = float(h.get('ruseGrow', 1.0) or 1.0)
        spd_g = float(h.get('speedGrow', 0.8) or 0.8)
        des_g = float(h.get('siegeGrow', 0.3) or 0.3)
        heros_list.append({
            'id': hid, 'name': name, 'camp': camp, 'army': army,
            'limit': limit, 'skill': skill, 'star': star,
            'attrs': {'atk':round(atk,2),'def':round(def_,2),'int':round(int_,2),'spd':round(spd,2),'des':round(des,2)},
            'attrs_grow': {'atk':round(atk_g,2),'def':round(def_g,2),'int':round(int_g,2),'spd':round(spd_g,2),'des':round(des_g,2)}
        })
    except Exception as e:
        print('skip', h.get('name','?'), e)

# 去重（按id保留最后一个）
heros_dict = {}
for h in heros_list:
    heros_dict[h['id']] = h
heros_list = sorted(heros_dict.values(), key=lambda x: x['id'])
print('去重后武将数量:', len(heros_list))

# 生成 Python 代码块
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
new_heros_code = '\n'.join(lines)

# 读 data.py，替换 _HEROS_LIST ... HEROS = ... 区块
src = open('battle_sim/data.py', encoding='utf-8').read()

if '_HEROS_LIST = [' in src:
    start = src.index('_HEROS_LIST = [')
    # 找 HEROS = 行结尾
    m = re.search(r"HEROS\s*=\s*\{h\['id'\][^\n]+\n", src[start:])
    if m:
        end = start + m.end()
    else:
        end = len(src)
    new_src = src[:start] + new_heros_code + '\n' + src[end:]
else:
    # 找旧的 HEROS = {} 替换
    m = re.search(r'HEROS\s*=\s*\{[^}]+\}', src)
    if m:
        new_src = src[:m.start()] + new_heros_code + src[m.end():]
    else:
        new_src = src + '\n' + new_heros_code

open('battle_sim/data.py', 'w', encoding='utf-8').write(new_src)
print('data.py 写入完成，总字符数:', len(new_src))

# 验证
import sys
for k in list(sys.modules.keys()):
    if 'battle_sim' in k: del sys.modules[k]
sys.path.insert(0,'.')
from battle_sim.data import HEROS
print('验证武将数量:', len(HEROS))
for h in list(HEROS.values())[:5]:
    print(' ', h['id'], h['name'], h['camp'], h['army'])
