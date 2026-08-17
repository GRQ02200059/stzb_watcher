# -*- coding: utf-8 -*-
"""
db_schema_v2.py - 新增/扩展表结构
"""
import sqlite3

DB_PATH = 'd:/nettest/stzb.db'

SCHEMA = '''
-- 补全 battles 表缺失字段
PRAGMA user_version = 2;

-- 战报完整解析（重建 battles 视图兼容层，实际扩展原表）
CREATE TABLE IF NOT EXISTS battles_v2 (
    battle_id       INTEGER PRIMARY KEY,
    time            INTEGER,
    time_str        TEXT,
    result          INTEGER,
    result_desc     TEXT,
    fight_type      INTEGER DEFAULT 0,  -- 0=野战 80=攻城 33=大城
    wid             INTEGER DEFAULT 0,  -- 格子坐标ID
    atk_name        TEXT,
    atk_uid         TEXT,               -- #后面的UID
    atk_union       TEXT,
    atk_level       INTEGER DEFAULT 0,  -- 州
    atk_gongxun     INTEGER DEFAULT 0,  -- 武勋
    atk_power       INTEGER DEFAULT 0,  -- 势力值
    def_name        TEXT,
    def_union       TEXT,
    def_level       INTEGER DEFAULT 0,
    def_gongxun     INTEGER DEFAULT 0,
    wid_code        TEXT,               -- 地图格子编码 如 430103
    source_file     TEXT
);
CREATE INDEX IF NOT EXISTS idx_bv2_time     ON battles_v2(time);
CREATE INDEX IF NOT EXISTS idx_bv2_atk_name ON battles_v2(atk_name);
CREATE INDEX IF NOT EXISTS idx_bv2_def_union ON battles_v2(def_union);
CREATE INDEX IF NOT EXISTS idx_bv2_fight_type ON battles_v2(fight_type);

-- 武勋统计（按玩家/时间段）
CREATE TABLE IF NOT EXISTS wuxun_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    battle_id   INTEGER,
    time        INTEGER,
    atk_name    TEXT,
    atk_union   TEXT,
    atk_level   INTEGER DEFAULT 0,
    gongxun     INTEGER DEFAULT 0,
    fight_type  INTEGER DEFAULT 0,
    result      INTEGER DEFAULT 0,
    wid         INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_wx_log_name  ON wuxun_log(atk_name);
CREATE INDEX IF NOT EXISTS idx_wx_log_union ON wuxun_log(atk_union);
CREATE INDEX IF NOT EXISTS idx_wx_log_time  ON wuxun_log(time);

-- 势力值日志
CREATE TABLE IF NOT EXISTS power_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    battle_id   INTEGER,
    time        INTEGER,
    atk_name    TEXT,
    atk_union   TEXT,
    atk_level   INTEGER DEFAULT 0,
    power       INTEGER DEFAULT 0,
    fight_type  INTEGER DEFAULT 0,
    result      INTEGER DEFAULT 0,
    wid         INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_pw_name  ON power_log(atk_name);
CREATE INDEX IF NOT EXISTS idx_pw_union ON power_log(atk_union);
CREATE INDEX IF NOT EXISTS idx_pw_time  ON power_log(time);

-- 打城排表（3分钟一个城，智能排班）
CREATE TABLE IF NOT EXISTS city_schedule (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  TEXT,               -- 场次ID，如 20260311_01
    slot_index  INTEGER DEFAULT 0,  -- 第几个格子
    wid         INTEGER DEFAULT 0,
    wid_code    TEXT,
    scheduled_at TEXT,
    assigned_group TEXT,            -- 分配的队伍/团
    notes       TEXT,
    created_at  TEXT
);

-- 打城考勤
CREATE TABLE IF NOT EXISTS attendance (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  TEXT,
    battle_id   INTEGER,
    time        INTEGER,
    player_name TEXT,
    player_uid  TEXT,
    union_name  TEXT,
    fight_type  INTEGER DEFAULT 0,
    wid         INTEGER DEFAULT 0,
    gongxun     INTEGER DEFAULT 0,
    role        TEXT DEFAULT 'main', -- main=主力 tear=拆迁 other=其他
    result      INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_att_session ON attendance(session_id);
CREATE INDEX IF NOT EXISTS idx_att_player  ON attendance(player_name);
CREATE INDEX IF NOT EXISTS idx_att_union   ON attendance(union_name);
CREATE INDEX IF NOT EXISTS idx_att_time    ON attendance(time);

-- 自定义积分
CREATE TABLE IF NOT EXISTS custom_scores (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    season_id       TEXT DEFAULT 'current',
    player_name     TEXT,
    player_uid      TEXT,
    union_name      TEXT,
    battles         INTEGER DEFAULT 0,
    wins            INTEGER DEFAULT 0,
    gongxun_total   INTEGER DEFAULT 0,
    power_total     INTEGER DEFAULT 0,
    main_city_cnt   INTEGER DEFAULT 0,  -- 主力打城次数
    tear_cnt        INTEGER DEFAULT 0,  -- 拆迁次数
    score           REAL DEFAULT 0,
    updated_at      TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_cs_player ON custom_scores(season_id, player_name);

-- 排行榜快照（定时计算缓存）
CREATE TABLE IF NOT EXISTS ranking_cache (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    period      TEXT,    -- 24h / week / season
    scope       TEXT,    -- player / union / group / region
    metric      TEXT,    -- wuxun / power / battles / wins
    rank_data   TEXT,    -- JSON数组
    updated_at  TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_rc_key ON ranking_cache(period, scope, metric);

-- 多州数据同步元信息
CREATE TABLE IF NOT EXISTS regions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    region_id   INTEGER UNIQUE,
    region_name TEXT,
    server_ip   TEXT,
    is_active   INTEGER DEFAULT 1,
    last_sync   TEXT
);

-- 插入默认区服
INSERT OR IGNORE INTO regions (region_id, region_name, server_ip, is_active)
VALUES (1023, '当前州', '45.253.141.10', 1);
''';

def migrate():
    conn = sqlite3.connect(DB_PATH)
    conn.execute('PRAGMA journal_mode=WAL')
    try:
        conn.executescript(SCHEMA)
        print('Schema v2 migration done')
    except sqlite3.OperationalError as e:
        print(f'executescript error: {e}')
        # 逐条回退
        for stmt in SCHEMA.split(';'):
            stmt = stmt.strip()
            if not stmt or stmt.startswith('--') or stmt.startswith('PRAGMA'):
                continue
            try:
                conn.execute(stmt)
            except sqlite3.OperationalError as e2:
                if 'already exists' not in str(e2):
                    print(f'WARN: {e2}')
        conn.commit()
    conn.close()

if __name__ == '__main__':
    migrate()

