import sqlite3
import unittest

from intelligence.world_service import WorldIntelligenceService, freshness
from test.test_world_scene_parser import world_city, world_payload
from world_scene.parser import parse_world_scene_packet
from world_scene.state_store import WorldStateStore


def army_tuple(wid_to=10004, end_time=1060):
    return [
        1, 42, 10001, wid_to, 900, end_time, 0, 0, 0, 0, 10001,
        0, 0, 0, 0, "", "1,2,3", "", "", None, None,
        0, 0, 0, 0, 0, 0, 100, 0, "", 0, "", 1,
    ]


class WorldIntelligenceServiceTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.row_factory = sqlite3.Row
        store = WorldStateStore(self.conn)
        store.ensure_schema()
        self.conn.execute(
            """
            CREATE TABLE player_self(
                id INTEGER PRIMARY KEY,
                name TEXT,
                union_name TEXT
            )
            """
        )
        self.conn.execute(
            "INSERT INTO player_self(id,name,union_name) VALUES(1,'主公','同盟')"
        )
        self.conn.executescript(
            """
            CREATE TABLE battles_v2(
                battle_id INTEGER PRIMARY KEY,
                time INTEGER,
                result INTEGER,
                wid INTEGER,
                atk_name TEXT,
                def_name TEXT
            );
            CREATE TABLE battle_heroes(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                battle_id INTEGER,
                side TEXT,
                pos INTEGER,
                hero_id INTEGER,
                hero_name TEXT,
                level INTEGER
            );
            INSERT INTO battles_v2 VALUES
                (1,1000,1,10004,'甲','乙'),
                (2,2000,2,10004,'丙','丁'),
                (3,3000,0,10004,'甲','丁');
            INSERT INTO battle_heroes(
                battle_id,side,pos,hero_id,hero_name,level
            ) VALUES
                (1,'atk',0,101,'武将甲',40),
                (1,'atk',1,102,'武将乙',40),
                (1,'atk',2,103,'武将丙',40),
                (2,'atk',0,101,'武将甲',40),
                (2,'atk',1,102,'武将乙',40),
                (2,'atk',2,103,'武将丙',40);
            """
        )
        payload = world_payload(
            marker=10,
            armies={"100": army_tuple(), "101": army_tuple(end_time=1120)},
            chunks={
                "10004": {
                    "0": world_city("七级资源地"),
                    "8": 73,
                }
            },
            block_armies={"1": [100, 101]},
        )
        payload[17] = [1, 1, 4, 4]
        store.apply_baseline(
            parse_world_scene_packet(5026, repr(payload), "fixture", 1_000_000)
        )
        self.service = WorldIntelligenceService(
            self.conn,
            now_ms=lambda: 1_060_000,
        )

    def test_freshness_boundaries(self):
        self.assertEqual(freshness(0, 1_000_000), "unknown")
        self.assertEqual(freshness(881_001, 1_000_000), "fresh")
        self.assertEqual(freshness(880_000, 1_000_000), "aging")
        self.assertEqual(freshness(401_000, 1_000_000), "aging")
        self.assertEqual(freshness(400_000, 1_000_000), "stale")

    def test_tile_detail_decodes_level_and_incoming_armies(self):
        detail = self.service.tile_detail(10004)
        self.assertEqual(detail["tile"]["landLevel"], 7)
        self.assertEqual(detail["tile"]["resourceKind"], 3)
        self.assertEqual(len(detail["incomingArmies"]), 2)
        self.assertEqual(detail["freshness"], "fresh")
        self.assertEqual(detail["worldStateVersion"], 1)
        self.assertEqual(detail["battleStats"]["evidenceClass"], "BATTLE_STAT")
        self.assertEqual(detail["battleStats"]["sampleSize"], 3)
        self.assertEqual(detail["battleStats"]["attackWinRate"], 50.0)
        self.assertEqual(detail["battleStats"]["recentBattles"][0]["battle_id"], 3)
        self.assertEqual(
            detail["battleStats"]["commonLineups"][0]["key"],
            "101.102.103",
        )

    def test_summary_exposes_real_tile_bounds_and_focus_window(self):
        summary = self.service.summary()
        self.assertEqual(
            summary["dataBounds"],
            {"rowUp": 1, "rowDown": 1, "colLeft": 4, "colRight": 4},
        )
        self.assertEqual(summary["focusWid"], 10004)
        self.assertLessEqual(
            summary["suggestedBounds"]["rowDown"]
            - summary["suggestedBounds"]["rowUp"]
            + 1,
            40,
        )
        self.assertTrue(
            summary["suggestedBounds"]["rowUp"]
            <= 1
            <= summary["suggestedBounds"]["rowDown"]
        )
        self.assertTrue(
            summary["suggestedBounds"]["colLeft"]
            <= 4
            <= summary["suggestedBounds"]["colRight"]
        )

    def test_overview_aggregates_non_empty_buckets(self):
        self.conn.execute(
            """
            INSERT INTO world_map_users(
                user_id,name,role_id,union_id,union_name,raw_json,source_seq
            ) VALUES(99,'敌将',9999,2000,'敌盟','[]',2)
            """
        )
        for wid, row, col, user_id, union_id, source_seq in (
            (10005, 1, 5, 99, 2000, 2),
            (20004, 2, 4, 0, 0, 3),
            (40004, 4, 4, 42, 1005, 4),
        ):
            self.conn.execute(
                """
                INSERT INTO world_tiles(
                    wid,row,col,city_type,city_param,user_id,union_id,
                    protect_end_time,name,belong_city,world_city_state,
                    guard_end_time,force,state_id,view_range_add,
                    raw_world_city,source_seq
                )
                SELECT ?,?,?,city_type,city_param,?,?,protect_end_time,
                       name,belong_city,world_city_state,guard_end_time,
                       force,state_id,view_range_add,raw_world_city,?
                FROM world_tiles WHERE wid=10004
                """,
                (wid, row, col, user_id, union_id, source_seq),
            )
            self.conn.execute(
                """
                INSERT INTO world_tile_chunks(
                    wid,chunk_type,raw_json,source_seq,observed_at_ms
                )
                SELECT ?,chunk_type,raw_json,?,observed_at_ms
                FROM world_tile_chunks WHERE wid=10004
                """,
                (wid, source_seq),
            )
        self.conn.execute(
            """
            INSERT INTO world_armies(
                army_id,state,user_id,wid_from,wid_to,begin_time,end_time,
                target_type,reside_wid,stay_wid,army_hero_type,morale,
                real_march_id,buff_ids,obstacle_wid,battle_show,state_id,
                raw_json,source_seq,deleted_at_seq
            )
            SELECT 102,state,99,wid_from,10005,begin_time,end_time,target_type,
                   reside_wid,stay_wid,army_hero_type,morale,real_march_id,
                   buff_ids,obstacle_wid,battle_show,state_id,raw_json,2,NULL
            FROM world_armies WHERE army_id=100
            """
        )
        self.conn.execute(
            """
            INSERT INTO world_state_events(
                state_version,packet_seq,event_type,entity_type,entity_id,
                observed_at_ms,evidence_json,diff_json
            ) VALUES(1,1,'tile_owner_changed','tile','10005',1000000,'{}','{}')
            """
        )

        result = self.service.overview(1, 4, 4, 5, 2, 2)

        self.assertEqual(result["bucketRows"], 2)
        self.assertEqual(result["bucketCols"], 2)
        self.assertEqual(len(result["buckets"]), 2)
        first = result["buckets"][0]
        self.assertEqual(
            (first["rowUp"], first["rowDown"], first["colLeft"], first["colRight"]),
            (1, 2, 4, 5),
        )
        self.assertEqual(first["tileCount"], 3)
        self.assertEqual(first["selfCount"], 1)
        self.assertEqual(first["enemyCount"], 1)
        self.assertEqual(first["unownedCount"], 1)
        self.assertEqual(first["armyCount"], 3)
        self.assertEqual(first["changeCount"], 1)
        self.assertEqual(first["focusWid"], 10005)
        self.assertGreaterEqual(first["riskMax"], first["riskAverage"])
        self.assertEqual(result["buckets"][1]["tileCount"], 1)

    def test_overview_rejects_invalid_or_excessive_bucket_grids(self):
        with self.assertRaises(ValueError):
            self.service.overview(1, 10, 1, 10, 0, 2)
        with self.assertRaises(ValueError):
            self.service.overview(10, 1, 1, 10, 2, 2)
        with self.assertRaises(ValueError):
            self.service.overview(0, 999, 0, 999, 10, 10)

    def test_risk_is_explainable(self):
        risk = self.service.risk_for_tile(10004)
        self.assertGreater(risk["score"], 0)
        self.assertEqual(risk["ownership"]["relation"], "self")
        self.assertEqual(risk["components"]["enemyOwnership"], 0)
        self.assertEqual(risk["components"]["incomingArmyCount"], 0)
        self.assertEqual(
            set(risk["components"]),
            {
                "landLevel",
                "enemyOwnership",
                "incomingArmyCount",
                "earliestArrival",
                "estimatedTroops",
                "protectionGuard",
                "staleIntel",
            },
        )
        self.assertIn("estimatedTroops", risk["unknownComponents"])
        self.assertLess(risk["confidence"], 1.0)

    def test_enemy_owner_and_armies_increase_risk(self):
        self.conn.execute(
            """
            INSERT INTO world_map_users(
                user_id,name,role_id,union_id,union_name,raw_json,source_seq
            ) VALUES(99,'敌将',9999,2000,'敌盟','[]',1)
            """
        )
        self.conn.execute(
            "UPDATE world_tiles SET user_id=99,union_id=2000 WHERE wid=10004"
        )
        self.conn.execute(
            "UPDATE world_armies SET user_id=99 WHERE wid_to=10004"
        )
        risk = self.service.risk_for_tile(10004)
        self.assertEqual(risk["ownership"]["relation"], "enemy")
        self.assertEqual(risk["components"]["enemyOwnership"], 15)
        self.assertEqual(risk["components"]["incomingArmyCount"], 14)

    def test_unknown_identity_does_not_assume_enemy(self):
        self.conn.execute("DELETE FROM player_self")
        risk = self.service.risk_for_tile(10004)
        self.assertEqual(risk["ownership"]["relation"], "unknown")
        self.assertEqual(risk["components"]["enemyOwnership"], 0)
        self.assertIn("enemyOwnership", risk["unknownComponents"])


if __name__ == "__main__":
    unittest.main()
