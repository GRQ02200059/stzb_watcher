# -*- coding: utf-8 -*-
import json

with open('D:/nettest/stzbBattleSimulator-main/cfg/skill_extra.json', encoding='utf-8') as f:
    all_skills = json.load(f)

# 按品质分类统计
from collections import Counter
quality_cnt = Counter(s.get('zfQuality','?') for s in all_skills)
print('quality distribution:', dict(quality_cnt))

# 提取A级
a_skills = [s for s in all_skills if s.get('zfQuality') == 'A']
print(f'A级战法总数: {len(a_skills)}')

# 按类型分类
from collections import defaultdict
by_type = defaultdict(list)
for s in a_skills:
    by_type[s.get('type','?')].append(s)
for t, lst in sorted(by_type.items()):
    print(f'  {t}: {len(lst)}个')

# 输出到 a_skills_clean.json
out = []
for s in a_skills:
    out.append({
        'id': s['id'],
        'name': s['name'],
        'type': s['type'],
        'probability': s.get('probability','--'),
        'distance': s.get('distance', 0),
        'desc': s.get('desc', '')
    })

out.sort(key=lambda x: x['id'])
with open('D:/nettest/a_skills_clean.json', 'w', encoding='utf-8') as f:
    json.dump(out, f, ensure_ascii=False, indent=2)
print('saved to a_skills_clean.json')

