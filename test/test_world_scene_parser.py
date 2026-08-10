import unittest

from world_scene.parser import WorldSceneAssembler, parse_world_scene_packet


def world_payload(*, marker=1, armies=None, chunks=None, real_march=None):
    slots = [{} for _ in range(31)]
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
    slots[7] = []
    slots[8] = {}
    slots[9] = []
    slots[10] = {}
    slots[11] = []
    slots[12] = {}
    slots[13] = {}
    slots[14] = chunks or {}
    slots[15] = {}
    slots[16] = {}
    slots[17] = None
    slots[18] = marker
    slots[19] = {}
    slots[20] = None
    slots[21] = {}
    slots[22] = {}
    slots[23] = {}
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

    def test_assembler_waits_for_final_5026_frame(self):
        assembler = WorldSceneAssembler()
        mid = parse_world_scene_packet(5026, repr(world_payload(marker=0)), "mid", 1)
        final = parse_world_scene_packet(
            5026, repr(world_payload(marker=8)), "final", 2
        )
        self.assertFalse(assembler.apply(mid).snapshot_complete)
        self.assertTrue(assembler.apply(final).snapshot_complete)
        self.assertEqual(assembler.last_completed_server_order_id, 8)


if __name__ == "__main__":
    unittest.main()
