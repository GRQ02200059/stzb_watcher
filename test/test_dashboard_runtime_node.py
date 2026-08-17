import subprocess
import unittest
from pathlib import Path
import re
from collections import Counter


ROOT = Path(__file__).resolve().parents[1]


class DashboardRuntimeNodeTest(unittest.TestCase):
    def test_hud_runtime_has_no_persistent_animation_loop(self):
        source = (ROOT / "static/dashboard-hud.mjs").read_text(encoding="utf-8")
        self.assertNotIn("setInterval(", source)
        self.assertNotRegex(
            source,
            r"requestAnimationFrameFn\s*\(\s*frame\s*\)\s*;\s*"
            r"requestAnimationFrameFn\s*\(\s*frame\s*\)",
        )

    def test_runtime_behavior(self):
        result = subprocess.run(
            ["node", "--test", "test/js/dashboard-runtime.test.mjs"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            result.returncode,
            0,
            msg=f"{result.stdout}\n{result.stderr}",
        )

    def test_app2_has_no_duplicate_top_level_function_names(self):
        source = (ROOT / "static/app2.js").read_text(encoding="utf-8")
        source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
        names = re.findall(
            r"^(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(",
            source,
            re.MULTILINE,
        )
        duplicates = sorted(
            name for name, count in Counter(names).items() if count > 1
        )
        self.assertEqual(duplicates, [])

    def test_hud_runtime_behavior(self):
        result = subprocess.run(
            ["node", "--test", "test/js/dashboard-hud.test.mjs"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            result.returncode,
            0,
            msg=f"{result.stdout}\n{result.stderr}",
        )

    def test_live_army_map_behavior(self):
        result = subprocess.run(
            ["node", "--test", "test/js/live-army-map.test.mjs"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            result.returncode,
            0,
            msg=f"{result.stdout}\n{result.stderr}",
        )

    def test_live_army_command_behavior(self):
        result = subprocess.run(
            ["node", "--test", "test/js/live-army-command.test.mjs"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            result.returncode,
            0,
            msg=f"{result.stdout}\n{result.stderr}",
        )

    def test_research_workbench_behavior(self):
        result = subprocess.run(
            ["node", "--test", "test/js/research-workbench.test.mjs"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            result.returncode,
            0,
            msg=f"{result.stdout}\n{result.stderr}",
        )

    def test_research_templates_behavior(self):
        result = subprocess.run(
            ["node", "--test", "test/js/research-templates.test.mjs"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            result.returncode,
            0,
            msg=f"{result.stdout}\n{result.stderr}",
        )

    def test_research_skill_chain_behavior(self):
        result = subprocess.run(
            ["node", "--test", "test/js/research-skill-chain.test.mjs"],
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
