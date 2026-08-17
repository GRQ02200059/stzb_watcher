import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class DashboardVendorAssetsTest(unittest.TestCase):
    def test_dashboard_uses_local_export_libraries(self):
        html = (ROOT / "static/dashboard.html").read_text(encoding="utf-8")
        self.assertNotIn("fonts.googleapis.com", html)
        self.assertNotIn("cdn.jsdelivr.net/npm/exceljs", html)
        self.assertNotIn("cdn.jsdelivr.net/npm/jspdf", html)
        for asset in (
            "vendor/exceljs-4.4.0.min.js",
            "vendor/jspdf-2.5.1.umd.min.js",
            "vendor/jspdf-autotable-3.8.2.min.js",
        ):
            self.assertIn(f"/static/{asset}", html)
            path = ROOT / "static" / asset
            self.assertTrue(path.exists(), asset)
            self.assertGreater(path.stat().st_size, 10_000)

    def test_vendor_manifest_records_versions_and_licenses(self):
        manifest = (ROOT / "static/vendor/README.md").read_text(encoding="utf-8")
        for package in ("ExcelJS 4.4.0", "jsPDF 2.5.1", "jsPDF-AutoTable 3.8.2"):
            self.assertIn(package, manifest)
        for license_file in (
            "exceljs.LICENSE",
            "jspdf.LICENSE",
            "jspdf-autotable.LICENSE",
        ):
            self.assertTrue((ROOT / "static/vendor" / license_file).exists())


if __name__ == "__main__":
    unittest.main()
