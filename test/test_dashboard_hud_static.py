import re
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "static/dashboard.html"
CSS = ROOT / "static/dashboard-design-system.css"
DS_JS = ROOT / "static/dashboard-design-system.js"
HUD_JS = ROOT / "static/dashboard-hud.mjs"

VISIBLE_DOMAINS = {
    7: "organization",
    8: "analysis",
    16: "operations",
    17: "organization",
    23: "analysis",
    24: "organization",
    25: "operations",
    26: "intelligence",
    32: "system",
    33: "intelligence",
    34: "analysis",
    35: "intelligence",
}


def function_body(source, name):
    match = re.search(
        rf"function {re.escape(name)}\([^)]*\) \{{(?P<body>.*?)\n  \}}",
        source,
        re.DOTALL,
    )
    if not match:
        raise AssertionError(f"missing function: {name}")
    return match.group("body")


def css_rule(css, selector):
    match = re.search(
        rf"{re.escape(selector)}\s*\{{(?P<body>[^}}]*)\}}",
        css,
    )
    if not match:
        raise AssertionError(f"missing CSS rule: {selector}")
    return match.group("body")


class DashboardHudStaticTest(unittest.TestCase):
    def test_visible_pages_have_the_approved_domains(self):
        html = HTML.read_text(encoding="utf-8")
        for tab_id, domain in VISIBLE_DOMAINS.items():
            self.assertRegex(
                html,
                rf'id=["\']tab{tab_id}["\'][^>]*'
                rf'data-visual-domain=["\']{domain}["\']',
            )

    def test_dashboard_loads_the_hud_module(self):
        html = HTML.read_text(encoding="utf-8")
        self.assertIn(
            'type="module" src="/static/dashboard-hud.mjs"',
            html,
        )
        self.assertTrue(HUD_JS.is_file())

    def test_dashboard_exposes_one_accessible_hud_toast_region(self):
        html = HTML.read_text(encoding="utf-8")
        self.assertEqual(html.count('id="hud-toast-region"'), 1)
        self.assertRegex(
            html,
            r'id="hud-toast-region"[^>]*role="region"',
        )
        self.assertIn('aria-label="系统通知"', html)

    def test_legacy_toast_is_not_a_second_live_region(self):
        html = HTML.read_text(encoding="utf-8")
        script = DS_JS.read_text(encoding="utf-8")
        legacy = re.search(
            r"<[^>]+\bid=['\"]toast['\"][^>]*>",
            html,
        )
        self.assertIsNotNone(legacy)
        self.assertNotRegex(legacy.group(0), r"\brole\s*=")
        self.assertNotRegex(legacy.group(0), r"\baria-live\s*=")

        accessibility = function_body(script, "enhanceAccessibility")
        self.assertNotIn("getElementById('toast')", accessibility)
        self.assertNotIn("setAttribute('role', 'status')", accessibility)
        self.assertNotIn("setAttribute('aria-live'", accessibility)

        hud = HUD_JS.read_text(encoding="utf-8")
        toast = function_body(hud, "toast")
        self.assertIn('getElementById?.("hud-toast-region")', toast)
        self.assertIn('"aria-live"', toast)

    def test_shared_hud_components_and_tokens_exist(self):
        css = CSS.read_text(encoding="utf-8")
        for token in (
            "--domain-intelligence",
            "--domain-operations",
            "--domain-organization",
            "--domain-analysis",
            "--domain-system",
            "--surface-glass",
            "--surface-elevated",
            "--surface-canvas",
            "--surface-panel",
            "--surface-raised",
            "--surface-overlay",
            "--surface-modal",
            "--surface-inner-highlight",
            "--shadow-hud",
            "--shadow-raised",
            "--shadow-overlay",
            "--shadow-modal",
            "--motion-fast",
            "--motion-standard",
            "--motion-slow",
            "--motion-press",
            "--motion-event",
        ):
            self.assertIn(token, css)
        for selector in (
            ".hud-page-head",
            ".hud-panel",
            ".hud-surface-panel",
            ".hud-surface-raised",
            ".hud-surface-overlay",
            ".hud-surface-modal",
            ".hud-kpi-grid",
            ".hud-kpi",
            ".hud-toolbar",
            ".hud-table-shell",
            ".hud-status-chip",
            ".hud-state",
            ".hud-refresh-line",
            ".hud-skeleton",
            ".hud-toast-region",
            ".hud-toast",
        ):
            self.assertIn(selector, css)

    def test_navigation_has_visual_groups_without_more_menu(self):
        script = DS_JS.read_text(encoding="utf-8")
        for label in (
            "INTELLIGENCE",
            "OPERATIONS",
            "ORGANIZATION",
            "ANALYSIS",
            "SYSTEM",
        ):
            self.assertIn(label, script)
        self.assertIn("ds-nav-group", script)
        self.assertNotIn("ds-nav-more", script)

    def test_body_has_one_main_landmark(self):
        html = HTML.read_text(encoding="utf-8")
        self.assertEqual(len(re.findall(r"<main\b", html)), 1)
        self.assertIn('id="dashboard-main"', html)

    def test_settings_expose_three_motion_levels_and_runtime_health(self):
        html = HTML.read_text(encoding="utf-8")
        self.assertIn('<option value="full">完整</option>', html)
        self.assertIn('<option value="standard">标准</option>', html)
        self.assertIn('<option value="reduced">精简</option>', html)
        self.assertIn('id="hud-health-grid"', html)
        self.assertIn('id="hud-health-refresh"', html)

    def test_hud_health_and_motion_seams_are_wired(self):
        hud = HUD_JS.read_text(encoding="utf-8")
        command = (
            ROOT / "static/dashboard-command-center.js"
        ).read_text(encoding="utf-8")
        css = CSS.read_text(encoding="utf-8")

        self.assertIn('"/api/hud/health"', hud)
        self.assertIn("loadHealth", hud)
        self.assertIn("HudSystem?.setMotionLevel", command)
        self.assertRegex(command, r"motion:\s*['\"]standard['\"]")
        self.assertIn('body[data-motion-level="reduced"]', css)
        for class_name in (
            ".hud-health-grid",
            ".hud-health-card",
            ".hud-pulse-info",
            ".hud-pulse-danger",
            ".hud-pulse-success",
        ):
            self.assertIn(class_name, css)

    def test_design_system_normalizes_overlay_surfaces(self):
        script = DS_JS.read_text(encoding="utf-8")
        targets = re.search(
            r"const OVERLAY_TARGETS = Object\.freeze\(\[(?P<body>.*?)\]\);",
            script,
            re.DOTALL,
        )
        self.assertIsNotNone(targets)
        selector_pairs = re.findall(
            r"\['([^']+)', '([^']+)'\]",
            targets.group("body"),
        )
        self.assertEqual(
            selector_pairs,
            [
                ("body > header", "hud-surface-overlay"),
                ("body > nav", "hud-surface-overlay"),
                (
                    "#cc-command-dialog .cc-command-shell",
                    "hud-surface-modal",
                ),
                (".hud-panel-glass", "hud-surface-raised"),
            ],
        )
        overlay_code = (
            targets.group("body")
            + function_body(script, "normalizeOverlaySurfaces")
        )
        self.assertNotRegex(overlay_code, r"\btr\b")

    def test_reduced_motion_keeps_loading_primitives_static(self):
        css = CSS.read_text(encoding="utf-8")
        app_skeleton = css_rule(
            css,
            'body[data-motion-level="reduced"] .hud-skeleton::after',
        )
        app_refresh = css_rule(
            css,
            'body[data-motion-level="reduced"] .hud-refresh-line::after',
        )
        self.assertIn("animation: none !important", app_skeleton)
        self.assertIn("transform: none", app_skeleton)
        self.assertIn("animation: none !important", app_refresh)
        self.assertIn("transform: scaleX(1)", app_refresh)

        reduced_media = re.search(
            r"@media \(prefers-reduced-motion: reduce\) \{(?P<body>.*?)"
            r"\n\}\n\n@supports",
            css,
            re.DOTALL,
        )
        self.assertIsNotNone(reduced_media)
        media_body = reduced_media.group("body")
        media_skeleton = css_rule(media_body, ".hud-skeleton::after")
        media_refresh = css_rule(
            media_body,
            ".hud-refresh-line::after",
        )
        self.assertIn("animation: none !important", media_skeleton)
        self.assertIn("transform: none", media_skeleton)
        self.assertIn("animation: none !important", media_refresh)
        self.assertIn("transform: scaleX(1)", media_refresh)

    def test_skeleton_animation_is_one_shot_and_compositor_only(self):
        css = CSS.read_text(encoding="utf-8")
        skeleton = css_rule(css, ".hud-skeleton::after")
        animation = re.search(r"animation\s*:\s*([^;]+);", skeleton)
        self.assertIsNotNone(animation)
        self.assertRegex(animation.group(1), r"\bhud-skeleton-shift\b")
        self.assertRegex(animation.group(1), r"\b1\b")
        self.assertNotIn("infinite", animation.group(1))
        self.assertNotIn("background-position", skeleton)

        keyframes = re.search(
            r"@keyframes hud-skeleton-shift\s*\{(?P<body>.*?)\n\}",
            css,
            re.DOTALL,
        )
        self.assertIsNotNone(keyframes)
        declarations = re.findall(
            r"([a-z-]+)\s*:",
            keyframes.group("body"),
        )
        self.assertTrue(declarations)
        self.assertTrue(
            set(declarations).issubset({"transform", "opacity"}),
            declarations,
        )

    def test_real_event_producers_emit_semantic_hud_events(self):
        sources = {
            "simulation": (
                ROOT / "static/simulator-workbench.js"
            ).read_text(encoding="utf-8"),
            "timeline": (
                ROOT / "static/dashboard-command-center.js"
            ).read_text(encoding="utf-8"),
            "intelligence": (
                ROOT / "static/intelligence-center.js"
            ).read_text(encoding="utf-8"),
            "score": (
                ROOT / "static/score-center.js"
            ).read_text(encoding="utf-8"),
        }
        expected_events = {
            "simulation": "simulation:completed",
            "timeline": "battle:report-arrived",
            "intelligence": "intelligence:risk-detected",
            "score": "score:recalculated",
        }
        for producer, event_type in expected_events.items():
            self.assertIn("HudSystem?.emit", sources[producer])
            self.assertIn(event_type, sources[producer])
        self.assertIn("#sim-result-summary", sources["simulation"])
        self.assertIn("data-event-id", sources["timeline"])
        self.assertIn("target: eventTarget", sources["timeline"])
        self.assertNotIn('target: "#cc-timeline-list"', sources["timeline"])
        self.assertIn("#intel-detail-panel", sources["intelligence"])
        self.assertIn("#score-board", sources["score"])

    def test_hud_keeps_the_legacy_pulse_bridge_compatible(self):
        script = r"""
const assert = require("node:assert/strict");
const { pathToFileURL } = require("node:url");
const path = require("node:path");

function fakeElement() {
  return {
    dataset: {},
    classList: {
      values: new Set(),
      add(value) { this.values.add(value); },
      remove(value) { this.values.delete(value); },
      contains(value) { return this.values.has(value); },
    },
    addEventListener() {},
  };
}

async function runCase(reduced, suffix) {
  const target = fakeElement();
  const body = fakeElement();
  body.dataset.motionLevel = reduced ? "reduced" : "standard";
  const windowListeners = {};
  const documentListeners = {};
  global.window = {
    addEventListener(type, callback) { windowListeners[type] = callback; },
    dispatchEvent(event) {
      windowListeners[event.type]?.(event);
    },
  };
  global.CustomEvent = class CustomEvent {
    constructor(type, init = {}) {
      this.type = type;
      this.detail = init.detail;
    }
  };
  global.document = {
    body,
    readyState: "complete",
    visibilityState: "visible",
    addEventListener(type, callback) { documentListeners[type] = callback; },
    querySelector(selector) {
      if (selector === "#legacy-target") return target;
      return null;
    },
    getElementById() { return null; },
    createElement() { return fakeElement(); },
  };
  global.matchMedia = () => ({ matches: reduced });
  const moduleUrl = pathToFileURL(
    path.resolve("static/dashboard-hud.mjs"),
  ).href + `?legacy=${suffix}`;
  await import(moduleUrl);
  let emitCalls = 0;
  const emit = window.HudSystem.emit.bind(window.HudSystem);
  window.HudSystem.emit = (event) => {
    emitCalls += 1;
    return emit(event);
  };
  window.dispatchEvent(new CustomEvent("stzb:hud-pulse", {
    detail: { selector: "#legacy-target", kind: "danger" },
  }));
  assert.equal(emitCalls, 1);
  assert.equal(
    target.classList.contains("hud-event-intelligence-risk-detected"),
    !reduced,
  );
}

(async () => {
  await runCase(false, "standard");
  await runCase(true, "reduced");
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
"""
        result = subprocess.run(
            ["node", "-e", script],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            result.returncode,
            0,
            msg=f"{result.stdout}\n{result.stderr}",
        )


if __name__ == "__main__":
    unittest.main()
