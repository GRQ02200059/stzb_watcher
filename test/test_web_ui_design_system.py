from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
HTML_PATH = ROOT / "static" / "dashboard.html"
CSS_PATH = ROOT / "static" / "dashboard-design-system.css"
JS_PATH = ROOT / "static" / "dashboard-design-system.js"


class WebUIDesignSystemTests(unittest.TestCase):
    def test_dashboard_loads_progressive_design_system_assets(self):
        html = HTML_PATH.read_text(encoding="utf-8")

        self.assertIn('/static/dashboard-design-system.css', html)
        self.assertIn('/static/dashboard-design-system.js', html)
        self.assertNotRegex(
            html,
            r'/static/[^?"\']+\.(?:js|css)\?v=',
        )
        self.assertIn('<nav aria-label="主导航"', html)
        self.assertIn('aria-live="polite"', html)

    def test_approved_semantic_tokens_are_defined(self):
        css = CSS_PATH.read_text(encoding="utf-8")
        expected_tokens = {
            "--bg-canvas": "var(--surface-canvas)",
            "--bg-sidebar": "#0a1028",
            "--surface-1": "var(--surface-panel)",
            "--surface-2": "var(--surface-raised)",
            "--surface-3": "#19284f",
            "--surface-4": "#243967",
            "--border-subtle": "#24365f",
            "--border-strong": "#365080",
            "--text-primary": "#f4f7ff",
            "--text-secondary": "#9aabca",
            "--text-tertiary": "#7183a7",
            "--primary": "#38bdf8",
            "--primary-strong": "#2f80ff",
            "--success": "#34d399",
            "--warning": "#f5b84b",
            "--danger": "#f05267",
            "--protected": "#8b6cff",
            "--info": "#54a6ff",
        }
        lowered = css.lower()
        for token, value in expected_tokens.items():
            with self.subTest(token=token):
                self.assertRegex(
                    lowered,
                    rf"{re.escape(token)}\s*:\s*{re.escape(value)}(?:\s*;|\b)",
                )

        self.assertIn('--font-sans:', css)
        self.assertIn('--font-mono:', css)
        self.assertIn('--gradient-primary:', css)
        self.assertIn('--shadow-focus:', css)

    def test_shell_and_shared_components_are_present(self):
        css = CSS_PATH.read_text(encoding="utf-8")
        js = JS_PATH.read_text(encoding="utf-8")

        for selector in (
            ".ds-brand",
            ".ds-nav-icon",
            ".ds-page-heading",
            ".ds-menu-toggle",
            ".ds-status",
            ".ds-empty-state",
        ):
            with self.subTest(selector=selector):
                self.assertIn(selector, css)

        self.assertIn('aria-current', js)
        self.assertIn('ds-nav-open', js)
        self.assertIn('data-tab-index', js)
        self.assertIn('createElementNS', js)
        self.assertIn("setAttribute('role', 'region')", js)
        self.assertNotIn("setAttribute('role', 'main')", js)
        self.assertIn("aria-hidden", js)

    def test_accessibility_and_responsive_contracts_are_present(self):
        css = CSS_PATH.read_text(encoding="utf-8")
        js = JS_PATH.read_text(encoding="utf-8")

        self.assertIn(':focus-visible', css)
        self.assertIn('@media (prefers-reduced-motion: reduce)', css)
        self.assertRegex(css, r"@media\s*\(max-width:\s*1279px\)")
        self.assertRegex(css, r"@media\s*\(max-width:\s*1023px\)")
        self.assertRegex(css, r"@media\s*\(max-width:\s*767px\)")
        self.assertIn('min-height:44px', css.replace(' ', ''))
        self.assertIn('overflow-x:hidden', css.replace(' ', ''))
        self.assertIn(
            "button.setAttribute('aria-label', meta[0])",
            js,
        )

    def test_active_legacy_css_has_no_transition_all(self):
        html = HTML_PATH.read_text(encoding="utf-8")
        active_legacy = html.split("@media not all {", 1)[0]
        self.assertNotRegex(
            active_legacy,
            r"transition\s*:\s*all\b",
        )

    def test_modern_polish_components_have_accessible_states(self):
        css = CSS_PATH.read_text(encoding="utf-8")
        for selector in (
            ".hud-toast-region",
            ".hud-toast[data-severity=\"critical\"]",
            ".hud-skeleton",
            ".hud-refresh-line",
            ".hud-event-critical",
        ):
            self.assertIn(selector, css)
        focus_rule = re.search(
            r"\.hud-toast-close:focus-visible,\s*"
            r"\.hud-toast-action:focus-visible\s*\{(?P<body>[^}]*)\}",
            css,
        )
        disabled_rule = re.search(
            r"\.hud-toast-close:disabled,\s*"
            r"\.hud-toast-action:disabled\s*\{(?P<body>[^}]*)\}",
            css,
        )
        self.assertIsNotNone(focus_rule)
        self.assertRegex(
            focus_rule.group("body"),
            r"\b(?:outline|box-shadow)\s*:",
        )
        self.assertIsNotNone(disabled_rule)
        self.assertIn("cursor: not-allowed", disabled_rule.group("body"))

    def test_task_nine_e2e_covers_global_acceptance_matrix(self):
        e2e = (ROOT / "test" / "js" / "dashboard-e2e.mjs").read_text(
            encoding="utf-8"
        )
        for token in (
            "visibleDomainByTab",
            "assertSurfaceAndInteraction",
            "assertPageStateLifecycle",
            "assertRealEventLifecycle",
            "assertResponsiveViewport",
            "collectVisibleVisualBudget",
            "visualBudgetSamples.length",
            "deviceScaleFactor: 2",
            'reducedMotion: "reduce"',
            "backdropFilter",
            '"::before"',
            '"::after"',
            '"::backdrop"',
            "animationName",
            "animationCount > 6",
            "peakAnimationCount <= 6",
            "__rafAnimationStats",
            "assertRafQuiescence",
            "firstWindow.pending",
            "secondWindow.scheduled",
            "blurCount > 4",
            "Emulation.setPageScaleFactor",
            "visualViewport.scale",
            "visualViewport.width",
            "{ width: 375, height: 812 }",
            "{ width: 1920, height: 1080 }",
            "queueOrganizationResponse",
            "loadPlayerBattleTeams",
            "triggerMessage",
            "MutationObserver",
            "VALUE:9876",
            "reducedPressedTransform",
            "task-nine-reduced-toast",
            "reducedFocusStyle",
            ".research-skill-chain",
            "live-army-index-toggle",
            ".click()",
        ):
            with self.subTest(token=token):
                self.assertIn(token, e2e)
        self.assertNotIn(
            'locator("#live-army-index-toggle").evaluate(',
            e2e,
        )
        event_lifecycle = e2e[
            e2e.index("async function assertRealEventLifecycle"):
            e2e.index("async function closeHudToasts")
        ]
        self.assertNotIn(
            'dispatchEvent(new CustomEvent("stzb:stream-event"',
            event_lifecycle,
        )


if __name__ == "__main__":
    unittest.main()
