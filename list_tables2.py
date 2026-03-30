import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
conn = sqlite3.connect('d:/nettest/stzb_42.186.96.143.db')
tables = conn.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").fetchall()
for t in tables:
    n = t[0]
    try:
        cnt = conn.execute(f'SELECT COUNT(*) FROM "{n}"').fetchone()[0]
        # 取列名
        cols = [c[1] for c in conn.execute(f'PRAGMA table_info("{n}")')]
        print(f'{n}: {cnt}条  字段={cols[:8]}')
    except Exception as e:
        print(f'{n}: ERR {e}')
conn.close()

