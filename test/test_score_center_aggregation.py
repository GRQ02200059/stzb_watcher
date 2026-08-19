import sqlite3
import unittest
from datetime import datetime

from score_center.aggregation import ScoreAggregator
from score_center.repository import ScoreRepository


def build_connection(with_attendance=True):
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    connection.executescript(
        """
        CREATE TABLE battles_v2(
            battle_id INTEGER PRIMARY KEY,
            time INTEGER,
            result INTEGER,
            atk_name TEXT,
            atk_uid TEXT,
            atk_union TEXT,
            def_union TEXT,
            atk_gongxun INTEGER,
            atk_power INTEGER,
            fight_type INTEGER
        );
        INSERT INTO battles_v2 VALUES
            (1,1000,1,'玩家甲','1','甲盟','错误敌盟',1000,50000,0),
            (2,1100,2,'玩家甲','1','','另一个敌盟',0,51000,80),
            (3,1200,0,'玩家甲','1','','另一个敌盟',0,52000,0),
            (4,1300,1,'玩家乙','2','乙盟','敌盟',0,30000,33);
        CREATE TABLE team_users(
            uid TEXT,
            name TEXT,
            union_name TEXT,
            group_name TEXT,
            wuxun INTEGER DEFAULT 0
        );
        INSERT INTO team_users VALUES('1','玩家甲','甲盟回退','一团',1234);
        CREATE TABLE custom_scores(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            season_id TEXT,
            player_name TEXT,
            player_uid TEXT,
            union_name TEXT,
            battles INTEGER DEFAULT 0,
            wins INTEGER DEFAULT 0,
            gongxun_total INTEGER DEFAULT 0,
            power_total INTEGER DEFAULT 0,
            main_city_cnt INTEGER DEFAULT 0,
            tear_cnt INTEGER DEFAULT 0,
            score REAL DEFAULT 0,
            updated_at TEXT
        );
        """
    )
    if with_attendance:
        connection.executescript(
            """
            CREATE TABLE attendance(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT,
                player_name TEXT,
                player_uid TEXT,
                union_name TEXT,
                role TEXT,
                time INTEGER
            );
            INSERT INTO attendance(
                session_id,player_name,player_uid,union_name,role,time
            ) VALUES
                ('s1','玩家甲','1','甲盟','main',1000),
                ('s1','玩家甲','1','甲盟','main',1001),
                ('s2','玩家甲','1','甲盟','tear',1100),
                ('s3','玩家甲','1','甲盟','other',1200);
            """
        )
    repository = ScoreRepository(connection)
    repository.ensure_schema()
    return connection, repository


class ScoreCenterAggregationTest(unittest.TestCase):
    def test_sunday_snapshot_preserves_member_wuxun_after_weekly_reset(self):
        connection, repository = build_connection()
        connection.execute("UPDATE team_users SET wuxun=1234 WHERE uid='1'")
        repository.capture_sunday_wuxun_snapshot(datetime(2026, 8, 16, 20, 0, 0))
        connection.execute("UPDATE team_users SET wuxun=0 WHERE uid='1'")
        rows = ScoreAggregator(connection, repository).aggregate("current")
        player = next(row for row in rows if row.player_name == "玩家甲")
        self.assertEqual(1234, player.metrics.gongxun_total)
        connection.close()

    def test_sunday_snapshots_accumulate_across_weeks(self):
        connection, repository = build_connection()
        connection.execute("UPDATE team_users SET wuxun=1234 WHERE uid='1'")
        repository.capture_sunday_wuxun_snapshot(datetime(2026, 8, 9, 20, 0, 0))
        connection.execute("UPDATE team_users SET wuxun=2345 WHERE uid='1'")
        repository.capture_sunday_wuxun_snapshot(datetime(2026, 8, 16, 20, 0, 0))
        connection.execute("UPDATE team_users SET wuxun=0 WHERE uid='1'")
        rows = ScoreAggregator(connection, repository).aggregate("current")
        player = next(row for row in rows if row.player_name == "玩家甲")
        self.assertEqual(3579, player.metrics.gongxun_total)
        connection.close()

    def test_aggregates_attack_identity_results_and_attendance(self):
        connection, repository = build_connection()
        try:
            rows = ScoreAggregator(connection, repository).aggregate("current")
            player = next(row for row in rows if row.player_name == "玩家甲")
            self.assertEqual(player.union_name, "甲盟")
            self.assertNotEqual(player.union_name, "错误敌盟")
            self.assertEqual(player.metrics.battles, 3)
            self.assertEqual(player.metrics.wins, 1)
            self.assertEqual(player.metrics.draws, 1)
            self.assertEqual(player.metrics.gongxun_total, 1234)
            self.assertEqual(player.metrics.main_city_cnt, 1)
            self.assertEqual(player.metrics.tear_cnt, 1)
            self.assertEqual(player.metrics.attendance_cnt, 1)
            self.assertEqual(player.data_completeness, "complete")
        finally:
            connection.close()

    def test_time_and_group_filters_are_parameterized(self):
        connection, repository = build_connection()
        try:
            rows = ScoreAggregator(connection, repository).aggregate(
                "current",
                start_time=1050,
                end_time=1250,
                union_filter="甲盟",
                group_filter="一团",
            )
            self.assertEqual(len(rows), 1)
            self.assertEqual(rows[0].metrics.battles, 2)
        finally:
            connection.close()

    def test_end_time_is_exclusive(self):
        connection, repository = build_connection()
        try:
            rows = ScoreAggregator(connection, repository).aggregate(
                "current",
                start_time=1000,
                end_time_exclusive=1200,
            )
            player = next(row for row in rows if row.player_name == "玩家甲")
            self.assertEqual(player.metrics.battles, 2)
        finally:
            connection.close()

    def test_missing_attendance_marks_partial_instead_of_zero_complete(self):
        connection, repository = build_connection(with_attendance=False)
        try:
            rows = ScoreAggregator(connection, repository).aggregate("current")
            player = next(row for row in rows if row.player_name == "玩家甲")
            self.assertEqual(player.data_completeness, "partial")
            self.assertIn("attendance", player.missing_sources)
        finally:
            connection.close()

    def test_zero_member_wuxun_is_a_valid_observed_value(self):
        connection, repository = build_connection()
        try:
            connection.execute("UPDATE battles_v2 SET atk_gongxun=0")
            connection.execute("UPDATE team_users SET wuxun=0")
            rows = ScoreAggregator(connection, repository).aggregate("current")
            player = next(row for row in rows if row.player_name == "玩家甲")
            self.assertEqual(player.metrics.gongxun_total, 0)
            self.assertEqual(player.data_completeness, "complete")
            self.assertNotIn("gongxun", player.missing_sources)
        finally:
            connection.close()

    def test_adjustment_only_player_is_included(self):
        connection, repository = build_connection()
        try:
            repository.add_adjustment(
                "current", "组织者", "9", 10, "组织奖励", "tester"
            )
            rows = ScoreAggregator(connection, repository).aggregate("current")
            player = next(row for row in rows if row.player_name == "组织者")
            self.assertEqual(player.adjustment_score, 10)
            self.assertEqual(player.metrics.battles, 0)
        finally:
            connection.close()


if __name__ == "__main__":
    unittest.main()
