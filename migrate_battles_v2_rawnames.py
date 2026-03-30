# -*- coding: utf-8 -*-
# 补全 battles_v2 缺少的原始字段名
import sqlite3

DB_PATH = 'D:/nettest/stzb.db'

NEW_COLS = [
    ('attack_all_hero_info',   'TEXT DEFAULT ""'),
    ('attack_advance',         'TEXT DEFAULT ""'),
    ('defend_all_hero_info',   'TEXT DEFAULT ""'),
    ('defend_advance',         'TEXT DEFAULT ""'),
    ('attack_hero_type',       'TEXT DEFAULT ""'),
    ('defend_hero_type',       'TEXT DEFAULT ""'),
    ('attacker_gear_info',     'TEXT DEFAULT ""'),
    ('defender_gear_info',     'TEXT DEFAULT ""'),
    ('attack_name',            'TEXT DEFAULT ""'),
    ('attack_union_name',      'TEXT DEFAULT ""'),
    ('defend_name',            'TEXT DEFAULT ""'),
    ('defend_union_name',      'TEXT DEFAULT ""'),
    ('attack_unionid',         'INTEGER DEFAULT 0'),
    ('defend_unionid',         'INTEGER DEFAULT 0'),
    ('attack_hp',              'INTEGER DEFAULT 0'),
    ('defend_hp',              'INTEGER DEFAULT 0'),
    ('attacker_force',         'INTEGER DEFAULT 0'),
    ('attacker_gongxun',       'INTEGER DEFAULT 0'),
    ('defender_gongxun',       'INTEGER DEFAULT 0'),
    ('defend_base_level',      'INTEGER DEFAULT 0'),
    ('in_night_mode',          'INTEGER DEFAULT 0'),
]

conn = sqlite3.connect(DB_PATH)
existing = {r[1] for r in conn.execute('PRAGMA table_info(battles_v2)').fetchall()}

added = []
for col, typedef in NEW_COLS:
    if col not in existing:
        conn.execute(f'ALTER TABLE battles_v2 ADD COLUMN {col} {typedef}')
        added.append(col)

conn.commit()
conn.close()

if added:
    print(f'已添加 {len(added)} 个字段:')
    for c in added: print(f'  + {c}')
else:
    print('所有字段已存在')

