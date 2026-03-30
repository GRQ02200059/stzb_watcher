import sqlite3
conn = sqlite3.connect('d:/nettest/stzb_42.186.96.143.db')
rows = conn.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").fetchall()
for r in rows:
    cnt = conn.execute(f'SELECT COUNT(*) FROM "{r[0]}"').fetchone()[0]
    print(f'{r[0]}: {cnt} 行')
conn.close()

