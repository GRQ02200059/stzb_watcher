# -*- coding: utf-8 -*-
# migrate_battles_v2_full.py — 照搬 stzbHelper Report struct 补全所有字段
import sqlite3

DB_PATH = 'D:/nettest/stzb.db'

# stzbHelper Report struct 全部字段，我们还缺的
NEW_COLS = [
    # string 类型
    ('aiid',                      'TEXT DEFAULT ""'),
    ('all_exp',                   'TEXT DEFAULT ""'),
    ('attack_all_surface',        'TEXT DEFAULT ""'),
    ('attack_army_group',         'TEXT DEFAULT ""'),
    ('attack_base_heroid',        'INTEGER DEFAULT 0'),
    ('attack_clan_name',          'TEXT DEFAULT ""'),
    ('attack_clanid',             'INTEGER DEFAULT 0'),
    ('attack_fight_event_count',  'INTEGER DEFAULT 0'),
    ('attack_fight_event_facade', 'INTEGER DEFAULT 0'),
    ('attack_help_id',            'TEXT DEFAULT ""'),
    ('attack_hero_policy',        'TEXT DEFAULT ""'),
    ('attack_hero_type_advance',  'TEXT DEFAULT ""'),
    ('attack_role_id',            'TEXT DEFAULT ""'),
    ('attack_ship_type',          'INTEGER DEFAULT 0'),
    ('attack_union_official',     'INTEGER DEFAULT 0'),
    ('attacker_army_effect',      'TEXT DEFAULT ""'),
    ('attacker_base_hero_detail', 'TEXT DEFAULT ""'),
    ('attacker_life_end_time',    'TEXT DEFAULT ""'),
    ('attacker_machine_stat_info','TEXT DEFAULT ""'),
    ('attacker_surface',          'TEXT DEFAULT ""'),
    ('attacker_xwc',              'INTEGER DEFAULT 0'),
    ('ambush',                    'INTEGER DEFAULT 0'),
    ('bandit',                    'INTEGER DEFAULT 0'),
    ('battle_scenes',             'INTEGER DEFAULT 0'),
    ('block_id',                  'INTEGER DEFAULT 0'),
    ('borrow_land',               'INTEGER DEFAULT 0'),
    ('city_type',                 'INTEGER DEFAULT 0'),
    ('defend_all_surface',        'TEXT DEFAULT ""'),
    ('defend_army_group',         'TEXT DEFAULT ""'),
    ('defend_base_heroid',        'INTEGER DEFAULT 0'),
    ('defend_clan_name',          'TEXT DEFAULT ""'),
    ('defend_clanid',             'INTEGER DEFAULT 0'),
    ('defend_fight_event_count',  'INTEGER DEFAULT 0'),
    ('defend_fight_event_facade', 'INTEGER DEFAULT 0'),
    ('defend_help_id',            'TEXT DEFAULT ""'),
    ('defend_hero_policy',        'TEXT DEFAULT ""'),
    ('defend_hero_type_advance',  'TEXT DEFAULT ""'),
    ('defend_role_id',            'TEXT DEFAULT ""'),
    ('defend_ship_type',          'INTEGER DEFAULT 0'),
    ('defend_union_official',     'INTEGER DEFAULT 0'),
    ('defender_army_effect',      'TEXT DEFAULT ""'),
    ('defender_base_hero_detail', 'TEXT DEFAULT ""'),
    ('defender_life_end_time',    'TEXT DEFAULT ""'),
    ('defender_machine_stat_info','TEXT DEFAULT ""'),
    ('defender_surface',          'TEXT DEFAULT ""'),
    ('defender_xwc',              'INTEGER DEFAULT 0'),
    ('extra_result',              'INTEGER DEFAULT 0'),
    ('first_occupy_lvn_land',     'INTEGER DEFAULT 0'),
    ('garrison',                  'INTEGER DEFAULT 0'),
    ('huangjin_convert',          'INTEGER DEFAULT 0'),
    ('is_shared',                 'INTEGER DEFAULT 0'),
    ('is_support',                'INTEGER DEFAULT 0'),
    ('lose_tips',                 'TEXT DEFAULT ""'),
    ('machine_effect',            'TEXT DEFAULT ""'),
    ('military',                  'INTEGER DEFAULT 0'),
    ('military_effect',           'INTEGER DEFAULT 0'),
    ('mvp_svp_pos',               'TEXT DEFAULT ""'),
    ('nation_member_union_info',  'TEXT DEFAULT ""'),
    ('no_owner_res',              'INTEGER DEFAULT 0'),
    ('press_attack',              'INTEGER DEFAULT 0'),
    ('reference_count',           'INTEGER DEFAULT 0'),
    ('res_type',                  'INTEGER DEFAULT 0'),
    ('rob',                       'INTEGER DEFAULT 0'),
    ('sand_extra_info',           'TEXT DEFAULT ""'),
    ('ship_effect',               'INTEGER DEFAULT 0'),
    ('tech_jian_jun_effect',      'INTEGER DEFAULT 0'),
    ('tech_quan_xiang_effect',    'INTEGER DEFAULT 0'),
    ('world_npc_army',            'TEXT DEFAULT ""'),
    ('yi_ling_press_attack',      'INTEGER DEFAULT 0'),
    ('attack_all_sub_hero_info',  'TEXT DEFAULT ""'),
    ('defend_all_sub_hero_info',  'TEXT DEFAULT ""'),
    ('attack_support_user_info',  'TEXT DEFAULT ""'),
    ('defend_support_user_info',  'TEXT DEFAULT ""'),
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
    for c in added:
        print(f'  + {c}')
else:
    print('所有字段已存在，无需迁移')

