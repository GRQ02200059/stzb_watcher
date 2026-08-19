import json
import tempfile
import unittest
from pathlib import Path

from protocol_registry import ProtocolRegistry


class ProtocolRegistryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        catalog = {
            "clientVersion": "9.2.2",
            "commands": [
                {
                    "hexId": "00000067",
                    "decimalId": 103,
                    "names": ["UNION_MEMBER_LIST"],
                    "evidence": "CLIENT_CONFIRMED",
                    "webStatus": "typed",
                }
            ],
        }
        fields = {
            "clientVersion": "9.2.2",
            "fields": [
                {
                    "hexId": "00000067",
                    "decimalId": 103,
                    "path": "[][10]",
                    "name": "memberWuxun",
                    "evidence": "CLIENT_CONFIRMED",
                    "businessApproved": True,
                },
                {
                    "hexId": "00000067",
                    "decimalId": 103,
                    "path": "[][99]",
                    "name": "guess",
                    "evidence": "IMPLEMENTATION_ASSUMED",
                    "businessApproved": False,
                },
            ],
        }
        catalog_bytes = (json.dumps(catalog, sort_keys=True) + "\n").encode()
        field_bytes = (json.dumps(fields, sort_keys=True) + "\n").encode()
        import hashlib
        manifest = {
            "files": {
                "command-catalog.json": {
                    "sha256": hashlib.sha256(catalog_bytes).hexdigest(),
                    "size": len(catalog_bytes),
                },
                "field-registry.json": {
                    "sha256": hashlib.sha256(field_bytes).hexdigest(),
                    "size": len(field_bytes),
                },
            }
        }
        (self.root / "command-catalog.json").write_bytes(catalog_bytes)
        (self.root / "field-registry.json").write_bytes(field_bytes)
        (self.root / "manifest.json").write_text(
            json.dumps(manifest), encoding="utf-8"
        )

    def tearDown(self):
        self.temp.cleanup()

    def test_hex_and_decimal_lookup_are_equivalent(self):
        registry = ProtocolRegistry(self.root)
        self.assertEqual(registry.command("00000067"), registry.command(103))
        self.assertEqual("UNION_MEMBER_LIST", registry.command("67")["names"][0])
        self.assertIsNone(registry.command(999999))

    def test_field_lookup_and_business_gate(self):
        registry = ProtocolRegistry(self.root)
        field = registry.field(103, "[][10]")
        self.assertEqual("memberWuxun", field["name"])
        self.assertEqual(field, registry.require_business_field("67", "[][10]"))
        with self.assertRaisesRegex(ValueError, "not approved"):
            registry.require_business_field(103, "[][99]")
        with self.assertRaisesRegex(ValueError, "not registered"):
            registry.require_business_field(103, "[][88]")

    def test_returned_values_do_not_mutate_registry(self):
        registry = ProtocolRegistry(self.root)
        command = registry.command(103)
        command["names"].append("MUTATED")
        self.assertEqual(["UNION_MEMBER_LIST"], registry.command(103)["names"])

    def test_rejects_manifest_checksum_mismatch(self):
        (self.root / "command-catalog.json").write_text("{}\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "checksum"):
            ProtocolRegistry(self.root)


if __name__ == "__main__":
    unittest.main()
