# -*- coding: utf-8 -*-
# 从 skill_extra.json 提取A级战法
import json

with open('D:/nettest/stzbBattleSimulator-main/cfg/skill_extra.json', encoding='utf-8') as f:
    all_skills = json.load(f)

# A级战法：quality == 4 (S=5, A=4, B=3, C=2, D=1)
# 先查看数据结构
print('total skills:', len(all_skills))
print('sample keys:', list(all_skills[0].keys()) if isinstance(all_skills, list) else list(list(all_skills.values())[0].keys()))
if isinstance(all_skills, list):
    sample = all_skills[0]
else:
    sample = list(all_skills.values())[0]
print('sample:', json.dumps(sample, ensure_ascii=False, indent=2)[:500])

