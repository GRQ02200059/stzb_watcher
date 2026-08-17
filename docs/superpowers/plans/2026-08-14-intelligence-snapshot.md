# STZB Versioned Intelligence Snapshot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Copy the approved client 9.2.2 configuration truth into a self-contained, validated, versioned intelligence snapshot that Web runtime can consume without `/Users/bytedance/stzb`.

**Architecture:** A deterministic Python synchronizer copies four allowlisted CSV files, derives two protocol/risk JSON files from checked-in constants, and writes a manifest plus checksums. Runtime loaders only read `data/intelligence/client-9.2.2/`; the external source root is used exclusively by the explicit synchronization command.

**Tech Stack:** Python 3.11, CSV, JSON, SHA-256, unittest

## Global Constraints

- Runtime code must not read `/Users/bytedance/stzb`.
- Copy only `hero_table.csv`, `skill_table.csv`, `skill_detail_table.csv`, and `skill_effect_table.csv`.
- Do not copy captures, account IDs, player names, alliance names, DLLs, APK output, or decompiled source.
- Reject symlink/path escape outside the explicit `--source-root`.
- Preserve source bytes for copied CSV files.
- Generated JSON must be UTF-8, deterministic, and sorted.
- Do not commit automatically; leave a reviewable working tree because the repository already contains user changes.

---

### Task 1: Snapshot Manifest Model and Safe File Copier

**Files:**
- Create: `intelligence/__init__.py`
- Create: `intelligence/snapshot.py`
- Create: `test/test_intelligence_snapshot.py`

**Interfaces:**
- Produces: `SnapshotFile`, `SnapshotManifest`, `sha256_file(path)`, `sync_snapshot(source_root, output_root, generated_at)`
- Consumes: four allowlisted CSV names from `ALLOWED_SOURCE_FILES`

- [ ] **Step 1: Write the failing safe-copy tests**

```python
class IntelligenceSnapshotTest(unittest.TestCase):
    def test_sync_copies_only_allowlisted_files_and_records_hashes(self):
        manifest = sync_snapshot(self.source, self.output, "2026-08-14T00:00:00+08:00")
        self.assertEqual(
            [item.target for item in manifest.files],
            [
                "hero_table.csv",
                "skill_detail_table.csv",
                "skill_effect_table.csv",
                "skill_table.csv",
            ],
        )
        self.assertEqual(
            manifest.files[0].sha256,
            sha256_file(self.output / manifest.files[0].target),
        )

    def test_sync_rejects_symlink_outside_source_root(self):
        (self.source / "hero_table.csv").unlink()
        (self.source / "hero_table.csv").symlink_to(self.external / "secret.csv")
        with self.assertRaisesRegex(ValueError, "outside source root"):
            sync_snapshot(self.source, self.output, "2026-08-14T00:00:00+08:00")
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
.venv/bin/python -m unittest test.test_intelligence_snapshot -v
```

Expected: import failure for missing `intelligence.snapshot`.

- [ ] **Step 3: Implement immutable manifest types and copier**

```python
ALLOWED_SOURCE_FILES = (
    "hero_table.csv",
    "skill_table.csv",
    "skill_detail_table.csv",
    "skill_effect_table.csv",
)

@dataclass(frozen=True)
class SnapshotFile:
    source: str
    target: str
    rows: int
    bytes: int
    sha256: str

def _resolve_source(root: Path, name: str) -> Path:
    root = root.resolve()
    source = (root / name).resolve()
    if root not in source.parents:
        raise ValueError(f"{name} resolves outside source root")
    return source
```

Copy with `shutil.copyfile`, count CSV data rows with `utf-8-sig`, and sort manifest entries by target.

- [ ] **Step 4: Run tests and verify GREEN**

Run:

```bash
.venv/bin/python -m unittest test.test_intelligence_snapshot -v
```

Expected: safe-copy tests pass.

- [ ] **Step 5: Review checkpoint**

Inspect:

```bash
git diff -- intelligence test/test_intelligence_snapshot.py
```

Confirm no external absolute path is embedded in runtime code.

### Task 2: CSV Schema, Primary-Key, and Reference Validation

**Files:**
- Modify: `intelligence/snapshot.py`
- Modify: `test/test_intelligence_snapshot.py`

**Interfaces:**
- Produces: `validate_snapshot_tables(root) -> ValidationReport`
- Consumes exact columns:
  - hero: `heroid,name,skill_init,attack_base,defence_base,intel_base,speed_base,destroy_base`
  - skill: `skill_id,name,main_detail,skill_type,prepare,probability_init,probability_max`
  - detail: `detail_id,effect_id`
  - effect: `effect_id,name`

- [ ] **Step 1: Add failing validation tests**

```python
def test_validation_rejects_duplicate_hero_ids(self):
    append_csv_row(self.source / "hero_table.csv", {"heroid": "100027"})
    with self.assertRaisesRegex(ValueError, "duplicate hero primary key"):
        validate_snapshot_tables(self.source)

def test_validation_reports_broken_skill_references(self):
    write_skill(self.source / "skill_table.csv", skill_id=200001, main_detail=29999999)
    report = validate_snapshot_tables(self.source)
    self.assertEqual(report.missing_main_details, (29999999,))
```

- [ ] **Step 2: Run and verify RED**

Run the focused unittest; expected failure because validation types/functions do not exist.

- [ ] **Step 3: Implement streaming validation**

Use `csv.DictReader`, normalize BOM headers, and enforce:

```python
ValidationReport(
    hero_rows=2077,
    skill_rows=6572,
    skill_detail_rows=12694,
    skill_effect_rows=206,
    duplicate_keys=(),
    missing_initial_skills=(),
    missing_main_details=(),
    missing_effects=(),
)
```

Do not fail the entire snapshot for unresolved references that are intentionally retained; record them in the report. Fail for missing required columns and duplicate primary keys.

- [ ] **Step 4: Run and verify GREEN**

Run:

```bash
.venv/bin/python -m unittest test.test_intelligence_snapshot -v
```

- [ ] **Step 5: Review checkpoint**

Verify validation reads CSV iteratively and does not load `/Users/bytedance/stzb` at import time.

### Task 3: Derived World Schema and Land Intelligence Rules

**Files:**
- Create: `intelligence/rules.py`
- Modify: `intelligence/snapshot.py`
- Modify: `test/test_intelligence_snapshot.py`

**Interfaces:**
- Produces: `world_scene_schema() -> dict`, `land_intelligence_rules() -> dict`
- Schema slot keys: string `"0"` through `"30"`
- Freshness values: `freshSeconds=120`, `staleSeconds=600`

- [ ] **Step 1: Add failing exact-value tests**

```python
def test_world_schema_distinguishes_baseline_and_delta(self):
    schema = world_scene_schema()
    self.assertEqual(schema["slots"]["6"], {
        "5026": "armies",
        "5028": "armyChanges",
        "type": "object",
    })
    self.assertEqual(schema["slots"]["7"]["5028"], "deletedArmies")
    self.assertEqual(schema["slots"]["20"]["5026Type"], "null")
    self.assertEqual(schema["slots"]["20"]["5028Type"], "array[2]")

def test_land_rules_lock_level_palette_and_freshness(self):
    rules = land_intelligence_rules()
    self.assertEqual(rules["levelColors"]["9"], "#ff174f")
    self.assertEqual(rules["freshness"], {"freshSeconds": 120, "staleSeconds": 600})
    self.assertEqual(rules["newResLv"], {"levelDigit": "tens", "resourceDigit": "ones"})
```

- [ ] **Step 2: Verify RED**

Expected: missing module/functions.

- [ ] **Step 3: Implement pure deterministic dictionaries**

Include evidence paths:

```python
"evidence": [
  "/Users/bytedance/stzb/docs/protocol/5026-5028-world-scene-fields.md",
  "/Users/bytedance/stzb/tools/monitor-agent/web/farming/map_codec.py",
  "/Users/bytedance/stzb/tools/monitor-agent/docs/superpowers/specs/2026-08-02-farming-map-risk-heatmap-design.md"
]
```

Mark evidence paths as provenance text only; runtime must not open them.

- [ ] **Step 4: Run and verify GREEN**

Run snapshot tests.

- [ ] **Step 5: Review checkpoint**

Compare all 31 slot names with the approved specification and ensure no old incorrect slot mapping appears.

### Task 4: CLI, Manifest, Check Mode, and Initial Snapshot

**Files:**
- Create: `scripts/sync_intelligence_snapshot.py`
- Create: `data/intelligence/client-9.2.2/SOURCE.md`
- Generate: `data/intelligence/client-9.2.2/manifest.json`
- Generate: `data/intelligence/client-9.2.2/checksums.sha256`
- Generate: `data/intelligence/client-9.2.2/*`
- Modify: `.gitignore` only if generated data is accidentally ignored
- Modify: `test/test_intelligence_snapshot.py`

**Interfaces:**
- CLI:
  - `--source-root PATH`
  - `--output-root PATH`
  - `--check`
- Exit codes: `0` valid/up-to-date, `1` validation/drift

- [ ] **Step 1: Add failing CLI integration tests**

```python
def test_check_detects_drift(self):
    sync_snapshot(self.source, self.output, FIXED_TIME)
    (self.output / "hero_table.csv").write_text("changed", encoding="utf-8")
    result = run_cli("--check", source=self.source, output=self.output)
    self.assertEqual(result.returncode, 1)
    self.assertIn("hero_table.csv checksum drift", result.stderr)
```

- [ ] **Step 2: Verify RED**

Run the focused CLI test and expect missing script failure.

- [ ] **Step 3: Implement CLI and deterministic output**

Write JSON with:

```python
json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
```

Write `manifest.json` with dataset metadata, validation report, provenance, and
the sorted file list. Write `checksums.sha256` sorted by filename.

- [ ] **Step 4: Generate the approved snapshot**

Run:

```bash
.venv/bin/python scripts/sync_intelligence_snapshot.py \
  --source-root /Users/bytedance/stzb \
  --output-root data/intelligence/client-9.2.2
```

Expected manifest rows:

- hero 2077
- skill 6572
- detail 12694
- effect 206

- [ ] **Step 5: Verify generated snapshot**

Run:

```bash
.venv/bin/python scripts/sync_intelligence_snapshot.py \
  --source-root /Users/bytedance/stzb \
  --output-root data/intelligence/client-9.2.2 \
  --check
```

Expected: exit `0`, no drift.

- [ ] **Step 6: Review checkpoint**

Search copied files for capture/account patterns and confirm only approved files exist:

```bash
find data/intelligence/client-9.2.2 -maxdepth 1 -type f -print | sort
```
