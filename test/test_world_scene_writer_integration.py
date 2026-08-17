import sqlite3
import tempfile
import unittest
from unittest.mock import patch

import realtime_writer
from test.test_world_scene_parser import world_payload


class WorldSceneWriterIntegrationTest(unittest.TestCase):
    def test_process_data_persists_alliance_battle_reports(self):
        with tempfile.NamedTemporaryFile(suffix=".db") as tmp:
            def connect():
                conn = sqlite3.connect(tmp.name)
                conn.row_factory = sqlite3.Row
                return conn

            conn = connect()
            try:
                conn.executescript(
                    """
                    CREATE TABLE battles_v2(
                        battle_id INTEGER PRIMARY KEY,
                        time INTEGER,
                        time_str TEXT,
                        result INTEGER,
                        result_desc TEXT,
                        fight_type INTEGER,
                        wid INTEGER,
                        wid_code TEXT,
                        atk_name TEXT,
                        atk_uid TEXT,
                        atk_union TEXT,
                        atk_unionid INTEGER,
                        atk_gongxun INTEGER,
                        atk_power INTEGER,
                        atk_hp INTEGER,
                        atk_hero_type TEXT,
                        atk_advance TEXT,
                        def_name TEXT,
                        def_union TEXT,
                        def_unionid INTEGER,
                        def_gongxun INTEGER,
                        def_level INTEGER,
                        def_hp INTEGER,
                        def_hero_type TEXT,
                        def_advance TEXT,
                        is_npc INTEGER,
                        is_ai INTEGER,
                        weather INTEGER,
                        in_night INTEGER,
                        all_skill_info TEXT,
                        attack_all_hero_info TEXT,
                        defend_all_hero_info TEXT,
                        attack_all_sub_hero_info TEXT,
                        defend_all_sub_hero_info TEXT,
                        source_file TEXT,
                        atk_team_id INTEGER DEFAULT 0,
                        def_team_id INTEGER DEFAULT 0
                    );
                    CREATE TABLE battle_heroes(
                        battle_id INTEGER,
                        side TEXT,
                        pos INTEGER,
                        hero_id INTEGER,
                        hero_name TEXT,
                        level INTEGER,
                        max_hp INTEGER,
                        remain_hp INTEGER,
                        damage_taken INTEGER,
                        star INTEGER
                    );
                    CREATE TABLE attendance(
                        battle_id INTEGER,
                        time INTEGER,
                        player_name TEXT,
                        player_uid TEXT,
                        union_name TEXT,
                        fight_type INTEGER,
                        wid INTEGER,
                        gongxun INTEGER,
                        result INTEGER,
                        profile_id TEXT
                    );
                    """
                )
                conn.commit()
            finally:
                conn.close()

            report = {
                "battle_id": 9001,
                "time": 1700000000,
                "result": 1,
                "attack_name": "东丨核弹",
                "attack_role_id": "role-a",
                "attack_union_name": "诸丨天地",
                "attack_unionid": 1030,
                "defend_name": "小股流寇营地",
                "defend_union_name": "",
                "defend_unionid": 0,
                "defend_base_level": 8,
                "wid": 10004,
                "wid_name": "土地Lv.3",
                "fight_type": 0,
                "npc": 0,
                "is_ai": 0,
                "weather": 0,
                "in_night_mode": 0,
                "attack_hp": 2000,
                "defend_hp": 1200,
                "attack_advance": "0,0,0,0,0,0;",
                "defend_advance": "0,0,0,0,0,0;",
                "attack_hero_type": "0,3,3,2,",
                "defend_hero_type": "3,2,1,0,",
                "attack_all_hero_info": "100019,30,1000,800,0;",
                "defend_all_hero_info": "100113,8,850,0,400;",
                "attack_all_sub_hero_info": "",
                "defend_all_sub_hero_info": "",
                "all_skill_info": "",
            }

            with patch.object(realtime_writer, "get_db", side_effect=connect):
                writer = realtime_writer.RealtimeWriter()
                writer.process_data("0000005c", [[report]], "alliance_fixture")

            conn = connect()
            try:
                row = conn.execute(
                    "SELECT battle_id, atk_name FROM battles_v2 WHERE battle_id=9001"
                ).fetchone()
                self.assertIsNotNone(row)
                self.assertEqual(row["atk_name"], "东丨核弹")
            finally:
                conn.close()

    def test_process_data_persists_world_scene_packet(self):
        with tempfile.NamedTemporaryFile(suffix=".db") as tmp:
            def connect():
                conn = sqlite3.connect(tmp.name)
                conn.row_factory = sqlite3.Row
                return conn

            with patch.object(realtime_writer, "get_db", side_effect=connect):
                writer = realtime_writer.RealtimeWriter()
                writer.process_data("000013a2", world_payload(marker=1), "fixture")

            conn = connect()
            try:
                row = conn.execute("SELECT COUNT(*) AS c FROM world_scene_packets").fetchone()
                self.assertEqual(row["c"], 1)
                version = conn.execute(
                    "SELECT version, source_cmd FROM world_state_versions"
                ).fetchone()
                self.assertEqual(version["version"], 1)
                self.assertEqual(version["source_cmd"], 5026)
            finally:
                conn.close()

    def test_process_data_pushes_world_scene_sse_event(self):
        with tempfile.NamedTemporaryFile(suffix=".db") as tmp:
            def connect():
                conn = sqlite3.connect(tmp.name)
                conn.row_factory = sqlite3.Row
                return conn

            events = []
            with patch.object(realtime_writer, "get_db", side_effect=connect), patch.object(
                realtime_writer, "push_event", side_effect=lambda t, d: events.append((t, d))
            ):
                writer = realtime_writer.RealtimeWriter()
                writer.process_data(
                    "5026",
                    world_payload(marker=8, visual_field={"10004": 1}),
                    "final",
                )

            self.assertEqual(len(events), 1)
            self.assertEqual(events[0][0], "world_snapshot_complete")
            self.assertEqual(events[0][1]["server_order_id"], 8)
            self.assertEqual(events[0][1]["counts"]["visual_fields"], 1)

    def test_process_data_uses_world_scene_assembler_gate(self):
        with tempfile.NamedTemporaryFile(suffix=".db") as tmp:
            def connect():
                conn = sqlite3.connect(tmp.name)
                conn.row_factory = sqlite3.Row
                return conn

            with patch.object(realtime_writer, "get_db", side_effect=connect):
                writer = realtime_writer.RealtimeWriter()
                writer.process_data("5026", world_payload(marker=0), "mid")
                writer.process_data("5026", world_payload(marker=8), "final")
                writer.process_data("5028", world_payload(marker=7), "stale")

            conn = connect()
            try:
                row = conn.execute("SELECT COUNT(*) AS c FROM world_scene_packets").fetchone()
                self.assertEqual(row["c"], 1)
                last = conn.execute(
                    "SELECT source, server_order_id FROM world_scene_packets"
                ).fetchone()
                self.assertEqual(last["source"], "mid|final")
                self.assertEqual(last["server_order_id"], 8)
            finally:
                conn.close()


if __name__ == "__main__":
    unittest.main()
