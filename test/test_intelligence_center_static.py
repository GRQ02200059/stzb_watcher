import json
import re
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def css_rules(css):
    rules = []
    for order, match in enumerate(re.finditer(r"([^{}]+)\{([^{}]*)\}", css)):
        declarations = {}
        for row in match.group(2).split(";"):
            name, separator, value = row.partition(":")
            if separator:
                declarations[name.strip()] = value.strip()
        for selector in match.group(1).split(","):
            normalized = selector.strip()
            if normalized and not normalized.startswith("@"):
                rules.append(
                    {
                        "selector": normalized,
                        "declarations": declarations,
                        "order": order,
                    }
                )
    return rules


def selector_specificity(selector):
    return (
        len(re.findall(r"#[\w-]+", selector)),
        len(re.findall(r"\.[\w-]+|\[[^\]]+\]", selector)),
        0,
    )


def matches_compound_selector(selector, classes):
    if any(token in selector for token in (" ", ">", "+", "~", ":")):
        return False
    required_classes = set(re.findall(r"\.([\w-]+)", selector))
    return required_classes.issubset(classes)


def final_declaration(css, property_name, classes):
    candidates = []
    for rule in css_rules(css):
        value = rule["declarations"].get(property_name)
        if value is None or not matches_compound_selector(
            rule["selector"], classes
        ):
            continue
        important = value.endswith("!important")
        candidates.append(
            (
                important,
                selector_specificity(rule["selector"]),
                rule["order"],
                rule,
            )
        )
    return max(candidates, default=None, key=lambda row: row[:3])


class IntelligenceCenterStaticTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dashboard = (ROOT / "static/dashboard.html").read_text(
            encoding="utf-8"
        )
        cls.center = (ROOT / "static/intelligence-center.js").read_text(
            encoding="utf-8"
        )
        cls.css = (ROOT / "static/intelligence-center.css").read_text(
            encoding="utf-8"
        )

    def test_dashboard_contains_intelligence_center(self):
        html = (ROOT / "static/dashboard.html").read_text(encoding="utf-8")
        self.assertIn("switchTab(33,this)", html)
        self.assertIn("id='tab33'", html)
        self.assertIn('id="intel-map-canvas"', html)
        self.assertIn('id="intel-detail-panel"', html)
        self.assertIn('id="intel-timeline"', html)
        self.assertIn("/static/intelligence-map.mjs", html)
        self.assertIn("/static/intelligence-center.js", html)
        self.assertIn("/static/intelligence-center.css", html)
        for view in ("map", "march", "army", "entity"):
            self.assertIn(f'data-intel-view="{view}"', html)
            self.assertIn(f'id="intel-view-{view}"', html)

    def test_app1_loads_intelligence_center(self):
        app1 = (ROOT / "static/app1.js").read_text(encoding="utf-8")
        self.assertIn("i===33", app1)
        self.assertIn("loadIntelligenceCenter", app1)

    def test_map_supports_navigation_favorites_and_battle_stats(self):
        js = (ROOT / "static/intelligence-center.js").read_text(encoding="utf-8")
        self.assertIn("toggleFavorite", js)
        self.assertIn("'wheel'", js)
        self.assertIn("'pointermove'", js)
        self.assertIn("BATTLE STAT", js)

    def test_map_auto_focuses_real_data_and_supports_wid_location(self):
        html = (ROOT / "static/dashboard.html").read_text(encoding="utf-8")
        js = (ROOT / "static/intelligence-center.js").read_text(encoding="utf-8")
        self.assertIn('id="intel-wid-input"', html)
        self.assertIn("locateWid", js)
        self.assertIn("/api/intelligence/world/summary", js)
        self.assertIn("suggestedBounds", js)

    def test_map_has_global_radar_semantic_zoom_and_navigation_controls(self):
        html = (ROOT / "static/dashboard.html").read_text(encoding="utf-8")
        js = (ROOT / "static/intelligence-center.js").read_text(encoding="utf-8")
        loader_pos = html.index("/static/intelligence-loader.mjs")
        overview_pos = html.index("/static/intelligence-map-overview.mjs")
        navigation_pos = html.index("/static/intelligence-map-navigation.mjs")
        center_pos = html.index("/static/intelligence-center.js")
        self.assertLess(loader_pos, center_pos)
        self.assertLess(overview_pos, center_pos)
        self.assertLess(navigation_pos, center_pos)
        for element_id in (
            "intel-loader-state",
            "intel-radar-canvas",
            "intel-map-mode",
            "intel-map-home",
            "intel-map-back",
            "intel-map-forward",
            "intel-radar-toggle",
        ):
            self.assertIn(f'id="{element_id}"', html)
        for token in (
            "semanticMapMode",
            "createViewportHistory",
            "AbortController",
            "requestAnimationFrame",
            "goHome",
            "goBack",
            "goForward",
            "focusBucket",
            "moveFromRadar",
            "openView",
            "WorldScenePanel",
            "createIntelligenceLoaderCoordinator",
        ):
            self.assertIn(token, js)

    def test_intelligence_loader_hosts_and_abort_lifecycle_are_explicit(self):
        for element_id in (
            "intel-loader-state",
            "intel-scene-march-status",
            "intel-scene-army-status",
            "intel-scene-entity-status",
        ):
            self.assertEqual(self.dashboard.count(f'id="{element_id}"'), 1)
        for token in (
            "AbortController",
            "aggregateLoader?.invalidate",
            "invalidateDetailRequest",
            "visibilitychange",
            "stzb:tab-changed",
            "stzb:stream-event",
        ):
            self.assertIn(token, self.center)
        self.assertNotIn("new EventSource", self.center)

    def test_scene_rows_commit_as_explicit_unversioned_compatibility_data(self):
        self.assertIn("createUnversionedSceneCompatibility", self.center)
        self.assertIn("state.sceneCompatibility[result.activeView]", self.center)
        self.assertIn("result.sceneCompatibility", self.center)

    def test_intelligence_pages_use_shared_hud_shells(self):
        tab33 = self.dashboard.split("id='tab33'", 1)[1].split(
            "id='tab34'", 1
        )[0]
        tab26 = self.dashboard.split("id='tab26'", 1)[1].split(
            "id='tab27'", 1
        )[0]

        for page in (tab33, tab26):
            self.assertIn("hud-page-head", page)
            self.assertIn("hud-toolbar", page)
            self.assertIn("hud-panel", page)
            self.assertIn("hud-status-chip", page)
        self.assertIn("hud-map-shell", tab33)
        self.assertIn("hud-detail-section", self.center)

    def test_intelligence_domain_updates_freshness_and_uses_tokens(self):
        self.assertIn("hud-world-freshness", self.center)
        self.assertIn("hud-region-updated", self.dashboard)
        self.assertIn("dataset.status", self.center)
        for selector in (
            ".hud-map-shell::before",
            ".hud-map-shell::after",
            ".hud-detail-section",
            ".hud-detail-section summary",
        ):
            self.assertIn(selector, self.css)

    def test_high_risk_uses_semantic_hud_event_without_legacy_pulse(self):
        self.assertIn("HudSystem?.emit", self.center)
        self.assertIn("intelligence:risk-detected", self.center)
        self.assertNotIn("stzb:hud-pulse", self.center)
        self.assertIn("dedupeKey: `risk:${wid}:${risk.level}`", self.center)
        self.assertIn("shouldEmitRiskEvent({", self.center)
        self.assertIn("lastEventKey: state.lastEmittedRiskKey", self.center)

    def test_intelligence_surfaces_and_risk_lock_use_shared_tokens(self):
        for token in (
            "var(--surface-panel)",
            "var(--surface-raised)",
            "var(--domain-accent)",
        ):
            self.assertIn(token, self.css)
        self.assertIn(".intel-risk-lock", self.css)
        self.assertIn("intel-risk-lock", self.center)
        self.assertIn("#tab26 .sr-map-panel", self.css)
        self.assertIn("#tab26 .sr-secondary-grid", self.css)

    def test_intelligence_info_card_final_background_is_panel_surface(self):
        winner = final_declaration(
            self.css,
            "background",
            {"intel-info-card", "hud-detail-section"},
        )
        self.assertIsNotNone(winner)
        rule = winner[3]
        self.assertEqual(
            rule["selector"],
            ".intel-info-card.hud-detail-section",
        )
        self.assertEqual(
            rule["declarations"]["background"],
            "var(--surface-panel)",
        )

    def test_intelligence_canvas_has_no_blur_or_filter(self):
        canvas_rules = [
            rule for rule in css_rules(self.css)
            if "#intel-map-canvas" in rule["selector"]
        ]
        self.assertTrue(canvas_rules)
        for rule in canvas_rules:
            self.assertNotIn("filter", rule["declarations"])
            self.assertNotIn("backdrop-filter", rule["declarations"])

    def test_risk_event_boundary_is_pure_and_preserves_navigation_input(self):
        script = r"""
const assert = require("node:assert/strict");
const { shouldEmitRiskEvent } = require(process.argv[1]);
const bounds = { rowUp: 10, rowDown: 20, colLeft: 30, colRight: 40 };
const selection = { selectedWid: 10001, bounds };
const original = JSON.stringify(selection);
const common = {
  selectedWid: selection.selectedWid,
  wid: 10001,
  eventKey: "risk:10001:high",
  lastEventKey: "",
};
assert.equal(shouldEmitRiskEvent({
  ...common,
  risk: { level: "low", score: 20 },
}), false);
assert.equal(shouldEmitRiskEvent({
  ...common,
  risk: { level: "high", score: 90 },
  wid: 10002,
}), false);
assert.equal(shouldEmitRiskEvent({
  ...common,
  risk: { level: "high", score: 90 },
  lastEventKey: common.eventKey,
}), false);
assert.equal(shouldEmitRiskEvent({
  ...common,
  risk: { level: "high", score: 90 },
}), true);
assert.equal(JSON.stringify(selection), original);
process.stdout.write(JSON.stringify({ ok: true }));
"""
        result = subprocess.run(
            [
                "node",
                "-e",
                script,
                str(ROOT / "static/intelligence-center.js"),
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout), {"ok": True})

    def test_detail_actions_use_delegated_safe_indexes_without_inline_handlers(self):
        self.assertNotIn("onclick=", self.center)
        self.assertIn("data-intel-action", self.center)
        self.assertIn("dataset.lineupIndex", self.center)
        self.assertIn("closest('[data-intel-action]')", self.center)

    def test_risk_event_has_business_cooldown_and_releases_resolved_key(self):
        self.assertIn("HudSystem?.resolveEvent", self.center)
        self.assertIn("cooldownMs: 10_000", self.center)
        self.assertIn("state.lastEmittedRiskKey = ''", self.center)

    def test_tile_detail_latest_request_wins_and_has_real_loader_cleanup(self):
        self.assertIn("let detailOwner = null", self.center)
        self.assertIn("function getDetailOwner()", self.center)
        self.assertIn("window.IntelligenceLoader", self.center)
        self.assertIn("createIntelligenceDetailOwner?.()", self.center)
        self.assertIn("getDetailOwner()?.begin", self.center)
        self.assertIn("getDetailOwner()?.isCurrent", self.center)
        self.assertIn("getDetailOwner()?.finish", self.center)
        self.assertIn("getDetailOwner()?.invalidate", self.center)
        self.assertIn("HudSystem?.renderState", self.center)
        select_body = re.search(
            r"async function selectWid\(.*?\) \{(?P<body>.*?)\n  \}",
            self.center,
            re.DOTALL,
        ).group("body")
        self.assertIn("finally", select_body)
        self.assertIn("finishDetailRequest(owner)", select_body)
        aggregate_body = re.search(
            r"async function performMapAggregate\(.*?\) \{(?P<body>.*?)\n  \}",
            self.center,
            re.DOTALL,
        ).group("body")
        self.assertIn("finally", aggregate_body)
        self.assertIn(
            "finishDetailRequest(aggregateDetailOwner)",
            aggregate_body,
        )
        invalidate_body = re.search(
            r"function invalidateDetailRequest\(.*?\) \{(?P<body>.*?)\n  \}",
            self.center,
            re.DOTALL,
        ).group("body")
        self.assertIn("removeAttribute('aria-busy')", invalidate_body)


if __name__ == "__main__":
    unittest.main()
