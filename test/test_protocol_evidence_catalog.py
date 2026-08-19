import json
import tempfile
import unittest
from pathlib import Path

from protocol_evidence.catalog import (
    decimal_command_id,
    normalize_hex_id,
    scan_capture_inventory,
)


class ProtocolEvidenceCatalogTest(unittest.TestCase):
    def test_normalizes_hex_and_decimal_ids(self):
        self.assertEqual("00000067", normalize_hex_id("67"))
        self.assertEqual("0000008f", normalize_hex_id("0000008F"))
        self.assertEqual(103, decimal_command_id("00000067"))
        self.assertEqual(143, decimal_command_id("8f"))

    def test_rejects_invalid_command_ids(self):
        for value in ("", "not-hex", "123456789", "-1", "0x67"):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    normalize_hex_id(value)

    def test_inventory_is_sorted_and_uses_relative_paths(self):
        with tempfile.TemporaryDirectory() as temp:
            repo_root = Path(temp)
            capture_root = repo_root / "capture_new"
            first = capture_root / "00000067"
            second = capture_root / "0000008f"
            first.mkdir(parents=True)
            second.mkdir()
            (first / "cap_2_00000067_zlib.json").write_text(
                json.dumps([[1, "member"]]), encoding="utf-8"
            )
            (first / "cap_1_00000067_zlib.json").write_text(
                json.dumps([[2, "member"]]), encoding="utf-8"
            )
            (second / "cap_1_0000008f_zlib_raw.txt").write_text(
                "raw", encoding="utf-8"
            )

            rows = scan_capture_inventory(capture_root)

        self.assertEqual(["00000067", "0000008f"], [row["hexId"] for row in rows])
        self.assertEqual(103, rows[0]["decimalId"])
        self.assertEqual(2, rows[0]["count"])
        self.assertEqual(["zlib"], rows[0]["decodeKinds"])
        self.assertEqual(
            [
                "capture_new/00000067/cap_1_00000067_zlib.json",
                "capture_new/00000067/cap_2_00000067_zlib.json",
            ],
            rows[0]["samplePaths"],
        )
        self.assertEqual(["zlib_raw"], rows[1]["decodeKinds"])

    def test_inventory_rejects_invalid_command_directory(self):
        with tempfile.TemporaryDirectory() as temp:
            capture_root = Path(temp) / "capture_new"
            (capture_root / "bad-command").mkdir(parents=True)
            with self.assertRaisesRegex(ValueError, "invalid command directory"):
                scan_capture_inventory(capture_root)


if __name__ == "__main__":
    unittest.main()
