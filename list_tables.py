import sqlite3
conn = sqlite3.connect('d:/nettest/stzb.db')
tables = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").fetchall()]
print('Tables:', tables)

