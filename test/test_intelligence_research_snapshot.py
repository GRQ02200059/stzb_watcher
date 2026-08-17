import hashlib
import json
import struct
import subprocess
import tempfile
import unittest
from pathlib import Path

from intelligence.research_snapshot import (
    SCHEMA_TABLE_ALLOWLIST,
    build_protocol_catalog,
    build_research_snapshot,
    parse_card_pack_tables,
    select_table_fields,
)


FIXED_TIME = "2026-08-15T00:00:00+08:00"
ROOT = Path(__file__).resolve().parents[1]


def _i(value):
    return struct.pack("<i", value)


def _memory_pack_table(keys, rows, string_count=0):
    return b"".join([
        _i(4),
        _i(string_count),
        b"\x02",
        _i(len(keys)),
        b"".join(_i(key) for key in keys),
        _i(len(keys)),
        b"".join(rows),
    ])


def _card_extract_row(pack_id, parent_id=0, container_id=0, priority=0):
    return b"".join([
        b"\x3e",
        _i(pack_id),
        _i(parent_id),
        _i(container_id),
        b"".join(_i(0) for _ in range(13)),
        _i(priority),
        b"".join(_i(0) for _ in range(10)),
        b"\x00" * 17,
        _i(0),
        _i(0),
        _i(0),
        _i(0),
        b"".join(_i(0) for _ in range(14)),
    ])


def _card_prob_row(pack_id, hero_id):
    return b"\x01" + _i(pack_id * 1_000_000 + hero_id)


class IntelligenceResearchSnapshotTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.source = Path(self.temp.name) / "source"
        self.output = Path(self.temp.name) / "output"
        (self.source / "client-config").mkdir(parents=True)
        (self.source / "protocol").mkdir()

    def tearDown(self):
        self.temp.cleanup()

    def _write_card_tables(self):
        config = self.source / "client-config"
        config.joinpath("tb_cfg_card_extract.bin").write_bytes(
            _memory_pack_table(
                [100, 101],
                [
                    _card_extract_row(100, priority=1),
                    _card_extract_row(101, parent_id=100, priority=2),
                ],
            )
        )
        config.joinpath("tb_cfg_card_prob.bin").write_bytes(
            _memory_pack_table(
                [101_100027, 101_100016],
                [
                    _card_prob_row(101, 100027),
                    _card_prob_row(101, 100016),
                ],
            )
        )

    def _write_protocol_tables(self):
        old = {
            "clientVersion": "9.2.2",
            "commands": [
                {
                    "id": 10,
                    "names": ["OLD"],
                    "requestSources": ["A.cs:10"],
                    "receiveSources": [],
                    "captureSendCount": 1,
                    "captureReceiveCount": 2,
                },
                {
                    "id": 20,
                    "names": ["REMOVED"],
                    "requestSources": [],
                    "receiveSources": [],
                    "captureSendCount": 0,
                    "captureReceiveCount": 0,
                },
                {
                    "id": 30,
                    "names": ["STABLE"],
                    "requestSources": ["Stable.cs:100"],
                    "receiveSources": [],
                    "captureSendCount": 0,
                    "captureReceiveCount": 0,
                },
            ],
        }
        new = {
            "clientVersion": "9.2.4",
            "commands": [
                {
                    "id": 10,
                    "names": ["NEW"],
                    "requestSources": ["A.cs:12"],
                    "receiveSources": [],
                    "captureSendCount": 1,
                    "captureReceiveCount": 2,
                },
                {
                    "id": 30,
                    "names": ["STABLE"],
                    "requestSources": ["Stable.cs:200"],
                    "receiveSources": [],
                    "captureSendCount": 0,
                    "captureReceiveCount": 0,
                },
                {
                    "id": 40,
                    "names": ["ADDED"],
                    "requestSources": [],
                    "receiveSources": [],
                    "captureSendCount": 0,
                    "captureReceiveCount": 0,
                },
            ],
        }
        (self.source / "protocol/client-9.2.2-command-inventory.json").write_text(
            json.dumps(old), encoding="utf-8"
        )
        (self.source / "protocol/client-9.2.4-command-inventory.json").write_text(
            json.dumps(new), encoding="utf-8"
        )
        return old, new

    def _write_field_types(self):
        payload = {
            name: [["id", "int"], ["name", "string"]]
            for name in SCHEMA_TABLE_ALLOWLIST
        }
        payload["Tb_private_debug"] = [["token", "string"]]
        (self.source / "tb_field_types.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )
        return payload

    def test_card_pack_parser_merges_child_pool(self):
        self._write_card_tables()

        packs = parse_card_pack_tables(self.source / "client-config")

        self.assertEqual([row["packId"] for row in packs], [100, 101])
        self.assertEqual(packs[0]["heroIds"], [100016, 100027])
        self.assertEqual(packs[0]["heroCount"], 2)
        self.assertEqual(packs[1]["parentPackId"], 100)
        self.assertEqual(packs[1]["sourceConfigs"], ["default"])

    def test_card_pack_parser_accepts_negative_empty_string_count(self):
        config = self.source / "client-config"
        config.joinpath("tb_cfg_card_extract.bin").write_bytes(
            _memory_pack_table(
                [101],
                [_card_extract_row(101)],
                string_count=-1,
            )
        )
        config.joinpath("tb_cfg_card_prob.bin").write_bytes(
            _memory_pack_table(
                [101_100027],
                [_card_prob_row(101, 100027)],
                string_count=-1,
            )
        )

        packs = parse_card_pack_tables(config)

        self.assertEqual(packs[0]["heroIds"], [100027])

    def test_protocol_diff_ignores_source_line_drift(self):
        old, new = self._write_protocol_tables()

        catalog = build_protocol_catalog(old, new)

        self.assertEqual(catalog["diff"]["summary"], {
            "added": 1,
            "removed": 1,
            "renamed": 1,
        })
        self.assertEqual([row["id"] for row in catalog["diff"]["added"]], [40])
        self.assertEqual([row["id"] for row in catalog["diff"]["removed"]], [20])
        self.assertEqual([row["id"] for row in catalog["diff"]["renamed"]], [10])
        self.assertNotIn(30, {
            row["id"]
            for kind in ("added", "removed", "renamed")
            for row in catalog["diff"][kind]
        })

    def test_schema_export_is_exact_allowlist(self):
        fields = self._write_field_types()

        selected = select_table_fields(fields)

        self.assertEqual(set(selected["tables"]), set(SCHEMA_TABLE_ALLOWLIST))
        self.assertNotIn("Tb_private_debug", selected["tables"])
        self.assertEqual(
            selected["tables"]["Tb_world_city"]["fields"][1],
            {"index": 1, "name": "name", "type": "string"},
        )

    def test_snapshot_writes_manifest_and_checksums(self):
        self._write_card_tables()
        self._write_protocol_tables()
        self._write_field_types()

        result = build_research_snapshot(
            self.source,
            self.output,
            FIXED_TIME,
        )

        self.assertEqual(result["validation"]["cardPackCount"], 2)
        self.assertEqual(result["validation"]["schemaTableCount"], 12)
        for name in (
            "card_packs.json",
            "protocol_commands.json",
            "table_fields.json",
            "manifest.json",
            "checksums.sha256",
        ):
            self.assertTrue((self.output / name).is_file(), name)
        manifest = json.loads(
            (self.output / "manifest.json").read_text(encoding="utf-8")
        )
        for item in manifest["files"]:
            path = self.output / item["target"]
            self.assertEqual(
                item["sha256"],
                hashlib.sha256(path.read_bytes()).hexdigest(),
            )

    def test_cli_research_only_generates_and_checks_nested_snapshot(self):
        self._write_card_tables()
        self._write_protocol_tables()
        self._write_field_types()
        command = [
            str(ROOT / ".venv/bin/python"),
            "scripts/sync_intelligence_snapshot.py",
            "--research-source-root",
            str(self.source),
            "--output-root",
            str(self.output),
            "--research-only",
        ]

        generated = subprocess.run(
            command,
            cwd=ROOT,
            capture_output=True,
            text=True,
        )

        self.assertEqual(generated.returncode, 0, generated.stderr)
        research_root = self.output / "research"
        self.assertTrue((research_root / "manifest.json").is_file())
        checked = subprocess.run(
            [
                str(ROOT / ".venv/bin/python"),
                "scripts/sync_intelligence_snapshot.py",
                "--output-root",
                str(self.output),
                "--check",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(checked.returncode, 0, checked.stderr)
        (research_root / "card_packs.json").write_text(
            "changed", encoding="utf-8"
        )
        drift = subprocess.run(
            [
                str(ROOT / ".venv/bin/python"),
                "scripts/sync_intelligence_snapshot.py",
                "--output-root",
                str(self.output),
                "--check",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(drift.returncode, 1)
        self.assertIn("research/card_packs.json checksum drift", drift.stderr)


if __name__ == "__main__":
    unittest.main()
