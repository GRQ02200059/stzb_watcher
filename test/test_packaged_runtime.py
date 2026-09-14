import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from battle_engine_adapter import BattleEngineAdapter


ROOT = Path(__file__).resolve().parents[1]


class PackagedRuntimeTest(unittest.TestCase):
    def test_frozen_app_reads_bundled_resources_from_an_unrelated_working_directory(self):
        with tempfile.TemporaryDirectory(prefix="stzb 中文 path ") as directory:
            code = """
import json, sys
sys.path.insert(0, sys.argv[1])
sys.frozen = True
sys._MEIPASS = sys.argv[1]
sys.executable = sys.argv[2]
import api_server
client = api_server.app.test_client()
for path in ('/', '/api/intelligence/config/manifest',
             '/api/intelligence/research/summary', '/api/simulate/heroes'):
    response = client.get(path)
    assert response.status_code == 200, (path, response.status_code)
    if path != '/':
        assert response.get_json()['ok'], path
print('PACKAGED_RESOURCES_OK')
"""
            result = subprocess.run(
                [sys.executable, "-B", "-c", code, str(ROOT),
                 str(Path(directory) / "STZB助手-Web.exe")],
                cwd=directory, capture_output=True, text=True, timeout=30,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PACKAGED_RESOURCES_OK", result.stdout)
            self.assertFalse((Path(directory) / "data").exists())

    def test_frozen_engine_uses_bundled_java_without_shell_or_host_java(self):
        with tempfile.TemporaryDirectory(prefix="stzb 中文 path ") as directory:
            directory = str(Path(directory).resolve())
            executable = str(Path(directory) / "STZB助手-Web.exe")
            with (
                patch.object(sys, "frozen", True, create=True),
                patch.object(sys, "executable", executable),
                patch.object(sys, "platform", "win32"),
                patch("battle_engine_adapter.subprocess.run") as run,
            ):
                run.return_value = subprocess.CompletedProcess([], 0, '{"ok":true}', '')
                result = BattleEngineAdapter()._run_cli({"repeat": 1})
            self.assertTrue(result["ok"])
            args, kwargs = run.call_args
            self.assertEqual(args[0][0], str(Path(directory) / "runtime/java/bin/java.exe"))
            self.assertIn(str(Path(directory) / "runtime/battle-engine/lib/*"), args[0])
            self.assertIn("com.stzb.battle.cli.BattleEngineCliKt", args[0])
            self.assertEqual(kwargs["encoding"], "utf-8")
            self.assertFalse(kwargs.get("shell", False))
            self.assertEqual(json.loads(kwargs["input"]), {"repeat": 1})


if __name__ == "__main__":
    unittest.main()
