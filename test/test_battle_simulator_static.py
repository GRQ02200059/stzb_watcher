import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "static/dashboard.html"
CSS = ROOT / "static/simulator-workbench.css"
WORKBENCH = ROOT / "static/simulator-workbench.js"
ANALYSIS = ROOT / "static/simulator-analysis.mjs"
E2E = ROOT / "test/js/dashboard-e2e.mjs"


def css_rule(css, selector):
    match = re.search(
        rf"{re.escape(selector)}\s*\{{(?P<body>[^}}]*)\}}",
        css,
    )
    if not match:
        raise AssertionError(f"missing CSS rule: {selector}")
    return match.group("body")


class BattleSimulatorStaticTest(unittest.TestCase):
    def test_dashboard_uses_semantic_simulator_shell(self):
        html = HTML.read_text(encoding="utf-8")
        for element_id in (
            "sim-workbench",
            "sim-attacker-team",
            "sim-defender-team",
            "sim-run-controls",
            "sim-result-summary",
            "sim-replay-view",
            "sim-library-dialog",
            "sim-template-dialog",
        ):
            self.assertIn('id="%s"' % element_id, html)

    def test_dashboard_removes_legacy_inline_simulator_block(self):
        html = HTML.read_text(encoding="utf-8")
        section = html.split("<!-- TAB 25", 1)[1].split(
            "<!-- TAB 26", 1
        )[0]

        self.assertNotIn('style="', section)
        self.assertNotIn("游戏风格战斗模拟器", section)
        self.assertNotIn('id="stzb-sim"', section)

    def test_simulator_assets_are_loaded(self):
        html = HTML.read_text(encoding="utf-8")

        self.assertIn(
            '<link rel="stylesheet" href="/static/simulator-workbench.css">',
            html,
        )
        self.assertIn(
            'type="module" src="/static/simulator-workbench.js"',
            html,
        )
        self.assertTrue(ANALYSIS.is_file())

    def test_workbench_css_has_desktop_and_mobile_contracts(self):
        css = CSS.read_text(encoding="utf-8")

        for selector in (
            ".sim-workbench",
            ".sim-header",
            ".sim-duel",
            ".sim-team",
            ".sim-run-controls",
            ".sim-hero-grid",
            ".sim-hero-card",
            ".sim-skill-slot",
            ".sim-result-summary",
            ".sim-insights",
            ".sim-replay",
            ".sim-replay-phases",
            ".sim-replay-stream",
            ".sim-replay-detail",
            ".sim-library-dialog",
            ".sim-template-dialog",
        ):
            self.assertIn(selector, css)
        self.assertIn("@media (max-width: 900px)", css)
        self.assertNotIn(":root", css)

    def test_workbench_uses_solid_business_and_modal_materials(self):
        css = CSS.read_text(encoding="utf-8")

        self.assertIn("var(--surface-raised)", css)
        self.assertIn("var(--surface-modal)", css)
        for selector in (
            ".sim-run-controls",
            ".sim-result-summary",
            ".sim-replay-detail",
            ".sim-library-dialog",
            ".sim-template-dialog",
        ):
            self.assertIn(selector, css)

    def test_workbench_shared_panel_has_solid_base_below_gradients(self):
        css = CSS.read_text(encoding="utf-8")
        workbench = css_rule(css, ".sim-workbench")

        self.assertIn("background:", workbench)
        self.assertIn(
            "background-color: var(--surface-panel)",
            workbench,
        )

    def test_workbench_controller_preserves_external_api(self):
        script = WORKBENCH.read_text(encoding="utf-8")

        self.assertIn("window.StzbSimulator", script)
        self.assertIn("loadLineup", script)
        self.assertIn("getState", script)
        self.assertIn("run", script)

    def test_dialogs_use_one_delegated_event_listener(self):
        script = WORKBENCH.read_text(encoding="utf-8")

        self.assertNotIn(
            'document.getElementById(id)?.addEventListener("click"',
            script,
        )

    def test_portrait_card_has_holographic_visual_contract(self):
        script = WORKBENCH.read_text(encoding="utf-8")
        css = CSS.read_text(encoding="utf-8")

        for token in (
            "sim-hero-portrait",
            "sim-hero-portrait-image",
            "sim-hero-scan",
            "sim-hero-glass",
            "sim-library-portrait",
            "data-sim-portrait",
            "data-fallback-src",
        ):
            self.assertIn(token, script + css)
        self.assertIn("scale(1.04)", css)
        self.assertIn("prefers-reduced-motion", css)

    def test_successful_simulation_uses_semantic_hud_event(self):
        script = WORKBENCH.read_text(encoding="utf-8")

        self.assertIn("simulation:completed", script)
        self.assertIn("HudSystem?.emit", script)
        self.assertNotIn("stzb:hud-pulse", script)
        self.assertIn("stzb:simulation-completed", script)

    def test_simulation_loader_uses_request_snapshot_revision_and_finally(self):
        script = WORKBENCH.read_text(encoding="utf-8")
        run = script.split(
            "async function runSimulation", 1
        )[1].split("function openLibrary", 1)[0]
        self.assertIn("createSimulationRequestSnapshot", run)
        self.assertIn("shouldCommitSimulationRequest", run)
        self.assertIn("HudSystem?.renderState", run)
        self.assertIn("renderSimulatorLoadState", run)
        self.assertIn('simulatorLoadStates.acquire("simulation")', run)
        self.assertIn("stateRevision: browserRuntime.stateRevision", run)
        self.assertIn('releaseSimulatorLoadState("simulation", ownerToken)', run)
        self.assertIn("finally", run)
        self.assertNotIn(
            "simulationCompletionEvent(\n      response,\n      repeat,\n      browserRuntime.sourceContext",
            run,
        )

    def test_simulator_input_identity_and_external_ownership_are_centralized(self):
        script = WORKBENCH.read_text(encoding="utf-8")
        dispatch = script.split(
            "function dispatch(action, render = true)", 1
        )[1].split("function renderEngineBadge", 1)[0]
        load_template = script.split(
            "export function loadTemplateAt", 1
        )[1].split("function importTemplateFromDialog", 1)[0]
        load_lineup = script.split(
            "async function loadLineup", 1
        )[1].split("function getWorkbenchState", 1)[0]

        self.assertIn("simulationActionAffectsRunIdentity(action)", dispatch)
        self.assertIn("browserRuntime.stateRevision += 1", dispatch)
        self.assertIn("simulationSourceContextAfterAction", dispatch)
        self.assertIn(
            'dispatchFn({ type: "loadTemplate", template }, false)',
            load_template,
        )
        self.assertNotIn("browserRuntime.state =", load_template)
        self.assertIn(
            'dispatch({ type: "loadLineup", side, lineup }, false)',
            load_lineup,
        )
        self.assertIn(
            "browserRuntime.sourceContext = createSourceContext(options)",
            load_lineup,
        )

    def test_initializer_uses_retryable_generation_lifecycle_and_reset_invalidation(self):
        script = WORKBENCH.read_text(encoding="utf-8")
        initialize = script.split(
            "const simulatorInitializer = createSimulatorInitializer", 1
        )[1].split("async function loadLineup", 1)[0]
        reset = script.split(
            '} else if (action === "reset") {', 1
        )[1].split('} else if (action === "run") {', 1)[0]

        self.assertIn("renderInitializationState", initialize)
        self.assertIn("simulatorLoadStates", script)
        self.assertIn('renderSimulatorLoadState("initialization"', script)
        self.assertIn("hasState: () => Boolean(browserRuntime.state)", initialize)
        self.assertIn("prepareState", initialize)
        self.assertIn("commit", initialize)
        self.assertIn("simulatorInitializer.initialize()", initialize)
        self.assertIn("HudSystem?.renderState", script)
        self.assertIn("actionLabel: visibleState.actionLabel", script)
        self.assertIn("simulatorInitializer.invalidate()", reset)
        self.assertIn("browserRuntime.requestRevision += 1", reset)
        self.assertIn("browserRuntime.loading = false", reset)
        self.assertIn("browserRuntime.sourceContext = null", reset)
        self.assertIn('renderSimulationLoadState(ownerToken, "success")', reset)

    def test_dashboard_e2e_tracks_semantic_event_lifecycle(self):
        script = E2E.read_text(encoding="utf-8")

        self.assertNotIn("hud-pulse-success", script)
        self.assertIn("hud-event-success", script)
        self.assertIn("hud-event-simulation-completed", script)
        self.assertIn("stzb:simulation-completed", script)
        self.assertIn("simulationCompletedCount", script)


if __name__ == "__main__":
    unittest.main()
