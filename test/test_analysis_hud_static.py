import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class AnalysisHudStaticTest(unittest.TestCase):
    def setUp(self):
        self.html = (ROOT / "static/dashboard.html").read_text(
            encoding="utf-8"
        )
        self.app2 = (ROOT / "static/app2.js").read_text(encoding="utf-8")
        self.score = (ROOT / "static/score-center.js").read_text(
            encoding="utf-8"
        )
        self.research = (
            ROOT / "static/intelligence-research.js"
        ).read_text(encoding="utf-8")
        css_path = ROOT / "static/analysis-hud.css"
        self.css = (
            css_path.read_text(encoding="utf-8")
            if css_path.is_file()
            else ""
        )

    def test_analysis_pages_use_shared_hud_shell(self):
        pages = (
            self.html.split("id='tab8'", 1)[1].split("id='tab9'", 1)[0],
            self.html.split("id='tab23'", 1)[1].split(
                "id='modal-overlay'", 1
            )[0],
            self.html.split("id='tab34'", 1)[1].split("</main>", 1)[0],
        )
        for page in pages:
            self.assertIn("hud-page-head", page)
            self.assertIn("hud-panel", page)
        self.assertIn("/static/analysis-hud.css", self.html)

    def test_analysis_renderers_use_evidence_rank_and_lineup_semantics(self):
        combined = self.app2 + self.score + self.research
        for class_name in (
            "analysis-evidence",
            "analysis-rank",
            "analysis-lineup-card",
            "analysis-detail-head",
            "analysis-evidence-row",
            "analysis-fact-grid",
            "analysis-related",
        ):
            self.assertIn(class_name, combined)
        self.assertIn("analysis-lineup-grid", self.html)

    def test_analysis_css_uses_global_tokens_and_no_emoji_medals(self):
        self.assertNotIn(":root", self.css)
        combo_renderer = self.app2.split(
            "async function loadHeroCombo", 1
        )[1].split("// ===== 团数据", 1)[0]
        self.assertNotIn("🥇", combo_renderer)
        self.assertNotIn("🥈", combo_renderer)
        self.assertNotIn("🥉", combo_renderer)
        for selector in (
            '.analysis-evidence[data-kind="config"]',
            '.analysis-evidence[data-kind="history"]',
            '.analysis-evidence[data-kind="simulation"]',
            '.analysis-rank[data-rank="1"]',
            '.analysis-rank[data-rank="2"]',
            '.analysis-rank[data-rank="3"]',
        ):
            self.assertIn(selector, self.css)

    def test_score_completion_targets_the_analysis_board(self):
        self.assertIn("#score-board", self.score)
        self.assertIn("score:recalculated", self.score)
        self.assertIn("HudSystem?.emit", self.score)
        self.assertNotIn("stzb:hud-pulse", self.score)

    def test_analysis_rows_expose_direction_without_color_only_state(self):
        for selector in (
            '.analysis-row[data-delta="up"]',
            '.analysis-row[data-delta="down"]',
        ):
            self.assertIn(selector, self.css)
        self.assertIn(
            'row.dataset.delta = delta > 0 ? "up" : delta < 0 ? "down" : "same";',
            self.score,
        )
        self.assertIn("delete row.dataset.delta", self.score)
        self.assertIn("score-delta-marker", self.score)
        self.assertIn("上升", self.score)
        self.assertIn("下降", self.score)

    def test_top_lineups_have_explicit_surface_tiers(self):
        combo_renderer = self.app2.split(
            "async function loadHeroCombo", 1
        )[1].split("// ===== 团数据", 1)[0]
        self.assertIn('data-rank-tier=', combo_renderer)
        self.assertIn("'top'", combo_renderer)
        self.assertIn("'standard'", combo_renderer)
        self.assertIn(
            '.analysis-lineup-card[data-rank-tier="top"]',
            self.css,
        )


if __name__ == "__main__":
    unittest.main()
