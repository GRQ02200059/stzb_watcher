import json
import tempfile
import unittest
from pathlib import Path

from protocol_evidence.build import build_protocol_evidence, check_protocol_evidence


class ProtocolEvidenceBuildTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.capture = self.root / "capture_new"
        self.client = self.root / "client"
        self.evidence = self.root / "evidence"
        self.output = self.root / "output"
        self.report = self.root / "coverage.md"
        self.android_contract = self.root / "protocol_contract.json"
        (self.capture / "00000067").mkdir(parents=True)
        (self.capture / "00000067" / "cap_1_00000067_zlib.json").write_text(
            json.dumps([[1, "fixture-secret", 2]]), encoding="utf-8"
        )
        command_def = self.client / "Game.Network" / "Tenth.Network" / "NetCommandDef.cs"
        command_def.parent.mkdir(parents=True)
        command_def.write_text(
            "public const int UNION_MEMBER_LIST = 103;\n", encoding="utf-8"
        )
        source = self.client / "Game" / "UnionData.cs"
        source.parent.mkdir()
        source.write_text("one\ntwo\nthree\n", encoding="utf-8")
        self.evidence.mkdir()
        (self.evidence / "core.json").write_text(
            json.dumps(
                {
                    "commands": [
                        {
                            "hexId": "00000067",
                            "decimalId": 103,
                            "names": ["UNION_MEMBER_LIST"],
                            "evidence": "CLIENT_CONFIRMED",
                            "webStatus": "typed",
                            "androidStatus": "typed",
                            "clientSources": [
                                {"file": "Game/UnionData.cs", "lines": [1, 2]}
                            ],
                            "fields": [],
                        }
                    ]
                }
            ),
            encoding="utf-8",
        )

    def tearDown(self):
        self.temp.cleanup()

    def build(self):
        return build_protocol_evidence(
            self.capture,
            self.client,
            self.evidence,
            self.output,
            self.report,
            client_version="9.2.2",
            android_contract_path=self.android_contract,
        )

    def test_two_builds_are_byte_identical_and_private(self):
        self.build()
        first = {
            path.relative_to(self.root).as_posix(): path.read_bytes()
            for path in sorted([*self.output.glob("*"), self.report, self.android_contract])
        }
        self.build()
        second = {
            path.relative_to(self.root).as_posix(): path.read_bytes()
            for path in sorted([*self.output.glob("*"), self.report, self.android_contract])
        }
        self.assertEqual(first, second)
        encoded = b"\n".join(first.values()).decode("utf-8")
        self.assertNotIn("fixture-secret", encoded)
        self.assertNotIn(str(self.root), encoded)

    def test_manifest_hashes_outputs_and_report_has_status_totals(self):
        result = self.build()
        manifest = json.loads((self.output / "manifest.json").read_text())
        self.assertEqual(1, manifest["commandCount"])
        self.assertEqual(1, result["commandCount"])
        self.assertEqual(
            {"typed": 1, "raw": 0, "unsupported": 0},
            manifest["webStatusCounts"],
        )
        self.assertIn("sha256", manifest["files"]["command-catalog.json"])
        report = self.report.read_text(encoding="utf-8")
        self.assertIn("Captured commands | 1", report)
        self.assertIn("Web typed | 1", report)

    def test_check_detects_artifact_drift(self):
        self.build()
        check_protocol_evidence(
            self.capture,
            self.client,
            self.evidence,
            self.output,
            self.report,
            client_version="9.2.2",
            android_contract_path=self.android_contract,
        )
        (self.output / "command-catalog.json").write_text("{}\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "out of date"):
            check_protocol_evidence(
                self.capture,
                self.client,
                self.evidence,
                self.output,
                self.report,
                client_version="9.2.2",
                android_contract_path=self.android_contract,
            )

    def test_android_contract_is_minimal_and_deterministic(self):
        self.build()
        first = self.android_contract.read_bytes()
        self.build()
        self.assertEqual(first, self.android_contract.read_bytes())
        contract = json.loads(first)
        self.assertEqual("9.2.2", contract["clientVersion"])
        self.assertEqual("x=wid/10000,y=wid%10000", contract["conventions"]["wid"])
        self.assertEqual([103], [row["decimalId"] for row in contract["commands"]])
        encoded = first.decode("utf-8")
        self.assertNotIn("samplePaths", encoded)
        self.assertNotIn("clientSources", encoded)


if __name__ == "__main__":
    unittest.main()
