# -*- coding: utf-8 -*-
# migrate_battles_v2.py — 给 battles_v2 / battle_heroes 补字段（照搬 stzbHelper BattleReport 结构）
import sqlite3, os

DB_PATH = 'D:/nettest/stzb.db'

NEW_COLS_BATTLES_V2 = [
    ('atk_hp',              'INTEGER DEFAULT 0'),
    ('def_hp',              'INTEGER DEFAULT 0'),
    ('is_npc',              'INTEGER DEFAULT 0'),
    ('is_ai',               'INTEGER DEFAULT 0'),
    ('weather',             'INTEGER DEFAULT 0'),
    ('in_night',            'INTEGER DEFAULT 0'),
    ('wid_name',            'TEXT DEFAULT ""'),
    ('atk_unionid',         'INTEGER DEFAULT 0'),
    ('def_unionid',         'INTEGER DEFAULT 0'),
    ('all_skill_info',      'TEXT DEFAULT ""'),
    ('atk_advance',         'TEXT DEFAULT ""'),
    ('def_advance',         'TEXT DEFAULT ""'),
    ('atk_hero_type',       'TEXT DEFAULT ""'),
    ('def_hero_type',       'TEXT DEFAULT ""'),
    ('atk_gear_info',       'TEXT DEFAULT ""'),
    ('def_gear_info',       'TEXT DEFAULT ""'),
    # 照搬 stzbHelper：独立武将 ID/等级/红度
    ('atk_hero1_id',        'INTEGER DEFAULT 0'),
    ('atk_hero2_id',        'INTEGER DEFAULT 0'),
    ('atk_hero3_id',        'INTEGER DEFAULT 0'),
    ('atk_hero1_level',     'INTEGER DEFAULT 0'),
    ('atk_hero2_level',     'INTEGER DEFAULT 0'),
    ('atk_hero3_level',     'INTEGER DEFAULT 0'),
    ('atk_hero1_star',      'INTEGER DEFAULT 0'),
    ('atk_hero2_star',      'INTEGER DEFAULT 0'),
    ('atk_hero3_star',      'INTEGER DEFAULT 0'),
    ('atk_total_star',      'INTEGER DEFAULT 0'),
    ('def_hero1_id',        'INTEGER DEFAULT 0'),
    ('def_hero2_id',        'INTEGER DEFAULT 0'),
    ('def_hero3_id',        'INTEGER DEFAULT 0'),
    ('def_hero1_level',     'INTEGER DEFAULT 0'),
    ('def_hero2_level',     'INTEGER DEFAULT 0'),
    ('def_hero3_level',     'INTEGER DEFAULT 0'),
    ('def_hero1_star',      'INTEGER DEFAULT 0'),
    ('def_hero2_star',      'INTEGER DEFAULT 0'),
    ('def_hero3_star',      'INTEGER DEFAULT 0'),
    ('def_total_star',      'INTEGER DEFAULT 0'),
    ('atk_idu',             'TEXT DEFAULT ""'),
    ('def_idu',             'TEXT DEFAULT ""'),
]

NEW_COLS_BATTLE_HEROES = [
    ('star', 'INTEGER DEFAULT 0'),
]

conn = sqlite3.connect(DB_PATH)
existing_battles = {r[1] for r in conn.execute('PRAGMA table_info(battles_v2)').fetchall()}
existing_heroes  = {r[1] for r in conn.execute('PRAGMA table_info(battle_heroes)').fetchall()}

added = []
for col, typedef in NEW_COLS_BATTLES_V2:
    if col not in existing_battles:
        conn.execute(f'ALTER TABLE battles_v2 ADD COLUMN {col} {typedef}')
        added.append(f'battles_v2.{col}')

for col, typedef in NEW_COLS_BATTLE_HEROES:
    if col not in existing_heroes:
        conn.execute(f'ALTER TABLE battle_heroes ADD COLUMN {col} {typedef}')
        added.append(f'battle_heroes.{col}')

conn.commit()
conn.close()

if added:
    print('已添加字段:')
    for c in added:
        print(f'  + {c}')
else:
    print('所有字段已存在，无需迁移')

