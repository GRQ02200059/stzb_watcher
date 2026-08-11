import unittest

import sim_data


class SimDataTest(unittest.TestCase):
    def test_loads_heroes_from_engine_csv(self):
        heroes = sim_data.load_heroes()
        self.assertGreater(len(heroes), 500)
        by_id = {h["id"]: h for h in heroes}
        # 6 位 hero id 与 Kotlin 引擎口径一致
        self.assertIn(100027, by_id)
        zhangliao = by_id[100027]
        self.assertEqual(zhangliao["name"], "张辽")

    def test_remaps_country_to_frontend_camp_code(self):
        by_id = {h["id"]: h for h in sim_data.load_heroes()}
        # 前端 SIM_CAMP_NAME=['','蜀','魏','吴','汉','群','晋']
        self.assertEqual(by_id[100027]["camp"], 2)  # 张辽 魏
        self.assertEqual(by_id[100016]["camp"], 1)  # 刘备 蜀
        self.assertEqual(by_id[100090]["camp"], 3)  # 太史慈 吴
        self.assertEqual(by_id[100013]["camp"], 5)  # 马超 群

    def test_hero_type_maps_to_army_code(self):
        by_id = {h["id"]: h for h in sim_data.load_heroes()}
        # 前端 SIM_ARMY_NAME=['','弓','步','骑']
        self.assertEqual(by_id[100090]["army"], 1)  # 太史慈 弓
        self.assertEqual(by_id[100016]["army"], 2)  # 刘备 步
        self.assertEqual(by_id[100027]["army"], 3)  # 张辽 骑

    def test_loads_skills_with_normalized_type(self):
        skills = sim_data.load_skills()
        self.assertGreater(len(skills), 200)
        by_id = {s["id"]: s for s in skills}
        # 衣带密诏 200001 引擎 rawType=3(主动) -> 前端 skill_type=2
        self.assertIn(200001, by_id)
        self.assertEqual(by_id[200001]["skill_type"], 2)
        self.assertEqual(by_id[200001]["name"], "衣带密诏")

    def test_skills_only_expose_frontend_types(self):
        # 前端只按 1指挥/2主动/3追击/4被动 分组渲染
        for s in sim_data.load_skills():
            self.assertIn(s["skill_type"], (1, 2, 3, 4))
            self.assertTrue(s["name"])


if __name__ == "__main__":
    unittest.main()
