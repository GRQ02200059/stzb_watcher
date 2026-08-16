import os
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
NODE_MODULES = (
    "/Users/bytedance/.cache/codex-runtimes/"
    "codex-primary-runtime/dependencies/node/node_modules"
)


class DashboardE2ETest(unittest.TestCase):
    def test_dashboard_interactions_in_chrome(self):
        env = dict(os.environ)
        env["NODE_PATH"] = NODE_MODULES
        result = subprocess.run(
            ["node", "test/js/dashboard-e2e.mjs"],
            cwd=ROOT,
            env=env,
            capture_output=True,
            text=True,
            timeout=120,
        )
        self.assertEqual(
            result.returncode,
            0,
            msg=f"{result.stdout}\n{result.stderr}",
        )


if __name__ == "__main__":
    unittest.main()
