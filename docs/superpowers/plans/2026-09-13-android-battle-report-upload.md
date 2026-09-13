# Android Battle Report Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Silently synchronize the current Android profile's structured battle reports to the existing authenticated server once per App process after startup authentication succeeds.

**Architecture:** Extend the Ubuntu authentication service with versioned battle-report manifest and upload APIs backed by separate SQLite tables. Then add an isolated Android sync package that reads the current profile database, creates cross-platform canonical hashes, negotiates missing reports, and uploads only changed reports through WorkManager. Deploy and verify the compatible server before enabling the Android startup trigger.

**Tech Stack:** Python 3.9-compatible Flask, SQLite, unittest, and Waitress on the server; Kotlin 2.0.21, Android 13+, SQLite, OkHttp 4.12, WorkManager 2.10, coroutines, JUnit 4, MockWebServer, and Android instrumentation tests on the client.

## Global Constraints

- Production base URL remains exactly http://152.136.236.184:9080.
- A new App process schedules at most one sync after authentication first enters AuthGateState.Ready.
- Activity recreation, Compose recomposition, and foreground resume do not schedule another sync in the same process.
- Only the current LocalProfile is synchronized; switching profiles does not trigger an immediate upload.
- Only allow-listed structured fields from battles_v2, battle_heroes, and battle_skills leave the device.
- Never upload the SQLite file, raw_json, source_msg_id, packets, diagnostics, AI data, passwords, or unrelated tables.
- The feature has no UI, notification, Toast, Snackbar, Dialog, menu item, or user-facing status.
- Network or server failure never blocks authenticated App use.
- Server identity key is (auth_user_id, profile_id, battle_id).
- Canonical payloads use schemaVersion = 1 and SHA-256 over exact UTF-8 canonical JSON bytes.
- Auth endpoints remain limited to 16 KiB; manifest is limited to 128 KiB; upload is limited to 2 MiB.
- Manifest batches contain at most 200 digests; upload batches contain at most 50 reports and 2 MiB.
- Local deletions do not delete server archives.
- Server worktree: /Users/bytedance/stzb_watcher/.worktrees/windows-desktop-auth on codex/windows-desktop-auth.
- Android worktree: /Users/bytedance/stzb_watcher on main.
- Both worktrees are dirty. Preserve all pre-existing edits, especially .worktrees/windows-desktop-auth/test/test_auth_service.py. Never use git add -A, git reset, git checkout --, or destructive cleanup.
- Follow red-green-refactor for every production change.

---

## File Structure

Server worktree:

- Create auth_service/battle_report_contract.py for schema-v1 validation, canonical JSON, and SHA-256.
- Create auth_service/battle_report_repository.py for profile/report persistence and atomic batch upsert.
- Create auth_service/battle_report_service.py for session-authorized manifest/upload use cases.
- Modify auth_service/schema.sql, auth_service/service.py, auth_service/api.py, auth_service/app.py, and auth_service/deploy/manifest.txt.
- Create scripts/verify_battle_report_sync.py and focused tests/fixtures under test/.

Android worktree:

- Create a focused com.local.stzb.sync package containing models, canonicalizer, read-only data source, transport, coordinator, Worker, and process-once launcher.
- Modify only StzbApplication.kt and StzbAppActivity.kt for dependency ownership and the Ready trigger.
- Add WorkManager test support and focused JVM/instrumentation tests.

---

## Preflight Evidence

Before Task 1, record both dirty-worktree baselines without changing files:

~~~bash
git -C /Users/bytedance/stzb_watcher status --short
git -C /Users/bytedance/stzb_watcher/.worktrees/windows-desktop-auth status --short
cd /Users/bytedance/stzb_watcher/.worktrees/windows-desktop-auth
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest discover -s test -v
cd /Users/bytedance/stzb_watcher
./astzb/gradlew -p astzb :app:testDebugUnitTest
~~~

Save the exact failing test names and exit codes in the task notes. The known server edit in test/test_auth_service.py belongs to the user and must remain unstaged by feature commits. If a planned production change makes that test relevant, stop and reconcile the intended version policy before modifying the user's test.

---

### Task 1: Server Schema-v1 Contract and Canonical Hashing

**Files:**

- Create: auth_service/battle_report_contract.py
- Create: test/fixtures/battle_report_schema_v1.json
- Create: test/test_battle_report_contract.py

**Interfaces:**

- Produces SCHEMA_VERSION = 1, MAX_MANIFEST_REPORTS = 200, MAX_UPLOAD_REPORTS = 50.
- Produces parse_profile(value: object) -> dict.
- Produces parse_manifest(value: object) -> list[dict].
- Produces parse_reports(value: object) -> list[dict].
- Produces canonical_report_bytes(report: dict) -> bytes.
- Produces report_content_hash(report: dict) -> str.
- Accepted report shape is exactly schemaVersion, battleId, contentHash, battle, heroes, skills. Hashing excludes contentHash.

- [ ] **Step 1: Add the cross-platform golden fixture**

Create test/fixtures/battle_report_schema_v1.json with this exact semantic content:

~~~json
{
  "report": {
    "schemaVersion": 1,
    "battleId": 42,
    "contentHash": "32c3d5ac64b34cb73b01878cdd55a1d3d9451b4b08f14438606152e535263e7f",
    "battle": {"battle_id": 42, "atk_name": "赵云", "result": 1, "captured_at": 1700000010},
    "heroes": [{"side": "atk", "pos": 0, "hero_id": 1001, "hero_name": "赵云", "level": 50}],
    "skills": [{"side": "atk", "pos": 0, "skill_id": 2001, "skill_name": "一骑当千", "skill_level": 10}]
  },
  "canonical": "{\"battle\":{\"atk_name\":\"赵云\",\"battle_id\":42,\"captured_at\":1700000010,\"result\":1},\"heroes\":[{\"hero_id\":1001,\"hero_name\":\"赵云\",\"level\":50,\"pos\":0,\"side\":\"atk\"}],\"schemaVersion\":1,\"skills\":[{\"pos\":0,\"side\":\"atk\",\"skill_id\":2001,\"skill_level\":10,\"skill_name\":\"一骑当千\"}]}",
  "sha256": "32c3d5ac64b34cb73b01878cdd55a1d3d9451b4b08f14438606152e535263e7f"
}
~~~

- [ ] **Step 2: Write failing contract tests**

Test exact golden bytes/hash, child sorting, unknown keys, forbidden raw_json/source_msg_id, booleans in integer fields, overlong strings, wrong schema version, duplicate battle IDs, more than 200 manifest rows, and more than 50 reports.

~~~python
def test_schema_v1_golden_bytes_and_hash_match_fixture(self):
    fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
    report = parse_reports([fixture["report"]])[0]
    self.assertEqual(fixture["canonical"].encode("utf-8"), canonical_report_bytes(report))
    self.assertEqual(fixture["sha256"], report_content_hash(report))
~~~

- [ ] **Step 3: Run RED**

From the server worktree:

~~~bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_battle_report_contract -v
~~~

Expected: import failure for auth_service.battle_report_contract.

- [ ] **Step 4: Implement strict parsing and canonicalization**

~~~python
SCHEMA_VERSION = 1
MAX_MANIFEST_REPORTS = 200
MAX_UPLOAD_REPORTS = 50
FORBIDDEN_FIELDS = frozenset(("raw_json", "source_msg_id"))

def canonical_report_bytes(report: dict) -> bytes:
    canonical = {
        "schemaVersion": SCHEMA_VERSION,
        "battle": report["battle"],
        "heroes": sorted(report["heroes"], key=lambda row: (row["side"], row["pos"], row.get("hero_id") or 0)),
        "skills": sorted(report["skills"], key=lambda row: (row["side"], row["pos"], row.get("skill_id") or 0)),
    }
    return json.dumps(canonical, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")

def report_content_hash(report: dict) -> str:
    return hashlib.sha256(canonical_report_bytes(report)).hexdigest()
~~~

Define explicit allow lists for every main, hero, and skill field. Reject unknown keys before canonicalization. Accept integers only when isinstance(value, int) and not isinstance(value, bool). Limit profile strings to 256 characters, names to 512, and structured text fields to 65,536.

The schema-v1 allow lists are exact:

~~~python
BATTLE_FIELDS = (
    "battle_id", "time", "time_str", "result", "result_desc", "fight_type",
    "wid", "wid_name", "wid_code", "atk_name", "atk_uid", "atk_union",
    "atk_unionid", "atk_power", "atk_gongxun", "atk_hp", "def_name",
    "def_uid", "def_union", "def_unionid", "def_level", "def_power",
    "def_gongxun", "def_hp", "weather", "in_night", "is_npc", "is_ai",
    "block_id", "city_type", "borrow_land", "garrison",
    "first_occupy_lvn_land", "atk_team_id", "def_team_id", "atk_advance",
    "def_advance", "atk_hero_type", "def_hero_type", "atk_gear_info",
    "def_gear_info", "all_skill_info", "attack_all_hero_info",
    "defend_all_hero_info", "attack_all_sub_hero_info",
    "defend_all_sub_hero_info", "attack_support_user_info",
    "defend_support_user_info", "captured_at",
)
HERO_FIELDS = (
    "side", "pos", "hero_id", "hero_name", "level", "star", "max_hp",
    "remain_hp", "damage_taken",
)
SKILL_FIELDS = (
    "side", "pos", "skill_id", "skill_name", "skill_level",
)
~~~

- [ ] **Step 5: Run GREEN**

Run Step 3 again. Expected: zero failures.

- [ ] **Step 6: Commit Task 1 only**

~~~bash
git add auth_service/battle_report_contract.py test/fixtures/battle_report_schema_v1.json test/test_battle_report_contract.py
git diff --cached --check
git commit -m "feat: define battle report sync contract"
~~~

---

### Task 2: Server Battle Report Repository

**Files:**

- Modify: auth_service/schema.sql
- Create: auth_service/battle_report_repository.py
- Modify: test/test_auth_repository.py
- Create: test/test_battle_report_repository.py

**Interfaces:**

- Produces BattleReportRepository(auth_repository: AuthRepository).
- Produces required_battle_ids(user_id: int, profile: dict, digests: list[dict]) -> list[int].
- Produces upsert_batch(user_id: int, profile: dict, reports: list[dict]) -> int.

- [ ] **Step 1: Extend the exact-schema test first**

Add four tables and six indexes to EXPECTED_TABLES/EXPECTED_INDEXES:

~~~python
"battle_report_profiles",
"battle_reports",
"battle_report_heroes",
"battle_report_skills",
"idx_battle_reports_battle_time",
"idx_battle_reports_profile",
"sqlite_autoindex_battle_report_profiles_1",
"sqlite_autoindex_battle_reports_1",
"sqlite_autoindex_battle_report_heroes_1",
"sqlite_autoindex_battle_report_skills_1",
~~~

Do not edit the existing uncommitted client-version test in test/test_auth_service.py.

- [ ] **Step 2: Write failing repository tests**

Test idempotent migration over an existing auth DB; user/profile isolation; unchanged and changed hashes; metadata refresh; duplicate upload; child replacement; no cloud delete for locally absent reports; full-batch rollback through an injected trigger.

~~~python
self.assertEqual([42], repository.required_battle_ids(user_a, profile_a, [{"battleId": 42, "contentHash": HASH}]))
repository.upsert_batch(user_a, profile_a, [REPORT])
self.assertEqual([], repository.required_battle_ids(user_a, profile_a, [{"battleId": 42, "contentHash": HASH}]))
self.assertEqual([42], repository.required_battle_ids(user_b, profile_a, [{"battleId": 42, "contentHash": HASH}]))
~~~

- [ ] **Step 3: Run RED**

~~~bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_auth_repository test.test_battle_report_repository -v
~~~

Expected: schema expectation/import failures.

- [ ] **Step 4: Append compatible schema**

Use CREATE TABLE IF NOT EXISTS, foreign keys, and these unique constraints:

~~~sql
UNIQUE (user_id, profile_id)
UNIQUE (user_id, profile_id, battle_id)
UNIQUE (report_id, side, pos)
UNIQUE (report_id, side, pos, skill_id)
~~~

Store only the canonical main battle object in payload_json. Index (user_id, profile_id) and (user_id, profile_id, battle_time DESC). Do not drop, rename, or rewrite authentication tables.

- [ ] **Step 5: Implement manifest lookup and atomic batch upsert**

~~~python
connection = self.auth_repository.connect()
try:
    connection.execute("BEGIN IMMEDIATE")
    self._upsert_profile(connection, user_id, profile)
    for report in reports:
        report_row_id = self._upsert_report(connection, user_id, profile["profileId"], report)
        connection.execute("DELETE FROM battle_report_heroes WHERE report_id = ?", (report_row_id,))
        connection.execute("DELETE FROM battle_report_skills WHERE report_id = ?", (report_row_id,))
        self._insert_heroes(connection, report_row_id, report["heroes"])
        self._insert_skills(connection, report_row_id, report["skills"])
    connection.commit()
except BaseException:
    connection.rollback()
    raise
finally:
    connection.close()
~~~

- [ ] **Step 6: Run GREEN**

Run Step 3. Expected: all repository tests pass.

- [ ] **Step 7: Commit Task 2 only**

~~~bash
git add auth_service/schema.sql auth_service/battle_report_repository.py test/test_auth_repository.py test/test_battle_report_repository.py
git diff --cached --check
git commit -m "feat: persist uploaded battle reports"
~~~

---

### Task 3: Authenticated Manifest and Upload APIs

**Files:**

- Modify: auth_service/service.py
- Create: auth_service/battle_report_service.py
- Modify: auth_service/api.py
- Modify: auth_service/app.py
- Modify: test/test_auth_api.py
- Create: test/test_battle_report_service.py
- Create: test/test_battle_report_api.py

**Interfaces:**

- Produces AuthService.require_active_session(token: str, client_version: str) -> dict containing user_id.
- Produces BattleReportService.manifest(token, client_version, profile, reports) -> dict.
- Produces BattleReportService.upload(token, client_version, profile, reports) -> dict.
- Produces POST /v1/battle-reports/manifest and POST /v1/battle-reports/upload.

- [ ] **Step 1: Write failing service tests**

Cover valid/revoked session, disabled account/service, version-policy parity, server-side hash recomputation, changed hashes, idempotency, and mismatch rejection.

~~~python
tampered = copy.deepcopy(REPORT)
tampered["battle"]["result"] = 2
self.assert_error_code("INVALID_INPUT", lambda: service.upload(token, VERSION, PROFILE, [tampered]))
~~~

- [ ] **Step 2: Write failing route/limit tests**

Prove auth endpoints reject over 16 KiB, manifest over 128 KiB, upload over 2 MiB; both new routes return no-store; logs contain neither token nor player names; malformed/over-limit values return INVALID_INPUT; rejected sessions retain existing codes/statuses.

- [ ] **Step 3: Run RED**

~~~bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_battle_report_service test.test_battle_report_api test.test_auth_api -v
~~~

- [ ] **Step 4: Extract reusable session authorization**

~~~python
def require_active_session(self, token: str, client_version: str) -> dict:
    self._require_service_enabled()
    self._require_supported_client(client_version)
    verified = self.repository.verify_active_session(self._token_hash(token))
    if verified is None:
        raise AuthError(SESSION_INVALID)
    if verified["verification_outcome"] == "account_disabled":
        raise AuthError(ACCOUNT_DISABLED)
    return verified
~~~

Make verify_session call this method while preserving its response exactly.

- [ ] **Step 5: Implement BattleReportService**

~~~python
def upload(self, token, client_version, profile_value, report_values):
    actor = self.auth_service.require_active_session(token, client_version)
    profile = parse_profile(profile_value)
    reports = parse_reports(report_values)
    for report in reports:
        if not hmac.compare_digest(report["contentHash"], report_content_hash(report)):
            raise AuthError(INVALID_INPUT)
    accepted = self.repository.upsert_batch(actor["user_id"], profile, reports)
    return {"ok": True, "accepted": accepted}
~~~

- [ ] **Step 6: Add route-specific request limits and routes**

~~~python
AUTH_BODY_LIMIT = 16 * 1024
MANIFEST_BODY_LIMIT = 128 * 1024
UPLOAD_BODY_LIMIT = 2 * 1024 * 1024
~~~

Set Flask global MAX_CONTENT_LENGTH to 2 MiB, then select the stricter limit by exact path in prepare_auth_request before parsing JSON. Register the new service in app.extensions. Replace the existing raw source_ip log value with _source_ip_log_label(), returning IPv4 /24 form such as 198.51.100.x, IPv6 /48 form, or unknown; request.remote_addr remains unchanged for policy checks.

- [ ] **Step 7: Run GREEN and server regression**

~~~bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_battle_report_service test.test_battle_report_api test.test_auth_api -v
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest discover -s test -v
~~~

Report the pre-existing uncommitted version-policy test separately if it still fails; do not modify it for this feature.

- [ ] **Step 8: Commit Task 3 only**

~~~bash
git add auth_service/service.py auth_service/battle_report_service.py auth_service/api.py auth_service/app.py test/test_auth_api.py test/test_battle_report_service.py test/test_battle_report_api.py
git diff --cached --check
git commit -m "feat: expose battle report sync API"
~~~

---

### Task 4: Package and Verify the Server Extension

**Files:**

- Modify: auth_service/deploy/manifest.txt
- Create: scripts/verify_battle_report_sync.py
- Modify: test/test_auth_deploy_assets.py
- Create: test/test_battle_report_contract_live.py

**Interfaces:**

- Produces verify_battle_report_sync(base_url: str, username: str, password: str, profile_id: str) -> tuple[bool, list[str]].
- Uses profile contract-sync-canary and battle ID 9223372036854775000 for an idempotent canary.

- [ ] **Step 1: Write failing packaging and verifier tests**

Require these sorted manifest entries:

~~~text
auth_service/battle_report_contract.py
auth_service/battle_report_repository.py
auth_service/battle_report_service.py
~~~

Against an ephemeral server, require stable output:

~~~text
AUTH ok
UPLOAD ok accepted=1
MANIFEST ok current
LOGOUT ok
~~~

Assert stdout/stderr contain no token, player name, or payload JSON.

- [ ] **Step 2: Run RED**

~~~bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_auth_deploy_assets test.test_battle_report_contract_live -v
~~~

- [ ] **Step 3: Package modules and implement verifier**

Add manifest entries in lexical order. Read disposable credentials only from STZB_AUTH_TEST_USERNAME and STZB_AUTH_TEST_PASSWORD, cap responses at 64 KiB, and never print HTTP bodies. The verifier performs register-or-login, upload, manifest confirmation, and logout under one monotonic twenty-second deadline. It keeps the session token in memory only and attempts logout in a finally block.

- [ ] **Step 4: Run GREEN and complete server verification**

~~~bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_auth_deploy_assets test.test_battle_report_contract_live -v
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest discover -s test -v
git diff --check
~~~

- [ ] **Step 5: Commit Task 4 only**

~~~bash
git add auth_service/deploy/manifest.txt scripts/verify_battle_report_sync.py test/test_auth_deploy_assets.py test/test_battle_report_contract_live.py
git diff --cached --check
git commit -m "test: verify battle report sync deployment"
~~~

---

### Task 5: Back Up and Deploy the Compatible Server Release

**Files:** No repository changes. Uses auth_service/deploy/backup.sh, auth_service/deploy/install.sh, /etc/stzb-auth.env, and /usr/local/bin/stzb-auth.

**Interfaces:** Produces a running service whose old four auth routes and new two sync routes are healthy.

- [ ] **Step 1: Perform read-only target checks**

~~~bash
ssh ubuntu@152.136.236.184 'sudo systemctl is-active stzb-auth.service; sudo stzb-auth status; sudo test -f /var/lib/stzb-auth/auth.db; sudo test ! -L /var/lib/stzb-auth/auth.db'
~~~

Expected: active service, successful CLI status, regular non-symlink database. Stop if any check fails.

- [ ] **Step 2: Create and validate a backup**

Run sudo /opt/stzb-auth/backup.sh remotely, resolve the newest explicit backup path, and run PRAGMA quick_check against that file with the installed Python. Expected: ok. Do not proceed without a valid backup.

- [ ] **Step 3: Stage a clean release**

Create an archive containing auth_service runtime/deploy assets, excluding var, .env, DB files, logs, caches, tests, and VCS metadata. Use task-specific temporary paths:

~~~bash
server_release_tmp=$(mktemp -d /private/tmp/stzb-auth-release.XXXXXX)
tar -C /Users/bytedance/stzb_watcher/.worktrees/windows-desktop-auth \
    --exclude='auth_service/var' \
    --exclude='auth_service/.env' \
    --exclude='*.db' \
    --exclude='__pycache__' \
    --exclude='*.pyc' \
    --exclude='*.log' \
    -czf "$server_release_tmp/stzb-auth.tgz" auth_service
tar -tzf "$server_release_tmp/stzb-auth.tgz"
remote_release_dir=$(ssh ubuntu@152.136.236.184 'mktemp -d /tmp/stzb-auth-release.XXXXXX')
scp "$server_release_tmp/stzb-auth.tgz" "ubuntu@152.136.236.184:$remote_release_dir/stzb-auth.tgz"
ssh ubuntu@152.136.236.184 "tar -xzf '$remote_release_dir/stzb-auth.tgz' -C '$remote_release_dir' && test -x '$remote_release_dir/auth_service/deploy/install.sh' && printf '%s\\n' '$remote_release_dir'"
~~~

Before upload, inspect tar -tzf output and reject any var/, .env, .db, __pycache__, log, test, or .git entry. Keep the printed remote_release_dir for Step 4.

- [ ] **Step 4: Install and inspect**

~~~bash
ssh ubuntu@152.136.236.184 "sudo '$remote_release_dir/auth_service/deploy/install.sh' && sudo systemctl status stzb-auth.service --no-pager && sudo journalctl -u stzb-auth.service -n 80 --no-pager"
~~~

Expected: installer exit 0, active service, no schema traceback, unchanged environment file.

- [ ] **Step 5: Verify old and new live contracts**

Use one unique disposable account. The old verifier registers and logs out; the new verifier then logs in, exercises sync, and logs out again:

~~~bash
auth_test_username="SyncCanary$(date -u +%m%d%H%M%S)"
read -r -s "auth_test_password?Disposable test password: "
printf '\n'
.venv/bin/python scripts/verify_auth_contract.py --base-url http://152.136.236.184:9080 --username "$auth_test_username" --password "$auth_test_password"
STZB_AUTH_TEST_USERNAME="$auth_test_username" STZB_AUTH_TEST_PASSWORD="$auth_test_password" .venv/bin/python scripts/verify_battle_report_sync.py --base-url http://152.136.236.184:9080 --profile-id contract-sync-canary
unset auth_test_password
~~~

Expected: AUTH ok, UPLOAD ok accepted=1, MANIFEST ok current, and LOGOUT ok. The verifier must attempt logout itself. Do not store credentials or tokens in repository files or logs.

- [ ] **Step 6: Recheck production DB integrity**

Over SSH, run PRAGMA quick_check and count tables whose names match battle_report%. Expected: ok and 4. If validation fails, use the installer's previous-release rollback and the verified backup; never delete the live database.

---

### Task 6: Android Models and Canonical Hashing

**Files:**

- Create: astzb/app/src/main/java/com/local/stzb/sync/BattleReportSyncModels.kt
- Create: astzb/app/src/main/java/com/local/stzb/sync/BattleReportCanonicalizer.kt
- Create: astzb/app/src/test/resources/battle_report_schema_v1.json
- Create: astzb/app/src/test/java/com/local/stzb/sync/BattleReportCanonicalizerTest.kt

**Interfaces:**

- Produces SyncProfile(profileId, serverAddress, roleId, displayName).
- Produces BattleReportPayload(battleId, battle, heroes, skills).
- Produces HashedBattleReport(payload, contentHash, encodedBytes).
- Produces BattleReportCanonicalizer.hash(payload) -> HashedBattleReport.

- [ ] **Step 1: Copy the golden fixture and write failing tests**

~~~bash
cmp .worktrees/windows-desktop-auth/test/fixtures/battle_report_schema_v1.json astzb/app/src/test/resources/battle_report_schema_v1.json
~~~

Test exact canonical text/hash, Unicode and control escaping, null/integer distinctions, child sorting, exclusion of local IDs, and hash changes for every uploaded field.

- [ ] **Step 2: Run RED**

~~~bash
./astzb/gradlew -p astzb :app:testDebugUnitTest --tests 'com.local.stzb.sync.BattleReportCanonicalizerTest'
~~~

- [ ] **Step 3: Implement deterministic models/writer**

~~~kotlin
internal fun canonicalJson(value: Any?): String = when (value) {
    null -> "null"
    is String -> JSONObject.quote(value)
    is Boolean -> if (value) "true" else "false"
    is Byte, is Short, is Int, is Long -> value.toString()
    is Map<*, *> -> value.entries.sortedBy { it.key as String }.joinToString(",", "{", "}") {
        JSONObject.quote(it.key as String) + ":" + canonicalJson(it.value)
    }
    is List<*> -> value.joinToString(",", "[", "]") { canonicalJson(it) }
    else -> error("Unsupported canonical value")
}
~~~

Hash a map containing battle, heroes, schemaVersion, skills after sorting children.

- [ ] **Step 4: Run GREEN and fixture comparison**

Run Step 2 and cmp from Step 1. Expected: pass.

- [ ] **Step 5: Commit Task 6 only**

~~~bash
git add astzb/app/src/main/java/com/local/stzb/sync/BattleReportSyncModels.kt astzb/app/src/main/java/com/local/stzb/sync/BattleReportCanonicalizer.kt astzb/app/src/test/resources/battle_report_schema_v1.json astzb/app/src/test/java/com/local/stzb/sync/BattleReportCanonicalizerTest.kt
git diff --cached --check
git commit -m "feat: model canonical battle report uploads"
~~~

---

### Task 7: Android Profile-scoped Read-only Data Source

**Files:**

- Create: astzb/app/src/main/java/com/local/stzb/sync/AndroidBattleReportSource.kt
- Create: astzb/app/src/androidTest/java/com/local/stzb/sync/AndroidBattleReportSourceTest.kt

**Interfaces:** Produces BattleReportSource.readBatch(profile: LocalProfile, afterBattleId: Long?, limit: Int) -> List<BattleReportPayload>.

- [ ] **Step 1: Write failing instrumentation tests**

Create two profile databases with overlapping IDs. Assert no cross-profile read, allowed main columns preserve null/integer/string types, children attach/sort correctly, missing DB/table returns empty, pagination is stable, and source opens read-only.

~~~kotlin
source.readBatch(profileA, null, 200).single().also { report ->
    assertEquals(42L, report.battleId)
    assertEquals(listOf(0L, 1L), report.heroes.map { it["pos"] })
    assertEquals(listOf(2001L, 2002L), report.skills.map { it["skill_id"] })
}
assertTrue(source.readBatch(profileB, null, 200).isEmpty())
~~~

- [ ] **Step 2: Compile tests RED**

~~~bash
./astzb/gradlew -p astzb :app:compileDebugAndroidTestKotlin
~~~

- [ ] **Step 3: Implement explicit allow-listed extraction**

Query battles_v2 with an explicit column list from LocalStzbDatabase except raw_json/source_msg_id. Fetch child rows for at most 200 IDs. Convert with Cursor.getType(). Validate databaseName against [A-Za-z0-9_.-]+\.db. Open with OPEN_READONLY and NO_LOCALIZED_COLLATORS.

- [ ] **Step 4: Run GREEN**

~~~bash
./astzb/gradlew -p astzb :app:assembleDebug :app:assembleAndroidTest
./astzb/gradlew -p astzb :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.stzb.sync.AndroidBattleReportSourceTest
~~~

If no device is available, record the exact device blocker and retain assembly evidence.

- [ ] **Step 5: Commit Task 7 only**

~~~bash
git add astzb/app/src/main/java/com/local/stzb/sync/AndroidBattleReportSource.kt astzb/app/src/androidTest/java/com/local/stzb/sync/AndroidBattleReportSourceTest.kt
git diff --cached --check
git commit -m "feat: read profile battle reports for sync"
~~~

---

### Task 8: Android HTTP Transport and Incremental Coordinator

**Files:**

- Create: astzb/app/src/main/java/com/local/stzb/sync/BattleReportSyncTransport.kt
- Create: astzb/app/src/main/java/com/local/stzb/sync/BattleReportSyncCoordinator.kt
- Create: astzb/app/src/test/java/com/local/stzb/sync/BattleReportSyncTransportTest.kt
- Create: astzb/app/src/test/java/com/local/stzb/sync/BattleReportSyncCoordinatorTest.kt

**Interfaces:**

- Produces sealed SyncHttpResult with Success, Retryable(retryAfterSeconds), SessionRejected, PermanentFailure.
- Produces transport.manifest(token, version, profile, digests) and transport.upload(token, version, profile, reports).
- Produces sealed SyncOutcome with Success, Retryable(retryAfterSeconds), SessionRejected, and PermanentFailure.
- Produces coordinator.sync(profile, token, clientVersion) -> SyncOutcome.

- [ ] **Step 1: Write failing MockWebServer tests**

Assert exact paths/fields/schemaVersion, no-store, response cap, redacted toString, timeout, Retry-After, session/account rejection, invalid JSON, accepted mismatch, and absence of raw_json/source_msg_id.

- [ ] **Step 2: Write failing coordinator tests**

Assert 201 reports create two manifests; only requested IDs upload; batches split at 50 and before 2 MiB; an oversized single report is skipped without blocking later reports; pagination terminates; retryable errors stop with retry; permanent/session errors stop terminally.

- [ ] **Step 3: Run RED**

~~~bash
./astzb/gradlew -p astzb :app:testDebugUnitTest --tests 'com.local.stzb.sync.BattleReportSyncTransportTest' --tests 'com.local.stzb.sync.BattleReportSyncCoordinatorTest'
~~~

- [ ] **Step 4: Implement strict transport**

~~~kotlin
when {
    response.code == 429 -> SyncHttpResult.Retryable(response.header("Retry-After")?.toLongOrNull())
    response.code >= 500 -> SyncHttpResult.Retryable(null)
    response.code == 401 || response.code == 403 -> SyncHttpResult.SessionRejected
    response.isSuccessful -> parseSuccess(responseBody)
    else -> SyncHttpResult.PermanentFailure
}
~~~

Use injected OkHttp/HttpUrl, require no-store, cap responses at 128 KiB, and do not reuse AuthRepository because its response model is auth-specific.

- [ ] **Step 5: Implement coordinator batching**

For each 200-report page, hash, negotiate, select required IDs, then pack by both count and serialized UTF-8 size. Require accepted == batch.size. Advance pagination by the last local battleId even when nothing uploads.

- [ ] **Step 6: Run GREEN**

Run Step 3. Expected: zero failures.

- [ ] **Step 7: Commit Task 8 only**

~~~bash
git add astzb/app/src/main/java/com/local/stzb/sync/BattleReportSyncTransport.kt astzb/app/src/main/java/com/local/stzb/sync/BattleReportSyncCoordinator.kt astzb/app/src/test/java/com/local/stzb/sync/BattleReportSyncTransportTest.kt astzb/app/src/test/java/com/local/stzb/sync/BattleReportSyncCoordinatorTest.kt
git diff --cached --check
git commit -m "feat: synchronize changed battle reports"
~~~

---

### Task 9: Silent WorkManager Scheduling After Authentication

**Files:**

- Modify: astzb/gradle/libs.versions.toml
- Modify: astzb/app/build.gradle.kts
- Create: astzb/app/src/main/java/com/local/stzb/sync/BattleReportSyncWorker.kt
- Create: astzb/app/src/main/java/com/local/stzb/sync/BattleReportSyncLauncher.kt
- Modify: astzb/app/src/main/java/com/local/stzb/StzbApplication.kt
- Modify: astzb/app/src/main/java/com/local/stzb/StzbAppActivity.kt
- Create: astzb/app/src/test/java/com/local/stzb/sync/BattleReportSyncLauncherTest.kt
- Create: astzb/app/src/androidTest/java/com/local/stzb/sync/BattleReportSyncWorkerTest.kt
- Modify: astzb/app/src/androidTest/java/com/local/stzb/StzbAppActivityTest.kt

**Interfaces:**

- Produces BattleReportWorkScheduler.enqueue(profileId: String) for cold-start REPLACE scheduling.
- Produces BattleReportWorkScheduler.enqueueRateLimitRetry(profileId: String, delaySeconds: Long) for a delayed APPEND_OR_REPLACE continuation.
- Produces BattleReportSyncLauncher.onAuthenticated() guarded by AtomicBoolean.
- Produces BattleReportSyncWorker result mapping.
- Produces StzbApplication.scheduleBattleReportSyncOnce().

- [ ] **Step 1: Add WorkManager testing dependency**

~~~toml
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "workManager" }
~~~

~~~kotlin
androidTestImplementation(libs.androidx.work.testing)
~~~

- [ ] **Step 2: Write failing process-once launcher tests**

Use fake scheduler/profile supplier. Repeated Ready callbacks against the application-owned launcher must enqueue once with the current profile. Missing profile must not consume the gate.

- [ ] **Step 3: Write failing Worker tests**

Using WorkManager test initialization, assert CONNECTED constraint, unique name stzb-battle-report-sync-{profileId}, REPLACE policy for cold start, only profileId in input, retry mapping, terminal handling for missing token/profile and auth/permanent rejection, and no foreground/notification path. For HTTP 429, assert the Worker queues an APPEND_OR_REPLACE continuation with initial delay max(30, Retry-After) seconds and returns success; this prevents a retry earlier than the server requested.

- [ ] **Step 4: Run RED**

~~~bash
./astzb/gradlew -p astzb :app:testDebugUnitTest --tests 'com.local.stzb.sync.BattleReportSyncLauncherTest'
./astzb/gradlew -p astzb :app:compileDebugAndroidTestKotlin
~~~

- [ ] **Step 5: Implement launcher, scheduler, and Worker**

~~~kotlin
class BattleReportSyncLauncher(
    private val currentProfileId: () -> String?,
    private val scheduler: BattleReportWorkScheduler,
) {
    private val scheduled = AtomicBoolean(false)
    fun onAuthenticated() {
        val profileId = currentProfileId()?.takeIf(String::isNotBlank) ?: return
        if (scheduled.compareAndSet(false, true)) scheduler.enqueue(profileId)
    }
}
~~~

WorkRequest uses CONNECTED, exponential 30-second backoff, and input containing only profileId. Enqueue cold-start work with REPLACE. Ordinary transport/5xx failures return Result.retry(). A 429 with Retry-After calls enqueueRateLimitRetry(profileId, maxOf(30, retryAfterSeconds)), creates a new request with that initial delay under the same unique chain using APPEND_OR_REPLACE, then returns Result.success().

- [ ] **Step 6: Wire application-owned dependencies**

Reuse AUTH_BASE_URL, authSessionStore, and profileManager. Create source/transport/coordinator only during Worker execution. Expose scheduleBattleReportSyncOnce() with no sync UI state.

- [ ] **Step 7: Trigger only when auth is Ready**

~~~kotlin
LaunchedEffect(uiState.state) {
    if (uiState.state is AuthGateState.Ready) {
        (application as StzbApplication).scheduleBattleReportSyncOnce()
    }
}
~~~

Keep the existing auth start effect and business UI unchanged.

- [ ] **Step 8: Run GREEN**

~~~bash
./astzb/gradlew -p astzb :app:testDebugUnitTest --tests 'com.local.stzb.sync.*'
./astzb/gradlew -p astzb :app:assembleDebug :app:assembleAndroidTest
./astzb/gradlew -p astzb :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.local.stzb.sync.BattleReportSyncWorkerTest,com.local.stzb.StzbAppActivityTest
~~~

- [ ] **Step 9: Commit Task 9 only**

~~~bash
git add astzb/gradle/libs.versions.toml astzb/app/build.gradle.kts astzb/app/src/main/java/com/local/stzb/sync/BattleReportSyncWorker.kt astzb/app/src/main/java/com/local/stzb/sync/BattleReportSyncLauncher.kt astzb/app/src/main/java/com/local/stzb/StzbApplication.kt astzb/app/src/main/java/com/local/stzb/StzbAppActivity.kt astzb/app/src/test/java/com/local/stzb/sync/BattleReportSyncLauncherTest.kt astzb/app/src/androidTest/java/com/local/stzb/sync/BattleReportSyncWorkerTest.kt astzb/app/src/androidTest/java/com/local/stzb/StzbAppActivityTest.kt
git diff --cached --check
git commit -m "feat: upload battle reports after Android login"
~~~

---

### Task 10: Full Regression, Privacy Audit, and Acceptance

**Files:** Modify only files from Tasks 1-9 if verification exposes a feature defect.

- [ ] **Step 1: Run full server verification**

~~~bash
cd /Users/bytedance/stzb_watcher/.worktrees/windows-desktop-auth
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest discover -s test -v
git diff --check
~~~

Read the entire result. Do not call the suite green unless it has zero failures.

- [ ] **Step 2: Run full Android JVM/build verification**

~~~bash
cd /Users/bytedance/stzb_watcher
./astzb/gradlew -p astzb :app:testDebugUnitTest
./astzb/gradlew -p astzb :app:assembleDebug :app:assembleAndroidTest
git diff --check
~~~

- [ ] **Step 3: Run focused device verification**

~~~bash
adb devices
./astzb/gradlew -p astzb :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.local.stzb.sync
~~~

If no authorized device exists, report that limitation and do not claim device verification.

- [ ] **Step 4: Audit forbidden data paths and worktree ownership**

~~~bash
rg -n "raw_json|source_msg_id|stzb_packets|password|notification|Toast|Snackbar" astzb/app/src/main/java/com/local/stzb/sync .worktrees/windows-desktop-auth/auth_service/battle_report_*.py
git status --short
git -C .worktrees/windows-desktop-auth status --short
~~~

Forbidden DB fields may appear only in explicit rejection guards/tests. The sync package must have no notification/UI code. Preserve unrelated dirty files.

- [ ] **Step 5: Perform live acceptance**

1. Install Debug APK on the authorized test device.
2. Log in with a disposable account and select a profile containing a known report.
3. Force-stop and launch; confirm business UI appears without waiting for sync.
4. Query aggregate server counts over SSH; confirm the test user/profile/report exists without printing payload data.
5. Force-stop and relaunch; confirm unchanged manifest requires zero uploads.
6. Change one test hero/skill row; relaunch and confirm only that report updates.
7. Launch offline; confirm App opens and work waits/retries silently.
8. Restore network; confirm unique work completes.
9. Recreate Activity and background/foreground; confirm no second same-process request.

- [ ] **Step 6: Record sanitized evidence**

Report test counts, build exit codes, service state, aggregate upload counts, and exact commits from both worktrees. Never report tokens, passwords, player names, UIDs, payload JSON, or server environment values.

---

## Execution Order and Stop Conditions

1. Complete Tasks 1-4 in the server worktree.
2. Deploy and validate Task 5 before enabling Android upload.
3. Complete Tasks 6-9 in the Android worktree.
4. Run Task 10 with fresh evidence before claiming completion.

Stop and report rather than guessing if:

- target host or service identity differs from 152.136.236.184 / stzb-auth;
- production DB is missing, symlinked, fails quick_check, or cannot be backed up;
- deployment would overwrite /etc/stzb-auth.env or replace the live DB;
- the pre-existing dirty test/test_auth_service.py edit overlaps required behavior;
- Android dirty edits overlap a planned hunk and cannot be preserved cleanly;
- any request or log contains forbidden fields or secrets;
- Python and Kotlin golden canonical bytes differ.
