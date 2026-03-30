import sys, json
sys.path.insert(0, 'D:/nettest')
import profile_manager

# 从最新 00000e66 报文注册真实账号
with open('D:/nettest/capture_new/stzb_42.186.96.143/00000e66/cap_20260313013408138_00000e66_zlib.json', encoding='utf-8') as f:
    parsed = json.load(f)

data_map = parsed[1]
server = data_map.get('server', [])
server_name = str(server[0]) if server else ''
log_data = data_map.get('log', {})
rn_raw = str(log_data.get('role_name', ''))
role_name = rn_raw.split('#')[0] if '#' in rn_raw else rn_raw
role_id = str(log_data.get('role_id', ''))
server_ip = '42.186.96.143'

print(f'注册账号: role_name={role_name}, role_id={role_id}, server={server_name}')
p = profile_manager.register_profile(server_ip, role_id, role_name, server_name, src_ip='42.186.96.143:8001')
print(f'profile_id: {p["profile_id"]}')
profile_manager.switch_profile(p['profile_id'])
print(f'已切换到: {p["profile_id"]}')

