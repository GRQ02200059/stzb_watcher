import json
import unittest
from pathlib import Path

from protocol_evidence.client_source import validate_source_anchor


ROOT = Path(__file__).resolve().parents[1]
CAPTURE_ROOT = ROOT / "capture_new"
ARTIFACT_ROOT = ROOT / "data/protocol/client-9.2.2"
CLIENT_ROOT = Path(
    "/Users/bytedance/stzb/"
    "stzb_9.2.2_out_branch_9.1.1776213/assets/decompiled"
)


class ProtocolEvidenceRealCaptureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = json.loads(
            (ARTIFACT_ROOT / "command-catalog.json").read_text(encoding="utf-8")
        )
        cls.registry = json.loads(
            (ARTIFACT_ROOT / "field-registry.json").read_text(encoding="utf-8")
        )
        cls.commands = cls.catalog["commands"]

    def test_all_capture_directories_are_cataloged(self):
        actual = sorted(path.name for path in CAPTURE_ROOT.iterdir() if path.is_dir())
        cataloged = [row["hexId"] for row in self.commands]
        self.assertEqual(94, len(actual))
        self.assertEqual(actual, cataloged)

    def test_every_typed_command_has_a_valid_real_sample(self):
        for row in self.commands:
            if "typed" not in {row["webStatus"], row["androidStatus"]}:
                continue
            with self.subTest(command=row["hexId"]):
                self.assertGreater(row["shape"]["parsedCount"], 0)
                self.assertTrue(row["samplePaths"])

    def test_every_client_source_anchor_exists(self):
        for row in self.commands:
            for anchor in row["clientSources"]:
                with self.subTest(command=row["hexId"], anchor=anchor):
                    validate_source_anchor(CLIENT_ROOT, anchor)
        for field in self.registry["fields"]:
            for anchor in field["clientSources"]:
                with self.subTest(field=field["name"], anchor=anchor):
                    validate_source_anchor(CLIENT_ROOT, anchor)

    def test_world_scene_samples_use_31_slots_when_valid(self):
        by_hex = {row["hexId"]: row for row in self.commands}
        for command in ("000013a2", "000013a4"):
            with self.subTest(command=command):
                row = by_hex[command]
                self.assertIn(31, row["shape"]["arrayLengths"])
                self.assertEqual(["array"], row["shape"]["rootTypes"])

    def test_hex_decimal_command_parity(self):
        for row in self.commands:
            with self.subTest(command=row["hexId"]):
                self.assertEqual(int(row["hexId"], 16), row["decimalId"])

    def test_confirmed_member_wuxun_field_exists(self):
        fields = {
            (field["hexId"], field["path"]): field
            for field in self.registry["fields"]
        }
        field = fields[("00000067", "[][10]")]
        self.assertEqual("memberWuxun", field["name"])
        self.assertEqual("CLIENT_CONFIRMED", field["evidence"])
        self.assertTrue(field["businessApproved"])

    def test_all_existing_typed_parser_commands_have_evidence(self):
        expected = {
            "0000000a", "00000015", "0000005c", "00000067",
            "000001fe", "0000029f", "0000030c", "00000834",
            "00000898", "000013a2", "000013a4", "000018aa",
            "00015f95",
        }
        by_hex = {row["hexId"]: row for row in self.commands}
        for command in expected:
            with self.subTest(command=command):
                self.assertIn(command, by_hex)
                self.assertEqual("CLIENT_CONFIRMED", by_hex[command]["evidence"])
                self.assertIn(
                    "typed",
                    {by_hex[command]["webStatus"], by_hex[command]["androidStatus"]},
                )


if __name__ == "__main__":
    unittest.main()
