import sqlite3
conn = sqlite3.connect('D:/nettest/stzb_42.186.96.143.db')
# 清空 team_users
conn.execute('DELETE FROM team_users')
conn.commit()
remain = conn.execute('SELECT COUNT(*) FROM team_users').fetchone()[0]
print(f'team_users 已清空，剩余: {remain} 条')
conn.close()
