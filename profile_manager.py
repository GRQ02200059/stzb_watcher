# -*- coding: utf-8 -*-
"""
profile_manager.py
账号档案管理模块

设计规则：
- 不同 server_ip（区号）→ 独立 .db 文件
- 同 server_ip 不同 role_id（账号）→ 共享战报(battles_v2)，
  但 team_users / attendance / tasks 等团队数据按 profile_id 隔离
- profile_id = server_ip + ':' + role_id（全局唯一）
"""
import json, os, time
from datetime import datetime

BASE_DIR      = os.path.dirname(os.path.abspath(__file__))
PROFILES_FILE = os.path.join(BASE_DIR, 'profiles.json')   # 所有历史账号列表
CURRENT_FILE  = os.path.join(BASE_DIR, 'current_profile.json')  # 当前激活账号


def _load_profiles() -> list:
    """读取所有已记录的账号列表"""
    try:
        if os.path.exists(PROFILES_FILE):
            with open(PROFILES_FILE, 'r', encoding='utf-8') as f:
                data = json.load(f)
            return data if isinstance(data, list) else []
    except:
        pass
    return []


def _save_profiles(profiles: list):
    """保存账号列表"""
    with open(PROFILES_FILE, 'w', encoding='utf-8') as f:
        json.dump(profiles, f, ensure_ascii=False, indent=2)


def _make_profile_id(server_ip: str, role_id: str) -> str:
    """生成唯一 profile_id"""
    return f"{server_ip}:{role_id}"


def _db_path_for_ip(server_ip: str) -> str:
    """根据 server_ip 确定数据库路径（同区共库）"""
    safe_ip = server_ip.replace(':', '_')
    return os.path.join(BASE_DIR, f'stzb_{safe_ip}.db')


def _cap_dir_for_ip(server_ip: str) -> str:
    """根据 server_ip 确定报文目录"""
    safe_ip = server_ip.replace(':', '_')
    return os.path.join(BASE_DIR, 'capture_new', f'stzb_{safe_ip}')


def get_all_profiles() -> list:
    """
    返回所有已记录账号，附加 active 标记。
    同时扫描磁盘上已有的 .db 文件，确保不遗漏。
    """
    profiles = _load_profiles()
    current  = load_current_profile()
    cur_pid  = current.get('profile_id', '')

    # 扫描磁盘上已有 db，补录缺失条目
    known_dbs = {p.get('db_path') for p in profiles}
    for fn in os.listdir(BASE_DIR):
        if fn.startswith('stzb_') and fn.endswith('.db'):
            fpath = os.path.join(BASE_DIR, fn)
            if fpath not in known_dbs:
                # 从文件名推断 server_ip
                server_ip = fn[5:-3].replace('_', '.')  # stzb_1.2.3.4.db → 1.2.3.4
                profiles.append({
                    'profile_id':  f'{server_ip}:unknown',
                    'server_ip':   server_ip,
                    'server_name': server_ip,
                    'role_id':     'unknown',
                    'role_name':   '（未知账号）',
                    'db_path':     fpath,
                    'cap_dir':     _cap_dir_for_ip(server_ip),
                    'created_at':  '',
                    'last_used':   '',
                })

    # 标注当前激活
    for p in profiles:
        p['active'] = (p.get('profile_id') == cur_pid)
        # 附加 db 文件大小
        db_path = p.get('db_path', '')
        if db_path and os.path.exists(db_path):
            p['db_size'] = os.path.getsize(db_path)
        else:
            p['db_size'] = 0

    return profiles


def load_current_profile() -> dict:
    """读取当前激活账号 profile"""
    try:
        if os.path.exists(CURRENT_FILE):
            with open(CURRENT_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
    except:
        pass
    return {}


def register_profile(server_ip: str, role_id: str, role_name: str,
                     server_name: str = '', src_ip: str = '') -> dict:
    """
    注册或更新一个账号档案（抓包时自动调用）。
    返回该账号的 profile dict。
    """
    profile_id = _make_profile_id(server_ip, role_id)
    db_path    = _db_path_for_ip(server_ip)
    cap_dir    = _cap_dir_for_ip(server_ip)
    now        = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    profiles = _load_profiles()
    # 查找已有
    existing = next((p for p in profiles if p.get('profile_id') == profile_id), None)
    if existing:
        # 更新可变字段
        existing['role_name']   = role_name or existing.get('role_name', '')
        existing['server_name'] = server_name or existing.get('server_name', '')
        existing['src_ip']      = src_ip or existing.get('src_ip', '')
        existing['last_used']   = now
        profile = existing
    else:
        profile = {
            'profile_id':  profile_id,
            'server_ip':   server_ip,
            'server_name': server_name or server_ip,
            'role_id':     role_id,
            'role_name':   role_name,
            'src_ip':      src_ip,
            'db_path':     db_path,
            'cap_dir':     cap_dir,
            'created_at':  now,
            'last_used':   now,
        }
        profiles.append(profile)

    _save_profiles(profiles)
    return profile


def switch_profile(profile_id: str) -> dict:
    """
    切换到指定账号，写入 current_profile.json。
    返回切换后的 profile，失败返回 None。
    """
    profiles = _load_profiles()
    target = next((p for p in profiles if p.get('profile_id') == profile_id), None)
    if not target:
        return None

    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    target['last_used'] = now
    _save_profiles(profiles)

    current = dict(target)
    current['bound_at'] = now
    with open(CURRENT_FILE, 'w', encoding='utf-8') as f:
        json.dump(current, f, ensure_ascii=False, indent=2)

    return current


def switch_profile_by_db(db_path: str) -> dict:
    """
    兼容旧逻辑：通过 db_path 切换，找不到精确匹配则自动注册一个匿名档案。
    """
    profiles = _load_profiles()
    target = next((p for p in profiles if p.get('db_path') == db_path), None)
    if not target:
        # 从路径推断 server_ip
        fn = os.path.basename(db_path)  # stzb_1.2.3.4.db
        if fn.startswith('stzb_') and fn.endswith('.db'):
            server_ip = fn[5:-3].replace('_', '.')
        else:
            server_ip = 'unknown'
        target = register_profile(server_ip, 'unknown', '（未知账号）',
                                  db_path=db_path if False else '')
        # 强制覆盖 db_path
        target['db_path'] = db_path
        profiles = _load_profiles()
        for p in profiles:
            if p.get('profile_id') == target.get('profile_id'):
                p['db_path'] = db_path
        _save_profiles(profiles)

    return switch_profile(target['profile_id'])


def get_current_profile_id() -> str:
    """快速获取当前 profile_id"""
    return load_current_profile().get('profile_id', '')


def migrate_profiles_from_current():
    """
    首次启动迁移：将已有的 current_profile.json 注册进 profiles.json
    """
    if not os.path.exists(CURRENT_FILE):
        return
    try:
        with open(CURRENT_FILE, 'r', encoding='utf-8') as f:
            cur = json.load(f)
        server_ip   = cur.get('server_ip', '')
        role_id     = cur.get('role_id', cur.get('src_ip', 'unknown'))
        role_name   = cur.get('role_name', '')
        server_name = cur.get('server_name', '')
        src_ip      = cur.get('src_ip', '')
        if server_ip and role_id:
            p = register_profile(server_ip, role_id, role_name, server_name, src_ip)
            # 补回 profile_id 到 current_profile.json
            if 'profile_id' not in cur:
                cur['profile_id'] = p['profile_id']
                with open(CURRENT_FILE, 'w', encoding='utf-8') as f:
                    json.dump(cur, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f'[profile_manager] migrate error: {e}')

