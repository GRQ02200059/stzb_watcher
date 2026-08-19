import tempfile
import unittest
from pathlib import Path

from protocol_evidence.client_source import (
    extract_command_constants,
    validate_source_anchor,
)


class ProtocolEvidenceClientSourceTest(unittest.TestCase):
    def test_extracts_aliases_and_ignores_non_constants(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "NetCommandDef.cs"
            source.write_text(
                "public const int B = 103;\n"
                "public const int A = 103;\n"
                "private const int PRIVATE = 104;\n"
                "int local = 103;\n",
                encoding="utf-8",
            )
            result = extract_command_constants(source)
        self.assertEqual({103: ["A", "B"]}, result)

    def test_rejects_conflicting_duplicate_constant_names(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "NetCommandDef.cs"
            source.write_text(
                "public const int DUP = 1;\npublic const int DUP = 2;\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "conflicting command constant"):
                extract_command_constants(source)

    def test_validates_relative_existing_bounded_anchor(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "Game" / "Commands.cs"
            source.parent.mkdir()
            source.write_text("one\ntwo\nthree\nfour\nfive\n", encoding="utf-8")
            validate_source_anchor(
                root,
                {"file": "Game/Commands.cs", "lines": [2, 5]},
            )

    def test_rejects_unsafe_or_invalid_anchor(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "Commands.cs"
            source.write_text("one\ntwo\n", encoding="utf-8")
            cases = (
                {"file": str(source.resolve()), "lines": [1, 1]},
                {"file": "../Commands.cs", "lines": [1, 1]},
                {"file": "missing.cs", "lines": [1, 1]},
                {"file": "Commands.cs", "lines": [0, 1]},
                {"file": "Commands.cs", "lines": [2, 1]},
                {"file": "Commands.cs", "lines": [1, 3]},
            )
            for anchor in cases:
                with self.subTest(anchor=anchor):
                    with self.assertRaises(ValueError):
                        validate_source_anchor(root, anchor)


if __name__ == "__main__":
    unittest.main()
