# -*- coding: utf-8 -*-
import json
data = json.load(open('stzbBattleSimulator-main/cfg/hero_extra.json', encoding='utf-8'))
missing = [200184, 200210, 200215, 200646, 200833, 200853, 200929, 200935]
for sid in missing:
    heroes = [h for h in data if int(float(h.get('methodId1') or h.get('methodId') or 0)) == sid]
    for h in heroes:
        print(f"{sid} {h['name']}: methodName={h.get('methodName1',h.get('methodName'))}")
        print(f"  desc: {h.get('methodDesc1', h.get('methodDesc',''))[:120]}")

