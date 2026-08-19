import json
import tempfile
import unittest
from pathlib import Path

from protocol_evidence.shapes import (
    summarize_command_samples,
    summarize_json_value,
)


class ProtocolEvidenceShapeTest(unittest.TestCase):
    def test_array_shape_contains_types_not_values(self):
        summary = summarize_json_value(
            ["secret-player", 7, None, {"10004": [1, "secret-chat"]}]
        )
        encoded = json.dumps(summary, ensure_ascii=False, sort_keys=True)

        self.assertEqual("array", summary["rootType"])
        self.assertEqual(4, summary["arrayLength"])
        self.assertEqual(["string"], summary["indexTypes"]["0"])
        self.assertEqual(["object"], summary["indexTypes"]["3"])
        self.assertNotIn("secret-player", encoded)
        self.assertNotIn("secret-chat", encoded)
        self.assertNotIn("10004", encoded)

    def test_object_summary_records_cardinality_and_value_types(self):
        summary = summarize_json_value({"uid-1": 1, "uid-2": "name"})
        self.assertEqual("object", summary["rootType"])
        self.assertEqual(2, summary["objectKeyCount"])
        self.assertEqual(["integer", "string"], summary["objectValueTypes"])

    def test_sample_summary_detects_drift_and_invalid_json(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = []
            for name, value in (
                ("one.json", [1, 2]),
                ("two.json", [1, 2, 3]),
            ):
                path = root / name
                path.write_text(json.dumps(value), encoding="utf-8")
                paths.append(name)
            (root / "bad.json").write_text("not json", encoding="utf-8")
            paths.append("bad.json")

            summary = summarize_command_samples(root, paths)

        self.assertTrue(summary["drift"])
        self.assertEqual(2, summary["parsedCount"])
        self.assertEqual(1, summary["invalidCount"])
        self.assertEqual([2, 3], summary["arrayLengths"])
        self.assertEqual(["array"], summary["rootTypes"])

    def test_sample_summary_accepts_client_numeric_object_keys(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "world.txt").write_text(
                '[{},{10496:["secret-player",951330]},null]',
                encoding="utf-8",
            )
            summary = summarize_command_samples(root, ["world.txt"])

        self.assertEqual(1, summary["parsedCount"])
        self.assertEqual(0, summary["invalidCount"])
        self.assertEqual([3], summary["arrayLengths"])

    def test_sample_limit_is_deterministic_but_keeps_total_count(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = []
            for index in range(5):
                path = root / f"{index}.json"
                path.write_text(json.dumps([index]), encoding="utf-8")
                paths.append(path.name)

            summary = summarize_command_samples(root, list(reversed(paths)), limit=2)

        self.assertEqual(5, summary["totalCount"])
        self.assertEqual(2, summary["scannedCount"])
        self.assertEqual(2, summary["parsedCount"])


if __name__ == "__main__":
    unittest.main()
