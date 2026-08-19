import sqlite3
import unittest

from test.test_world_scene_parser import world_city, world_payload
from world_scene.parser import parse_world_scene_packet
from world_scene.state_store import WorldStateStore


def army_tuple(user_id=42, wid_from=10001, wid_to=10004, state_id=1):
    return [
        1, user_id, wid_from, wid_to, 1, 9, 0, 0, 0, 0, wid_from,
        0, 0, 0, 0, "", "1,2,3", "", "", None, None,
        0, 0, 0, 0, 0, 0, 100, 0, "", 0, "", state_id,
    ]


class WorldStateStoreTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.row_factory = sqlite3.Row
        self.store = WorldStateStore(self.conn)
        self.store.ensure_schema()

    def packet(self, cmd, payload, source="fixture", observed=1000):
        return parse_world_scene_packet(cmd, repr(payload), source, observed)

    def test_baseline_versions_state_and_clears_only_observed_area(self):
        first = world_payload(
            marker=10,
            chunks={
                "10001": {"0": world_city("范围内旧格")},
                "30001": {"0": world_city("范围外")},
            },
        )
        first[17] = [1, 1, 1, 1]
        self.store.apply_baseline(self.packet(5026, first))

        second = world_payload(
            marker=20,
            chunks={"10002": {"0": world_city("范围内新格")}},
        )
        second[17] = [1, 1, 1, 2]
        result = self.store.apply_baseline(self.packet(5026, second, observed=2000))

        rows = self.conn.execute(
            "SELECT wid FROM world_tiles ORDER BY wid"
        ).fetchall()
        self.assertEqual([row["wid"] for row in rows], [10002, 30001])
        self.assertEqual(result.state_version, 2)
        current = self.store.current_version()
        self.assertEqual(current["version"], 2)
        self.assertEqual(current["latest_baseline_order_id"], 20)
        self.assertEqual(current["completeness"], "full-baseline")

    def test_baseline_without_area_is_upsert_only(self):
        self.store.apply_baseline(
            self.packet(
                5026,
                world_payload(
                    marker=10,
                    chunks={"10001": {"0": world_city("旧格")}},
                ),
            )
        )
        result = self.store.apply_baseline(
            self.packet(
                5026,
                world_payload(
                    marker=20,
                    chunks={"10002": {"0": world_city("新格")}},
                ),
            )
        )
        rows = self.conn.execute(
            "SELECT wid FROM world_tiles ORDER BY wid"
        ).fetchall()
        self.assertEqual([row["wid"] for row in rows], [10001, 10002])
        self.assertEqual(result.completeness, "partial-baseline")

    def test_block_unlink_deletes_only_after_last_membership(self):
        baseline = world_payload(
            marker=10,
            armies={"100": army_tuple()},
            block_armies={"1": [100], "2": [100]},
        )
        self.store.apply_baseline(self.packet(5026, baseline))

        unlink_one = world_payload(marker=11, block_deleted=[100])
        unlink_one[20] = [2, 1]
        self.store.apply_delta(self.packet(5028, unlink_one, observed=2000))
        army = self.conn.execute(
            "SELECT deleted_at_seq FROM world_armies WHERE army_id=100"
        ).fetchone()
        self.assertIsNone(army["deleted_at_seq"])

        unlink_two = world_payload(marker=12, block_deleted=[100])
        unlink_two[20] = [2, 2]
        self.store.apply_delta(self.packet(5028, unlink_two, observed=3000))
        army = self.conn.execute(
            "SELECT deleted_at_seq FROM world_armies WHERE army_id=100"
        ).fetchone()
        self.assertIsNotNone(army["deleted_at_seq"])

    def test_delta_adds_army_to_current_block(self):
        baseline = world_payload(marker=10, block_armies={"1": []})
        self.store.apply_baseline(self.packet(5026, baseline))
        delta = world_payload(marker=11, armies={"100": army_tuple()})
        delta[20] = [2, 1]
        self.store.apply_delta(self.packet(5028, delta, observed=2000))
        row = self.conn.execute(
            "SELECT block_id FROM world_army_blocks WHERE army_id=100"
        ).fetchone()
        self.assertEqual(row["block_id"], 1)

    def test_baseline_replaces_all_block_membership_types(self):
        first = world_payload(
            marker=10,
            armies={"100": army_tuple()},
            war_ships={"800": [1, 42]},
            assist_armies={"900": [1, 42]},
            block_armies={"1": [100]},
            block_ships={"1": [800]},
            block_assist_armies={"1": [900]},
        )
        self.store.apply_baseline(self.packet(5026, first))
        second = world_payload(
            marker=20,
            block_armies={"2": []},
            block_ships={"2": []},
            block_assist_armies={"2": []},
        )
        self.store.apply_baseline(self.packet(5026, second, observed=2000))

        for table in (
            "world_army_blocks",
            "world_ship_blocks",
            "world_assist_army_blocks",
        ):
            self.assertEqual(
                self.conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0],
                0,
            )

    def test_ship_and_assist_unlink_delete_after_last_membership(self):
        baseline = world_payload(
            marker=10,
            war_ships={"800": [1, 42]},
            assist_armies={"900": [1, 42]},
            block_ships={"1": [800], "2": [800]},
            block_assist_armies={"1": [900], "2": [900]},
        )
        self.store.apply_baseline(self.packet(5026, baseline))

        unlink_one = world_payload(
            marker=11,
            ship_deleted=[800],
            assist_deleted=[900],
        )
        unlink_one[20] = [2, 1]
        self.store.apply_delta(self.packet(5028, unlink_one, observed=2000))
        for category, entity_id in (("war_ship", 800), ("assist_army", 900)):
            row = self.conn.execute(
                """
                SELECT deleted_at_seq FROM world_scene_entities
                WHERE category=? AND entity_id=?
                """,
                (category, entity_id),
            ).fetchone()
            self.assertIsNone(row["deleted_at_seq"])

        unlink_two = world_payload(
            marker=12,
            ship_deleted=[800],
            assist_deleted=[900],
        )
        unlink_two[20] = [2, 2]
        self.store.apply_delta(self.packet(5028, unlink_two, observed=3000))
        for category, entity_id in (("war_ship", 800), ("assist_army", 900)):
            row = self.conn.execute(
                """
                SELECT deleted_at_seq FROM world_scene_entities
                WHERE category=? AND entity_id=?
                """,
                (category, entity_id),
            ).fetchone()
            self.assertIsNotNone(row["deleted_at_seq"])

    def test_baseline_replaces_real_marches_and_delta_overlays(self):
        march1 = [1, 2, 3, 4, 5, 6, 7, 8, 9, 1, 42, 100, 0, 0]
        march2 = [2, 3, 4, 5, 6, 7, 8, 9, 10, 1, 42, 100, 0, 0]
        self.store.apply_baseline(
            self.packet(
                5026,
                world_payload(marker=10, real_march={"1": march1, "2": march2}),
            )
        )
        self.store.apply_baseline(
            self.packet(
                5026,
                world_payload(marker=20, real_march={"2": march2}),
            )
        )
        ids = self.conn.execute(
            "SELECT real_march_id FROM world_real_marches ORDER BY real_march_id"
        ).fetchall()
        self.assertEqual([row["real_march_id"] for row in ids], [2])

        self.store.apply_delta(
            self.packet(
                5028,
                world_payload(marker=21, real_march={"1": march1}),
                observed=3000,
            )
        )
        ids = self.conn.execute(
            "SELECT real_march_id FROM world_real_marches ORDER BY real_march_id"
        ).fetchall()
        self.assertEqual([row["real_march_id"] for row in ids], [1, 2])

    def test_tile_chunks_upsert_and_clear_by_subtype(self):
        baseline = world_payload(
            marker=10,
            chunks={
                "10004": {
                    "0": world_city("资源地"),
                    "4": ["custom", "position"],
                    "8": 73,
                }
            },
        )
        self.store.apply_baseline(self.packet(5026, baseline))
        rows = self.conn.execute(
            """
            SELECT chunk_type, raw_json FROM world_tile_chunks
            WHERE wid=10004 ORDER BY chunk_type
            """
        ).fetchall()
        self.assertEqual([row["chunk_type"] for row in rows], ["0", "4", "8"])

        delta = world_payload(marker=11, clear_chunks={"10004": ["4"]})
        self.store.apply_delta(self.packet(5028, delta, observed=2000))
        rows = self.conn.execute(
            "SELECT chunk_type FROM world_tile_chunks WHERE wid=10004 ORDER BY chunk_type"
        ).fetchall()
        self.assertEqual([row["chunk_type"] for row in rows], ["0", "8"])

    def test_delta_clears_strategy_hunter_and_career_support_entities(self):
        baseline = world_payload(
            marker=10,
            strategies={"101": [1]},
            short_messages={"107": ["hunter"]},
            career_support_add={"106": [1]},
        )
        self.store.apply_baseline(self.packet(5026, baseline))

        delta = world_payload(
            marker=11,
            career_support_remove=[106],
            clear_hunter=[107],
            clear_strategy=[101],
        )
        result = self.store.apply_delta(self.packet(5028, delta, observed=2000))

        rows = self.conn.execute(
            """
            SELECT category,entity_id,deleted_at_seq
            FROM world_scene_entities
            WHERE category IN ('strategy','short_message','career_support')
            ORDER BY category
            """
        ).fetchall()
        self.assertEqual(3, len(rows))
        self.assertTrue(all(row["deleted_at_seq"] is not None for row in rows))
        events = self.conn.execute(
            "SELECT event_type,entity_type,entity_id FROM world_state_events "
            "WHERE state_version=? ORDER BY seq",
            (result.state_version,),
        ).fetchall()
        self.assertIn(("entity_deleted", "strategy", "101"), [tuple(row) for row in events])
        self.assertIn(("entity_deleted", "career_support", "106"), [tuple(row) for row in events])


if __name__ == "__main__":
    unittest.main()
