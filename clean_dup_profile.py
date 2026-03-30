import sqlite3
conn = sqlite3.connect('D:/nettest/stzb_42.186.96.143.db')
conn.execute("DELETE FROM team_users WHERE profile_id='42.186.96.143:42.186.96.143:8001'")
conn.commit()
remain = conn.execute('SELECT profile_id, COUNT(*) FROM team_users GROUP BY profile_id').fetchall()
for r in remain:
    print(r[0], '->', r[1], '人')
conn.close()
print('清理完成')

