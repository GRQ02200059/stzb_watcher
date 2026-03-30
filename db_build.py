# -*- coding: utf-8 -*-
"""
率土之滨 数据库建库 + 导入脚本
使用 SQLite，支持增量导入
"""
import sqlite3, json, os, re, glob
from datetime import datetime

DB_PATH = 'd:/nettest/stzb.db'
DATA_ROOT = 'd:/nettest'

# 武将名称映射
try:
    with open(f'{DATA_ROOT}/hero_scraper/output/heroes.json', 'r', encoding='utf-8') as f:
        heroes_raw = json.load(f)
    HERO_NAMES = {str(h['id']): h['name'] for h in heroes_raw if h.get('id') and h.get('name')}
except:
    HERO_NAMES = {}

try:
    with open(f'{DATA_ROOT}/hero_scraper/output/skills_full.json', 'r', encoding='utf-8') as f:
        skills_raw = json.load(f)
    SKILL_NAMES = {str(s['id']): s['name'] for s in skills_raw if s.get('id') and s.get('name')}
except:
    SKILL_NAMES = {}

def hero_name(hid): return HERO_NAMES.get(str(hid), f'武将{hid}')
def skill_name(sid): return SKILL_NAMES.get(str(sid), f'技能{sid}')

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute('PRAGMA journal_mode=WAL')
    conn.execute('PRAGMA synchronous=NORMAL')
    return conn

def create_tables(conn):
    conn.executescript('''
    CREATE TABLE IF NOT EXISTS unions (
        union_id    INTEGER PRIMARY KEY,
        name        TEXT NOT NULL,
        level       INTEGER DEFAULT 0,
        power       INTEGER DEFAULT 0,
        total_member INTEGER DEFAULT 0,
        region      INTEGER DEFAULT 0,
        last_updated TEXT
    );

    CREATE TABLE IF NOT EXISTS battles (
        battle_id   INTEGER PRIMARY KEY,
        time        INTEGER,
        time_str    TEXT,
        result      INTEGER,
        result_desc TEXT,
        fight_type  INTEGER,
        city_type   INTEGER,
        wid_name    TEXT,
        is_ai       INTEGER DEFAULT 0,
        is_npc      INTEGER DEFAULT 0,
        weather     INTEGER DEFAULT 0,
        in_night    INTEGER DEFAULT 0,
        battle_scenes INTEGER DEFAULT 0,
        atk_name    TEXT,
        atk_union   TEXT,
        atk_unionid INTEGER DEFAULT 0,
        atk_level   INTEGER DEFAULT 0,
        atk_hp      INTEGER DEFAULT 0,
        atk_hero_count INTEGER DEFAULT 0,
        atk_total_dmg  INTEGER DEFAULT 0,
        atk_gongxun    INTEGER DEFAULT 0,
        def_name    TEXT,
        def_union   TEXT,
        def_unionid INTEGER DEFAULT 0,
        def_level   INTEGER DEFAULT 0,
        def_hp      INTEGER DEFAULT 0,
        def_hero_count INTEGER DEFAULT 0,
        def_total_dmg  INTEGER DEFAULT 0,
        def_gongxun    INTEGER DEFAULT 0,
        source_file TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_battles_time ON battles(time);
    CREATE INDEX IF NOT EXISTS idx_battles_atk_name ON battles(atk_name);
    CREATE INDEX IF NOT EXISTS idx_battles_def_name ON battles(def_name);
    CREATE INDEX IF NOT EXISTS idx_battles_atk_unionid ON battles(atk_unionid);
    CREATE INDEX IF NOT EXISTS idx_battles_def_unionid ON battles(def_unionid);

    CREATE TABLE IF NOT EXISTS battle_heroes (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        battle_id   INTEGER,
        side        TEXT,  -- atk / def
        pos         INTEGER,
        hero_id     INTEGER,
        hero_name   TEXT,
        level       INTEGER DEFAULT 0,
        max_hp      INTEGER DEFAULT 0,
        remain_hp   INTEGER DEFAULT 0,
        damage_taken INTEGER DEFAULT 0,
        FOREIGN KEY (battle_id) REFERENCES battles(battle_id)
    );
    CREATE INDEX IF NOT EXISTS idx_bh_battle ON battle_heroes(battle_id);
    CREATE INDEX IF NOT EXISTS idx_bh_hero ON battle_heroes(hero_id);
    CREATE INDEX IF NOT EXISTS idx_bh_name ON battle_heroes(hero_name);

    CREATE TABLE IF NOT EXISTS battle_skills (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        battle_id   INTEGER,
        side        TEXT,
        pos         INTEGER,
        skill_id    INTEGER,
        skill_name  TEXT,
        skill_level INTEGER DEFAULT 0,
        FOREIGN KEY (battle_id) REFERENCES battles(battle_id)
    );
    CREATE INDEX IF NOT EXISTS idx_bs_battle ON battle_skills(battle_id);

    CREATE TABLE IF NOT EXISTS player_teams (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        player_name TEXT,
        side        TEXT,
        hero1       TEXT,
        hero2       TEXT,
        hero3       TEXT,
        heroes_str  TEXT,
        used_count  INTEGER DEFAULT 0,
        win_count   INTEGER DEFAULT 0,
        win_rate    REAL DEFAULT 0
    );
    CREATE INDEX IF NOT EXISTS idx_pt_player ON player_teams(player_name);

    CREATE TABLE IF NOT EXISTS wuxun (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        battle_id   INTEGER,
        time        INTEGER,
        player_name TEXT,
        union_name  TEXT,
        side        TEXT,
        gongxun     INTEGER DEFAULT 0,
        fight_type  INTEGER DEFAULT 0,
        city_type   INTEGER DEFAULT 0,
        result      INTEGER DEFAULT 0
    );
    CREATE INDEX IF NOT EXISTS idx_wx_player ON wuxun(player_name);
    CREATE INDEX IF NOT EXISTS idx_wx_union  ON wuxun(union_name);

    CREATE TABLE IF NOT EXISTS scores (
        id              INTEGER PRIMARY KEY AUTOINCREMENT,
        player_name     TEXT,
        union_name      TEXT,
        period          TEXT DEFAULT 'all',
        battle_count    INTEGER DEFAULT 0,
        atk_count       INTEGER DEFAULT 0,
        def_count       INTEGER DEFAULT 0,
        win_count       INTEGER DEFAULT 0,
        wuxun_total     INTEGER DEFAULT 0,
        hero_diversity  INTEGER DEFAULT 0,
        custom_score    REAL DEFAULT 0
    );
    CREATE UNIQUE INDEX IF NOT EXISTS idx_scores_player_period ON scores(player_name, period);
    ''')
    conn.commit()
    print('表结构创建完成')

