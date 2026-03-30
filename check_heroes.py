import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
conn = sqlite3.connect('d:/nettest/stzb_42.186.96.143.db')
conn.row_factory = sqlite3.Row

# battle_heroes 样本
print('=== battle_heroes 样本 ===')
for r in conn.execute('SELECT * FROM battle_heroes LIMIT 5'):
    print(dict(r))

# battles_v2 result 值分布
print('\n=== battles_v2 result 分布 ===')
for r in conn.execute('SELECT result, result_desc, COUNT(*) as cnt FROM battles_v2 GROUP BY result ORDER BY cnt DESC'):
    print(dict(r))

# 看一个有武将记录的战报
print('\n=== 有武将记录的战报样本 ===')
for r in conn.execute('''
    SELECT bv.battle_id, bv.result, bv.result_desc, bv.atk_name,
           GROUP_CONCAT(bh.hero_name, ',') as heroes
    FROM battles_v2 bv
    JOIN battle_heroes bh ON bh.battle_id=bv.battle_id AND bh.side='atk'
    GROUP BY bv.battle_id
    LIMIT 5
'''):
    print(dict(r))
conn.close()

