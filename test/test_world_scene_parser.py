import unittest

from world_scene.parser import WorldSceneAssembler, parse_world_scene_packet


def world_payload(
    *,
    marker=1,
    visual_field=None,
    armies=None,
    chunks=None,
    real_march=None,
    block_deleted=None,
    clear_chunks=None,
    war_ships=None,
    assist_armies=None,
    army_groups=None,
    short_messages=None,
    block_ships=None,
    block_assist_armies=None,
):
    slots = [{} for _ in range(31)]
    slots[0] = visual_field or {}
    slots[1] = {
        "42": [
            "主公",
            10001,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            [1005, 0, "同盟"],
            None,
            None,
            0,
            0,
            0,
            0,
            0,
            0,
            "",
            "",
            0,
            0,
        ]
    }
    slots[3] = {"1005": [1005, 0, "同盟"]}
    slots[6] = armies or {}
    slots[7] = block_deleted or []
    slots[8] = war_ships or {}
    slots[9] = []
    slots[10] = assist_armies or {}
    slots[11] = []
    slots[12] = army_groups or {}
    slots[13] = short_messages or {}
    slots[14] = chunks or {}
    slots[15] = clear_chunks or {}
    slots[16] = {}
    slots[17] = None
    slots[18] = marker
    slots[19] = {}
    slots[20] = None
    slots[21] = {}
    slots[22] = block_ships or {}
    slots[23] = block_assist_armies or {}
    slots[24] = {}
    slots[25] = []
    slots[26] = []
    slots[27] = []
    slots[28] = []
    slots[29] = real_march or {}
    slots[30] = None
    return slots


class WorldSceneParserTest(unittest.TestCase):
    def test_rejects_non_31_slot_payload(self):
        with self.assertRaises(ValueError):
            parse_world_scene_packet(5026, "[1,2,3]", "fixture", 1000)

    def test_parses_army_world_city_and_real_march(self):
        payload = world_payload(
            armies={
                "1001": [
                    1,
                    42,
                    10001,
                    10004,
                    1700000000,
                    1700000030,
                    0,
                    0,
                    0,
                    0,
                    10001,
                    0,
                    0,
                    0,
                    0,
                    "",
                    "1,2,3",
                    "",
                    "",
                    None,
                    None,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    100,
                    9001,
                    "501,502",
                    0,
                    "show",
                    77,
                ]
            },
            chunks={
                "10004": {
                    "0": [
                        1,
                        0,
                        42,
                        1005,
                        0,
                        "facade",
                        "土地名",
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        "build",
                        0,
                        0,
                        0,
                        0,
                        0,
                        88,
                        2,
                    ]
                }
            },
            real_march={
                "9001": [
                    10001,
                    10002,
                    10004,
                    1700000000,
                    1700000010,
                    1700000030,
                    123,
                    5,
                    1,
                    42,
                    0,
                    100,
                    0,
                    0,
                ]
            },
        )
        packet = parse_world_scene_packet(5026, repr(payload), "fixture", 1000)
        self.assertEqual(packet.server_order_id, 1)
        self.assertEqual(packet.armies[1001].army_id, 1001)
        self.assertEqual(packet.armies[1001].state_id, 77)
        self.assertEqual(packet.tiles[10004].state_id, 88)
        self.assertEqual(packet.real_marches[9001].next_wid, 10004)

    def test_preserves_visual_field_and_protocol_breadth_slots(self):
        packet = parse_world_scene_packet(
            5026,
            repr(
                world_payload(
                    visual_field={"10004": 1},
                    war_ships={"3001": [1, 42, 10001]},
                    assist_armies={"4001": [2, 42, 10002]},
                    army_groups={"5001": ["group", 42]},
                    short_messages={"6001": ["msg", 10004]},
                    block_ships={"7001": [3001]},
                    block_assist_armies={"8001": [4001]},
                )
            ),
            "fixture",
            1000,
        )
        self.assertEqual(packet.visual_field_raw, {"10004": 1})
        self.assertIn(3001, packet.entities["war_ship"])
        self.assertIn(4001, packet.entities["assist_army"])
        self.assertIn(5001, packet.entities["army_group"])
        self.assertIn(6001, packet.entities["short_message"])
        self.assertIn(7001, packet.entities["block_ship"])
        self.assertIn(8001, packet.entities["block_assist_army"])

    def test_assembler_waits_for_final_5026_frame(self):
        assembler = WorldSceneAssembler()
        mid = parse_world_scene_packet(5026, repr(world_payload(marker=0)), "mid", 1)
        final = parse_world_scene_packet(
            5026, repr(world_payload(marker=8)), "final", 2
        )
        self.assertFalse(assembler.apply(mid).snapshot_complete)
        self.assertTrue(assembler.apply(final).snapshot_complete)
        self.assertEqual(assembler.last_completed_server_order_id, 8)

    def test_assembler_rejects_stale_5028_and_allows_special_bypass(self):
        assembler = WorldSceneAssembler()
        final = parse_world_scene_packet(
            5026, repr(world_payload(marker=8)), "final", 1
        )
        assembler.apply(final)

        stale = parse_world_scene_packet(
            5028, repr(world_payload(marker=7)), "stale", 2
        )
        stale_result = assembler.apply(stale)
        self.assertFalse(stale_result.accepted)
        self.assertEqual(stale_result.reason, "STALE_5028")

        bypass = parse_world_scene_packet(
            5028, repr(world_payload(marker=-999999999)), "bypass", 3
        )
        self.assertTrue(assembler.apply(bypass).accepted)


if __name__ == "__main__":
    unittest.main()
