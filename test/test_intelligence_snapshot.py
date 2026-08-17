import csv
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from intelligence.rules import land_intelligence_rules, world_scene_schema
from intelligence.snapshot import (
    sha256_file,
    sync_snapshot,
    validate_snapshot_tables,
)


ROOT = Path(__file__).resolve().parents[1]
FIXED_TIME = "2026-08-14T00:00:00+08:00"


class IntelligenceSnapshotTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.source = root / "source"
        self.output = root / "output"
        self.external = root / "external"
        self.source.mkdir()
        self.external.mkdir()
        self._write_fixture_tables()

    def tearDown(self):
        self.temp.cleanup()

    def _write_csv(self, name, headers, rows):
        with (self.source / name).open("w", encoding="utf-8-sig", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=headers)
            writer.writeheader()
            writer.writerows(rows)

    def _write_fixture_tables(self):
        self._write_csv(
            "hero_table.csv",
            [
                "_key",
                "heroid",
                "name",
                "skill_init",
                "attack_base",
                "defence_base",
                "intel_base",
                "speed_base",
                "destroy_base",
            ],
            [{
                "_key": 1,
                "heroid": 100027,
                "name": "张辽",
                "skill_init": 200027,
                "attack_base": 9000,
                "defence_base": 8000,
                "intel_base": 7000,
                "speed_base": 9500,
                "destroy_base": 6000,
            }],
        )
        self._write_csv(
            "skill_table.csv",
            [
                "_key",
                "skill_id",
                "name",
                "main_detail",
                "skill_type",
                "prepare",
                "probability_init",
                "probability_max",
            ],
            [{
                "_key": 200027,
                "skill_id": 200027,
                "name": "其疾如风",
                "main_detail": 20002701,
                "skill_type": 2,
                "prepare": 0,
                "probability_init": 100,
                "probability_max": 100,
            }],
        )
        self._write_csv(
            "skill_detail_table.csv",
            ["_key", "detail_id", "effect_id"],
            [{"_key": 20002701, "detail_id": 20002701, "effect_id": 101}],
        )
        self._write_csv(
            "skill_effect_table.csv",
            ["_key", "effect_id", "name"],
            [{"_key": 101, "effect_id": 101, "name": "攻击属性提高"}],
        )

    def test_sync_copies_only_allowlisted_files_and_records_hashes(self):
        manifest = sync_snapshot(self.source, self.output, FIXED_TIME)
        targets = [item.target for item in manifest.files]
        self.assertEqual(
            targets,
            [
                "hero_table.csv",
                "land_intelligence_rules.json",
                "skill_detail_table.csv",
                "skill_effect_table.csv",
                "skill_table.csv",
                "world_scene_schema.json",
            ],
        )
        first = manifest.files[0]
        self.assertEqual(first.sha256, sha256_file(self.output / first.target))
        self.assertTrue((self.output / "manifest.json").exists())
        self.assertTrue((self.output / "checksums.sha256").exists())

    def test_sync_rejects_symlink_outside_source_root(self):
        (self.source / "hero_table.csv").unlink()
        secret = self.external / "secret.csv"
        secret.write_text("secret", encoding="utf-8")
        (self.source / "hero_table.csv").symlink_to(secret)
        with self.assertRaisesRegex(ValueError, "outside source root"):
            sync_snapshot(self.source, self.output, FIXED_TIME)

    def test_validation_rejects_duplicate_hero_ids(self):
        path = self.source / "hero_table.csv"
        with path.open("a", encoding="utf-8", newline="") as file:
            file.write("2,100027,重复,200027,1,1,1,1,1\n")
        with self.assertRaisesRegex(ValueError, "duplicate hero primary key"):
            validate_snapshot_tables(self.source)

    def test_validation_reports_broken_references(self):
        self._write_csv(
            "skill_table.csv",
            [
                "_key",
                "skill_id",
                "name",
                "main_detail",
                "skill_type",
                "prepare",
                "probability_init",
                "probability_max",
            ],
            [{
                "_key": 200027,
                "skill_id": 200027,
                "name": "其疾如风",
                "main_detail": 29999999,
                "skill_type": 2,
                "prepare": 0,
                "probability_init": 100,
                "probability_max": 100,
            }],
        )
        report = validate_snapshot_tables(self.source)
        self.assertEqual(report.missing_main_details, (29999999,))

    def test_world_schema_distinguishes_baseline_and_delta(self):
        schema = world_scene_schema()
        self.assertEqual(
            schema["slots"]["6"],
            {"5026": "armies", "5028": "armyChanges", "type": "object"},
        )
        self.assertEqual(schema["slots"]["7"]["5028"], "deletedArmies")
        self.assertEqual(schema["slots"]["20"]["5026Type"], "null")
        self.assertEqual(schema["slots"]["20"]["5028Type"], "array[2]")

    def test_land_rules_lock_palette_and_freshness(self):
        rules = land_intelligence_rules()
        self.assertEqual(rules["levelColors"]["9"], "#ff174f")
        self.assertEqual(
            rules["freshness"],
            {"freshSeconds": 120, "staleSeconds": 600},
        )
        self.assertEqual(
            rules["newResLv"],
            {"levelDigit": "tens", "resourceDigit": "ones"},
        )

    def test_cli_check_detects_drift(self):
        sync_snapshot(self.source, self.output, FIXED_TIME)
        (self.output / "hero_table.csv").write_text("changed", encoding="utf-8")
        result = subprocess.run(
            [
                str(ROOT / ".venv/bin/python"),
                "scripts/sync_intelligence_snapshot.py",
                "--source-root",
                str(self.source),
                "--output-root",
                str(self.output),
                "--check",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 1)
        self.assertIn("hero_table.csv checksum drift", result.stderr)


if __name__ == "__main__":
    unittest.main()
