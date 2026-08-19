import unittest

import realtime_writer


class TeamUserProtocolFieldsTest(unittest.TestCase):
    def test_command_67_uses_client_confirmed_member_fields(self):
        row = [0] * 31
        row[0] = 42
        row[1] = "玩家甲"
        row[10] = 1234
        row[13] = "一团"
        row[16] = 88
        row[17] = "frame-a"
        row[26] = 567
        row[27] = 8901
        row[30] = 1700000000

        user = realtime_writer.parse_team_users_67("unused", data=[row])[0]

        self.assertEqual(user["wuxun"], 1234)
        self.assertEqual(user["head_id"], 88)
        self.assertEqual(user["head_frame"], "frame-a")
        self.assertEqual(user["week_wuxun"], 567)
        self.assertEqual(user["total_wuxun"], 8901)
        self.assertNotIn("hero_config_id", user)
        self.assertNotIn("hero_skills", user)


if __name__ == "__main__":
    unittest.main()
