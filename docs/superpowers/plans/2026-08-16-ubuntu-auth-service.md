# Ubuntu Authentication Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and deploy the startup-only username/password authentication service used by the Windows STZB desktop client.

**Architecture:** Add an isolated `auth_service` Flask application with an app factory, SQLite repository, Argon2id password hashing, opaque session tokens, rate limiting, and an SSH-only administration CLI. The public API runs under systemd on Ubuntu `152.136.236.184`; no game data enters this service and no administrator HTTP surface exists.

**Tech Stack:** Python 3.11, Flask, SQLite, argon2-cffi, Waitress, Python unittest, systemd, Ubuntu.

## Global Constraints

- Public API base for v1 is `http://152.136.236.184:9080`.
- HTTP is an accepted v1 risk; transport configuration must remain centralized for later HTTPS migration.
- Registration is open and uses case-insensitive usernames plus passwords.
- Username is 4-24 ASCII letters, digits, or underscores; password minimum is 8 characters.
- There is no email, phone, recovery code, password reset, administrator web UI, payment, activation code, device binding, or runtime heartbeat.
- Passwords use Argon2id; session tokens are random and only token hashes are persisted.
- Authentication logs never contain passwords or complete session tokens.
- Service disable and account disable take effect on the client's next startup verification.
- No battle report, capture, SQLite, profile, or game-account data is accepted.
- Keep all code Python 3.9 syntax-compatible unless a deployment-only dependency requires newer Python.
- Every commit message ends with `Co-authored-by: TRAE CLI <noreply@bytedance.com>`.

---

### Task 1: Authentication Package and Configuration

**Files:**
- Create: `auth_service/__init__.py`
- Create: `auth_service/config.py`
- Create: `auth_service/app.py`
- Create: `auth_service/requirements.txt`
- Create: `test/test_auth_config.py`

**Interfaces:**
- Produces: `create_auth_app(config: Optional[AuthConfig] = None) -> flask.Flask`
- Produces: `AuthConfig.from_env() -> AuthConfig`
- Produces environment variables `STZB_AUTH_DB`, `STZB_AUTH_BIND`, `STZB_AUTH_PORT`, `STZB_AUTH_TOKEN_PEPPER`

- [ ] **Step 1: Write failing configuration tests**

Test that `AuthConfig.from_env()` requires a non-empty 32-byte-or-longer token pepper, defaults the database to `auth_service/var/auth.db`, binds `127.0.0.1:9080`, and rejects relative deployment database paths when `STZB_AUTH_ENV=production`.

- [ ] **Step 2: Run the tests RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_auth_config -v
```

Expected: import failure because `auth_service.config` does not exist.

- [ ] **Step 3: Implement immutable configuration**

Use `@dataclass(frozen=True)` and one `from_env()` constructor. Do not read environment variables elsewhere in the package.

- [ ] **Step 4: Add the app factory**

`create_auth_app()` must configure JSON output, register only the v1 blueprint, and attach repository/service instances through `app.extensions`.

- [ ] **Step 5: Pin service dependencies**

`auth_service/requirements.txt` contains exact compatible release pins for:

```text
Flask
argon2-cffi
waitress
```

Resolve pins with:

```bash
.venv/bin/python -m pip index versions Flask argon2-cffi waitress
```

Record the selected exact versions in the file; do not use ranges.

- [ ] **Step 6: Run tests GREEN**

- [ ] **Step 7: Commit**

```bash
git add auth_service test/test_auth_config.py
git commit -m "feat: scaffold authentication service" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 2: SQLite Schema and Repository

**Files:**
- Create: `auth_service/repository.py`
- Create: `auth_service/schema.sql`
- Create: `test/test_auth_repository.py`

**Interfaces:**
- Consumes: `AuthConfig.database_path`
- Produces: `AuthRepository.connect()`
- Produces: `ensure_schema()`
- Produces: `create_user(username_display: str, username_normalized: str, password_hash: str) -> dict`
- Produces: `get_user_by_normalized(username: str) -> Optional[dict]`
- Produces: `set_user_status(username: str, status: str) -> bool`
- Produces: `create_session(user_id: int, token_hash: str) -> dict`
- Produces: `get_active_session(token_hash: str) -> Optional[dict]`
- Produces: `revoke_session(token_hash: str) -> bool`
- Produces: `get_service_state() -> dict`
- Produces: `set_service_state(enabled: bool, announcement: Optional[str] = None) -> dict`
- Produces: `record_login_attempt(username_normalized: str, source_ip: str, succeeded: bool) -> None`

- [ ] **Step 1: Write failing idempotent schema tests**

Run `ensure_schema()` twice against a temporary database and assert the exact tables and indexes from the design exist.

- [ ] **Step 2: Write failing uniqueness and transaction tests**

Assert normalized usernames are unique under concurrent connection attempts and failed inserts leave no partial user or session rows.

- [ ] **Step 3: Write failing service-state tests**

Assert the first migration creates one enabled service-state row and subsequent updates preserve the announcement unless explicitly replaced.

- [ ] **Step 4: Run repository tests RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_auth_repository -v
```

- [ ] **Step 5: Implement schema**

Create `users`, `sessions`, `service_state`, and `login_attempts` with foreign keys, timestamps in UTC ISO-8601, normalized username uniqueness, session token-hash uniqueness, and lookup indexes.

- [ ] **Step 6: Implement repository transactions**

Use `sqlite3.Row`, parameterized SQL, `PRAGMA foreign_keys=ON`, WAL mode, a five-second busy timeout, and explicit transaction boundaries for writes.

- [ ] **Step 7: Run repository tests GREEN**

- [ ] **Step 8: Commit**

```bash
git add auth_service/repository.py auth_service/schema.sql test/test_auth_repository.py
git commit -m "feat: add authentication repository" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 3: Credentials, Tokens, and Redaction

**Files:**
- Create: `auth_service/security.py`
- Create: `auth_service/redaction.py`
- Create: `test/test_auth_security.py`

**Interfaces:**
- Produces: `normalize_username(value: str) -> str`
- Produces: `validate_username(value: str) -> str`
- Produces: `validate_password(value: str) -> str`
- Produces: `hash_password(password: str) -> str`
- Produces: `verify_password(password_hash: str, password: str) -> bool`
- Produces: `new_session_token() -> str`
- Produces: `hash_session_token(token: str, pepper: str) -> str`
- Produces: `redact_mapping(value: dict) -> dict`

- [ ] **Step 1: Write failing username and password tests**

Cover case folding, whitespace rejection, non-ASCII rejection, 4/24-character boundaries, password length, and boolean/non-string values.

- [ ] **Step 2: Write failing password-hash tests**

Assert hashes identify Argon2id, use different salts for identical passwords, verify correctly, and never include the plaintext.

- [ ] **Step 3: Write failing token tests**

Generate 1,000 tokens and assert uniqueness, at least 256 bits of entropy after decoding, stable peppered hashing, and no token substring in its hash.

- [ ] **Step 4: Write failing redaction tests**

Redact case-insensitive keys `password`, `token`, `authorization`, and `cookie` recursively while preserving safe fields.

- [ ] **Step 5: Run security tests RED**

- [ ] **Step 6: Implement minimal security helpers**

Use `argon2.PasswordHasher(type=Type.ID)`, `secrets.token_urlsafe(32)`, and `hmac.new(pepper, token, hashlib.sha256)`.

- [ ] **Step 7: Run security tests GREEN**

- [ ] **Step 8: Commit**

```bash
git add auth_service/security.py auth_service/redaction.py test/test_auth_security.py
git commit -m "feat: secure authentication credentials" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 4: Authentication Domain Service

**Files:**
- Create: `auth_service/service.py`
- Create: `auth_service/errors.py`
- Create: `test/test_auth_service.py`

**Interfaces:**
- Consumes repository and security interfaces from Tasks 2-3.
- Produces: `AuthService.register(username: str, password: str, client_version: str, source_ip: str) -> dict`
- Produces: `AuthService.login(username: str, password: str, client_version: str, source_ip: str) -> dict`
- Produces: `AuthService.verify_session(token: str, client_version: str, source_ip: str) -> dict`
- Produces: `AuthService.logout(token: str) -> None`
- Produces stable error codes `INVALID_INPUT`, `USERNAME_TAKEN`, `INVALID_CREDENTIALS`, `ACCOUNT_DISABLED`, `SERVICE_DISABLED`, `SESSION_INVALID`, `CLIENT_UNSUPPORTED`, `RATE_LIMITED`

- [ ] **Step 1: Write failing registration tests**

Assert successful registration creates a user and session, duplicate normalized names return `USERNAME_TAKEN`, disabled service blocks registration, and the response contains no password hash or token hash.

- [ ] **Step 2: Write failing login tests**

Assert unknown users and wrong passwords share `INVALID_CREDENTIALS`, disabled accounts return `ACCOUNT_DISABLED`, and successful login updates `last_login_at`.

- [ ] **Step 3: Write failing startup verification tests**

Assert active token plus enabled account/service succeeds; revoked token, disabled account, and disabled service return distinct stable codes.

- [ ] **Step 4: Write failing logout tests**

Logout revokes the presented token and is idempotent.

- [ ] **Step 5: Run service tests RED**

- [ ] **Step 6: Implement the domain service**

Return:

```json
{
  "ok": true,
  "user": {"username": "display_name"},
  "sessionToken": "opaque-value",
  "service": {"enabled": true, "announcement": ""},
  "client": {"minimumVersion": "1.0.0"}
}
```

Only registration and login return `sessionToken`; verification never rotates it in v1.

- [ ] **Step 7: Run service tests GREEN**

- [ ] **Step 8: Commit**

```bash
git add auth_service/service.py auth_service/errors.py test/test_auth_service.py
git commit -m "feat: implement authentication workflow" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 5: Rate Limiting and Public API

**Files:**
- Create: `auth_service/rate_limit.py`
- Create: `auth_service/api.py`
- Create: `test/test_auth_api.py`

**Interfaces:**
- Consumes: `AuthService`
- Produces exact routes:
  - `POST /v1/register`
  - `POST /v1/login`
  - `POST /v1/session/verify`
  - `POST /v1/logout`
- Produces: `SlidingWindowLimiter.check(action: str, source_ip: str, username: str = "") -> RateLimitResult`

- [ ] **Step 1: Write failing route-contract tests**

Assert JSON-only requests, 16 KiB maximum body, required fields, stable HTTP status mapping, `Cache-Control: no-store`, and request IDs.

- [ ] **Step 2: Write failing source-IP tests**

Trust `request.remote_addr` only. Ignore `X-Forwarded-For` until a configured reverse proxy is added.

- [ ] **Step 3: Write failing rate-limit tests**

Enforce:

```text
register: 5 per IP per hour
login: 20 per IP per 10 minutes
login: 10 failures per normalized username per 10 minutes
verify: 60 per IP per 10 minutes
```

Return `429` plus `Retry-After`.

- [ ] **Step 4: Write failing redaction/log tests**

Capture Flask logs and prove request JSON, passwords, and complete tokens never appear.

- [ ] **Step 5: Run API tests RED**

- [ ] **Step 6: Implement blueprint and error mapper**

All responses use:

```json
{
  "ok": false,
  "error": {"code": "INVALID_CREDENTIALS", "message": "用户名或密码错误"},
  "requestId": "uuid"
}
```

- [ ] **Step 7: Run API tests GREEN**

- [ ] **Step 8: Commit**

```bash
git add auth_service/rate_limit.py auth_service/api.py test/test_auth_api.py
git commit -m "feat: expose authentication API" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 6: SSH Administration CLI

**Files:**
- Create: `auth_service/cli.py`
- Create: `test/test_auth_cli.py`

**Interfaces:**
- Produces executable module: `python -m auth_service.cli`
- Produces commands:
  - `user list`
  - `user disable USERNAME`
  - `user enable USERNAME`
  - `service disable`
  - `service enable`
  - `announcement set TEXT`
  - `status`

- [ ] **Step 1: Write failing CLI tests**

Use a temporary database and assert exit codes, stable table output, unknown-user behavior, and idempotent enable/disable operations.

- [ ] **Step 2: Write negative capability tests**

Assert parser help contains no password view, password set, password reset, session-token export, or administrator HTTP commands.

- [ ] **Step 3: Run CLI tests RED**

- [ ] **Step 4: Implement CLI**

Use `argparse`; print usernames, status, creation date, and last login only. Never print password hashes or session hashes.

- [ ] **Step 5: Run CLI tests GREEN**

- [ ] **Step 6: Commit**

```bash
git add auth_service/cli.py test/test_auth_cli.py
git commit -m "feat: add SSH authentication controls" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 7: Production Runner and Ubuntu Deployment Assets

**Files:**
- Create: `auth_service/wsgi.py`
- Create: `auth_service/deploy/stzb-auth.service`
- Create: `auth_service/deploy/install.sh`
- Create: `auth_service/deploy/backup.sh`
- Create: `auth_service/README.md`
- Test: `test/test_auth_deploy_assets.py`

**Interfaces:**
- Produces systemd unit `stzb-auth.service`
- Produces server command `/opt/stzb-auth/venv/bin/waitress-serve --listen=0.0.0.0:9080 auth_service.wsgi:app`
- Produces CLI wrapper `/usr/local/bin/stzb-auth`
- Produces backup directory `/var/backups/stzb-auth`

- [ ] **Step 1: Write failing deployment-asset tests**

Assert the unit uses an unprivileged `stzb-auth` user, `UMask=0077`, restart-on-failure, a dedicated environment file, write access only to `/var/lib/stzb-auth`, and no embedded secrets.

- [ ] **Step 2: Write failing installation-script tests**

Assert `install.sh` is idempotent, creates the service user/directories, installs pinned requirements into `/opt/stzb-auth/venv`, installs the CLI wrapper, and does not start until `/etc/stzb-auth.env` contains a valid pepper.

- [ ] **Step 3: Write failing backup-script tests**

Assert backup uses SQLite online backup, creates timestamped mode-0600 files, keeps 14 daily backups, and never copies the environment file.

- [ ] **Step 4: Run deploy tests RED**

- [ ] **Step 5: Implement runner and assets**

`wsgi.py` creates exactly one app. README documents firewall opening for TCP 9080 and repeats the accepted HTTP interception risk.

- [ ] **Step 6: Run deploy tests GREEN**

- [ ] **Step 7: Commit**

```bash
git add auth_service test/test_auth_deploy_assets.py
git commit -m "feat: add Ubuntu auth deployment" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 8: Local Integration and Contract Verification

**Files:**
- Create: `scripts/verify_auth_contract.py`
- Create: `test/test_auth_contract.py`
- Modify: `auth_service/README.md`

**Interfaces:**
- Produces: `verify_auth_contract.py --base-url URL --username USERNAME --password PASSWORD`
- Contract output contains register/login/verify/logout status without printing secrets.

- [ ] **Step 1: Write failing contract tests**

Start the Flask app on an ephemeral port and verify register, verify, logout, rejected verify, service disable, and account disable behavior end to end.

- [ ] **Step 2: Run contract tests RED**

- [ ] **Step 3: Implement the verifier**

Use standard-library `urllib.request`; hold the token only in memory and print redacted phase results.

- [ ] **Step 4: Run all auth tests GREEN**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest \
  test.test_auth_config \
  test.test_auth_repository \
  test.test_auth_security \
  test.test_auth_service \
  test.test_auth_api \
  test.test_auth_cli \
  test.test_auth_deploy_assets \
  test.test_auth_contract -v
```

Expected: all tests pass with zero errors or failures.

- [ ] **Step 5: Commit**

```bash
git add scripts/verify_auth_contract.py test/test_auth_contract.py auth_service/README.md
git commit -m "test: verify authentication contract" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 9: Deploy to Ubuntu and Perform Live Smoke Test

**Files:**
- Server create: `/opt/stzb-auth`
- Server create: `/var/lib/stzb-auth`
- Server create: `/etc/stzb-auth.env`
- Server create: `/etc/systemd/system/stzb-auth.service`

**Interfaces:**
- Consumes: Tasks 1-8 and SSH access to `ubuntu@152.136.236.184`
- Produces: public API `http://152.136.236.184:9080/v1/*`

- [ ] **Step 1: Obtain explicit deployment confirmation**

Deployment changes a remote server and opens a public port. Before SSH, show the exact files, service name, port, and firewall command and obtain user confirmation.

- [ ] **Step 2: Inspect the server without mutation**

Run:

```bash
ssh ubuntu@152.136.236.184 'uname -a; python3 --version; systemctl --version | head -1; sudo ufw status'
```

Record current Python, firewall, disk, and conflicting port/service state.

- [ ] **Step 3: Generate the server-only pepper**

Generate on the server:

```bash
python3 -c 'import secrets; print(secrets.token_urlsafe(48))'
```

Write it only to `/etc/stzb-auth.env` with mode `0600`. Never copy it into the repository or chat output.

- [ ] **Step 4: Install and start**

Upload the tracked service package, run `sudo auth_service/deploy/install.sh`, open TCP 9080 only if UFW is active, and start `stzb-auth.service`.

- [ ] **Step 5: Verify process and API**

Run:

```bash
ssh ubuntu@152.136.236.184 'sudo systemctl --no-pager --full status stzb-auth'
.venv/bin/python scripts/verify_auth_contract.py \
  --base-url http://152.136.236.184:9080 \
  --username smoke_test_user \
  --password 'temporary-test-password'
```

Expected: register, verify, logout, and rejected post-logout verification behave per contract.

- [ ] **Step 6: Disable the smoke-test account**

Run `sudo stzb-auth user disable smoke_test_user`; retain the row as an audit fixture rather than adding a delete-user capability.

- [ ] **Step 7: Verify backup and restart**

Run one backup, restart the service, and verify an existing valid non-smoke session remains valid.

- [ ] **Step 8: Record deployment evidence**

Add the deployed service version, port, UTC deployment time, and verification commands to `auth_service/README.md`; do not record secrets.

- [ ] **Step 9: Commit deployment documentation**

```bash
git add auth_service/README.md
git commit -m "docs: record auth service deployment" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```
