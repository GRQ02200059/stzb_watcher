from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Tuple
import csv
import hashlib
import json
import shutil

from .rules import land_intelligence_rules, world_scene_schema


ALLOWED_SOURCE_FILES = (
    "hero_table.csv",
    "skill_table.csv",
    "skill_detail_table.csv",
    "skill_effect_table.csv",
)

REQUIRED_COLUMNS = {
    "hero_table.csv": {
        "heroid",
        "name",
        "skill_init",
        "attack_base",
        "defence_base",
        "intel_base",
        "speed_base",
        "destroy_base",
    },
    "skill_table.csv": {
        "skill_id",
        "name",
        "main_detail",
        "skill_type",
        "prepare",
        "probability_init",
        "probability_max",
    },
    "skill_detail_table.csv": {"detail_id", "effect_id"},
    "skill_effect_table.csv": {"effect_id", "name"},
}

PRIMARY_KEYS = {
    "hero_table.csv": "heroid",
    "skill_table.csv": "skill_id",
    "skill_detail_table.csv": "detail_id",
    "skill_effect_table.csv": "effect_id",
}


@dataclass(frozen=True)
class SnapshotFile:
    source: str
    target: str
    rows: int
    bytes: int
    sha256: str


@dataclass(frozen=True)
class ValidationReport:
    hero_rows: int
    skill_rows: int
    skill_detail_rows: int
    skill_effect_rows: int
    missing_initial_skills: Tuple[int, ...]
    missing_main_details: Tuple[int, ...]
    missing_effects: Tuple[int, ...]


@dataclass(frozen=True)
class SnapshotManifest:
    dataset_version: str
    client_package: str
    client_version: str
    generated_at: str
    files: Tuple[SnapshotFile, ...]
    validation: ValidationReport

    def to_json(self) -> dict:
        return {
            "datasetVersion": self.dataset_version,
            "clientPackage": self.client_package,
            "clientVersion": self.client_version,
            "generatedAt": self.generated_at,
            "files": [asdict(item) for item in self.files],
            "validation": asdict(self.validation),
            "privacy": {
                "containsCaptureData": False,
                "containsAccountData": False,
                "containsPlayerOrAllianceData": False,
            },
        }


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _resolve_source(root: Path, name: str) -> Path:
    root = Path(root).resolve()
    source = (root / name).resolve()
    if root not in source.parents:
        raise ValueError(f"{name} resolves outside source root")
    if not source.is_file():
        raise ValueError(f"missing source file: {name}")
    return source


def _read_table(root: Path, name: str):
    path = _resolve_source(root, name)
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file)
        headers = set(reader.fieldnames or ())
        missing = REQUIRED_COLUMNS[name] - headers
        if missing:
            raise ValueError(f"{name} missing required columns: {sorted(missing)}")
        rows = list(reader)
    key_name = PRIMARY_KEYS[name]
    keys = []
    for row in rows:
        try:
            keys.append(int(row.get(key_name) or 0))
        except ValueError:
            keys.append(0)
    duplicates = sorted({key for key in keys if key and keys.count(key) > 1})
    if duplicates:
        label = {
            "hero_table.csv": "hero",
            "skill_table.csv": "skill",
            "skill_detail_table.csv": "skill detail",
            "skill_effect_table.csv": "skill effect",
        }[name]
        raise ValueError(f"duplicate {label} primary key: {duplicates[0]}")
    return rows


def validate_snapshot_tables(root: Path) -> ValidationReport:
    heroes = _read_table(root, "hero_table.csv")
    skills = _read_table(root, "skill_table.csv")
    details = _read_table(root, "skill_detail_table.csv")
    effects = _read_table(root, "skill_effect_table.csv")
    skill_ids = {int(row["skill_id"]) for row in skills if row.get("skill_id")}
    detail_ids = {int(row["detail_id"]) for row in details if row.get("detail_id")}
    effect_ids = {int(row["effect_id"]) for row in effects if row.get("effect_id")}
    missing_initial = sorted(
        {
            int(row["skill_init"])
            for row in heroes
            if row.get("skill_init")
            and int(row["skill_init"]) > 0
            and int(row["skill_init"]) not in skill_ids
        }
    )
    missing_main = sorted(
        {
            int(row["main_detail"])
            for row in skills
            if row.get("main_detail")
            and int(row["main_detail"]) > 0
            and int(row["main_detail"]) not in detail_ids
        }
    )
    missing_effects = sorted(
        {
            int(row["effect_id"])
            for row in details
            if row.get("effect_id")
            and int(row["effect_id"]) > 0
            and int(row["effect_id"]) not in effect_ids
        }
    )
    return ValidationReport(
        hero_rows=len(heroes),
        skill_rows=len(skills),
        skill_detail_rows=len(details),
        skill_effect_rows=len(effects),
        missing_initial_skills=tuple(missing_initial),
        missing_main_details=tuple(missing_main),
        missing_effects=tuple(missing_effects),
    )


def _write_json(path: Path, payload: dict) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def _csv_rows(path: Path) -> int:
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return max(0, sum(1 for _ in file) - 1)


def sync_snapshot(
    source_root: Path,
    output_root: Path,
    generated_at: str,
) -> SnapshotManifest:
    source_root = Path(source_root)
    output_root = Path(output_root)
    validation = validate_snapshot_tables(source_root)
    output_root.mkdir(parents=True, exist_ok=True)
    for name in ALLOWED_SOURCE_FILES:
        shutil.copyfile(_resolve_source(source_root, name), output_root / name)
    _write_json(output_root / "world_scene_schema.json", world_scene_schema())
    _write_json(
        output_root / "land_intelligence_rules.json",
        land_intelligence_rules(),
    )
    source_map = {
        name: str(_resolve_source(source_root, name))
        for name in ALLOWED_SOURCE_FILES
    }
    source_map.update(
        {
            "world_scene_schema.json": (
                "/Users/bytedance/stzb/docs/protocol/"
                "5026-5028-world-scene-fields.md"
            ),
            "land_intelligence_rules.json": (
                "/Users/bytedance/stzb/tools/monitor-agent/"
                "docs/superpowers/specs/"
                "2026-08-02-farming-map-risk-heatmap-design.md"
            ),
        }
    )
    files = []
    for path in sorted(output_root.glob("*")):
        if not path.is_file() or path.name in {
            "manifest.json",
            "checksums.sha256",
            "SOURCE.md",
        }:
            continue
        rows = _csv_rows(path) if path.suffix == ".csv" else 0
        files.append(
            SnapshotFile(
                source=source_map[path.name],
                target=path.name,
                rows=rows,
                bytes=path.stat().st_size,
                sha256=sha256_file(path),
            )
        )
    manifest = SnapshotManifest(
        dataset_version="client-9.2.2",
        client_package="com.netease.stzb.netease",
        client_version="9.2.2",
        generated_at=generated_at,
        files=tuple(files),
        validation=validation,
    )
    _write_json(output_root / "manifest.json", manifest.to_json())
    (output_root / "checksums.sha256").write_text(
        "".join(f"{item.sha256}  {item.target}\n" for item in manifest.files),
        encoding="utf-8",
    )
    return manifest
