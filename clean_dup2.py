import sqlite3
conn = sqlite3.connect('D:/nettest/stzb_42.186.96.143.db')
# 只保留 3z5ddrkg06946 的数据，删除 3z5ddqy811007 的重复数据
conn.execute("DELETE FROM team_users WHERE profile_id='42.186.96.143:3z5ddqy811007'")
conn.commit()
rows = conn.execute('SELECT profile_id, COUNT(*) FROM team_users GROUP BY profile_id').fetchall()
print('清理后 team_users 分布:')
for r in rows:
    print(' ', repr(r[0]), '->', r[1])
conn.close()

