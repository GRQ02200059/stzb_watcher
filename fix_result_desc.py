# -*- coding: utf-8 -*-
# 修复 battles_v2 里 result_desc 为数字的记录
import sqlite3

RESULT_MAP = {
    0:'平局', 1:'攻方胜', 2:'守方胜', 3:'攻方溃', 4:'守方溃',
    5:'双溃', 6:'守方胜(NPC)', 7:'攻方胜', 8:'攻方溃',
    9:'守方溃', 10:'平局', 11:'攻方胜', 12:'守方胜', 13:'攻方溃',
    14:'守方溃', 15:'双溃',
}

conn = sqlite3.connect('d:/nettest/stzb.db')
conn.execute('PRAGMA journal_mode=WAL')

# 修复 battles_v2
updated = 0
rows = conn.execute('SELECT battle_id, result, result_desc FROM battles_v2').fetchall()
for bid, result, desc in rows:
    correct = RESULT_MAP.get(result, str(result))
    if desc != correct:
        conn.execute('UPDATE battles_v2 SET result_desc=? WHERE battle_id=?', (correct, bid))
        updated += 1

# 同步修复 battles 旧表
rows2 = conn.execute('SELECT battle_id, result, result_desc FROM battles').fetchall()
updated2 = 0
for bid, result, desc in rows2:
    correct = RESULT_MAP.get(result, str(result))
    if desc != correct:
        conn.execute('UPDATE battles SET result_desc=? WHERE battle_id=?', (correct, bid))
        updated2 += 1

conn.commit()
conn.close()
print(f'battles_v2 修复 {updated} 条, battles 修复 {updated2} 条')

