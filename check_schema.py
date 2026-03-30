import sqlite3
conn = sqlite3.connect('d:/nettest/stzb_42.186.96.143.db')
rows = conn.execute("SELECT sql FROM sqlite_master WHERE type IN ('table','index') AND name LIKE '%team_users%'").fetchall()
for r in rows:
    print(r[0])
    print()
conn.close()

