import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "static/dashboard.html"
CSS = ROOT / "static/live-army-command.css"
CONTROLLER = ROOT / "static/live-army-command.mjs"
MAP = ROOT / "static/live-army-map.mjs"


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


def matches_card_selector(selector, classes, attributes):
    if any(token in selector for token in (" ", ">", "+", "~", ":")):
        return False
    required_classes = set(re.findall(r"\.([\w-]+)", selector))
    if not required_classes.issubset(classes):
        return False
    for name, value in re.findall(
        r'\[data-([\w-]+)="([^"]+)"\]',
        selector,
    ):
        if attributes.get(name) != value:
            return False
    return True


def final_declaration(css, property_name, classes, attributes):
    candidates = []
    for rule in css_rules(css):
        value = rule["declarations"].get(property_name)
        if value is None or not matches_card_selector(
            rule["selector"], classes, attributes
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


class LiveArmyStaticTest(unittest.TestCase):
    def setUp(self):
        self.html = HTML.read_text(encoding="utf-8")
        self.css = CSS.read_text(encoding="utf-8") if CSS.exists() else ""

    def test_live_army_is_flat_navigation_after_intelligence(self):
        nav = self.html.split('<nav aria-label="主导航">', 1)[1].split(
            "</nav>", 1
        )[0]
        self.assertIn(
            "<button onclick='switchTab(35,this)'>实时部队</button>",
            nav,
        )
        self.assertLess(
            nav.index("switchTab(33,this)"),
            nav.index("switchTab(35,this)"),
        )
        self.assertLess(
            nav.index("switchTab(35,this)"),
            nav.index("switchTab(34,this)"),
        )

    def test_live_army_page_has_three_column_hud_shell(self):
        section = self.html.split("id='tab35'", 1)[1].split(
            "id='tab34'", 1
        )[0]
        for token in (
            "hud-page-head",
            "live-army-shell",
            "live-army-index",
            "live-army-map-panel",
            "live-army-detail-panel",
            "live-army-current-list",
            "live-army-offline-list",
            "live-army-map-canvas",
            "live-army-search",
            "live-army-status-filter",
            "live-army-time-filter",
            "live-army-index-toggle",
            "live-army-observed-at",
            "live-army-summary",
        ):
            self.assertIn(token, section)
        self.assertIn("data-visual-domain='intelligence'", section)

    def test_live_army_assets_are_loaded_before_controller(self):
        self.assertIn(
            '<link rel="stylesheet" href="/static/live-army-command.css">',
            self.html,
        )
        self.assertIn(
            'type="module" src="/static/live-army-command.mjs"',
            self.html,
        )
        self.assertTrue(MAP.is_file())
        self.assertTrue(CONTROLLER.is_file())

    def test_live_army_css_has_required_semantics_without_root_tokens(self):
        self.assertNotIn(":root", self.css)
        for selector in (
            ".live-army-shell",
            ".live-army-index",
            ".live-army-list",
            ".live-army-card",
            ".live-army-card.is-selected",
            ".live-army-card.is-offline",
            ".live-army-map-panel",
            ".live-army-map-wrap",
            ".live-army-detail-panel",
            ".live-army-hero-grid",
            ".live-army-hero",
            ".live-army-evidence",
        ):
            self.assertIn(selector, self.css)
        self.assertIn("@media (max-width: 1279px)", self.css)
        self.assertIn("@media (max-width: 767px)", self.css)
        self.assertIn("@media (prefers-reduced-motion: reduce)", self.css)
        self.assertIn(".live-army-index-toggle", self.css)
        self.assertIn(
            ".live-army-index.is-collapsed .live-army-list-shell",
            self.css,
        )
        self.assertRegex(
            self.css,
            r"@media \(max-width: 767px\)[\s\S]*?"
            r"\.live-army-index\s*\{[^}]*display:\s*contents",
        )
        self.assertRegex(
            self.css,
            r"@media \(max-width: 767px\)[\s\S]*?"
            r"\.live-army-toolbar\s*\{[^}]*order:\s*0",
        )
        self.assertRegex(
            self.css,
            r"\.live-army-map-status\s*,\s*"
            r"\.live-army-map-legend\s*\{[^}]*pointer-events:\s*none",
        )

    def test_controller_exposes_stable_browser_api(self):
        script = CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("window.LiveArmyCommand", script)
        for token in (
            "load",
            "selectArmy",
            "locateArmy",
            "openInIntelligence",
            "setFilter",
        ):
            self.assertIn(token, script)

    def test_live_army_uses_surface_tokens_and_semantic_states(self):
        for token in (
            "var(--surface-panel)",
            "var(--surface-raised)",
            "var(--domain-accent)",
        ):
            self.assertIn(token, self.css)
        for selector in (
            '.live-army-card[data-freshness="stale"]',
            '.live-army-card[data-lineup-status="unknown"]',
            '.live-army-card[data-activity="offline"]',
            ".live-army-card.is-selected",
        ):
            self.assertIn(selector, self.css)

    def test_live_army_controller_sets_semantic_state_attributes(self):
        script = CONTROLLER.read_text(encoding="utf-8")
        for attribute in (
            "data-freshness",
            "data-lineup-status",
            "data-activity",
        ):
            self.assertIn(attribute, script)
        self.assertIn("emitHudEvent", script)

    def test_selected_stale_and_offline_cards_keep_domain_accent_primary(self):
        states = (
            (
                {"live-army-card", "is-selected"},
                {"freshness": "stale", "activity": "current"},
            ),
            (
                {"live-army-card", "is-selected", "is-offline"},
                {"freshness": "stale", "activity": "offline"},
            ),
        )
        for classes, attributes in states:
            border = final_declaration(
                self.css,
                "border-color",
                classes,
                attributes,
            )
            shadow = final_declaration(
                self.css,
                "box-shadow",
                classes,
                attributes,
            )
            background = final_declaration(
                self.css,
                "background",
                classes,
                attributes,
            )
            self.assertIsNotNone(border)
            self.assertIsNotNone(shadow)
            self.assertIsNotNone(background)
            self.assertIn(".is-selected", border[3]["selector"])
            self.assertIn("var(--domain-accent)", border[3]["declarations"]["border-color"])
            self.assertIn(".is-selected", shadow[3]["selector"])
            self.assertIn("var(--domain-accent)", shadow[3]["declarations"]["box-shadow"])
            self.assertIn(".is-selected", background[3]["selector"])
            self.assertIn(
                "var(--domain-accent)",
                background[3]["declarations"]["background"],
            )

    def test_countdowns_do_not_use_transform_animation(self):
        countdown_rules = [
            rule for rule in css_rules(self.css)
            if ".live-army-card-countdown" in rule["selector"]
        ]
        self.assertTrue(countdown_rules)
        for rule in countdown_rules:
            declarations = rule["declarations"]
            self.assertNotIn("transform", declarations)
            if "animation" in declarations:
                self.assertEqual(declarations["animation"], "none")
            if "transition" in declarations:
                self.assertEqual(declarations["transition"], "none")


if __name__ == "__main__":
    unittest.main()
