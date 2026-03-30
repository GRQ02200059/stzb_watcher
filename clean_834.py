import sqlite3
conn = sqlite3.connect('d:/nettest/stzb_42.186.96.143.db')

# 删除 834 写入的战报相关记录
cur = conn.execute("DELETE FROM battles_v2 WHERE source_file LIKE '%00000834%'")
print('battles_v2 删除:', cur.rowcount)

cur = conn.execute("DELETE FROM wuxun_log WHERE battle_id IN (SELECT battle_id FROM battles_v2 WHERE source_file LIKE '%00000834%')")
print('wuxun_log 删除:', cur.rowcount)

cur = conn.execute("DELETE FROM battle_heroes WHERE battle_id NOT IN (SELECT battle_id FROM battles_v2)")
print('battle_heroes 孤立记录删除:', cur.rowcount)

cur = conn.execute("DELETE FROM attendance WHERE battle_id NOT IN (SELECT battle_id FROM battles_v2)")
print('attendance 孤立记录删除:', cur.rowcount)

conn.commit()
conn.close()
print('完成')

