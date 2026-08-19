import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = [
    ROOT / "data/protocol/client-9.2.2/command-catalog.json",
    ROOT / "data/protocol/client-9.2.2/field-registry.json",
    ROOT / "data/protocol/client-9.2.2/manifest.json",
    ROOT / "docs/verification/protocol-coverage-client-9.2.2.md",
]


class ProtocolEvidencePrivacyTest(unittest.TestCase):
    def test_generated_artifacts_contain_no_absolute_roots_or_secret_keys(self):
        forbidden = (
            "/Users/bytedance",
            "sessionToken",
            "password",
            "passport",
            "role_name",
            "AUTH_TOKEN_PEPPER",
        )
        for path in ARTIFACTS:
            text = path.read_text(encoding="utf-8")
            with self.subTest(path=path.name):
                for value in forbidden:
                    self.assertNotIn(value, text)

    def test_catalog_sample_paths_are_relative_and_under_capture_new(self):
        catalog = json.loads(ARTIFACTS[0].read_text(encoding="utf-8"))
        for command in catalog["commands"]:
            for sample in command["samplePaths"]:
                with self.subTest(sample=sample):
                    self.assertFalse(Path(sample).is_absolute())
                    self.assertTrue(sample.startswith("capture_new/"))
                    self.assertNotIn("..", Path(sample).parts)

    def test_catalog_has_no_payload_value_fields(self):
        catalog = json.loads(ARTIFACTS[0].read_text(encoding="utf-8"))
        serialized = json.dumps(catalog, ensure_ascii=False)
        forbidden_keys = ("payload", "preview", "rawJson", "decodedText", "value")
        for key in forbidden_keys:
            self.assertNotRegex(serialized, rf'"{re.escape(key)}"\s*:')


if __name__ == "__main__":
    unittest.main()
