# -*- coding: utf-8 -*-
# 获取64个缺失战法的描述，用于实现
import json

extra = json.load(open('stzbBattleSimulator-main/cfg/hero_extra.json', encoding='utf-8'))

missing_ids = [200004, 200006, 200007, 200009, 200017, 200025, 200026, 200032, 200033, 200035, 200037, 200080, 200094, 200266, 200655, 200705, 200707, 200719, 200785, 200790, 200791, 200795, 200796, 200836, 200837, 200843, 200846, 200848, 200849, 200851, 200852, 200857, 200864, 200865, 200885, 200887, 200891, 200902, 200927, 200928, 200933, 200934, 200936, 200937, 200939, 200940, 200943, 200944, 200945, 200946, 200947, 200948, 200950, 200951, 200954, 200958, 200962, 200963, 200965, 200968, 200988, 200989, 200990, 201007]

# 收集每个id的名称和描述
sk_info = {}
for e in extra:
    mid = e.get('methodId')
    mname = e.get('methodName', '')
    mdesc = e.get('methodDesc', '')
    if not mid: continue
    try: mid = int(float(mid))
    except: continue
    if mid in missing_ids and mid not in sk_info:
        sk_info[mid] = {'name': mname, 'desc': mdesc, 'hero': e.get('name','')}

for sid in missing_ids:
    info = sk_info.get(sid, {})
    print(f"{sid} [{info.get('hero','')}] {info.get('name','?')}: {info.get('desc','')[:100]}")

print(f'\n找到: {len(sk_info)}/{len(missing_ids)}')

