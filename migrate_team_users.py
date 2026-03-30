import sqlite3

conn = sqlite3.connect('d:/nettest/stzb_42.186.96.143.db')
cur = conn.cursor()

# 1. 清空旧数据（已无用）
cur.execute('DELETE FROM team_users')

# 2. 重命名旧表
cur.execute('ALTER TABLE team_users RENAME TO team_users_old')

# 3. 建新表，复合主键 (uid, profile_id)
cur.execute('''
CREATE TABLE team_users (
    uid INTEGER NOT NULL,
    profile_id TEXT NOT NULL DEFAULT '',
    name TEXT,
    contribute_total INTEGER DEFAULT 0,
    contribute_week INTEGER DEFAULT 0,
    pos INTEGER DEFAULT 0,
    power INTEGER DEFAULT 0,
    wuxun INTEGER DEFAULT 0,
    group_name TEXT DEFAULT '',
    join_time INTEGER DEFAULT 0,
    wid INTEGER DEFAULT 0,
    hero_config_id INTEGER DEFAULT 0,
    hero_skills TEXT DEFAULT '',
    account_id TEXT DEFAULT '',
    updated_at TEXT,
    PRIMARY KEY (uid, profile_id)
)
''')

# 4. 删除旧表
cur.execute('DROP TABLE team_users_old')

conn.commit()
conn.close()
print('迁移完成：team_users 已重建为 (uid, profile_id) 复合主键')

