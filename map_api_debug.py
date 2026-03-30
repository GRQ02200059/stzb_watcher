# -*- coding: utf-8 -*-
import sys, urllib.request, json
sys.stdout = open('D:/nettest/map_api_debug.txt', 'w', encoding='utf-8')

r = urllib.request.urlopen('http://127.0.0.1:8765/api/map_stats')
d = json.loads(r.read())
print('type_dist:')
for t in d['type_dist']:
    print(f"  cell_type={t['cell_type']} city_name={t['city_name']!r} cnt={t['cnt']}")
print('\nnamed前20:')
for c in d['named_cities'][:20]:
    print(f"  type={c['cell_type']} city={c['city_name']!r} owner={c['owner_name']!r} wid={c['wid']}")
sys.stdout.close()

