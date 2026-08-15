# Windows Desktop Distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver `STZB助手-Setup-x64.exe`, which authenticates at startup, launches the bundled local backend, preserves the existing Dashboard in WebView2, manages the system tray, and guides Npcap installation only when capture is unavailable.

**Architecture:** First separate immutable application resources from `%LOCALAPPDATA%` user data and expose a controlled desktop backend entrypoint. Then build a self-contained .NET 8 WPF/WebView2 shell that owns authentication, credentials, sidecar lifecycle, tray behavior, and diagnostics. Finally assemble Python, Java, Kotlin, WebView2 Fixed Version, and the shell with Inno Setup on Windows.

**Tech Stack:** Python 3.9-compatible backend, Flask, Scapy, PyInstaller, .NET 8 WPF, WebView2, Windows Credential Manager, Java 17 runtime, Kotlin installDist, Inno Setup 6, PowerShell, Python unittest, xUnit.

## Global Constraints

- Target Windows 10/11 x64 only.
- Preserve the current Dashboard HTML, CSS, JavaScript, navigation, and `208px` desktop sidebar.
- The desktop app never opens the Dashboard in the system browser during normal startup.
- Flask listens only on `127.0.0.1` with a dynamic port selected by the shell.
- Mutable files live under `%LOCALAPPDATA%\STZBWatcher`; the install directory is treated as read-only.
- Do not scan or migrate `D:\nettest`.
- The authentication API base is centralized and initially points to `http://152.136.236.184:9080`.
- Startup authentication is mandatory; there is no runtime heartbeat.
- Passwords are never stored locally; session tokens use Windows Credential Manager.
- The login/register screen states that the software is completely free and resale is prohibited.
- There is no password recovery; registration warns users that passwords cannot be recovered.
- Npcap free installer is not bundled; failed capture capability detection opens the official Npcap path and supports recheck.
- Python, Java, .NET, WebView2 Fixed Version, Dashboard resources, and Kotlin engine are bundled.
- No automatic updater in v1.
- Keep backend syntax compatible with Python 3.9.
- Every commit message ends with `Co-authored-by: TRAE CLI <noreply@bytedance.com>`.

---

### Task 1: Runtime Path Model

**Files:**
- Create: `runtime_paths.py`
- Create: `test/test_runtime_paths.py`
- Modify: `api_server.py`
- Modify: `profile_manager.py`
- Modify: `realtime_writer.py`
- Modify: `scrapy_v2.py`
- Modify: `battle_engine_adapter.py`

**Interfaces:**
- Produces: `RuntimePaths.from_env(module_file: str, frozen: bool = False) -> RuntimePaths`
- Produces immutable fields `resource_dir`, `data_dir`, `capture_dir`, `log_dir`, `cache_dir`, `database_path`, `profiles_path`, `current_profile_path`, `battle_engine_dir`, `java_home`
- Environment contract:
  - `STZB_RESOURCE_DIR`
  - `STZB_DATA_DIR`
  - `STZB_JAVA_HOME`
  - `STZB_BATTLE_ENGINE_DIR`

- [ ] **Step 1: Write failing path tests**

Cover development defaults, frozen executable defaults, explicit environment overrides, Windows path normalization, directory creation, and the rule that no writable path falls under `resource_dir` when `STZB_DATA_DIR` is supplied.

- [ ] **Step 2: Run tests RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_runtime_paths -v
```

- [ ] **Step 3: Implement `RuntimePaths`**

Use `pathlib.Path`, `@dataclass(frozen=True)`, and one `ensure_writable_dirs()` method. Do not create directories at module import.

- [ ] **Step 4: Replace global path derivation**

Route mutable database/profile/capture/log paths through `RuntimePaths`; route static files, intelligence snapshots, hero data, and battle-engine resources through `resource_dir`.

- [ ] **Step 5: Add regression assertions**

Import each affected module with a temporary `STZB_DATA_DIR` and assert it does not create `stzb.db`, `capture_new`, or profile files beside source code.

- [ ] **Step 6: Run focused tests GREEN**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest \
  test.test_runtime_paths \
  test.test_battle_engine_adapter \
  test.test_scrapy_capture -v
```

- [ ] **Step 7: Commit**

```bash
git add runtime_paths.py api_server.py profile_manager.py realtime_writer.py scrapy_v2.py battle_engine_adapter.py test/test_runtime_paths.py
git commit -m "refactor: separate runtime data paths" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 2: Controlled Backend Lifecycle

**Files:**
- Create: `desktop_backend.py`
- Create: `desktop_control.py`
- Create: `test/test_desktop_backend.py`
- Modify: `api_server.py`
- Modify: `scrapy_v2.py`
- Modify: `realtime_writer.py`

**Interfaces:**
- Produces CLI:
  - `python desktop_backend.py --host 127.0.0.1 --port PORT --data-dir PATH --control-token TOKEN`
- Produces `GET /api/desktop/health`
- Produces authenticated `POST /api/desktop/capture/pause`
- Produces authenticated `POST /api/desktop/capture/resume`
- Produces authenticated `POST /api/desktop/shutdown`
- Header: `X-STZB-Desktop-Token`
- Produces: `CaptureController.status()`, `.pause()`, `.resume()`

- [ ] **Step 1: Write failing lifecycle tests**

Assert desktop mode never opens a browser, binds only the requested host/port, returns component health, and imports without starting writer/sniffer threads.

- [ ] **Step 2: Write failing control-auth tests**

Missing or wrong token returns `401`; correct token pauses/resumes capture and requests graceful shutdown. Use `hmac.compare_digest`.

- [ ] **Step 3: Write failing state-transition tests**

Cover `starting`, `running`, `paused`, `failed`, and `stopped`; repeated pause/resume calls are idempotent.

- [ ] **Step 4: Run tests RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest test.test_desktop_backend -v
```

- [ ] **Step 5: Implement desktop entrypoint**

Parse arguments, set runtime environment before importing `api_server`, start runtime services explicitly, and emit one machine-readable startup line:

```json
{"event":"ready","port":49152,"pid":1234}
```

- [ ] **Step 6: Implement graceful shutdown**

Stop capture first, stop writer polling, close resources, then terminate the Flask server. If the chosen serving API cannot be stopped safely, run Flask through `werkzeug.serving.make_server` owned by `desktop_backend.py`.

- [ ] **Step 7: Run tests GREEN**

- [ ] **Step 8: Commit**

```bash
git add desktop_backend.py desktop_control.py api_server.py scrapy_v2.py realtime_writer.py test/test_desktop_backend.py
git commit -m "feat: add desktop backend lifecycle" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 3: Capture Capability Probe

**Files:**
- Create: `capture_capability.py`
- Create: `test/test_capture_capability.py`
- Modify: `desktop_control.py`

**Interfaces:**
- Produces enum values `AVAILABLE`, `DRIVER_MISSING`, `PERMISSION_DENIED`, `NO_INTERFACE`, `UNKNOWN_ERROR`
- Produces: `probe_capture_capability(filter_expression: str = "tcp and src port 8001", timeout_seconds: float = 0.2) -> CaptureProbeResult`
- `CaptureProbeResult.to_json()` returns `status`, `message`, `interfaces`, and `detailsCode`

- [ ] **Step 1: Write failing classifier tests**

Mock Scapy/libpcap failures for missing Npcap DLL, access denied, empty interface list, BPF compilation failure, and success.

- [ ] **Step 2: Write failing non-persistence test**

Assert the probe does not write packet files, change profile binding, start `RealtimeWriter`, or retain captured packets.

- [ ] **Step 3: Run tests RED**

- [ ] **Step 4: Implement the short-lived probe**

Enumerate interfaces, select a usable non-loopback interface, compile/start the current filter for at most 200 ms with `store=False`, and map exceptions without leaking raw paths to the UI.

- [ ] **Step 5: Expose probe status in desktop health**

Health response reports capture status independently from backend and battle-engine status.

- [ ] **Step 6: Run tests GREEN**

- [ ] **Step 7: Commit**

```bash
git add capture_capability.py desktop_control.py test/test_capture_capability.py
git commit -m "feat: detect Windows capture capability" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 4: Python Sidecar Build

**Files:**
- Create: `packaging/pyinstaller/stzb-backend.spec`
- Create: `packaging/pyinstaller/hooks/hook-scapy.py`
- Create: `packaging/scripts/build_backend.ps1`
- Create: `packaging/scripts/verify_backend_bundle.py`
- Modify: `.gitignore`
- Test: `test/test_backend_packaging_manifest.py`

**Interfaces:**
- Produces: `dist/backend/stzb-backend.exe`
- Produces resource folders `static`, `data/intelligence`, hero/config JSON assets, and Python modules required at runtime
- Consumes `STZB_DATA_DIR`, `STZB_JAVA_HOME`, `STZB_BATTLE_ENGINE_DIR`

- [ ] **Step 1: Write failing manifest tests**

Parse the spec and assert required modules/assets are included, databases/captures/logs are excluded, and `desktop_backend.py` is the entrypoint.

- [ ] **Step 2: Write failing bundle-verifier test**

The verifier starts the built backend with a temporary data directory, waits for `/api/desktop/health`, validates `/` and representative static assets, and shuts down with the control token.

- [ ] **Step 3: Run tests RED**

- [ ] **Step 4: Create deterministic PyInstaller spec**

Use one-folder mode, not one-file mode, so startup is fast and resources are inspectable. Pin PyInstaller in `packaging/pyinstaller/requirements-build.txt`.

- [ ] **Step 5: Implement Windows build script**

`build_backend.ps1` creates an isolated build venv, installs exact build/runtime pins, runs PyInstaller, and invokes the verifier. It fails on missing assets or nonzero backend exit.

- [ ] **Step 6: Run static packaging tests GREEN on macOS**

Run the manifest/parser tests only; do not claim a Windows executable build from macOS.

- [ ] **Step 7: Build and verify on Windows**

```powershell
powershell -ExecutionPolicy Bypass -File packaging\scripts\build_backend.ps1
python packaging\scripts\verify_backend_bundle.py dist\backend
```

Expected: backend health, `/`, and static probes pass; shutdown leaves no process.

- [ ] **Step 8: Commit**

```bash
git add packaging/pyinstaller packaging/scripts .gitignore test/test_backend_packaging_manifest.py
git commit -m "build: package Python desktop backend" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 5: Desktop Solution and Startup State Machine

**Files:**
- Create: `desktop/StzbDesktop.sln`
- Create: `desktop/src/StzbDesktop/StzbDesktop.csproj`
- Create: `desktop/src/StzbDesktop/App.xaml`
- Create: `desktop/src/StzbDesktop/App.xaml.cs`
- Create: `desktop/src/StzbDesktop/Startup/StartupState.cs`
- Create: `desktop/src/StzbDesktop/Startup/StartupCoordinator.cs`
- Create: `desktop/tests/StzbDesktop.Tests/StzbDesktop.Tests.csproj`
- Create: `desktop/tests/StzbDesktop.Tests/StartupCoordinatorTests.cs`
- Create: `desktop/Directory.Build.props`

**Interfaces:**
- Produces states `Initializing`, `AuthenticationRequired`, `VerifyingSession`, `StartingBackend`, `CheckingCapture`, `LoadingDashboard`, `Ready`, `Blocked`, `Failed`
- Produces: `StartupCoordinator.RunAsync(CancellationToken) -> Task<StartupResult>`
- Consumes abstractions `IAuthClient`, `ICredentialStore`, `IBackendProcess`, `ICaptureStatusProvider`

- [ ] **Step 1: Create the Windows solution**

On Windows with .NET 8 SDK:

```powershell
dotnet new sln -n StzbDesktop -o desktop
dotnet new wpf -n StzbDesktop -o desktop\src\StzbDesktop --framework net8.0
dotnet new xunit -n StzbDesktop.Tests -o desktop\tests\StzbDesktop.Tests --framework net8.0
dotnet sln desktop\StzbDesktop.sln add desktop\src\StzbDesktop\StzbDesktop.csproj
dotnet sln desktop\StzbDesktop.sln add desktop\tests\StzbDesktop.Tests\StzbDesktop.Tests.csproj
```

- [ ] **Step 2: Pin NuGet packages**

Pin exact versions of `Microsoft.Web.WebView2`, `CredentialManagement`, and the xUnit test packages after checking NuGet. Commit `packages.lock.json`; enable locked restore.

- [ ] **Step 3: Write failing state-machine tests**

Cover saved-token success, no token, invalid token, service disabled, auth unreachable, backend failure, capture unavailable, and user cancellation.

- [ ] **Step 4: Run tests RED**

```powershell
dotnet test desktop\StzbDesktop.sln --no-restore
```

- [ ] **Step 5: Implement the coordinator**

State transitions are explicit and observable. Authentication failure must occur before any backend process starts.

- [ ] **Step 6: Run tests GREEN**

- [ ] **Step 7: Commit**

```bash
git add desktop
git commit -m "feat: scaffold Windows desktop shell" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 6: Authentication Client and Windows Credential Manager

**Files:**
- Create: `desktop/src/StzbDesktop/Auth/AuthClient.cs`
- Create: `desktop/src/StzbDesktop/Auth/AuthModels.cs`
- Create: `desktop/src/StzbDesktop/Auth/AuthSettings.cs`
- Create: `desktop/src/StzbDesktop/Security/WindowsCredentialStore.cs`
- Create: `desktop/tests/StzbDesktop.Tests/AuthClientTests.cs`
- Create: `desktop/tests/StzbDesktop.Tests/WindowsCredentialStoreTests.cs`
- Create: `desktop/src/StzbDesktop/appsettings.json`

**Interfaces:**
- Consumes the exact API contract from the Ubuntu auth plan.
- Produces: `RegisterAsync(username, password, clientVersion, cancellationToken)`
- Produces: `LoginAsync(username, password, clientVersion, cancellationToken)`
- Produces: `VerifySessionAsync(token, clientVersion, cancellationToken)`
- Produces: `LogoutAsync(token, cancellationToken)`
- Credential target: `STZBWatcher/AuthSession`

- [ ] **Step 1: Write failing HTTP contract tests**

Use a local fake `HttpMessageHandler` and assert JSON names, 10-second timeout, status/error mapping, `Cache-Control` handling, no automatic retries for password requests, and no token in exception text.

- [ ] **Step 2: Write failing credential tests**

Assert save/read/delete behavior, no password field, overwrite semantics, and deletion after `ACCOUNT_DISABLED` or `SESSION_INVALID`.

- [ ] **Step 3: Run tests RED**

- [ ] **Step 4: Implement centralized auth settings**

`appsettings.json` contains one `AuthBaseUrl` value initially set to `http://152.136.236.184:9080`; no other source file contains that IP.

- [ ] **Step 5: Implement auth and credential adapters**

Use `HttpClient`, `System.Text.Json`, and Windows Credential Manager. Redact `password`, `sessionToken`, and Authorization headers before logging.

- [ ] **Step 6: Run tests GREEN**

- [ ] **Step 7: Commit**

```bash
git add desktop/src/StzbDesktop/Auth desktop/src/StzbDesktop/Security desktop/src/StzbDesktop/appsettings.json desktop/tests/StzbDesktop.Tests
git commit -m "feat: connect desktop authentication" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 7: Login, Registration, and Startup UI

**Files:**
- Create: `desktop/src/StzbDesktop/Views/StartupWindow.xaml`
- Create: `desktop/src/StzbDesktop/Views/StartupWindow.xaml.cs`
- Create: `desktop/src/StzbDesktop/ViewModels/StartupViewModel.cs`
- Create: `desktop/src/StzbDesktop/Styles/Colors.xaml`
- Create: `desktop/src/StzbDesktop/Styles/Controls.xaml`
- Create: `desktop/tests/StzbDesktop.Tests/StartupViewModelTests.cs`

**Interfaces:**
- Consumes: `StartupCoordinator` and `IAuthClient`
- Produces commands `LoginCommand`, `RegisterCommand`, `RetryCommand`, `CancelCommand`
- Produces UI states login, register, startup progress, blocked, and error

- [ ] **Step 1: Write failing view-model tests**

Cover field validation, command disabling during requests, password clearing after failure, saved-token auto verify, service announcement, and stable user-facing errors.

- [ ] **Step 2: Write failing copy tests**

Assert the rendered resources contain exactly:

```text
本软件完全免费，禁止任何形式的倒卖、付费代装或捆绑销售。
密码无法找回，请自行妥善保存。
```

- [ ] **Step 3: Run tests RED**

- [ ] **Step 4: Implement the login/startup window**

Use the existing dark blue/cyan visual language without embedding or modifying the Dashboard. Password boxes never bind plaintext into logs or persistent settings.

- [ ] **Step 5: Add accessible behavior**

Enter submits the active form, labels are keyboard accessible, errors receive focus, and progress text exposes the current startup stage.

- [ ] **Step 6: Run tests GREEN**

- [ ] **Step 7: Commit**

```bash
git add desktop/src/StzbDesktop/Views desktop/src/StzbDesktop/ViewModels desktop/src/StzbDesktop/Styles desktop/tests/StzbDesktop.Tests
git commit -m "feat: add desktop login experience" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 8: Backend Process Ownership and Diagnostics

**Files:**
- Create: `desktop/src/StzbDesktop/Backend/BackendProcess.cs`
- Create: `desktop/src/StzbDesktop/Backend/BackendHealthClient.cs`
- Create: `desktop/src/StzbDesktop/Backend/BackendPaths.cs`
- Create: `desktop/src/StzbDesktop/Diagnostics/RedactingLogger.cs`
- Create: `desktop/tests/StzbDesktop.Tests/BackendProcessTests.cs`
- Create: `desktop/tests/StzbDesktop.Tests/RedactingLoggerTests.cs`

**Interfaces:**
- Produces: `BackendProcess.StartAsync(port, dataDir, controlToken, cancellationToken)`
- Produces: `BackendProcess.StopAsync(timeout, cancellationToken)`
- Produces: `BackendHealthClient.WaitUntilReadyAsync(baseUri, timeout, cancellationToken)`
- Produces owned PID and process start timestamp for safe cleanup

- [ ] **Step 1: Write failing command-line tests**

Assert exact quoting for paths with spaces, loopback host, dynamic port, data directory, generated 256-bit control token, bundled Java path, and bundled engine path.

- [ ] **Step 2: Write failing ownership tests**

Only terminate the exact child process created by this shell. Never kill by image name. Graceful shutdown gets five seconds before process-tree termination.

- [ ] **Step 3: Write failing crash/log tests**

Capture stdout/stderr to `%LOCALAPPDATA%\STZBWatcher\logs\backend.log`, retain exit code, cap log size, and redact control/auth tokens.

- [ ] **Step 4: Run tests RED**

- [ ] **Step 5: Implement backend ownership**

Reserve an ephemeral loopback port, release it immediately before process start, retry once on bind failure, and health-check `/api/desktop/health`.

- [ ] **Step 6: Implement stale-instance cleanup**

Persist PID plus start timestamp in the cache directory. On next startup, clean only when both PID and start timestamp match a process launched by this application.

- [ ] **Step 7: Run tests GREEN**

- [ ] **Step 8: Commit**

```bash
git add desktop/src/StzbDesktop/Backend desktop/src/StzbDesktop/Diagnostics desktop/tests/StzbDesktop.Tests
git commit -m "feat: manage desktop backend process" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 9: WebView2 Main Window and Navigation Policy

**Files:**
- Create: `desktop/src/StzbDesktop/Views/MainWindow.xaml`
- Create: `desktop/src/StzbDesktop/Views/MainWindow.xaml.cs`
- Create: `desktop/src/StzbDesktop/Web/DashboardHost.cs`
- Create: `desktop/src/StzbDesktop/Web/WebViewRuntimeLocator.cs`
- Create: `desktop/tests/StzbDesktop.Tests/DashboardHostTests.cs`

**Interfaces:**
- Consumes local backend base URI.
- Produces: `DashboardHost.InitializeAsync(CoreWebView2Environment environment, Uri localBaseUri)`
- Allowed navigation origins: the exact dynamic `http://127.0.0.1:<port>` origin only

- [ ] **Step 1: Write failing navigation-policy tests**

Allow local Dashboard routes/assets; cancel other in-WebView navigation and open `http`/`https` external links through the system browser. Reject `file`, `javascript`, custom schemes, and non-loopback local origins.

- [ ] **Step 2: Write failing runtime-location tests**

Assert the shell uses `runtime\webview2` beside the installed app and a user-data directory under `%LOCALAPPDATA%\STZBWatcher\cache\webview2`.

- [ ] **Step 3: Run tests RED**

- [ ] **Step 4: Implement WebView host**

Wait for backend health, create the fixed-version environment, navigate to `/`, expose reload/restart diagnostics, and keep browser developer tools disabled in release builds.

- [ ] **Step 5: Verify Dashboard compatibility on Windows**

Run the existing Dashboard E2E against the sidecar and manually verify the 12 visible pages, command panel, SSE updates, simulator, exports, and responsive layout inside WebView2.

- [ ] **Step 6: Run tests GREEN**

- [ ] **Step 7: Commit**

```bash
git add desktop/src/StzbDesktop/Views/MainWindow.xaml desktop/src/StzbDesktop/Views/MainWindow.xaml.cs desktop/src/StzbDesktop/Web desktop/tests/StzbDesktop.Tests
git commit -m "feat: host dashboard in WebView2" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 10: Tray, Capture Controls, and Exit Semantics

**Files:**
- Create: `desktop/src/StzbDesktop/Tray/TrayController.cs`
- Create: `desktop/src/StzbDesktop/Tray/TrayMenuState.cs`
- Create: `desktop/src/StzbDesktop/Settings/DesktopSettings.cs`
- Create: `desktop/tests/StzbDesktop.Tests/TrayControllerTests.cs`
- Modify: `desktop/src/StzbDesktop/Views/MainWindow.xaml.cs`

**Interfaces:**
- Produces tray actions open, pause/resume capture, status, logs, restart backend, logout, and full exit.
- Produces setting `ShowCloseToTrayNotice`

- [ ] **Step 1: Write failing tray-state tests**

Cover running, paused, backend failed, capture unavailable, and shutdown states; assert menu labels and enabled actions.

- [ ] **Step 2: Write failing close-semantics tests**

First window close hides to tray and shows the exact explanatory notice; later closes respect the stored preference. Full exit performs graceful backend shutdown.

- [ ] **Step 3: Write failing logout tests**

Logout revokes the remote session when reachable, always deletes the local credential, stops the backend, and returns to the login window.

- [ ] **Step 4: Run tests RED**

- [ ] **Step 5: Implement tray controller**

Use `System.Windows.Forms.NotifyIcon` from WPF. Keep one instance for the application lifetime and dispose it on full exit.

- [ ] **Step 6: Implement backend restart**

Restart retains authentication, allocates a new port/control token, and reloads WebView only after health passes.

- [ ] **Step 7: Run tests GREEN**

- [ ] **Step 8: Commit**

```bash
git add desktop/src/StzbDesktop/Tray desktop/src/StzbDesktop/Settings desktop/src/StzbDesktop/Views/MainWindow.xaml.cs desktop/tests/StzbDesktop.Tests
git commit -m "feat: add tray lifecycle controls" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 11: Npcap Guidance

**Files:**
- Create: `desktop/src/StzbDesktop/Capture/CaptureCapabilityViewModel.cs`
- Create: `desktop/src/StzbDesktop/Capture/NpcapInstallerGuide.cs`
- Create: `desktop/src/StzbDesktop/Views/CaptureSetupDialog.xaml`
- Create: `desktop/src/StzbDesktop/Views/CaptureSetupDialog.xaml.cs`
- Create: `desktop/tests/StzbDesktop.Tests/CaptureSetupTests.cs`

**Interfaces:**
- Consumes backend capture status.
- Produces official URL constant `https://npcap.com/#download`
- Produces commands `OpenOfficialDownload`, `Recheck`, `ContinueWithoutCapture`

- [ ] **Step 1: Write failing status-mapping tests**

Map `driver_missing`, `permission_denied`, `no_interface`, and `unknown_error` to distinct Chinese guidance.

- [ ] **Step 2: Write failing redistribution guard test**

Assert no Npcap executable is present in packaging manifests and only the official HTTPS URL is used.

- [ ] **Step 3: Run tests RED**

- [ ] **Step 4: Implement the setup dialog**

Driver missing opens the official system-browser URL. Recheck invokes the backend probe. Continuing without capture enters the Dashboard with a persistent “实时采集未启用” status.

- [ ] **Step 5: Run tests GREEN**

- [ ] **Step 6: Commit**

```bash
git add desktop/src/StzbDesktop/Capture desktop/src/StzbDesktop/Views/CaptureSetupDialog.xaml desktop/src/StzbDesktop/Views/CaptureSetupDialog.xaml.cs desktop/tests/StzbDesktop.Tests
git commit -m "feat: guide Windows capture setup" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 12: Java, Kotlin, and WebView2 Runtime Assembly

**Files:**
- Create: `packaging/runtime/runtime-lock.json`
- Create: `packaging/scripts/fetch_runtimes.ps1`
- Create: `packaging/scripts/build_battle_engine.ps1`
- Create: `packaging/scripts/assemble_app.ps1`
- Create: `packaging/scripts/verify_runtime_hashes.py`
- Test: `test/test_runtime_lock.py`

**Interfaces:**
- Produces staging tree:

```text
artifacts/app/
├── STZB助手.exe
├── backend/
├── battle-engine/
├── runtime/java/
└── runtime/webview2/
```

- [ ] **Step 1: Write failing lock-file tests**

Require exact version, architecture, official URL, SHA-256, license name, and target directory for Java 17 x64 and WebView2 Fixed Version x64.

- [ ] **Step 2: Write failing assembly tests**

Assert no development database, capture, log, `.git`, `.venv`, Gradle cache, test fixture archive, SSH key, or auth pepper enters staging.

- [ ] **Step 3: Run tests RED**

- [ ] **Step 4: Implement verified runtime fetching**

Download only when absent, verify SHA-256 before extraction, and fail closed on mismatch. Preserve accompanying runtime licenses.

- [ ] **Step 5: Build the Kotlin distribution**

Use Java 17 and:

```powershell
.\gradlew.bat -p battle-engine test installDist
```

Copy the installDist output without Gradle caches or test resources.

- [ ] **Step 6: Publish the WPF shell**

```powershell
dotnet publish desktop\src\StzbDesktop\StzbDesktop.csproj `
  -c Release -r win-x64 --self-contained true `
  -p:PublishSingleFile=false -p:PublishReadyToRun=true `
  -o artifacts\shell
```

- [ ] **Step 7: Assemble and smoke-test staging**

Start `artifacts/app/STZB助手.exe`, use a test auth service, verify login, backend health, Dashboard load, simulator metadata, and full exit.

- [ ] **Step 8: Run tests GREEN**

- [ ] **Step 9: Commit**

```bash
git add packaging/runtime packaging/scripts test/test_runtime_lock.py
git commit -m "build: assemble Windows application runtimes" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 13: Inno Setup Installer

**Files:**
- Create: `packaging/windows/STZBWatcher.iss`
- Create: `packaging/windows/license.txt`
- Create: `packaging/windows/README.md`
- Create: `packaging/scripts/build_installer.ps1`
- Test: `test/test_windows_installer_manifest.py`

**Interfaces:**
- Produces: `artifacts/installer/STZB助手-Setup-x64.exe`
- App data root: `{localappdata}\STZBWatcher`
- Install root: `{autopf}\STZBWatcher`

- [ ] **Step 1: Write failing installer-manifest tests**

Assert x64-only install, per-machine executable install, desktop/start-menu shortcuts, no Npcap binary, no legacy-data scan, preservation of LocalAppData on upgrade, and optional explicit data deletion at uninstall.

- [ ] **Step 2: Write failing version tests**

Assert installer version, shell assembly version, backend version file, and runtime lock application version match.

- [ ] **Step 3: Run tests RED**

- [ ] **Step 4: Implement Inno Setup script**

Install the staged tree, create shortcuts, start the application after install, and display no claim that Npcap is bundled.

- [ ] **Step 5: Implement uninstall data prompt**

Default is “保留本地数据”. Delete `%LOCALAPPDATA%\STZBWatcher` only after explicit affirmative confirmation.

- [ ] **Step 6: Build installer on Windows**

```powershell
powershell -ExecutionPolicy Bypass -File packaging\scripts\build_installer.ps1
```

Expected: Inno Setup exits 0 and creates exactly one x64 setup executable.

- [ ] **Step 7: Commit**

```bash
git add packaging/windows packaging/scripts/build_installer.ps1 test/test_windows_installer_manifest.py
git commit -m "build: add Windows installer" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 14: Automated Regression and Clean-Machine Acceptance

**Files:**
- Create: `desktop/tests/StzbDesktop.IntegrationTests/StzbDesktop.IntegrationTests.csproj`
- Create: `desktop/tests/StzbDesktop.IntegrationTests/DesktopSmokeTests.cs`
- Create: `packaging/windows/ACCEPTANCE.md`
- Modify: `README.md`

**Interfaces:**
- Consumes auth service, staged app, and installer.
- Produces recorded acceptance checklist with installer SHA-256 and version.

- [ ] **Step 1: Add integration-test harness**

Use a local fake auth server for deterministic tests. Verify saved-token startup, blocked startup, backend process ownership, Dashboard HTTP load, external-link routing, tray hide/restore, logout, and full exit.

- [ ] **Step 2: Run backend regression suite**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache .venv/bin/python -m unittest discover -s test -v
```

Expected: all project Python tests pass. Record unrelated pre-existing failures instead of modifying unrelated code.

- [ ] **Step 3: Run Windows .NET tests**

```powershell
dotnet test desktop\StzbDesktop.sln -c Release --locked-mode
```

Expected: all desktop unit and integration tests pass.

- [ ] **Step 4: Run Kotlin tests**

```powershell
.\gradlew.bat -p battle-engine test
```

Expected: build succeeds with zero failed tests.

- [ ] **Step 5: Test on clean Windows 10 x64**

Verify install without preinstalled Python, Java, or .NET; register/login; Dashboard parity; Npcap guidance; capture after Npcap install; simulator; tray; full exit; upgrade preservation; uninstall preservation/deletion.

- [ ] **Step 6: Test on clean Windows 11 x64**

Repeat the same checklist and verify WebView2 Fixed Version does not rely on installed Edge runtime.

- [ ] **Step 7: Test live account controls**

With user approval and the deployed auth service, disable a test account and globally disable service. Verify each blocks the next startup but does not interrupt an already-running client. Re-enable service after the test.

- [ ] **Step 8: Record release evidence**

Document OS builds, app version, installer SHA-256, test commands, test counts, Npcap version installed from the official source, and known HTTP authentication risk.

- [ ] **Step 9: Update user documentation**

README explains installation, registration, free/no-resale notice, no password recovery, close-to-tray, Npcap guidance, data location, manual upgrades, and uninstall behavior.

- [ ] **Step 10: Commit**

```bash
git add desktop/tests/StzbDesktop.IntegrationTests packaging/windows/ACCEPTANCE.md README.md
git commit -m "test: verify Windows desktop release" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```
