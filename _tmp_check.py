import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
conn = sqlite3.connect('d:/nettest/stzb_42.186.96.143.db')
conn.row_factory = sqlite3.Row
print('=== team_users wuxun样本 ===')
for r in conn.execute('SELECT name, group_name, wuxun, contribute_total, contribute_week FROM team_users ORDER BY wuxun DESC LIMIT 10'):
    print(dict(r))
conn.close()
