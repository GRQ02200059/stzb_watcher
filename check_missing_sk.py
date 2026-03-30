# -*- coding: utf-8 -*-
import json
data = json.load(open('stzbBattleSimulator-main/cfg/hero_extra.json', encoding='utf-8'))
skill_data = json.load(open('a_skills_clean.json', encoding='utf-8'))

missing_ids = [200184, 200210, 200215, 200646, 200829, 200833, 200839, 200850,
               200853, 200883, 200917, 200929, 200935, 200941, 200949, 200953,
               200956, 200959, 200964, 200967, 200981, 200982, 201008]

# 从 a_skills_clean.json 找描述
skill_map = {s['id']:s for s in skill_data}
for sid in missing_ids:
    s = skill_map.get(sid)
    if s:
        print(f"{sid} [{s.get('type','')}] {s['name']}: {s.get('desc','')[:80]}")
    else:
        print(f"{sid}: NOT IN a_skills_clean.json")

