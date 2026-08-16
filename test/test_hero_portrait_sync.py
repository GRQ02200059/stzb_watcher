import stat
import tempfile
import unittest
from pathlib import Path

from scripts.sync_hero_portraits import (
    XOR_KEY,
    decode_client_jpeg,
    load_portrait_mappings,
    sync_portraits,
)


class HeroPortraitSyncTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.target = self.root / "target"
        self.source.mkdir()
        self.hero_table = self.root / "hero_table.csv"
        self.hero_table.write_text(
            "heroid,icon_hero_id,is_release,name\n"
            "100001,100900,1,甲\n"
            "100002,100900,1,乙\n"
            "100003,0,1,丙\n"
            "100004,0,0,未发布\n",
            encoding="utf-8",
        )
        self.jpeg = b"\xff\xd8\xff\xe0fixture-jpeg\xff\xd9"
        for image_id in (100900, 100003):
            encoded = bytes(
                value ^ XOR_KEY[index % len(XOR_KEY)]
                for index, value in enumerate(self.jpeg)
            )
            (self.source / f"big_card_{image_id}.jpg").write_bytes(encoded)

    def tearDown(self):
        self.temp.cleanup()

    def _fake_cwebp(self):
        path = self.root / "fake-cwebp"
        path.write_text(
            "#!/usr/bin/env python3\n"
            "import pathlib,sys\n"
            "source = pathlib.Path(sys.argv[-3])\n"
            "output = pathlib.Path(sys.argv[sys.argv.index('-o') + 1])\n"
            "data = source.read_bytes()\n"
            "if not data.startswith(b'\\xff\\xd8'):\n"
            "    raise SystemExit(2)\n"
            "output.write_bytes(b'RIFFfixtureWEBP')\n",
            encoding="utf-8",
        )
        path.chmod(path.stat().st_mode | stat.S_IEXEC)
        return str(path)

    def test_decode_client_jpeg_uses_verified_cycle_xor_key(self):
        encoded = bytes(
            value ^ XOR_KEY[index % len(XOR_KEY)]
            for index, value in enumerate(self.jpeg)
        )

        self.assertEqual(self.jpeg, decode_client_jpeg(encoded))

    def test_decode_client_jpeg_appends_missing_eoi_once(self):
        encoded = bytes(
            value ^ XOR_KEY[index % len(XOR_KEY)]
            for index, value in enumerate(self.jpeg[:-2])
        )

        self.assertEqual(self.jpeg, decode_client_jpeg(encoded))

    def test_icon_id_precedes_hero_id_and_deduplicates_assets(self):
        mappings = load_portrait_mappings(self.hero_table, self.source)

        by_hero = {row["heroId"]: row for row in mappings}
        self.assertEqual(100900, by_hero[100001]["iconId"])
        self.assertEqual(100900, by_hero[100002]["iconId"])
        self.assertEqual(100003, by_hero[100003]["iconId"])
        self.assertEqual(
            {100900, 100003},
            {row["iconId"] for row in mappings if row["sourceExists"]},
        )

    def test_sync_writes_unique_assets_manifest_and_placeholder(self):
        manifest = sync_portraits(
            self.source,
            self.hero_table,
            self.target,
            cwebp=self._fake_cwebp(),
        )

        self.assertEqual(1, manifest["schemaVersion"])
        self.assertEqual(2, manifest["assetCount"])
        self.assertEqual(3, manifest["heroCount"])
        self.assertTrue((self.target / "cards/100900.webp").is_file())
        self.assertTrue((self.target / "cards/100003.webp").is_file())
        self.assertTrue((self.target / "placeholder.svg").is_file())
        self.assertTrue((self.target / "manifest.json").is_file())
        self.assertEqual(
            manifest["heroes"]["100001"]["iconId"],
            manifest["heroes"]["100002"]["iconId"],
        )

    def test_check_rejects_modified_output(self):
        converter = self._fake_cwebp()
        sync_portraits(
            self.source,
            self.hero_table,
            self.target,
            cwebp=converter,
        )
        (self.target / "cards/100900.webp").write_bytes(b"drift")

        with self.assertRaisesRegex(ValueError, "portrait output drift"):
            sync_portraits(
                self.source,
                self.hero_table,
                self.target,
                cwebp=converter,
                check=True,
            )

    def test_invalid_decoded_jpeg_is_recorded_without_stopping_other_assets(self):
        (self.source / "big_card_100003.jpg").write_bytes(b"invalid")

        manifest = sync_portraits(
            self.source,
            self.hero_table,
            self.target,
            cwebp=self._fake_cwebp(),
        )

        self.assertEqual(1, manifest["assetCount"])
        self.assertEqual(1, len(manifest["errors"]))
        self.assertEqual(100003, manifest["errors"][0]["iconId"])
        self.assertFalse(manifest["heroes"]["100003"]["local"])


if __name__ == "__main__":
    unittest.main()
