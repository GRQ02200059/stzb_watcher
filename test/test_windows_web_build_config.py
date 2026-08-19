import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class WindowsWebBuildConfigTest(unittest.TestCase):
    def test_spec_resolves_launcher_from_repository_root(self):
        spec = (ROOT / "packaging/pyinstaller/stzb-web.spec").read_text(encoding="utf-8")
        self.assertIn("SPECPATH", spec)
        self.assertIn("ROOT / \"run_web_exe.py\"", spec)
        self.assertIn("ROOT / \"static\"", spec)

    def test_powershell_uses_join_path_and_single_file_output(self):
        script = (ROOT / "packaging/scripts/build_web_exe.ps1").read_text(encoding="utf-8")
        self.assertIn("Join-Path", script)
        self.assertIn("STZB助手-Web.exe", script)
        self.assertNotIn("distSTZB助手-Web", script)

    def test_workflow_uploads_single_file(self):
        workflow = (ROOT / ".github/workflows/build-windows-web.yml").read_text(encoding="utf-8")
        self.assertIn("dist/STZB助手-Web.exe", workflow)


if __name__ == "__main__":
    unittest.main()
