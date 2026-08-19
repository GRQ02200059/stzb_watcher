import json
import tempfile
import unittest
from pathlib import Path

from protocol_evidence.evidence import load_evidence_files


class ProtocolEvidenceOverlayTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.client_root = self.root / "client"
        self.evidence_root = self.root / "evidence"
        self.client_root.mkdir()
        self.evidence_root.mkdir()
        source = self.client_root / "Game" / "Commands.cs"
        source.parent.mkdir()
        source.write_text("one\ntwo\nthree\n", encoding="utf-8")
        self.constants = {103: ["UNION_MEMBER_LIST"]}

    def tearDown(self):
        self.temp.cleanup()

    def write_evidence(self, command):
        (self.evidence_root / "commands.json").write_text(
            json.dumps({"commands": [command]}), encoding="utf-8"
        )

    def valid_command(self):
        return {
            "hexId": "00000067",
            "decimalId": 103,
            "names": ["UNION_MEMBER_LIST"],
            "evidence": "CLIENT_CONFIRMED",
            "webStatus": "typed",
            "androidStatus": "typed",
            "clientSources": [
                {"file": "Game/Commands.cs", "lines": [1, 2]}
            ],
            "fields": [
                {
                    "path": "[10]",
                    "name": "memberWuxun",
                    "rawTypes": ["integer"],
                    "nullable": False,
                    "unit": "points",
                    "evidence": "CLIENT_CONFIRMED",
                    "businessApproved": True,
                    "clientSources": [
                        {"file": "Game/Commands.cs", "lines": [2, 3]}
                    ],
                }
            ],
        }

    def test_loads_and_normalizes_valid_evidence(self):
        self.write_evidence(self.valid_command())
        result = load_evidence_files(
            self.evidence_root, self.client_root, self.constants
        )
        self.assertEqual(["UNION_MEMBER_LIST"], result["00000067"]["names"])
        self.assertEqual("memberWuxun", result["00000067"]["fields"][0]["name"])

    def test_rejects_invalid_evidence_level(self):
        command = self.valid_command()
        command["evidence"] = "GUESSED"
        self.write_evidence(command)
        with self.assertRaisesRegex(ValueError, "evidence level"):
            load_evidence_files(self.evidence_root, self.client_root, self.constants)

    def test_rejects_hex_decimal_or_constant_mismatch(self):
        command = self.valid_command()
        command["decimalId"] = 104
        self.write_evidence(command)
        with self.assertRaisesRegex(ValueError, "command id mismatch"):
            load_evidence_files(self.evidence_root, self.client_root, self.constants)

    def test_rejects_duplicate_field_paths(self):
        command = self.valid_command()
        command["fields"].append(dict(command["fields"][0]))
        self.write_evidence(command)
        with self.assertRaisesRegex(ValueError, "duplicate field path"):
            load_evidence_files(self.evidence_root, self.client_root, self.constants)

    def test_rejects_unconfirmed_business_field(self):
        command = self.valid_command()
        command["fields"][0]["evidence"] = "IMPLEMENTATION_ASSUMED"
        self.write_evidence(command)
        with self.assertRaisesRegex(ValueError, "business-approved"):
            load_evidence_files(self.evidence_root, self.client_root, self.constants)

    def test_rejects_bad_source_anchor(self):
        command = self.valid_command()
        command["clientSources"][0]["file"] = "missing.cs"
        self.write_evidence(command)
        with self.assertRaisesRegex(ValueError, "does not exist"):
            load_evidence_files(self.evidence_root, self.client_root, self.constants)


if __name__ == "__main__":
    unittest.main()
