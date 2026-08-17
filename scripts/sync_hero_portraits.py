#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import shutil
import subprocess
import tempfile
from datetime import datetime
from pathlib import Path


XOR_KEY = bytes.fromhex("8e509fe8596791fb")
SCHEMA_VERSION = 1
DEFAULT_QUALITY = 78
DEFAULT_HEIGHT = 720


def decode_client_jpeg(encoded):
    decoded = bytes(
        value ^ XOR_KEY[index % len(XOR_KEY)]
        for index, value in enumerate(encoded)
    )
    if not decoded.startswith(b"\xff\xd8"):
        raise ValueError("decoded portrait is not JPEG")
    if not decoded.endswith(b"\xff\xd9"):
        decoded += b"\xff\xd9"
    return decoded


def load_portrait_mappings(hero_table, source_root):
    hero_table = Path(hero_table)
    source_root = Path(source_root)
    rows = []
    with hero_table.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            if row.get("is_release") != "1":
                continue
            hero_id = int(row.get("heroid") or 0)
            if hero_id <= 0:
                continue
            configured_icon = int(row.get("icon_hero_id") or 0)
            candidates = (
                [configured_icon, hero_id]
                if configured_icon
                else [hero_id]
            )
            icon_id = next(
                (
                    candidate
                    for candidate in candidates
                    if (
                        source_root
                        / ("big_card_%s.jpg" % candidate)
                    ).is_file()
                ),
                candidates[0],
            )
            source = source_root / ("big_card_%s.jpg" % icon_id)
            rows.append(
                {
                    "heroId": hero_id,
                    "iconId": icon_id,
                    "source": str(source),
                    "sourceExists": source.is_file(),
                }
            )
    return rows


def _sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def _sha256_file(path):
    return _sha256_bytes(Path(path).read_bytes())


def _placeholder_svg():
    return """<svg xmlns="http://www.w3.org/2000/svg" width="720" height="720" viewBox="0 0 720 720">
<defs><radialGradient id="g"><stop stop-color="#193653"/><stop offset="1" stop-color="#07101d"/></radialGradient></defs>
<rect width="720" height="720" fill="url(#g)"/>
<path d="M190 500c34-88 93-132 170-132s136 44 170 132" fill="none" stroke="#43d5ff55" stroke-width="18"/>
<circle cx="360" cy="258" r="94" fill="none" stroke="#43d5ff55" stroke-width="18"/>
<text x="360" y="650" text-anchor="middle" fill="#8ca3bb" font-family="monospace" font-size="24" letter-spacing="6">PORTRAIT OFFLINE</text>
</svg>
"""


def _resolve_executable(command):
    path = shutil.which(str(command))
    if not path:
        raise ValueError(
            "cwebp not found; install WebP tools or pass --cwebp"
        )
    return path


def _convert_portrait(decoded, output, cwebp):
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".jpg") as source:
        source.write(decoded)
        source.flush()
        result = subprocess.run(
            [
                cwebp,
                "-quiet",
                "-q",
                str(DEFAULT_QUALITY),
                "-resize",
                "0",
                str(DEFAULT_HEIGHT),
                "-metadata",
                "none",
                source.name,
                "-o",
                str(output),
            ],
            capture_output=True,
            text=True,
            check=False,
        )
    if result.returncode != 0:
        output.unlink(missing_ok=True)
        raise ValueError(
            (result.stderr or result.stdout or "cwebp failed").strip()
        )


def _build_manifest(
    source_root,
    hero_table,
    target_root,
    mappings,
    cwebp,
):
    cards_root = target_root / "cards"
    cards_root.mkdir(parents=True, exist_ok=True)
    heroes = {}
    assets = {}
    errors = []
    icon_results = {}

    for mapping in mappings:
        icon_id = mapping["iconId"]
        if icon_id in icon_results:
            continue
        source = Path(mapping["source"])
        result = {
            "iconId": icon_id,
            "source": str(source.relative_to(source_root))
            if source.is_relative_to(source_root)
            else str(source),
            "sourceExists": source.is_file(),
            "local": False,
        }
        if not source.is_file():
            icon_results[icon_id] = result
            continue
        result["sourceSha256"] = _sha256_file(source)
        try:
            decoded = decode_client_jpeg(source.read_bytes())
            result["decodedSha256"] = _sha256_bytes(decoded)
            output = cards_root / ("%s.webp" % icon_id)
            _convert_portrait(decoded, output, cwebp)
            result.update(
                {
                    "local": True,
                    "output": str(output.relative_to(target_root)),
                    "outputSha256": _sha256_file(output),
                    "outputBytes": output.stat().st_size,
                }
            )
            assets[str(icon_id)] = dict(result)
        except (OSError, ValueError) as exc:
            (cards_root / ("%s.webp" % icon_id)).unlink(
                missing_ok=True
            )
            result["error"] = str(exc)
            errors.append(
                {
                    "iconId": icon_id,
                    "source": result["source"],
                    "error": str(exc),
                }
            )
        icon_results[icon_id] = result

    for mapping in mappings:
        icon = icon_results[mapping["iconId"]]
        heroes[str(mapping["heroId"])] = {
            "iconId": mapping["iconId"],
            "local": bool(icon.get("local")),
            "output": icon.get("output", ""),
        }

    expected_outputs = {
        target_root / row["output"]
        for row in assets.values()
    }
    for output in cards_root.glob("*.webp"):
        if output not in expected_outputs:
            output.unlink()

    placeholder = target_root / "placeholder.svg"
    placeholder.write_text(_placeholder_svg(), encoding="utf-8")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "sourceRoot": str(source_root.resolve()),
        "heroTable": str(hero_table.resolve()),
        "generatedAt": datetime.now().astimezone().isoformat(
            timespec="seconds"
        ),
        "xorKey": XOR_KEY.hex(),
        "conversion": {
            "format": "webp",
            "quality": DEFAULT_QUALITY,
            "height": DEFAULT_HEIGHT,
            "metadata": "none",
        },
        "heroCount": len(heroes),
        "assetCount": len(assets),
        "placeholderSha256": _sha256_file(placeholder),
        "heroes": heroes,
        "assets": assets,
        "errors": errors,
    }


def _verify_manifest(
    source_root,
    hero_table,
    target_root,
    mappings,
):
    manifest_path = target_root / "manifest.json"
    if not manifest_path.is_file():
        raise ValueError("portrait manifest missing")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    expected_heroes = {
        str(row["heroId"]): row["iconId"] for row in mappings
    }
    actual_heroes = {
        hero_id: int(row.get("iconId") or 0)
        for hero_id, row in manifest.get("heroes", {}).items()
    }
    if expected_heroes != actual_heroes:
        raise ValueError("portrait hero mapping drift")
    if manifest.get("sourceRoot") != str(source_root.resolve()):
        raise ValueError("portrait source root drift")
    if manifest.get("heroTable") != str(hero_table.resolve()):
        raise ValueError("portrait hero table drift")

    for icon_id, row in manifest.get("assets", {}).items():
        source = source_root / row["source"]
        if not source.is_file():
            raise ValueError(
                "portrait source missing: %s" % icon_id
            )
        if _sha256_file(source) != row.get("sourceSha256"):
            raise ValueError(
                "portrait source drift: %s" % icon_id
            )
        output = target_root / row["output"]
        if not output.is_file():
            raise ValueError(
                "portrait output missing: %s" % icon_id
            )
        if _sha256_file(output) != row.get("outputSha256"):
            raise ValueError(
                "portrait output drift: %s" % icon_id
            )
    expected_outputs = {
        target_root / row["output"]
        for row in manifest.get("assets", {}).values()
    }
    actual_outputs = set((target_root / "cards").glob("*.webp"))
    if expected_outputs != actual_outputs:
        raise ValueError("portrait output file set drift")
    placeholder = target_root / "placeholder.svg"
    if not placeholder.is_file():
        raise ValueError("portrait placeholder missing")
    if (
        _sha256_file(placeholder)
        != manifest.get("placeholderSha256")
    ):
        raise ValueError("portrait placeholder drift")
    return manifest


def sync_portraits(
    source_root,
    hero_table,
    target_root,
    cwebp="cwebp",
    check=False,
):
    source_root = Path(source_root).resolve()
    hero_table = Path(hero_table).resolve()
    target_root = Path(target_root).resolve()
    if not source_root.is_dir():
        raise ValueError(
            "portrait source root missing: %s" % source_root
        )
    if not hero_table.is_file():
        raise ValueError("hero table missing: %s" % hero_table)
    mappings = load_portrait_mappings(hero_table, source_root)
    if check:
        return _verify_manifest(
            source_root,
            hero_table,
            target_root,
            mappings,
        )
    converter = _resolve_executable(cwebp)
    target_root.mkdir(parents=True, exist_ok=True)
    manifest = _build_manifest(
        source_root,
        hero_table,
        target_root,
        mappings,
        converter,
    )
    (target_root / "manifest.json").write_text(
        json.dumps(
            manifest,
            ensure_ascii=False,
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    return manifest


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--hero-table", type=Path, required=True)
    parser.add_argument("--target-root", type=Path, required=True)
    parser.add_argument("--cwebp", default="cwebp")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    manifest = sync_portraits(
        args.source_root,
        args.hero_table,
        args.target_root,
        cwebp=args.cwebp,
        check=args.check,
    )
    if args.check:
        print("hero portrait mirror check: PASS")
    else:
        print(
            "synced hero portraits: heroes=%s assets=%s errors=%s"
            % (
                manifest["heroCount"],
                manifest["assetCount"],
                len(manifest["errors"]),
            )
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
