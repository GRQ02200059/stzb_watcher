# -*- coding: utf-8 -*-
import json

with open('output/heroes.json', 'r', encoding='utf-8') as f:
    heroes = json.load(f)

with open('output/skills_full.json', 'r', encoding='utf-8') as f:
    skills = json.load(f)

# 生成 HERO_NAMES JS 映射
hero_map = {}
for h in heroes:
    hid = h.get('id')
    name = h.get('name', '').strip()
    if hid and name:
        hero_map[hid] = name

print(f'武将总数: {len(hero_map)}')

# 生成 JS 对象字符串
hero_entries = ','.join(f'{k}:"{v}"' for k, v in sorted(hero_map.items()))
hero_js = f'const HERO_NAMES={{{hero_entries}}};'

# 生成 SKILL_NAMES JS 映射
skill_map = {}
for sk in skills:
    sid = sk.get('id')
    name = sk.get('name', '').strip()
    stype = sk.get('type', '').strip()
    if sid and name:
        skill_map[sid] = {'name': name, 'type': stype}

skill_entries = ','.join(f'{k}:{{name:"{v["name"]}",type:"{v["type"]}"}}' for k, v in sorted(skill_map.items()))
skill_js = f'const SKILL_DATA={{{skill_entries}}};'

# 写入文件
with open('output/hero_names.js', 'w', encoding='utf-8') as f:
    f.write(hero_js + '\n')
    f.write(skill_js + '\n')

print(f'技能总数: {len(skill_map)}')
print(f'已写入 output/hero_names.js ({len(hero_js)+len(skill_js)} chars)')
print()
print('前5个武将:')
for k, v in list(sorted(hero_map.items()))[:5]:
    print(f'  {k}: {v}')
print()
print('前5个技能:')
for k, v in list(sorted(skill_map.items()))[:5]:
    print(f'  {k}: {v}')

