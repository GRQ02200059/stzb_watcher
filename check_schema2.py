import sqlite3
conn = sqlite3.connect('D:/nettest/stzb_42.186.96.143.db')
row = conn.execute("SELECT sql FROM sqlite_master WHERE name='team_users'").fetchone()
print(row[0] if row else 'NOT FOUND')
conn.close()

