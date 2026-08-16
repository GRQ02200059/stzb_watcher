import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class IntelligenceMapNodeTest(unittest.TestCase):
    def test_intelligence_map_modules(self):
        result = subprocess.run(
            [
                "node",
                "--test",
                "test/js/intelligence-map.test.mjs",
                "test/js/intelligence-map-overview.test.mjs",
                "test/js/intelligence-map-navigation.test.mjs",
            ],
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
