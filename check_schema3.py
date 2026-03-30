import sqlite3
conn = sqlite3.connect('D:/nettest/stzb_42.186.96.143.db')
for t in ['tasks', 'attendance', 'task_members']:
    row = conn.execute(f"SELECT sql FROM sqlite_master WHERE name='{t}'").fetchone()
    print(f'{t}: {row[0] if row else "NOT FOUND"}')
    print()
conn.close()

