import sqlite3
import unittest

from intelligence.live_army_service import LiveArmyService, army_state_meta


NOW_MS = 1_800_000_000_000


def create_connection():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    connection.executescript(
        """
        CREATE TABLE world_scene_packets(
            seq INTEGER PRIMARY KEY AUTOINCREMENT,
            cmd_id INTEGER NOT NULL,
            source TEXT NOT NULL DEFAULT '',
            observed_at_ms INTEGER NOT NULL,
            server_order_id INTEGER NOT NULL DEFAULT 0,
            payload_len INTEGER NOT NULL DEFAULT 0,
            raw_payload TEXT NOT NULL DEFAULT ''
        );
        CREATE TABLE world_state_versions(
            version INTEGER PRIMARY KEY AUTOINCREMENT,
            packet_seq INTEGER NOT NULL,
            source_cmd INTEGER NOT NULL,
            latest_baseline_order_id INTEGER NOT NULL DEFAULT -1,
            observed_at_ms INTEGER NOT NULL,
            completeness TEXT NOT NULL DEFAULT 'full-baseline',
            coverage_json TEXT,
            change_summary_json TEXT NOT NULL DEFAULT '{}'
        );
        CREATE TABLE world_map_users(
            user_id INTEGER PRIMARY KEY,
            name TEXT,
            role_id INTEGER,
            union_id INTEGER,
            union_name TEXT,
            raw_json TEXT,
            source_seq INTEGER NOT NULL
        );
        CREATE TABLE world_unions(
            union_id INTEGER PRIMARY KEY,
            force INTEGER,
            name TEXT,
            source_seq INTEGER NOT NULL
        );
        CREATE TABLE world_tiles(
            wid INTEGER PRIMARY KEY,
            row INTEGER NOT NULL,
            col INTEGER NOT NULL,
            city_type INTEGER,
            city_param INTEGER,
            user_id INTEGER,
            union_id INTEGER,
            protect_end_time INTEGER,
            name TEXT,
            belong_city INTEGER,
            world_city_state INTEGER,
            guard_end_time INTEGER,
            force INTEGER,
            state_id INTEGER,
            view_range_add INTEGER,
            raw_world_city TEXT,
            source_seq INTEGER NOT NULL
        );
        CREATE TABLE world_armies(
            army_id INTEGER PRIMARY KEY,
            state INTEGER,
            user_id INTEGER,
            wid_from INTEGER,
            wid_to INTEGER,
            begin_time INTEGER,
            end_time INTEGER,
            target_type INTEGER,
            reside_wid INTEGER,
            stay_wid INTEGER,
            army_hero_type TEXT,
            morale INTEGER,
            real_march_id INTEGER,
            buff_ids TEXT,
            obstacle_wid INTEGER,
            battle_show TEXT,
            state_id INTEGER,
            raw_json TEXT,
            source_seq INTEGER NOT NULL,
            deleted_at_seq INTEGER
        );
        CREATE TABLE world_real_marches(
            real_march_id INTEGER PRIMARY KEY,
            last_wid INTEGER,
            current_wid INTEGER,
            next_wid INTEGER,
            start_time INTEGER,
            next_time INTEGER,
            end_time INTEGER,
            path_id INTEGER,
            unit_time_cost INTEGER,
            march_type INTEGER,
            belong_id INTEGER,
            raw_json TEXT,
            source_seq INTEGER NOT NULL
        );
        CREATE TABLE battles_v2(
            battle_id INTEGER PRIMARY KEY,
            time INTEGER,
            time_str TEXT,
            result INTEGER DEFAULT 0,
            atk_name TEXT DEFAULT '',
            atk_team_id INTEGER DEFAULT 0,
            def_name TEXT DEFAULT '',
            def_team_id INTEGER DEFAULT 0,
            all_skill_info TEXT DEFAULT '',
            atk_hero1_id INTEGER DEFAULT 0,
            atk_hero1_level INTEGER DEFAULT 0,
            atk_hero1_star INTEGER DEFAULT 0,
            atk_hero2_id INTEGER DEFAULT 0,
            atk_hero2_level INTEGER DEFAULT 0,
            atk_hero2_star INTEGER DEFAULT 0,
            atk_hero3_id INTEGER DEFAULT 0,
            atk_hero3_level INTEGER DEFAULT 0,
            atk_hero3_star INTEGER DEFAULT 0,
            def_hero1_id INTEGER DEFAULT 0,
            def_hero1_level INTEGER DEFAULT 0,
            def_hero1_star INTEGER DEFAULT 0,
            def_hero2_id INTEGER DEFAULT 0,
            def_hero2_level INTEGER DEFAULT 0,
            def_hero2_star INTEGER DEFAULT 0,
            def_hero3_id INTEGER DEFAULT 0,
            def_hero3_level INTEGER DEFAULT 0,
            def_hero3_star INTEGER DEFAULT 0
        );
        """
    )
    packet_seq = insert_packet(
        connection,
        cmd_id=5026,
        observed_at_ms=NOW_MS - 30_000,
        server_order_id=700,
    )
    connection.execute(
        """
        INSERT INTO world_state_versions(
            packet_seq,source_cmd,latest_baseline_order_id,observed_at_ms,
            completeness,coverage_json,change_summary_json
        ) VALUES(?,?,?,?,?,?,?)
        """,
        (
            packet_seq,
            5026,
            700,
            NOW_MS - 30_000,
            "full-baseline",
            '{"rowUp":1,"rowDown":300,"colLeft":1,"colRight":2000}',
            "{}",
        ),
    )
    connection.commit()
    return connection


def insert_packet(
    connection,
    *,
    cmd_id=5026,
    observed_at_ms=NOW_MS - 30_000,
    server_order_id=1,
):
    cursor = connection.execute(
        """
        INSERT INTO world_scene_packets(
            cmd_id,source,observed_at_ms,server_order_id,payload_len,raw_payload
        ) VALUES(?,?,?,?,?,?)
        """,
        (cmd_id, "fixture", observed_at_ms, server_order_id, 0, ""),
    )
    return int(cursor.lastrowid)


def insert_army(
    connection,
    *,
    army_id,
    state=0,
    user_id=42,
    wid_from=10001,
    wid_to=10009,
    begin_time=1_899_999_900,
    end_time=1_900_000_060,
    target_type=1,
    reside_wid=10002,
    stay_wid=0,
    army_hero_type="1,2,3",
    morale=100,
    real_march_id=0,
    source_seq=None,
    deleted_at_seq=None,
):
    source_seq = source_seq or insert_packet(connection)
    connection.execute(
        """
        INSERT INTO world_armies(
            army_id,state,user_id,wid_from,wid_to,begin_time,end_time,
            target_type,reside_wid,stay_wid,army_hero_type,morale,
            real_march_id,buff_ids,obstacle_wid,battle_show,state_id,
            raw_json,source_seq,deleted_at_seq
        ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        (
            army_id,
            state,
            user_id,
            wid_from,
            wid_to,
            begin_time,
            end_time,
            target_type,
            reside_wid,
            stay_wid,
            army_hero_type,
            morale,
            real_march_id,
            "",
            0,
            "",
            1,
            "[]",
            source_seq,
            deleted_at_seq,
        ),
    )
    connection.execute(
        """
        INSERT OR REPLACE INTO world_map_users(
            user_id,name,role_id,union_id,union_name,raw_json,source_seq
        ) VALUES(?,?,?,?,?,?,?)
        """,
        (user_id, f"玩家{user_id}", user_id, 1005, "测试同盟", "[]", source_seq),
    )
    connection.execute(
        "INSERT OR REPLACE INTO world_unions(union_id,force,name,source_seq) VALUES(?,?,?,?)",
        (1005, 0, "测试同盟", source_seq),
    )
    row, col = divmod(wid_to, 10000)
    connection.execute(
        """
        INSERT OR REPLACE INTO world_tiles(
            wid,row,col,city_type,city_param,user_id,union_id,
            protect_end_time,name,belong_city,world_city_state,
            guard_end_time,force,state_id,view_range_add,raw_world_city,
            source_seq
        ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        (
            wid_to,
            row,
            col,
            0,
            0,
            0,
            0,
            0,
            f"目标{wid_to}",
            0,
            0,
            0,
            123,
            1,
            0,
            "[]",
            source_seq,
        ),
    )
    connection.commit()
    return source_seq


def insert_march(
    connection,
    *,
    real_march_id,
    last_wid=10003,
    current_wid=10004,
    next_wid=10005,
    start_time=1_899_999_900,
    next_time=1_900_000_030,
    end_time=1_900_000_060,
    path_id=77,
    unit_time_cost=3,
    march_type=1,
    belong_id=42,
):
    source_seq = insert_packet(connection)
    connection.execute(
        """
        INSERT INTO world_real_marches(
            real_march_id,last_wid,current_wid,next_wid,start_time,next_time,
            end_time,path_id,unit_time_cost,march_type,belong_id,raw_json,
            source_seq
        ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        (
            real_march_id,
            last_wid,
            current_wid,
            next_wid,
            start_time,
            next_time,
            end_time,
            path_id,
            unit_time_cost,
            march_type,
            belong_id,
            "[]",
            source_seq,
        ),
    )
    connection.commit()


def insert_battle(
    connection,
    *,
    battle_id,
    time,
    atk_team_id=0,
    def_team_id=0,
    atk_name="攻方",
    def_name="守方",
    atk_hero_ids=(0, 0, 0),
    atk_levels=(0, 0, 0),
    atk_stars=(0, 0, 0),
    def_hero_ids=(0, 0, 0),
    def_levels=(0, 0, 0),
    def_stars=(0, 0, 0),
    all_skill_info="",
):
    values = [
        battle_id,
        time,
        "2026-08-14 21:49:27",
        1,
        atk_name,
        atk_team_id,
        def_name,
        def_team_id,
        all_skill_info,
    ]
    for position in range(3):
        values.extend(
            (
                atk_hero_ids[position],
                atk_levels[position],
                atk_stars[position],
            )
        )
    for position in range(3):
        values.extend(
            (
                def_hero_ids[position],
                def_levels[position],
                def_stars[position],
            )
        )
    connection.execute(
        """
        INSERT INTO battles_v2 VALUES(
            ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?
        )
        """,
        values,
    )
    connection.commit()


class LiveArmyTestCase(unittest.TestCase):
    def setUp(self):
        self.connection = create_connection()

    def tearDown(self):
        self.connection.close()


class LiveArmyStateTest(LiveArmyTestCase):
    def test_authoritative_army_states_and_unknown_state(self):
        expected = {
            0: ("normal", "待命", False),
            1: ("expedition", "出征中", True),
            2: ("reside-going", "驻守前往", True),
            3: ("reinforce-going", "增援前往", True),
            4: ("returning", "返回中", True),
            5: ("reside", "驻守", False),
            6: ("reinforce", "增援", False),
            25: ("stay", "停留", False),
        }
        for state, (key, label, moving) in expected.items():
            with self.subTest(state=state):
                meta = army_state_meta(state)
                self.assertEqual(key, meta["key"])
                self.assertEqual(label, meta["label"])
                self.assertEqual(moving, meta["isMoving"])
        self.assertEqual(
            {
                "key": "unknown",
                "label": "状态 99",
                "category": "unknown",
                "isMoving": False,
            },
            army_state_meta(99),
        )


class LiveArmyLocationTest(LiveArmyTestCase):
    def test_real_march_overrides_fallback_location(self):
        insert_army(
            self.connection,
            army_id=18411352,
            state=1,
            real_march_id=9001,
            wid_from=10001,
            wid_to=10009,
            reside_wid=10002,
            stay_wid=10003,
        )
        insert_march(
            self.connection,
            real_march_id=9001,
            current_wid=10004,
            next_wid=10005,
        )

        item = LiveArmyService(
            self.connection,
            now_ms=NOW_MS,
        ).snapshot()["current"][0]

        self.assertEqual(
            {
                "currentWid": 10004,
                "nextWid": 10005,
                "targetWid": 10009,
                "fromWid": 10001,
                "resideWid": 10002,
                "stayWid": 10003,
                "source": "real-march",
            },
            item["location"],
        )
        self.assertEqual(9001, item["march"]["realMarchId"])

    def test_location_falls_back_stay_reside_from(self):
        insert_army(
            self.connection,
            army_id=1,
            stay_wid=10007,
            reside_wid=10006,
            wid_from=10005,
        )
        insert_army(
            self.connection,
            army_id=2,
            stay_wid=0,
            reside_wid=10006,
            wid_from=10005,
        )
        insert_army(
            self.connection,
            army_id=3,
            stay_wid=0,
            reside_wid=0,
            wid_from=10005,
        )

        rows = {
            row["armyId"]: row["location"]
            for row in LiveArmyService(
                self.connection,
                now_ms=NOW_MS,
            ).snapshot()["current"]
        }

        self.assertEqual((10007, "stay"), (rows[1]["currentWid"], rows[1]["source"]))
        self.assertEqual(
            (10006, "reside"),
            (rows[2]["currentWid"], rows[2]["source"]),
        )
        self.assertEqual((10005, "from"), (rows[3]["currentWid"], rows[3]["source"]))


class LiveArmyLineupTest(LiveArmyTestCase):
    def test_exact_attack_lineup_maps_real_names_and_portraits(self):
        insert_army(self.connection, army_id=18411352)
        insert_battle(
            self.connection,
            battle_id=5289170,
            time=1786724967,
            atk_team_id=18411352,
            def_team_id=999,
            atk_hero_ids=(100705, 100707, 100101),
            atk_levels=(45, 44, 43),
            atk_stars=(5, 4, 3),
            all_skill_info=(
                "1,200001,10,200027,10;"
                "2,200001,9;"
                "3,200027,8"
            ),
        )

        lineup = LiveArmyService(self.connection).snapshot()["current"][0]["lineup"]

        self.assertEqual("exact", lineup["status"])
        self.assertTrue(lineup["complete"])
        self.assertEqual(5289170, lineup["battleId"])
        self.assertEqual("atk", lineup["side"])
        self.assertEqual(
            ["杜预", "卫瓘", "灵帝"],
            [hero["name"] for hero in lineup["heroes"]],
        )
        self.assertEqual(45, lineup["heroes"][0]["level"])
        self.assertEqual(5, lineup["heroes"][0]["advance"])
        self.assertTrue(
            lineup["heroes"][0]["portraitUrl"].startswith(
                "/static/hero-portraits/"
            )
        )

    def test_exact_defense_lineup_uses_defender_columns(self):
        insert_army(self.connection, army_id=9002)
        insert_battle(
            self.connection,
            battle_id=20,
            time=2000,
            atk_team_id=111,
            def_team_id=9002,
            def_hero_ids=(100013, 100649, 100023),
        )

        lineup = LiveArmyService(self.connection).snapshot()["current"][0]["lineup"]

        self.assertEqual("exact", lineup["status"])
        self.assertEqual("def", lineup["side"])
        self.assertEqual(
            [100013, 100649, 100023],
            [hero["id"] for hero in lineup["heroes"]],
        )

    def test_latest_invalid_report_falls_back_to_latest_valid_same_army(self):
        insert_army(self.connection, army_id=77)
        insert_battle(
            self.connection,
            battle_id=2,
            time=200,
            atk_team_id=77,
            atk_hero_ids=(0, 0, 0),
        )
        insert_battle(
            self.connection,
            battle_id=1,
            time=100,
            atk_team_id=77,
            atk_hero_ids=(100027, 100016, 0),
        )

        lineup = LiveArmyService(self.connection).snapshot()["current"][0]["lineup"]

        self.assertEqual(1, lineup["battleId"])
        self.assertEqual("exact", lineup["status"])
        self.assertFalse(lineup["complete"])
        self.assertEqual(2, len(lineup["heroes"]))

    def test_no_same_army_report_is_unknown_without_player_fallback(self):
        insert_army(self.connection, army_id=814501, user_id=14455)
        insert_battle(
            self.connection,
            battle_id=30,
            time=3000,
            atk_team_id=999999,
            atk_name="玩家14455",
            atk_hero_ids=(100705, 100707, 100101),
        )

        lineup = LiveArmyService(self.connection).snapshot()["current"][0]["lineup"]

        self.assertEqual(
            {
                "status": "unknown",
                "complete": False,
                "battleId": 0,
                "battleTime": 0,
                "battleTimeText": "",
                "side": "",
                "heroes": [],
                "message": "无同 ID 战报，阵容未知",
            },
            lineup,
        )


class LiveArmyOfflineTest(LiveArmyTestCase):
    def test_recent_offline_includes_exact_ten_minute_boundary(self):
        deletion_seq = insert_packet(
            self.connection,
            cmd_id=5028,
            observed_at_ms=NOW_MS - 10 * 60 * 1000,
        )
        insert_army(
            self.connection,
            army_id=88,
            deleted_at_seq=deletion_seq,
        )

        snapshot = LiveArmyService(
            self.connection,
            now_ms=NOW_MS,
        ).snapshot(offline_minutes=10)

        self.assertEqual(1, len(snapshot["recentOffline"]))
        offline = snapshot["recentOffline"][0]["offline"]
        self.assertEqual("5028 增量", offline["sourceLabel"])
        self.assertEqual(600_000, offline["ageMs"])

    def test_recent_offline_excludes_rows_older_than_window(self):
        deletion_seq = insert_packet(
            self.connection,
            cmd_id=5026,
            observed_at_ms=NOW_MS - 600_001,
        )
        insert_army(
            self.connection,
            army_id=89,
            deleted_at_seq=deletion_seq,
        )

        snapshot = LiveArmyService(
            self.connection,
            now_ms=NOW_MS,
        ).snapshot(offline_minutes=10)

        self.assertEqual([], snapshot["recentOffline"])

    def test_missing_optional_tables_degrades_without_exception(self):
        connection = sqlite3.connect(":memory:")
        connection.row_factory = sqlite3.Row
        try:
            snapshot = LiveArmyService(
                connection,
                now_ms=NOW_MS,
            ).snapshot()
        finally:
            connection.close()

        self.assertEqual([], snapshot["current"])
        self.assertEqual([], snapshot["recentOffline"])
        self.assertEqual("unknown", snapshot["freshness"])


class LiveArmyFreshnessTest(LiveArmyTestCase):
    def test_snapshot_exposes_world_state_observation_time_and_age(self):
        insert_army(self.connection, army_id=77)

        snapshot = LiveArmyService(
            self.connection,
            now_ms=NOW_MS,
        ).snapshot()

        self.assertEqual(NOW_MS - 30_000, snapshot["worldStateObservedAtMs"])
        self.assertEqual(30_000, snapshot["worldStateAgeMs"])

    def test_old_army_source_is_stale_even_when_world_state_is_fresh(self):
        old_source = insert_packet(
            self.connection,
            cmd_id=5026,
            observed_at_ms=NOW_MS - 11 * 60 * 1000,
        )
        insert_army(
            self.connection,
            army_id=814501,
            source_seq=old_source,
        )

        snapshot = LiveArmyService(
            self.connection,
            now_ms=NOW_MS,
        ).snapshot()
        army = snapshot["current"][0]

        self.assertEqual("fresh", snapshot["freshness"])
        self.assertEqual(660_000, army["source"]["ageMs"])
        self.assertEqual("stale", army["source"]["freshness"])
        self.assertTrue(army["source"]["isStale"])
        self.assertEqual(1, snapshot["summary"]["staleCurrent"])
        self.assertEqual(0, snapshot["summary"]["usableCurrent"])

    def test_recent_army_source_is_usable_and_not_stale(self):
        source = insert_packet(
            self.connection,
            cmd_id=5028,
            observed_at_ms=NOW_MS - 90_000,
        )
        insert_army(
            self.connection,
            army_id=18411352,
            source_seq=source,
        )

        snapshot = LiveArmyService(
            self.connection,
            now_ms=NOW_MS,
        ).snapshot()
        army = snapshot["current"][0]

        self.assertEqual("fresh", army["source"]["freshness"])
        self.assertFalse(army["source"]["isStale"])
        self.assertEqual(0, snapshot["summary"]["staleCurrent"])
        self.assertEqual(1, snapshot["summary"]["usableCurrent"])


if __name__ == "__main__":
    unittest.main()
