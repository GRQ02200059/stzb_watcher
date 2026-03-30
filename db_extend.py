# -*- coding: utf-8 -*-
"""
扩展数据库表 - 解析更多消息类型
"""
import sqlite3, json, os, glob
from datetime import datetime

DB_PATH = 'd:/nettest/stzb.db'
DATA_ROOT = 'd:/nettest/decompressed_data_report'

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute('PRAGMA journal_mode=WAL')
    return conn

def create_ext_tables(conn):
    conn.executescript('''
    -- 玩家坐标列表 (00000f1d / 000013b9)
    CREATE TABLE IF NOT EXISTS player_locations (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        wid         INTEGER,
        player_name TEXT,
        level       INTEGER DEFAULT 0,
        power       INTEGER DEFAULT 0,
        region      INTEGER DEFAULT 0,
        x           REAL DEFAULT 0,
        union_id    INTEGER DEFAULT 0,
        union_name  TEXT,
        city_type   INTEGER DEFAULT 0,
        city_id     INTEGER DEFAULT 0,
        durability  INTEGER DEFAULT 0,
        score       INTEGER DEFAULT 0,
        source_type TEXT,
        captured_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_pl_name ON player_locations(player_name);
    CREATE INDEX IF NOT EXISTS idx_pl_wid  ON player_locations(wid);

    -- 玩家英雄背包 (00000ad*)
    CREATE TABLE IF NOT EXISTS player_heroes (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        hero_inst_id INTEGER,
        hero_id     INTEGER,
        user_id     INTEGER DEFAULT 0,
        level       INTEGER DEFAULT 0,
        star        INTEGER DEFAULT 0,
        hp          INTEGER DEFAULT 0,
        atk         INTEGER DEFAULT 0,
        def_val     INTEGER DEFAULT 0,
        speed       INTEGER DEFAULT 0,
        intel       INTEGER DEFAULT 0,
        skill_str   TEXT,
        captured_at TEXT,
        source_type TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_ph_hero ON player_heroes(hero_id);
    CREATE INDEX IF NOT EXISTS idx_ph_user ON player_heroes(user_id);

    -- 玩家战绩 (000001fe / 000008a3 / 00001105)
    CREATE TABLE IF NOT EXISTS player_records (
        id              INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id         INTEGER DEFAULT 0,
        wuxun_total     INTEGER DEFAULT 0,
        army_kill_max   INTEGER DEFAULT 0,
        attack_npc_city INTEGER DEFAULT 0,
        destroy_build   INTEGER DEFAULT 0,
        force_max       INTEGER DEFAULT 0,
        week_wuxun      INTEGER DEFAULT 0,
        raw_json        TEXT,
        captured_at     TEXT,
        source_type     TEXT
    );

    -- 联盟成员城池列表 (00002624 / 0000262a / 000026b6 / 000146a4)
    CREATE TABLE IF NOT EXISTS union_cities (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        wid         INTEGER,
        player_name TEXT,
        player_id   INTEGER DEFAULT 0,
        union_name  TEXT,
        union_id    INTEGER DEFAULT 0,
        level       INTEGER DEFAULT 0,
        power       INTEGER DEFAULT 0,
        hp          INTEGER DEFAULT 0,
        city_type   INTEGER DEFAULT 0,
        hero_ids    TEXT,
        captured_at TEXT,
        source_type TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_uc_player ON union_cities(player_name);
    CREATE INDEX IF NOT EXISTS idx_uc_union  ON union_cities(union_id);

    -- 数据库同步记录 (00015f95 / 00000ad* / 00000bc*)
    CREATE TABLE IF NOT EXISTS db_sync (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        op          INTEGER,   -- 1=insert/update 2=update 3=delete
        table_name  TEXT,
        row_id      INTEGER DEFAULT 0,
        raw_json    TEXT,
        captured_at TEXT,
        source_type TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_dbs_table ON db_sync(table_name);
    CREATE INDEX IF NOT EXISTS idx_dbs_rowid ON db_sync(row_id);

    -- 英雄解锁时间 (0000029f / 00000fd4 / 000010e2)
    CREATE TABLE IF NOT EXISTS hero_unlock (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        hero_id     INTEGER,
        unlock_time INTEGER,
        captured_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_hu_hero ON hero_unlock(hero_id);
    ''')
    conn.commit()
    print('扩展表创建完成')

