import json
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class OperationsHudStaticTest(unittest.TestCase):
    def setUp(self):
        self.html = (ROOT / "static/dashboard.html").read_text(
            encoding="utf-8"
        )
        css_path = ROOT / "static/operations-hud.css"
        self.css = (
            css_path.read_text(encoding="utf-8")
            if css_path.is_file()
            else ""
        )
        self.app2 = (ROOT / "static/app2.js").read_text(encoding="utf-8")
        self.simulator_css = (
            ROOT / "static/simulator-workbench.css"
        ).read_text(encoding="utf-8")

    def test_operations_pages_have_hud_heads_and_panels(self):
        tab16 = self.html.split("id='tab16'", 1)[1].split(
            "id='tab17'", 1
        )[0]
        tab25 = self.html.split('id="tab25"', 1)[1].split(
            "id='tab26'", 1
        )[0]

        for page in (tab16, tab25):
            self.assertIn("hud-page-head", page)
            self.assertIn("hud-panel", page)
        self.assertIn("operation-stage", tab16 + self.css)
        self.assertIn("/static/operations-hud.css", self.html)

    def test_attendance_projects_four_read_only_operation_stages(self):
        self.assertIn("function attendanceStage", self.app2)
        self.assertIn("operation-stage-strip", self.app2)
        for stage in ("preparing", "assembling", "executing", "complete"):
            self.assertIn(stage, self.app2)

    def test_attendance_emits_only_real_stage_changes(self):
        self.assertIn("operation:stage-changed", self.app2)
        self.assertIn("function operationStageEvents", self.app2)
        self.assertIn("operationStageEvents(", self.app2)
        self.assertIn("HudSystem?.emit", self.app2)

    def test_task_renderer_uses_model_backed_delegated_actions(self):
        start = self.app2.index("async function loadTasks")
        end = self.app2.index("\nasync function viewTaskDetail", start)
        renderer = self.app2[start:end]

        self.assertIn("_taskModels", self.app2)
        self.assertIn("bindTaskActions", self.app2)
        self.assertIn("data-task-index", renderer)
        self.assertIn("data-task-action", renderer)
        self.assertIn("closest?.('[data-task-action]')", self.app2)
        self.assertNotIn("onclick=", renderer)
        self.assertNotIn("data-task-id", renderer)
        self.assertNotRegex(
            renderer,
            r"\[data-task-(?:id|index)=['\"]?\$\{t\.id\}",
        )

    def test_attendance_stage_event_state_machine_behavior(self):
        node_script = r"""
const fs = require("fs");
const vm = require("vm");
const source = fs.readFileSync("static/app2.js", "utf8");
const start = source.indexOf("function taskStageKey");
const end = source.indexOf("\nasync function loadTasks", start);
if (start < 0 || end < 0) {
  throw new Error("missing task stage helpers");
}
const context = {};
vm.runInNewContext(source.slice(start, end), context);
const maliciousId = String.raw`task'"\) ; window.__taskXss=1;//`;
const task = (id, index, name, stage, target = null) => ({
  key: context.taskStageKey({id}, index),
  index,
  name,
  stage,
  target,
});
const run = (previous, current, initialized) =>
  context.operationStageEvents(previous, current, initialized);
const previous = new Map([
  ["id-1", "preparing"],
  ["index-2", "preparing"],
]);
const marker = {nodeType: 1};
const changed = run(
  previous,
  [task(1, 0, "首攻", "assembling", marker)],
  true,
);
const maliciousChanged = run(
  previous,
  [task(maliciousId, 2, "恶意任务", "assembling", marker)],
  true,
);
const cases = {
  initial: run(new Map(), [task(1, 0, "首攻", "preparing")], false),
  newTask: run(previous, [
    task(1, 0, "首攻", "preparing"),
    task(2, 1, "增援", "assembling"),
  ], true),
  sameStage: run(previous, [task(1, 0, "首攻", "preparing")], true),
  changed,
  changedTargetIsMarker: changed[0]?.target === marker,
  numericStableAcrossIndexes:
    context.taskStageKey({id: 1}, 0) ===
    context.taskStageKey({id: 1}, 9),
  maliciousKey: context.taskStageKey({id: maliciousId}, 2),
  maliciousChanged,
  maliciousTargetIsMarker: maliciousChanged[0]?.target === marker,
  maliciousId,
};
process.stdout.write(JSON.stringify(cases));
"""
        result = subprocess.run(
            ["node", "-e", node_script],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        cases = json.loads(result.stdout)
        self.assertEqual(cases["initial"], [])
        self.assertEqual(cases["newTask"], [])
        self.assertEqual(cases["sameStage"], [])
        self.assertEqual(len(cases["changed"]), 1)
        self.assertEqual(
            cases["changed"][0]["type"],
            "operation:stage-changed",
        )
        self.assertTrue(cases["changedTargetIsMarker"])
        self.assertEqual(
            cases["changed"][0]["dedupeKey"],
            "operation-task:id-1:assembling",
        )
        self.assertNotIn("data-task-id", cases["changed"][0])
        self.assertTrue(cases["numericStableAcrossIndexes"])
        self.assertEqual(cases["maliciousKey"], "index-2")
        self.assertTrue(cases["maliciousTargetIsMarker"])
        self.assertEqual(
            cases["maliciousChanged"][0]["dedupeKey"],
            "operation-task:index-2:assembling",
        )
        self.assertNotIn(
            cases["maliciousId"],
            json.dumps(cases["maliciousChanged"], ensure_ascii=False),
        )

    def test_legacy_loader_deferred_node_contract(self):
        result = subprocess.run(
            ["node", "--test", "test/js/legacy-loaders.test.mjs"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)

    def test_operations_css_uses_global_tokens_and_motion_contract(self):
        self.assertNotIn(":root", self.css)
        for selector in (
            ".operations-task-grid",
            ".operation-stage",
            ".operation-progress",
            ".operation-target",
            ".operation-member-chip",
            ".operation-battle-feed",
        ):
            self.assertIn(selector, self.css)
        self.assertIn(
            'body[data-motion-level="standard"] .sim-hero-scan',
            self.simulator_css,
        )
        self.assertIn(
            'body[data-motion-level="reduced"] .sim-hero-scan',
            self.simulator_css,
        )
        self.assertIn(
            '.operation-stage[data-state="active"]',
            self.css,
        )


if __name__ == "__main__":
    unittest.main()
