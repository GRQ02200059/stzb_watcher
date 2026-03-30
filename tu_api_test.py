# -*- coding: utf-8 -*-
import sys, urllib.request, json
sys.stdout = open('D:/nettest/tu_api_test.txt', 'w', encoding='utf-8')

r = urllib.request.urlopen('http://127.0.0.1:8765/api/team_stats')
d = json.loads(r.read())
print('总人数:', d['total'])
print('TOP10势力:')
for p in d['top_power'][:5]:
    print(f"  {p['name']} power={p['power']} wu={p['wuxun']}")
print('分组:')
for g in d['groups']:
    print(f"  {g['group_name'] or '未分组'}: {g['cnt']}人 总势力={g['total_power']} 总武勋={g['total_wuxun']}")
sys.stdout.close()

