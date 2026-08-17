import unittest
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def function_body(source, name):
    match = re.search(
        rf"function {re.escape(name)}\([^)]*\) \{{(?P<body>.*?)\n  \}}",
        source,
        re.DOTALL,
    )
    if not match:
        raise AssertionError(f"missing function: {name}")
    return match.group("body")


class CommandCenterStaticTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html = (ROOT / "static/dashboard.html").read_text(encoding="utf-8")
        cls.app1 = (ROOT / "static/app1.js").read_text(encoding="utf-8")
        command_path = ROOT / "static/dashboard-command-center.js"
        cls.command = (
            command_path.read_text(encoding="utf-8") if command_path.exists() else ""
        )
        cls.css = (ROOT / "static/dashboard-design-system.css").read_text(
            encoding="utf-8"
        )

    def test_dashboard_exposes_overview_settings_and_command_palette(self):
        self.assertIn("id='tab31'", self.html)
        self.assertIn("id='tab32'", self.html)
        self.assertIn("id='tab18'", self.html)
        self.assertIn("id='ul-body'", self.html)
        self.assertIn("id='upr-body'", self.html)
        self.assertIn('id="cc-command-dialog"', self.html)
        self.assertIn('id="cc-kpi-grid"', self.html)
        self.assertIn('id="cc-alert-list"', self.html)
        self.assertIn('id="cc-timeline-list"', self.html)
        self.assertIn('id="cc-favorites-list"', self.html)
        self.assertIn("/static/dashboard-command-center.js", self.html)

    def test_app_keeps_command_center_loaders_and_defaults_to_intelligence(self):
        self.assertIn("i===31", self.app1)
        self.assertIn("loadCommandCenterOverview", self.app1)
        self.assertIn("i===32", self.app1)
        self.assertIn("loadCommandCenterSettings", self.app1)
        self.assertIn("switchTab(33", self.app1)

    def test_settings_have_persisted_interaction_controls(self):
        for control_id in (
            "cc-setting-refresh",
            "cc-setting-density",
            "cc-setting-motion",
            "cc-setting-sound",
            "cc-setting-intel",
            "cc-settings-reset",
        ):
            self.assertIn(f'id="{control_id}"', self.html)

    def test_command_center_script_contract(self):
        for token in (
            "window.CommandCenter",
            "loadCommandCenterOverview",
            "loadCommandCenterSettings",
            "localStorage",
            "keydown",
            "ctrlKey",
            "metaKey",
            "bufferedEvents",
            "toggleTimelinePause",
            "addFavorite",
            "convergence",
            "arrival",
            "try {",
        ):
            self.assertIn(token, self.command)
        self.assertIn("readStoredHome", self.app1)
        self.assertIn("catch(_)", self.app1)
        self.assertIn("stzb:stream-event", self.app1)
        self.assertIn("stzb:stream-event", self.command)
        self.assertNotIn("new EventSource", self.command)

    def test_command_center_visual_contract(self):
        for selector in (
            ".cc-overview-grid",
            ".cc-kpi-grid",
            ".cc-command-dialog",
            ".cc-timeline",
            ".cc-alert",
            ".cc-settings-grid",
            ".cc-favorite",
            "@media (max-width: 1280px)",
            "@media (max-width: 768px)",
            "@media (prefers-reduced-motion: reduce)",
        ):
            self.assertIn(selector, self.css)

    def test_system_domain_events_use_semantic_hud_contract(self):
        self.assertIn("connection:restored", self.app1)
        self.assertIn("data:stale", self.command)
        self.assertIn("HudSystem?.emit", self.command)
        self.assertIn('data-visual-domain="system"', self.html)
        self.assertNotIn("stzb:hud-pulse", self.command)

    def test_command_center_numbers_use_the_shared_hud_animation_runtime(self):
        animate_number = function_body(self.command, "animateNumber")
        self.assertIn("window.HudSystem?.animateValue", animate_number)
        self.assertNotIn("requestAnimationFrame", animate_number)
        self.assertNotIn("performance.now", animate_number)

    def test_battle_event_targets_the_new_timeline_row_without_selector_injection(self):
        push_timeline = function_body(self.command, "pushTimeline")
        render_timeline = function_body(self.command, "renderTimeline")

        self.assertIn("renderTimeline(item.id)", push_timeline)
        self.assertIn("const eventTarget = renderTimeline(item.id)", push_timeline)
        self.assertIn("target: eventTarget", push_timeline)
        self.assertNotIn('target: "#cc-timeline-list"', push_timeline)
        self.assertIn("data-event-id", render_timeline)
        self.assertNotIn("querySelector", push_timeline)
        self.assertNotIn("querySelector", render_timeline)
        self.assertIn("stableBattleKey", push_timeline)
        self.assertNotIn("dedupeKey: `battle:${item.id}`", push_timeline)

    def test_dynamic_alerts_and_favorites_use_dom_properties_and_safe_indexes(self):
        render_alerts = function_body(self.command, "renderAlerts")
        render_favorites = function_body(self.command, "renderFavorites")
        load_overview = function_body(
            self.command,
            "loadCommandCenterOverview",
        )

        self.assertIn("createElement", render_alerts)
        self.assertIn("dataset.alertIndex", render_alerts)
        self.assertIn("ALERT_LEVELS.has", render_alerts)
        self.assertNotIn("innerHTML", render_alerts)
        self.assertIn("createElement", render_favorites)
        self.assertIn("dataset.favoriteIndex", render_favorites)
        self.assertIn("dataset.removeFavorite", render_favorites)
        self.assertNotIn("innerHTML", render_favorites)
        self.assertNotIn("onclick=", load_overview)

    def test_system_surfaces_are_explicit_in_static_markup(self):
        self.assertRegex(
            self.html,
            r'id="hud-health-grid"[^>]*class="[^"]*hud-surface-panel[^"]*"',
        )
        self.assertRegex(
            self.html,
            r'class="[^"]*cc-command-shell[^"]*hud-surface-modal[^"]*"',
        )

    def test_stale_events_only_follow_explicit_backend_truth(self):
        self.assertIn("reportOverviewStaleness", self.command)
        self.assertIn("reportHealthStaleness", self.command)
        self.assertRegex(self.command, r"\w+\.kind === 'stale_data'")
        self.assertIn("component.status === 'stale'", self.command)
        self.assertEqual(self.command.count("window.markStreamStale?.()"), 2)
        self.assertNotIn("optionalTables", self.command)
        self.assertNotIn("missingTables", self.command)

    def test_setting_feedback_is_only_called_after_user_persistence(self):
        self.assertIn('title: "设置已保存"', self.command)
        self.assertIn('source: "设置中心"', self.command)
        self.assertIn("dedupeKey: `setting:${key}`", self.command)
        self.assertIn("notifySettingSaved(config[0]", self.command)
        self.assertNotIn(
            "notifySettingSaved",
            function_body(self.command, "loadCommandCenterSettings"),
        )


if __name__ == "__main__":
    unittest.main()
