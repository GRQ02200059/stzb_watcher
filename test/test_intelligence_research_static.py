import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class IntelligenceResearchStaticTest(unittest.TestCase):
    def test_dashboard_contains_research_center(self):
        html = (ROOT / "static/dashboard.html").read_text(encoding="utf-8")
        self.assertIn("switchTab(34,this)", html)
        self.assertIn("id='tab34'", html)
        for token in (
            "research-mode-tabs",
            "research-library-kind",
            "research-search",
            "research-library-filters",
            "research-results",
            "research-stage",
            "research-evidence-tabs",
            "research-evidence-body",
            "research-template-dialog",
        ):
            self.assertIn(token, html)
        self.assertIn('data-kind="card-pack"', html)
        self.assertNotIn('data-kind="protocol"', html)
        self.assertIn("/static/intelligence-research.js", html)
        self.assertIn("/static/intelligence-research-catalog.js", html)
        self.assertIn("/static/intelligence-research.css", html)
        self.assertIn("/static/research-workbench.mjs", html)
        self.assertIn("/static/research-skill-chain.mjs", html)
        self.assertIn("/static/research-templates.mjs", html)

    def test_research_workbench_styles_cover_shell_and_responsive_states(self):
        css = (
            ROOT / "static/intelligence-research.css"
        ).read_text(encoding="utf-8")
        for selector in (
            ".research-workbench-shell",
            ".research-library",
            ".research-stage",
            ".research-evidence-panel",
            ".research-lineup-grid",
            ".research-hero-card",
            ".research-skill-slot",
            ".research-matchup-stage",
            ".research-skill-chain",
        ):
            self.assertIn(selector, css)
        self.assertIn("1279px", css)
        self.assertIn("767px", css)
        self.assertIn("prefers-reduced-motion", css)
        self.assertNotIn(":root", css)

    def test_research_center_has_config_evidence_and_simulator_handoff(self):
        js = "\n".join(
            (
                (
                    ROOT / "static/intelligence-research.js"
                ).read_text(encoding="utf-8"),
                (
                    ROOT / "static/research-workbench.mjs"
                ).read_text(encoding="utf-8"),
            )
        )
        self.assertIn("CONFIG FACT", js)
        self.assertIn("BATTLE STAT", js)
        self.assertIn("SIMULATION", js)
        self.assertIn("openLineup", js)
        self.assertIn("sendToSimulator", js)
        self.assertIn("/api/intelligence/heroes", js)
        self.assertIn("/api/intelligence/skills", js)
        self.assertIn("/api/intelligence/lineups", js)

    def test_catalog_research_only_exposes_card_pack_interactions(self):
        js = "\n".join(
            (
                (
                    ROOT / "static/intelligence-research-catalog.js"
                ).read_text(encoding="utf-8"),
                (
                    ROOT / "static/research-workbench.mjs"
                ).read_text(encoding="utf-8"),
            )
        )
        self.assertIn("/api/intelligence/card-packs", js)
        self.assertIn("openCardPack", js)
        self.assertIn("countryDistribution", js)
        self.assertNotIn("/api/intelligence/protocol/commands", js)
        self.assertNotIn("/api/intelligence/protocol/schema", js)
        self.assertNotIn("openCommand", js)
        self.assertNotIn("openSchema", js)
        self.assertNotIn("protocol-change-added", js)
        self.assertNotIn("research-schema-grid", js)
        self.assertNotIn("抽卡概率", js)
        self.assertNotIn("执行 SQL", js)
        self.assertNotIn("发送命令", js)

    def test_simulator_exposes_stable_lineup_handoff_api(self):
        js = (ROOT / "static/sim.js").read_text(encoding="utf-8")
        self.assertIn("window.StzbSimulator", js)
        self.assertIn("loadLineup", js)
        self.assertIn("stzb:simulation-completed", js)

    def test_research_uses_shared_surfaces_and_adapter_owned_hud(self):
        html = (
            ROOT / "static/dashboard.html"
        ).read_text(encoding="utf-8")
        css = (
            ROOT / "static/intelligence-research.css"
        ).read_text(encoding="utf-8")
        controller = (
            ROOT / "static/research-workbench.mjs"
        ).read_text(encoding="utf-8")
        adapter = (
            ROOT / "static/intelligence-research.js"
        ).read_text(encoding="utf-8")
        for token in (
            "var(--surface-panel)",
            "var(--surface-raised)",
            "var(--surface-modal)",
        ):
            self.assertIn(token, css)
        for host_id in (
            "research-detail-status",
            "research-lineup-status",
            "research-matchup-status",
            "research-chain-status",
            "research-evidence-panel",
        ):
            self.assertIn('id="%s"' % host_id, html)
        self.assertIn('id="research-stage-content"', html)
        self.assertIn("onSimulationEvidence", controller)
        self.assertNotIn("HudSystem", controller)
        self.assertIn("renderRequestState", controller)
        self.assertNotIn("research-detail-status", controller)
        self.assertNotIn("research-lineup-status", controller)
        self.assertNotIn("research-matchup-status", controller)
        self.assertNotIn("research-chain-status", controller)
        self.assertIn("renderRequestState", adapter)
        self.assertIn("HudSystem?.renderState", adapter)
        self.assertIn("researchRequestOwners", adapter)
        self.assertIn("model.ownerToken", adapter)
        self.assertIn("replace: model.replace", adapter)
        self.assertIn("aria-busy", adapter)
        self.assertIn(
            'if (workbench.state.libraryKind !== "hero")',
            adapter,
        )
        self.assertIn(
            'if (workbench.state.libraryKind !== "skill")',
            adapter,
        )
        self.assertIn(
            'if (workbench.state.libraryKind !== "card-pack")',
            adapter,
        )
        self.assertIn("HudSystem?.emit", adapter)
        self.assertIn('type: "simulation:completed"', adapter)
        self.assertIn('target: "#research-evidence-body"', adapter)
        self.assertIn("research-simulation:${lineupKey}", adapter)

    def test_config_and_simulation_evidence_keep_distinct_badges(self):
        css = (
            ROOT / "static/intelligence-research.css"
        ).read_text(encoding="utf-8")
        self.assertIn(".evidence-config", css)
        self.assertIn(".evidence-sim", css)
        self.assertIn(
            ".evidence-config{--research-selection-accent:",
            css,
        )
        self.assertIn(
            ".evidence-sim{--research-selection-accent:",
            css,
        )


if __name__ == "__main__":
    unittest.main()
