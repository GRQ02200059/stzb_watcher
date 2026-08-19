# Protocol Evidence Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Build a deterministic, privacy-safe protocol evidence pipeline that catalogs all 94 captured commands, links confirmed fields to client 9.2.2 source evidence, and gives Web and Android one versioned contract.

**Architecture:** A focused Python package scans command directories, summarizes JSON shapes, extracts constants from the decompiled client, validates hand-maintained evidence, and writes deterministic artifacts. Web uses a read-only generated registry; Android packages a generated subset as an asset. Raw captures remain ignored and are never copied into versioned output.

**Tech Stack:** Python 3.9+, Python standard library, JSON, unittest, Kotlin/JVM Android unit tests, Markdown.

## Global Constraints

- Treat capture_new as read-only user data. Never modify, move, rename, print, or commit payloads.
- Client 9.2.2 decompiled source is the primary semantic authority.
- Generated output contains structural summaries, relative paths, and source anchors only.
- Evidence levels are CLIENT_CONFIRMED, CAPTURE_CONFIRMED, IMPLEMENTATION_ASSUMED, and UNKNOWN.
- Business statistics consume only CLIENT_CONFIRMED or explicitly approved CAPTURE_CONFIRMED fields.
- Preserve raw and normalized values separately.
- Unknown commands remain capturable and never stop packet processing.
- Generated JSON is UTF-8, stable-key sorted, newline terminated, and deterministic.
- Commit only files owned by the current task; preserve all unrelated dirty-worktree changes.

---

### Task 1: Command ID and Capture Inventory Core

**Files:**
- Create: protocol_evidence/__init__.py
- Create: protocol_evidence/catalog.py
- Test: test/test_protocol_evidence_catalog.py

**Interfaces:**
- Produces normalize_hex_id(value: str) -> str.
- Produces decimal_command_id(hex_id: str) -> int.
- Produces scan_capture_inventory(capture_root: Path) -> list[dict].
- Inventory rows contain hexId, decimalId, count, decodeKinds, and samplePaths.

- [ ] **Step 1: Write failing ID and inventory tests**

Test that 67 normalizes to 00000067, decimal conversion returns 103, invalid/non-eight-compatible IDs fail, rows and paths sort deterministically, decode kind is parsed from filenames, and sample paths are relative to capture_root.parent.

- [ ] **Step 2: Run RED**

Run: PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_protocol_evidence_catalog -v

Expected: import failure for protocol_evidence.catalog.

- [ ] **Step 3: Implement strict scanning**

Use fullmatch [0-9a-fA-F]{1,8}; return lower-case eight-character hex. Reject invalid command directories, ignore non-files, derive decode kind from the final filename suffix, and never open payloads in this task.

- [ ] **Step 4: Run GREEN**

Run Step 2. Expected: all tests pass.

- [ ] **Step 5: Commit**

Run: git add protocol_evidence/__init__.py protocol_evidence/catalog.py test/test_protocol_evidence_catalog.py

Run: git commit -m "feat: inventory captured protocol commands" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"

### Task 2: Privacy-Safe JSON Shape Analysis

**Files:**
- Create: protocol_evidence/shapes.py
- Test: test/test_protocol_evidence_shapes.py

**Interfaces:**
- Consumes Task 1 samplePaths.
- Produces summarize_json_value(value) -> dict.
- Produces summarize_command_samples(repo_root: Path, sample_paths: list[str]) -> dict.

- [ ] **Step 1: Write failing tests**

Test root types, array lengths, per-index type sets, object key-count/type distributions, invalid JSON counts, structure drift, and a privacy assertion proving a fixture username and numeric object key never appear in serialized summaries.

- [ ] **Step 2: Run RED**

Run: PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_protocol_evidence_shapes -v

Expected: missing module/functions.

- [ ] **Step 3: Implement structural summaries**

Recognize null, boolean, integer, number, string, array, and object. Never emit literal scalar values or object keys. Limit parsed samples deterministically while retaining total counts.

- [ ] **Step 4: Run GREEN and commit**

Run Step 2, then commit shapes.py and its test as feat: summarize protocol sample shapes with the required co-author trailer.

### Task 3: Client Command Constant Extraction

**Files:**
- Create: protocol_evidence/client_source.py
- Test: test/test_protocol_evidence_client_source.py

**Interfaces:**
- Produces extract_command_constants(net_command_def: Path) -> dict[int, list[str]].
- Produces validate_source_anchor(client_root: Path, anchor: dict) -> None.
- Anchors contain relative file and inclusive [start,end] lines.

- [ ] **Step 1: Write failing tests**

Test aliases, non-constant exclusion, conflicting duplicate names, path traversal, absolute paths, missing files, and out-of-range lines.

- [ ] **Step 2: Run RED**

Run: PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_protocol_evidence_client_source -v

- [ ] **Step 3: Implement extraction and validation**

Match only public const int NAME = DECIMAL declarations. Sort aliases and enforce 1 <= start <= end <= source line count.

- [ ] **Step 4: Run GREEN and commit**

Commit client_source.py and its test as feat: extract client protocol command evidence with the trailer.

### Task 4: Evidence Overlay and Field Registry Validation

**Files:**
- Create: protocol_evidence/evidence.py
- Create: protocol/evidence/client-9.2.2/README.md
- Create: protocol/evidence/client-9.2.2/core-commands.json
- Test: test/test_protocol_evidence_overlay.py

**Interfaces:**
- Produces load_evidence_files(evidence_root, client_root, constants) -> dict[str,dict].
- Returns normalized command and field overlays keyed by eight-digit hex ID.

- [ ] **Step 1: Write failing validation tests**

Reject invalid evidence levels, hex/decimal mismatch, absent anchors, duplicate field paths, invalid units/types, and business-approved fields below CAPTURE_CONFIRMED. Verify one valid command/field round-trip.

- [ ] **Step 2: Run RED**

Run: PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_protocol_evidence_overlay -v

- [ ] **Step 3: Implement explicit standard-library validation**

Document the exact JSON shape in README.md. Do not add a JSON-schema dependency.

- [ ] **Step 4: Add confirmed core-command evidence**

Cover IDs 10, 92, 103, 143, 510, 671, 780, 2100, 2200, 5026, and 5028 using real relative source anchors. Include confirmed fields only; omit unresolved semantics.

- [ ] **Step 5: Run GREEN and commit**

Run Task 3 and Task 4 tests, then commit evidence.py, evidence files, README, and tests as feat: register confirmed protocol field evidence with the trailer.

### Task 5: Deterministic Generator, CLI, and Coverage Report

**Files:**
- Create: protocol_evidence/build.py
- Create: scripts/build_protocol_evidence.py
- Test: test/test_protocol_evidence_build.py
- Generate: data/protocol/client-9.2.2/command-catalog.json
- Generate: data/protocol/client-9.2.2/field-registry.json
- Generate: data/protocol/client-9.2.2/manifest.json
- Generate: docs/verification/protocol-coverage-client-9.2.2.md

**Interfaces:**
- Produces build_protocol_evidence(capture_root, client_root, evidence_root, output_root, report_path) -> dict.
- CLI flags: --capture-root, --client-root, --evidence-root, --output-root, --report, --check.

- [ ] **Step 1: Write failing deterministic build tests**

Assert two builds are byte-identical, manifest hashes match, check mode detects manual drift, reports contain typed/raw/unknown totals, and no generated file contains temporary absolute paths or fixture secrets.

- [ ] **Step 2: Run RED**

Run: PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_protocol_evidence_build -v

- [ ] **Step 3: Implement build and CLI**

Write stable sorted JSON with one trailing newline. Manifest records SHA-256, byte size, generator version, client version, and command count. Do not record generation time.

- [ ] **Step 4: Generate approved artifacts**

Run: .venv/bin/python scripts/build_protocol_evidence.py --capture-root capture_new --client-root /Users/bytedance/stzb/stzb_9.2.2_out_branch_9.1.1776213/assets/decompiled --evidence-root protocol/evidence/client-9.2.2 --output-root data/protocol/client-9.2.2 --report docs/verification/protocol-coverage-client-9.2.2.md

Expected: exactly 94 captured commands in the report.

- [ ] **Step 5: Verify check mode and commit**

Run the Step 4 command with --check; expect exit 0 and protocol evidence is current. Commit generator, tests, artifacts, and report as feat: generate versioned protocol evidence with the trailer.

### Task 6: Real Capture and Client Source Contract Tests

**Files:**
- Create: test/test_protocol_evidence_real_captures.py
- Create: test/test_protocol_evidence_privacy.py

**Interfaces:**
- Consumes Task 5 generated artifacts and local read-only inputs.
- Produces release gates for coverage, source anchors, real shapes, and privacy.

- [ ] **Step 1: Write real-input contract tests**

Assert every actual capture command appears in the catalog, count is 94 for the approved input, every typed command has a valid sample, every source anchor exists, valid 5026/5028 arrays have 31 slots, the WID convention is declared, and hex/decimal parity holds.

- [ ] **Step 2: Write privacy tests**

Scan generated JSON/Markdown for absolute roots, fixture secrets, raw payload fragments, and forbidden keys password, sessionToken, passport, and role_name.

- [ ] **Step 3: Run tests**

Run: PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_protocol_evidence_real_captures test.test_protocol_evidence_privacy -v

Expected: pass without printing capture values.

- [ ] **Step 4: Commit**

Commit the two tests as test: verify protocol evidence against real captures with the trailer.

### Task 7: Web Read-Only Registry and Approved-Field Gate

**Files:**
- Create: protocol_registry.py
- Test: test/test_protocol_registry.py
- Modify: score_center/aggregation.py only to replace duplicated field-source assumptions; do not change scoring behavior.

**Interfaces:**
- Produces ProtocolRegistry(root: Path).
- Produces command(hex_or_decimal) -> dict | None.
- Produces field(command_id, path) -> dict | None.
- Produces require_business_field(command_id, path) -> dict and raises for unapproved evidence.

- [ ] **Step 1: Write failing registry tests**

Test hex/decimal parity, immutable returned values, missing commands, manifest checksum rejection, and rejection of UNKNOWN/IMPLEMENTATION_ASSUMED business fields.

- [ ] **Step 2: Run RED**

Run: PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_protocol_registry -v

- [ ] **Step 3: Implement minimal loader**

Load once, verify manifest checksums, expose defensive immutable values, and perform no capture/client-source access at application runtime.

- [ ] **Step 4: Gate Score Center member wuxun**

Require command 00000067 field [10] before enabling weekly member-wuxun snapshots. Missing/stale evidence raises a precise configuration error; never fall back to battle gongxun.

- [ ] **Step 5: Run Web tests**

Run: PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_protocol_registry test.test_score_center_aggregation test.test_score_center_service -v

- [ ] **Step 6: Commit**

Commit protocol_registry.py, tests, and the focused score change as feat: gate Web protocol fields by evidence with the trailer.

### Task 8: Android Contract Asset and Parity Tests

**Files:**
- Create: astzb/app/src/main/assets/protocol_contract_client_9_2_2.json
- Create: astzb/app/src/test/java/com/example/myapplication/ProtocolContractTest.kt
- Modify: scripts/build_protocol_evidence.py to accept --android-contract.
- Modify: test/test_protocol_evidence_build.py for Android determinism.

**Interfaces:**
- Android asset contains typed commands, approved fields, evidence levels, and conventions only.
- The asset is generated and never hand-edited.

- [ ] **Step 1: Write failing Python and Kotlin tests**

Python asserts deterministic Android JSON. Kotlin loads the asset and verifies: 00000067 maps to 103, 000013a2 maps to 5026, 000013a4 maps to 5028, field [10] of 00000067 is CLIENT_CONFIRMED, and WID is x=wid/10000,y=wid%10000.

- [ ] **Step 2: Run RED**

Run: PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_protocol_evidence_build -v

Run: ./astzb/gradlew -p astzb :app:testDebugUnitTest --tests com.example.myapplication.ProtocolContractTest

- [ ] **Step 3: Generate Android subset**

Add --android-contract. Exclude sample and client-source paths from the APK asset. Include only IDs, names, approved fields, evidence, and conventions.

- [ ] **Step 4: Run GREEN and commit**

Run Step 2, then commit generator, test updates, generated asset, and Kotlin test as feat: share protocol contract with Android with the trailer.

### Task 9: Full Stage-One Verification and Handoff

**Files:**
- Modify through generator: docs/verification/protocol-coverage-client-9.2.2.md
- Create: docs/verification/protocol-evidence-stage-one-acceptance.md

**Interfaces:**
- Produces evidence for all design gates and prerequisites for the battlefield phase.

- [ ] **Step 1: Run all foundation tests**

Run all test_protocol_evidence_* modules and test_protocol_registry with unittest. Expected: zero failures/errors.

- [ ] **Step 2: Run generator check mode**

Use Task 5 command with --check and --android-contract astzb/app/src/main/assets/protocol_contract_client_9_2_2.json. Expected: current.

- [ ] **Step 3: Run affected Web tests**

Run: PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_scrapy_capture test.test_score_center_aggregation test.test_score_center_repository test.test_score_center_service test.test_score_center_api -v

- [ ] **Step 4: Run Android tests and build**

Run: ./astzb/gradlew -p astzb :app:testDebugUnitTest :app:assembleDebug

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Audit all eight design gates**

Record direct evidence for 94 cataloged commands, evidence coverage for each existing typed parser, raw/unknown statuses, Web/Android parity, privacy, check mode, tests, and accurate coverage categories. Missing evidence means incomplete.

- [ ] **Step 6: Write acceptance document**

Record counts, checksums, commands/results, UNKNOWN commands, source version, privacy result, and next-stage prerequisites. Do not claim all commands are business parsed.

- [ ] **Step 7: Diff check and commit**

Run git diff --check. Commit generated coverage and acceptance as test: verify protocol evidence foundation with the trailer.

## Completion Audit Checklist

| Requirement | Required artifact or evidence |
|---|---|
| All 94 captured commands | Catalog count and real-capture test |
| Client names and anchors | Evidence overlay and source validation |
| Field evidence levels | Field registry and overlay tests |
| Drift and broken samples | Catalog shape section and coverage report |
| No sensitive capture content | Privacy tests and generated-output scan |
| Web approved-field gate | ProtocolRegistry and Score Center tests |
| Android shared contract | Generated asset and ProtocolContractTest |
| Unknown commands remain raw | Coverage status totals |
| Deterministic current output | Manifest hashes and check mode |
| No regressions | Task 9 Web/Android test commands |

Stage one is not complete if any row lacks direct artifact or command evidence.
