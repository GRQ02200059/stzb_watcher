#!/usr/bin/env python3
import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from intelligence.research_snapshot import build_research_snapshot
from intelligence.snapshot import sha256_file, sync_snapshot


def _check_manifest(output_root: Path, label: str = "") -> int:
    manifest_path = Path(output_root) / "manifest.json"
    if not manifest_path.exists():
        print(f"{label}manifest.json missing", file=sys.stderr)
        return 1
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for item in manifest.get("files", []):
        path = output_root / item["target"]
        if not path.exists():
            print(f"{label}{item['target']} missing", file=sys.stderr)
            return 1
        if sha256_file(path) != item["sha256"]:
            print(f"{label}{item['target']} checksum drift", file=sys.stderr)
            return 1
    return 0


def _check(output_root: Path) -> int:
    output_root = Path(output_root)
    checked = False
    if (output_root / "manifest.json").exists():
        checked = True
        if _check_manifest(output_root):
            return 1
    research_root = output_root / "research"
    if (research_root / "manifest.json").exists():
        checked = True
        if _check_manifest(research_root, "research/"):
            return 1
    if not checked:
        print("manifest.json missing", file=sys.stderr)
        return 1
    return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path)
    parser.add_argument("--research-source-root", type=Path)
    parser.add_argument("--research-only", action="store_true")
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    if args.check:
        return _check(args.output_root)
    generated_at = datetime.now().astimezone().isoformat(timespec="seconds")
    if not args.research_only:
        if args.source_root is None:
            parser.error("--source-root is required unless --research-only is used")
        sync_snapshot(args.source_root, args.output_root, generated_at)
    if args.research_only or args.research_source_root is not None:
        if args.research_source_root is None:
            parser.error("--research-source-root is required for research sync")
        build_research_snapshot(
            args.research_source_root,
            args.output_root / "research",
            generated_at,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
