import sys, os
sys.path.insert(0, 'D:/nettest')
from realtime_writer import parse_team_users_67, upsert_team_users, get_writer_instance
import profile_manager
import sqlite3

# 用最新报文重放
fpath = 'D:/nettest/capture_new/stzb_42.186.96.143/00000067/cap_20260313012613650_00000067_zlib.json'
users = parse_team_users_67(fpath)
print(f'解析到 {len(users)} 人')
if users:
    pid = profile_manager.get_current_profile_id()
    print(f'当前 profile_id: {pid}')
    conn = sqlite3.connect('D:/nettest/stzb_42.186.96.143.db')
    conn.row_factory = sqlite3.Row
    upsert_team_users(conn, users, profile_id=pid)
    total = conn.execute('SELECT COUNT(*) FROM team_users WHERE profile_id=?', (pid,)).fetchone()[0]
    print(f'写入后 team_users 共 {total} 人')
    conn.close()

