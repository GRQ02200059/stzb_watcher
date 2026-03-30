import sqlite3
conn = sqlite3.connect('d:/nettest/stzb_42.186.96.143.db')
cur = conn.execute("UPDATE battles_v2 SET is_npc=1 WHERE source_file LIKE '%00000834%'")
print('更新行数:', cur.rowcount)
conn.commit()
conn.close()

