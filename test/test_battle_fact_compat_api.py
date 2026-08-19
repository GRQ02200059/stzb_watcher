import os
import sqlite3
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo
from unittest.mock import patch

import api_server


class BattleFactCompatApiTest(unittest.TestCase):
    def setUp(self):
        fd, self.db_path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        conn = sqlite3.connect(self.db_path)
        sunday = int(datetime(2026, 8, 9, 23, 0, tzinfo=ZoneInfo("Asia/Shanghai")).timestamp())
        monday = int(datetime(2026, 8, 10, 23, 0, tzinfo=ZoneInfo("Asia/Shanghai")).timestamp())
        conn.executescript(
            f"""
            CREATE TABLE battles_v2(
                battle_id INTEGER PRIMARY KEY,time INTEGER,time_str TEXT,result INTEGER,
                result_desc TEXT,fight_type INTEGER,wid INTEGER,wid_code TEXT,city_type INTEGER,
                atk_name TEXT,atk_uid TEXT,atk_union TEXT,atk_unionid INTEGER,atk_power INTEGER,
                def_name TEXT,def_uid TEXT,def_union TEXT,def_unionid INTEGER,def_power INTEGER,
                is_npc INTEGER DEFAULT 0
            );
            INSERT INTO battles_v2 VALUES
                (1,{sunday},'2026-08-09 23:00:00',1,'胜利',33,10004,'1,4',8,
                 '玩家甲','42','甲盟',7,50000,'玩家乙','43','乙盟',8,49000,0),
                (2,{monday},'2026-08-10 23:00:00',2,'失败',0,10005,'1,5',0,
                 '玩家甲','42','甲盟',7,51000,'玩家丙','44','丙盟',9,52000,0);
            CREATE TABLE battle_heroes(battle_id INTEGER,side TEXT,pos INTEGER,hero_id INTEGER,hero_name TEXT);
            CREATE TABLE battle_skills(battle_id INTEGER,side TEXT,pos INTEGER,skill_id INTEGER,skill_name TEXT);
            CREATE TABLE player_teams(player_name TEXT,side TEXT,used_count INTEGER);
            CREATE TABLE team_users(
                uid INTEGER,name TEXT,union_name TEXT,group_name TEXT,power INTEGER,wuxun INTEGER
            );
            INSERT INTO team_users VALUES(42,'玩家甲','甲盟','一团',51000,0);
            CREATE TABLE wuxun_weekly_snapshots(
                profile_id TEXT DEFAULT '',week_start TEXT,uid TEXT,player_name TEXT,
                union_name TEXT,group_name TEXT,wuxun INTEGER,captured_at TEXT
            );
            INSERT INTO wuxun_weekly_snapshots VALUES
                ('','2026-08-03','42','玩家甲','甲盟','一团',1200,'2026-08-09T23:00:00'),
                ('','2026-08-10','42','玩家甲','甲盟','一团',2300,'2026-08-16T23:00:00');
            CREATE TABLE custom_scores(
                season_id TEXT,player_name TEXT,player_uid TEXT,union_name TEXT,
                battles INTEGER,wins INTEGER,draws INTEGER,gongxun_total INTEGER,
                score REAL,rule_version_id INTEGER
            );
            INSERT INTO custom_scores VALUES('s1','玩家甲','42','甲盟',2,1,0,3500,18.5,3);
            """
        )
        conn.commit()
        conn.close()

    def tearDown(self):
        try:
            os.unlink(self.db_path)
        except FileNotFoundError:
            pass

    def connect(self):
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def get(self, path):
        with patch("api_server.get_db", self.connect):
            return api_server.app.test_client().get(path)

    def test_legacy_battle_urls_read_battles_v2_without_legacy_tables(self):
        listing = self.get("/api/battles?player=%E7%8E%A9%E5%AE%B6%E7%94%B2")
        self.assertEqual(listing.status_code, 200)
        self.assertEqual(listing.get_json()["total"], 2)
        detail = self.get("/api/battles/1")
        self.assertEqual(detail.status_code, 200)
        self.assertEqual(detail.get_json()["battle"]["battle_id"], 1)

    def test_players_wuxun_and_scores_share_new_fact_sources(self):
        players = self.get("/api/players?season=s1").get_json()
        self.assertEqual(players[0]["wuxun_total"], 3500)
        self.assertEqual(players[0]["custom_score"], 18.5)
        wuxun = self.get("/api/wuxun").get_json()
        self.assertEqual(wuxun[0]["total_wx"], 3500)
        scores = self.get("/api/scores?season=s1&union=%E7%94%B2%E7%9B%9F").get_json()
        self.assertEqual(scores[0]["player_name"], "玩家甲")

    def test_personal_season_trend_combines_weekly_battles_and_member_wuxun(self):
        response = self.get("/api/players/%E7%8E%A9%E5%AE%B6%E7%94%B2/season-trend?season=s1")
        self.assertEqual(response.status_code, 200)
        trend = response.get_json()["trend"]
        self.assertEqual([row["weekStart"] for row in trend], ["2026-08-03", "2026-08-10"])
        self.assertEqual([row["memberWuxun"] for row in trend], [1200, 2300])
        self.assertEqual([row["battles"] for row in trend], [1, 1])

    def test_runtime_api_has_no_legacy_battle_score_fact_reads(self):
        source = Path(api_server.__file__).read_text(encoding="utf-8")
        import re
        self.assertIsNone(re.search(r"\b(?:FROM|JOIN)\s+battles\b", source, re.I))
        self.assertIsNone(re.search(r"\b(?:FROM|JOIN)\s+wuxun\b", source, re.I))
        self.assertIsNone(re.search(r"\b(?:FROM|JOIN)\s+scores\b", source, re.I))


if __name__ == "__main__":
    unittest.main()
