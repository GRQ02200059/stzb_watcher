import sqlite3, sys, time
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
conn = sqlite3.connect('d:/nettest/stzb_42.186.96.143.db')
conn.row_factory = sqlite3.Row

# 验证团数据统计逻辑
# 按分组统计：出战次数、胜场、功勋
rows = conn.execute('''
    SELECT
        COALESCE(tu.group_name, '未知') as group_name,
        COUNT(DISTINCT bv.atk_name) as player_cnt,
        COUNT(*) as battles,
        SUM(CASE WHEN bv.result=1 THEN 1 ELSE 0 END) as wins,
        SUM(CASE WHEN bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as city_battles,
        SUM(CASE WHEN bv.result=1 AND bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as city_wins,
        SUM(bv.atk_gongxun) as total_gongxun,
        SUM(bv.atk_power) as total_power,
        MAX(bv.atk_power) as max_power
    FROM battles_v2 bv
    LEFT JOIN team_users tu ON tu.name = bv.atk_name
    WHERE bv.is_npc=0
    GROUP BY COALESCE(tu.group_name, '未知')
    ORDER BY battles DESC
''').fetchall()

print('=== 按分组统计 ===')
for r in rows:
    wr = round(r['wins']*100/r['battles'],1) if r['battles'] else 0
    print(f"{r['group_name']}: 人数={r['player_cnt']} 战报={r['battles']} 胜={r['wins']} 胜率={wr}% 攻城={r['city_battles']} 功勋={r['total_gongxun']}")

print()
# 按成员统计
rows2 = conn.execute('''
    SELECT
        bv.atk_name,
        COALESCE(tu.group_name, '未知') as group_name,
        COUNT(*) as battles,
        SUM(CASE WHEN bv.result=1 THEN 1 ELSE 0 END) as wins,
        SUM(CASE WHEN bv.fight_type IN (2,80,33) THEN 1 ELSE 0 END) as city_battles,
        SUM(bv.atk_gongxun) as total_gongxun,
        MAX(bv.atk_power) as max_power
    FROM battles_v2 bv
    LEFT JOIN team_users tu ON tu.name = bv.atk_name
    WHERE bv.is_npc=0
    GROUP BY bv.atk_name
    ORDER BY battles DESC
    LIMIT 15
''').fetchall()

print('=== 按成员统计 TOP15 ===')
for r in rows2:
    wr = round(r['wins']*100/r['battles'],1) if r['battles'] else 0
    print(f"{r['atk_name']}[{r['group_name']}]: 战报={r['battles']} 胜率={wr}% 攻城={r['city_battles']} 功勋={r['total_gongxun']}")

conn.close()
