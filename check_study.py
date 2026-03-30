# -*- coding: utf-8 -*-
import json
data = json.load(open('stzbBattleSimulator-main/cfg/skill_extra.json', encoding='utf-8'))
a_study = [d for d in data if 200000 <= d.get('id',0) < 300000 and d.get('studyDesc','无') != '无']
print('可学习A级战法数量:', len(a_study))
for s in a_study[:20]:
    print(str(s['id']) + ' ' + s['name'] + ': ' + str(s.get('studyDesc','')) + '  star=' + str(s.get('studyStar','')))
print('---全部ID---')
print([s['id'] for s in a_study])

