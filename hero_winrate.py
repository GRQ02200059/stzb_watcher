import sqlite3, sys
from collections import defaultdict
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
conn = sqlite3.connect('d:/nettest/stzb_42.186.96.143.db')
conn.row_factory = sqlite3.Row

rows = conn.execute('''
    SELECT bv.battle_id, bv.result, bv.fight_type,
           GROUP_CONCAT(bh.hero_name) as heroes
    FROM battles_v2 bv
    JOIN battle_heroes bh ON bh.battle_id=bv.battle_id AND bh.side='atk'
    WHERE bv.is_npc=0
    GROUP BY bv.battle_id
    HAVING COUNT(bh.id) >= 2
''').fetchall()

hero_stats = defaultdict(lambda: {'total':0,'win':0,'lose':0})
combo_stats = defaultdict(lambda: {'total':0,'win':0,'lose':0})
pair_stats  = defaultdict(lambda: {'total':0,'win':0,'lose':0})

for r in rows:
    heroes = [h.strip() for h in (r['heroes'] or '').split(',') if h.strip() and not h.startswith('武将')]
    result = r['result']
    win  = (result == 1)
    lose = (result == 0)
    # 单英雄
    for h in heroes:
        hero_stats[h]['total'] += 1
        if win:  hero_stats[h]['win']  += 1
        if lose: hero_stats[h]['lose'] += 1
    # 双人组合
    for i in range(len(heroes)):
        for j in range(i+1, len(heroes)):
            k = tuple(sorted([heroes[i], heroes[j]]))
            pair_stats[k]['total'] += 1
            if win:  pair_stats[k]['win']  += 1
            if lose: pair_stats[k]['lose'] += 1
    # 三人组合
    if len(heroes) >= 3:
        k = tuple(sorted(heroes[:3]))
        combo_stats[k]['total'] += 1
        if win:  combo_stats[k]['win']  += 1
        if lose: combo_stats[k]['lose'] += 1

print(f'总战报数: {len(rows)}')
print()

print('=== 单英雄胜率 TOP20（>=5次）===')
hero_list = [(h,s) for h,s in hero_stats.items() if s['total']>=5]
hero_list.sort(key=lambda x: x[1]['win']/x[1]['total'], reverse=True)
for h,s in hero_list[:20]:
    wr = s['win']/s['total']*100
    print(f"{h}: 出战{s['total']}次 胜{s['win']} 败{s['lose']} 胜率{wr:.1f}%")

print()
print('=== 三人组合胜率 TOP15（>=3次）===')
combo_list = [(k,s) for k,s in combo_stats.items() if s['total']>=3]
combo_list.sort(key=lambda x: x[1]['win']/x[1]['total'], reverse=True)
for k,s in combo_list[:15]:
    wr = s['win']/s['total']*100
    print(f"{'+'.join(k)}: {s['total']}次 胜{s['win']} 败{s['lose']} 胜率{wr:.1f}%")

print()
print('=== 双人组合胜率 TOP15（>=5次）===')
pair_list = [(k,s) for k,s in pair_stats.items() if s['total']>=5]
pair_list.sort(key=lambda x: x[1]['win']/x[1]['total'], reverse=True)
for k,s in pair_list[:15]:
    wr = s['win']/s['total']*100
    print(f"{'+'.join(k)}: {s['total']}次 胜{s['win']} 败{s['lose']} 胜率{wr:.1f}%")

conn.close()
