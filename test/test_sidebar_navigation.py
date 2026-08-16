import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VISIBLE_LABELS = [
    "玩家队伍",
    "自定义积分",
    "打城考勤",
    "同盟成员队伍",
    "武将阵容",
    "团数据",
    "战斗模拟",
    "州郡分布",
    "设置中心",
    "战场情报",
    "实时部队",
    "阵容战法研究",
]
REMOVED_FLAT_NAV_LABELS = {
    "实时战报",
    "全部战报",
    "队伍统计",
    "城池地图",
    "同盟成员",
    "13A2 队伍索引",
    "队伍索引",
    "战区玩家",
    "战场消息",
    "战场监控",
    "打城排表",
    "数据同步",
    "玩家战绩",
    "分组武勋",
}
REMOVED_LABELS = {
    "战场总览",
    "排行榜",
    "排行中心",
    "武勋统计",
    "势力值",
    "战场分析",
    "同盟势力",
    "游戏公告",
    "古代中国地图",
}


class SidebarNavigationTest(unittest.TestCase):
    def setUp(self):
        self.html = (ROOT / "static/dashboard.html").read_text(encoding="utf-8")
        self.app1 = (ROOT / "static/app1.js").read_text(encoding="utf-8")
        self.command_center = (
            ROOT / "static/dashboard-command-center.js"
        ).read_text(encoding="utf-8")
        self.design_system = (
            ROOT / "static/dashboard-design-system.js"
        ).read_text(encoding="utf-8")
        self.nav = re.search(
            r"<nav aria-label=\"主导航\">(.*?)</nav>",
            self.html,
            re.S,
        ).group(1)

    def test_removed_features_are_not_sidebar_buttons(self):
        for label in REMOVED_LABELS:
            self.assertNotIn(f">{label}</button>", self.nav)
        self.assertNotIn(">世界场景</button>", self.nav)
        self.assertNotIn(">战场·总览</button>", self.nav)
        for label in REMOVED_FLAT_NAV_LABELS:
            self.assertNotIn(f">{label}</button>", self.nav)

    def test_visible_sidebar_is_flat_and_exactly_ordered(self):
        visible = re.findall(
            r"<button(?![^>]*display:none)[^>]*>([^<]+)</button>",
            self.nav,
        )
        self.assertEqual(visible, VISIBLE_LABELS)
        self.assertNotIn("ds-nav-more", self.design_system)
        self.assertNotIn("更多功能", self.design_system)
        self.assertNotIn("核心工作台", self.design_system)
        self.assertNotIn("扩展工具", self.design_system)
        self.assertNotIn("PRIMARY_TABS", self.design_system)
        self.assertNotIn("ds-show-more", self.design_system)
        self.assertNotIn("data.dsSecondary", self.design_system)

    def test_city_siege_attendance_is_restored(self):
        self.assertIn(
            "<button onclick='switchTab(16,this)'>打城考勤</button>",
            self.nav,
        )
        self.assertIn(
            "{label: '打城考勤'",
            self.command_center,
        )

    def test_intelligence_is_the_default_active_page(self):
        self.assertIn(
            "<button class='active' onclick='switchTab(33,this)'>战场情报</button>",
            self.nav,
        )
        self.assertNotIn("<div class='page active' id='tab31'>", self.html)
        self.assertIn(
            "<div class='page hud-page active' id='tab33' "
            "data-visual-domain='intelligence'>",
            self.html,
        )
        self.assertIn("return 33;", self.app1)
        self.assertIn("VISIBLE_HOME_TABS", self.app1)
        self.assertIn("VISIBLE_HOME_TABS.has(home)?home:33", self.app1)

    def test_removed_features_are_not_auxiliary_navigation(self):
        for label in (
            "战场总览",
            "排行榜",
            "同盟势力",
            "战场分析",
        ):
            self.assertNotIn(f"label: '{label}'", self.command_center)
        self.assertNotIn(
            '<option value="31">战场总览</option>',
            self.html,
        )
        self.assertNotIn(
            '<option value="30">世界场景</option>',
            self.html,
        )
        self.assertNotIn('id="cc-setting-home"', self.html)
        self.assertIn("home: 33", self.command_center)
        for label in (
            "实时战报",
            "全部战报",
            "队伍统计",
            "城池地图",
            "同盟成员",
        ):
            self.assertNotIn(f"label: '{label}'", self.command_center)
        for label in (
            "战场监控",
            "战场消息",
            "分组武勋",
        ):
            self.assertNotIn(f"label: '{label}'", self.command_center)

    def test_removed_pages_are_kept_for_compatibility(self):
        for tab_id in (1, 2, 3, 4, 6, 16, 18, 20, 29, 31):
            self.assertIn(f"id='tab{tab_id}'", self.html)


if __name__ == "__main__":
    unittest.main()
