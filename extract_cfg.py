# -*- coding: utf-8 -*-
# 从 stzbHelper_ref/teamweb/src/cfg.js 提取 herocfg 和 skillcfg
import re, json, sys

with open('D:/nettest/stzbHelper_ref/teamweb/src/cfg.js', 'r', encoding='utf-8') as f:
    content = f.read()

# 提取 herocfg JSON 字符串
hero_match = re.search(r'export const herocfg = `(.*?)`', content, re.DOTALL)
skill_match = re.search(r'export const skillcfg = `(.*?)`', content, re.DOTALL)

if hero_match and skill_match:
    hero_json = hero_match.group(1)
    skill_json = skill_match.group(1)
    
    herocfg = json.loads(hero_json)
    skillcfg = json.loads(skill_json)
    
    print(f'武将数: {len(herocfg)}')
    print(f'技能数: {len(skillcfg)}')
    
    # 保存为 JS 文件供前端使用
    with open('D:/nettest/static/herocfg.js', 'w', encoding='utf-8') as f:
        f.write(f'const HERO_CFG = {json.dumps(herocfg, ensure_ascii=False)};\n')
        f.write(f'const SKILL_CFG = {json.dumps(skillcfg, ensure_ascii=False)};\n')
    
    # 也保存为 JSON 供 API 使用
    with open('D:/nettest/static/herocfg.json', 'w', encoding='utf-8') as f:
        json.dump(herocfg, f, ensure_ascii=False)
    with open('D:/nettest/static/skillcfg.json', 'w', encoding='utf-8') as f:
        json.dump(skillcfg, f, ensure_ascii=False)
    
    print('已保存 herocfg.js, herocfg.json, skillcfg.json')
    
    # 验证几个武将
    for hid in ['100021', '100476', '100496', '100585']:
        h = herocfg.get(hid, {})
        print(f'  {hid}: {h.get("name")} [{h.get("country")} {h.get("type")}] 品质{h.get("quality")}')
else:
    print('提取失败')

