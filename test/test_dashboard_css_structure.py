import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VISIBLE_TABS = (7, 8, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35)
MIGRATED_SHELL_CLASSES = (
    "hud-page-head",
    "hud-toolbar",
    "hud-panel",
    "hud-kpi-grid",
    "hud-table-shell",
)
BACKDROP_DECLARATION = re.compile(
    r"(?<![\w-])(?:-webkit-)?backdrop-filter\s*:"
)
FILTER_BLUR_DECLARATION = re.compile(
    r"(?<![\w-])filter\s*:[^;{}]*\bblur\s*\(",
    re.IGNORECASE,
)


def backdrop_declaration_selectors(css):
    css = re.sub(r"/\*.*?\*/", "", css, flags=re.DOTALL)
    selectors = []
    for match in re.finditer(r"(?P<selectors>[^{}]+)\{(?P<body>[^{}]*)\}", css):
        if not BACKDROP_DECLARATION.search(match.group("body")):
            continue
        selectors.extend(
            selector.strip()
            for selector in match.group("selectors").split(",")
            if selector.strip()
        )
    return selectors


class DashboardCssStructureTest(unittest.TestCase):
    def test_dashboard_isolates_legacy_overrides_before_design_system(self):
        html = (ROOT / "static/dashboard.html").read_text(encoding="utf-8")
        self.assertIn("@media not all {", html)
        legacy = html.index("<style>")
        design = html.index("/static/dashboard-design-system.css")
        self.assertLess(legacy, design)

    def test_design_system_owns_active_semantic_tokens(self):
        html = (ROOT / "static/dashboard.html").read_text(encoding="utf-8")
        design = (ROOT / "static/dashboard-design-system.css").read_text(
            encoding="utf-8"
        )
        for token in (
            "--bg-canvas",
            "--surface-1",
            "--border-strong",
            "--text-primary",
            "--primary",
        ):
            active_legacy = html.split("@media not all {", 1)[0]
            self.assertNotRegex(active_legacy, rf"{re.escape(token)}\s*:")
            self.assertRegex(design, rf"{re.escape(token)}\s*:")

    def test_visible_migrated_shells_do_not_use_inline_theme_or_layout(self):
        html = (ROOT / "static/dashboard.html").read_text(encoding="utf-8")
        page_matches = list(
            re.finditer(
                r"<div[^>]*class=['\"][^'\"]*\bpage\b[^'\"]*['\"]"
                r"[^>]*id=['\"]tab(\d+)['\"][^>]*>",
                html,
            )
        )
        page_sections = {}
        for index, match in enumerate(page_matches):
            end = (
                page_matches[index + 1].start()
                if index + 1 < len(page_matches)
                else html.index("</main>")
            )
            page_sections[int(match.group(1))] = html[match.start():end]

        for tab_id in VISIBLE_TABS:
            with self.subTest(tab_id=tab_id):
                self.assertIn(tab_id, page_sections)
                section = page_sections[tab_id]
                for tag in re.findall(r"<[^>]+>", section):
                    if not any(
                        re.search(
                            rf"class=['\"][^'\"]*\b{re.escape(class_name)}\b",
                            tag,
                        )
                        for class_name in MIGRATED_SHELL_CLASSES
                    ):
                        continue
                    style_match = re.search(
                        r"\sstyle=['\"]([^'\"]*)['\"]",
                        tag,
                    )
                    if not style_match:
                        continue
                    declarations = [
                        item.strip()
                        for item in style_match.group(1).split(";")
                        if item.strip()
                    ]
                    self.assertTrue(
                        all(item.startswith("--") for item in declarations),
                        f"tab{tab_id} migrated shell has inline style: {tag}",
                    )

    def test_design_system_skips_nested_hud_page_heads(self):
        script = (
            ROOT / "static/dashboard-design-system.js"
        ).read_text(encoding="utf-8")
        self.assertIn(
            "page.querySelector('.hud-page-head, :scope > .ds-page-heading')",
            script,
        )

    def test_hud_has_feature_detection_fallbacks_and_performance_budget(self):
        design = (
            ROOT / "static/dashboard-design-system.css"
        ).read_text(encoding="utf-8")
        hud_path = ROOT / "static/dashboard-hud.mjs"
        hud = hud_path.read_text(encoding="utf-8")

        self.assertIn(
            "@supports not (backdrop-filter: blur(1px))",
            design,
        )
        self.assertIn(
            "@supports not (color: color-mix(in srgb, white, black))",
            design,
        )
        self.assertLess(hud_path.stat().st_size, 25_000)

        animate_start = hud.index("function animateValue")
        animate_end = hud.index("\n  function renderState", animate_start)
        without_animation = hud[:animate_start] + hud[animate_end:]
        self.assertNotIn("requestAnimationFrameFn(", without_animation)

        domain_css_size = sum(
            (ROOT / "static" / name).stat().st_size
            for name in (
                "operations-hud.css",
                "organization-hud.css",
                "analysis-hud.css",
                "intelligence-center.css",
            )
        )
        self.assertLess(domain_css_size, 35_000)

    def test_global_polish_uses_bounded_glass_and_compositor_properties(self):
        css = (ROOT / "static/dashboard-design-system.css").read_text(
            encoding="utf-8"
        )
        self.assertIn("--surface-overlay:", css)
        self.assertIn("--surface-modal:", css)
        self.assertIn("@supports not (backdrop-filter: blur(1px))", css)
        self.assertNotRegex(
            css,
            r"transition\s*:\s*all\b",
        )
        self.assertNotRegex(
            css,
            r"animation(?:-[a-z]+)?\s*:[^;]*(?:width|height|left|top)",
        )

    def test_global_polish_keeps_css_and_hud_runtime_bounded(self):
        hud = ROOT / "static/dashboard-hud.mjs"
        self.assertLess(hud.stat().st_size, 35_000)
        design_path = ROOT / "static/dashboard-design-system.css"
        css = design_path.read_text(
            encoding="utf-8"
        )
        self.assertNotRegex(css, r"transition\s*:\s*all\b")

        approved_selectors = {
            "body.stzb-console > header",
            "body.stzb-console > nav",
            ".hud-surface-modal",
            ".hud-toast",
        }
        self.assertEqual(
            set(backdrop_declaration_selectors(css)),
            approved_selectors,
            "only the four approved global spatial layers may use blur",
        )
        self.assertEqual(
            css.count("@supports not (backdrop-filter: blur(1px))"),
            1,
        )

        for css_path in sorted((ROOT / "static").glob("*.css")):
            if css_path == design_path:
                continue
            business_css = css_path.read_text(encoding="utf-8")
            self.assertIsNone(
                BACKDROP_DECLARATION.search(business_css),
                f"{css_path.name} must use solid business surfaces",
            )
            self.assertIsNone(
                FILTER_BLUR_DECLARATION.search(business_css),
                f"{css_path.name} must not add business blur effects",
            )

        for source_name in ("dashboard.html", "app2.js"):
            source = (ROOT / "static" / source_name).read_text(encoding="utf-8")
            self.assertIsNone(
                BACKDROP_DECLARATION.search(source),
                f"{source_name} must not create inline business blur",
            )
            self.assertIsNone(
                FILTER_BLUR_DECLARATION.search(source),
                f"{source_name} must not create inline business blur effects",
            )

        for css_path in sorted((ROOT / "static").glob("*.css")):
            source = css_path.read_text(encoding="utf-8")
            for match in re.finditer(
                r"(?P<selectors>[^{}]*::backdrop[^{}]*)"
                r"\{(?P<body>[^{}]*)\}",
                source,
            ):
                self.assertIsNone(
                    BACKDROP_DECLARATION.search(match.group("body")),
                    f"{css_path.name} {match.group('selectors').strip()} "
                    "must use a solid backdrop",
                )

    def test_app_reduced_motion_disables_existing_card_translation(self):
        css = (ROOT / "static/dashboard-design-system.css").read_text(
            encoding="utf-8"
        )
        for selector in (
            r'body\[data-motion-level="reduced"\]\s+'
            r'\.hud-panel\[data-interactive="true"\]:hover',
            r'body\[data-motion-level="reduced"\]\s+\.cc-kpi-card:hover',
        ):
            self.assertRegex(
                css,
                selector + r"\s*\{[^}]*transform\s*:\s*none\s*;",
            )

    def test_command_center_skeleton_is_one_shot_and_compositor_only(self):
        css = (ROOT / "static/dashboard-design-system.css").read_text(
            encoding="utf-8"
        )
        skeleton_rules = re.findall(
            r"\.cc-skeleton\s*\{(?P<body>[^{}]*)\}",
            css,
        )
        self.assertTrue(skeleton_rules)
        animation_values = [
            match.group(1)
            for body in skeleton_rules
            for match in re.finditer(r"animation\s*:\s*([^;]+)", body)
            if not match.group(1).strip().startswith("none")
        ]
        self.assertTrue(animation_values)
        self.assertTrue(all("infinite" not in value for value in animation_values))
        self.assertTrue(all(re.search(r"(?:^|\s)1(?:\s|$)", value) for value in animation_values))
        keyframes = re.search(
            r"@keyframes\s+cc-skeleton-enter\s*\{(?P<body>.*?)\n\}",
            css,
            re.DOTALL,
        )
        self.assertIsNotNone(keyframes)
        declarations = re.findall(
            r"([a-z-]+)\s*:",
            keyframes.group("body"),
        )
        self.assertTrue(declarations)
        self.assertEqual(set(declarations), {"opacity", "transform"})

    def test_hud_runtime_contract_lives_in_design_documentation(self):
        design = (
            ROOT
            / "docs/superpowers/specs/2026-08-15-modular-immersive-hud-system-design.md"
        ).read_text(encoding="utf-8")
        for token in (
            "模块化沉浸 HUD",
            "intelligence",
            "operations",
            "organization",
            "analysis",
            "system",
            "data-visual-domain",
            "prefers-reduced-motion",
            "208px",
        ):
            self.assertIn(token, design)

    def test_product_readme_documents_dual_platform_and_screenshot_contract(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        visible_readme = re.sub(r"<!--.*?-->", "", readme, flags=re.DOTALL)
        screenshot_names = (
            "overview-intelligence.webp",
            "gallery-live-army.webp",
            "gallery-simulator.webp",
            "gallery-research.webp",
            "gallery-score.webp",
            "gallery-attendance.webp",
            "gallery-player-teams.webp",
            "android-battlefield.webp",
            "android-teams.webp",
            "android-simulator.webp",
        )
        for token in (
            "Web + Android 双端战场数据平台",
            "核心数据模型、业务口径、阵容语义与模拟结果",
            "| 战场 | 战场情报、地图格子、行军、实时部队、世界事件 | 支持 | 支持 |",
            "| 模拟 | 双方配置、批量推演、结果与事件回放 | 支持 | 支持 |",
            "http://127.0.0.1:8080",
            "astzb/app/build/outputs/apk/debug/app-debug.apk",
            "docs/assets/screenshots/README.md",
        ):
            with self.subTest(token=token):
                self.assertIn(token, readme)
        for screenshot_name in screenshot_names:
            with self.subTest(screenshot=screenshot_name):
                self.assertIn(screenshot_name, readme)
                self.assertRegex(
                    visible_readme,
                    rf"<img[^>]+{re.escape(screenshot_name)}[^>]*>",
                )
        for deprecated_positioning in ("PoC", "精简版", "部分迁移", "附属端"):
            self.assertNotIn(deprecated_positioning, readme)

    def test_android_beta_docs_match_current_navigation_and_capture_gate(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        guide = (ROOT / "docs/USER_GUIDE.md").read_text(encoding="utf-8")
        android_readme = (ROOT / "astzb/README.md").read_text(encoding="utf-8")
        combined = "\n".join((readme, guide, android_readme))

        for token in (
            "独立抓包与核心分析 Beta",
            "战场 / 战报 / 同盟 / 工具",
            "Native 组件",
            "VPN 通道",
            "SOCKS 连接",
            "已知协议",
            "本地入库",
            "停止与网络恢复",
            "原生",
            "经典兼容",
            "Web 独有",
        ):
            with self.subTest(token=token):
                self.assertIn(token, combined)

        self.assertNotIn("底部有五个一级入口", guide)
        self.assertNotIn("`6314`：攻城战场动态", android_readme)
        self.assertNotIn("`6318`：攻城队列", android_readme)


if __name__ == "__main__":
    unittest.main()
