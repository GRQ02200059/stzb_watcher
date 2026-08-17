# Android Startup Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Android 10-day local trial gate with the shared startup-only account registration, login, session verification, and logout contract.

**Architecture:** Keep `StzbAppActivity` as the launcher and render a Compose authentication gate before the existing `StzbApp`. A focused auth package owns HTTP, encrypted session storage, startup state, and process access; legacy activities and background services consult the same in-memory guard so no internal intent can bypass startup authentication.

**Tech Stack:** Kotlin 2.0.21, Android 13+ (minSdk 33), Jetpack Compose Material 3, coroutines/StateFlow, OkHttp 4.12, MockWebServer, Android Keystore AES/GCM, JUnit 4, Compose UI tests.

## Global Constraints

- Auth base URL is exactly `http://152.136.236.184:9080`.
- Client version is `BuildConfig.VERSION_NAME`; application version remains `1.0.0`.
- Authentication runs once per cold process start; no runtime heartbeat or periodic verification.
- Passwords never persist; session tokens use Android Keystore-backed AES-256-GCM storage.
- `SESSION_INVALID` and `ACCOUNT_DISABLED` delete the token; `SERVICE_DISABLED`, timeout, invalid response, and transport failure retain it.
- Authentication fails closed: no offline bypass.
- Login UI must display `本软件完全免费，禁止任何形式的倒卖、付费代装或捆绑销售。`
- Login UI must display `密码无法找回，请自行妥善保存。`
- Local packets, SQLite data, battle reports, and game data never leave the device through the auth client.
- Existing Compose business screens and navigation remain visually unchanged except for adding logout under “更多”.
- Every commit message ends with exactly one `Co-authored-by: TRAE CLI <noreply@bytedance.com>` trailer.

---

### Task 1: Android Auth HTTP Contract

**Files:**
- Modify: `astzb/gradle/libs.versions.toml`
- Modify: `astzb/app/build.gradle.kts`
- Create: `astzb/app/src/main/java/com/local/stzb/auth/AuthModels.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/auth/AuthRepository.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/auth/AuthRepositoryTest.kt`

**Interfaces:**
- Produces: suspend functions `AuthRepository.register(username, password, clientVersion)`, `login(...)`, `verify(token, clientVersion)`, and `logout(token)`.
- Produces: `AuthResult`, `AuthErrorCode`, and `AuthTransport` for the startup state machine.

- [ ] **Step 1: Add failing request and response tests**

Create tests using `MockWebServer` that assert:

```kotlin
@Test
fun `login posts exact contract and parses success`() = runTest {
    server.enqueue(
        MockResponse()
            .setResponseCode(200)
            .addHeader("Cache-Control", "no-store")
            .setBody("""{"ok":true,"user":{"username":"player"},"sessionToken":"token","service":{"enabled":true,"announcement":""},"client":{"minimumVersion":"1.0.0"},"requestId":"r1"}""")
    )

    val result = repository.login("player", "secret-value", "1.0.0")

    val request = server.takeRequest()
    assertEquals("/v1/login", request.path)
    assertEquals("POST", request.method)
    assertEquals(
        JSONObject("""{"username":"player","password":"secret-value","clientVersion":"1.0.0"}""").toString(),
        JSONObject(request.body.readUtf8()).toString(),
    )
    assertTrue(result.isSuccess)
    assertEquals("token", result.sessionToken)
}
```

Also test register, verify, logout, missing `no-store`, mismatched HTTP/`ok`, malformed JSON, stable error mapping, and a short injected timeout without printing body data.

- [ ] **Step 2: Run tests RED**

Run:

```bash
./astzb/gradlew -p astzb :app:testDebugUnitTest --tests 'com.local.stzb.auth.AuthRepositoryTest'
```

Expected: compilation fails because `AuthRepository` and auth models do not exist.

- [ ] **Step 3: Add OkHttp dependencies**

Add:

```toml
okhttpVersion = "4.12.0"
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttpVersion" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttpVersion" }
```

Add `implementation(libs.okhttp)` and `testImplementation(libs.mockwebserver)`.

- [ ] **Step 4: Implement strict models and repository**

Define:

```kotlin
enum class AuthErrorCode {
    NONE, INVALID_INPUT, USERNAME_TAKEN, INVALID_CREDENTIALS,
    ACCOUNT_DISABLED, SERVICE_DISABLED, SESSION_INVALID,
    CLIENT_UNSUPPORTED, RATE_LIMITED, INTERNAL_ERROR,
    INVALID_RESPONSE, TRANSPORT_UNAVAILABLE, UNKNOWN,
}

data class AuthResult(
    val isSuccess: Boolean,
    val errorCode: AuthErrorCode = AuthErrorCode.NONE,
    val message: String? = null,
    val username: String? = null,
    val sessionToken: String? = null,
    val announcement: String? = null,
    val minimumVersion: String? = null,
    val requestId: String? = null,
)

interface AuthTransport {
    suspend fun register(username: String, password: String, clientVersion: String): AuthResult
    suspend fun login(username: String, password: String, clientVersion: String): AuthResult
    suspend fun verify(token: String, clientVersion: String): AuthResult
    suspend fun logout(token: String): AuthResult
}
```

`AuthRepository` implements `AuthTransport`. Use `JSONObject`, `RequestBody.create`, one `OkHttpClient` with 10-second call/connect/read/write timeouts, `Cache-Control: no-store` validation, and no logging interceptor. Never include exception text or response bodies in `AuthResult`.

- [ ] **Step 5: Run Task 1 tests GREEN**

Run the focused test command. Expected: all `AuthRepositoryTest` cases pass.

- [ ] **Step 6: Commit**

```bash
git add astzb/gradle/libs.versions.toml astzb/app/build.gradle.kts astzb/app/src/main/java/com/local/stzb/auth astzb/app/src/test/java/com/local/stzb/auth/AuthRepositoryTest.kt
git commit -m "feat: add Android authentication client" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 2: Keystore-Backed Session Storage

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/auth/AuthSessionStore.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/auth/AndroidAuthSessionStore.kt`
- Modify: `astzb/app/src/main/res/xml/backup_rules.xml`
- Modify: `astzb/app/src/main/res/xml/data_extraction_rules.xml`
- Test: `astzb/app/src/test/java/com/local/stzb/auth/AuthSessionStoreTest.kt`
- Android Test: `astzb/app/src/androidTest/java/com/local/stzb/auth/AndroidAuthSessionStoreTest.kt`

**Interfaces:**
- Produces: `AuthSessionStore.readToken()`, `saveToken(token)`, `deleteToken()`, `readUsername()`, and `saveUsername(username)`.
- Produces: `SessionCipher.encrypt`/`decrypt`, allowing JVM tests without Android Keystore.

- [ ] **Step 1: Write failing JVM storage tests**

Test round-trip, delete, username preference, corrupted Base64, unsupported format version, decrypt failure, and empty token rejection:

```kotlin
@Test
fun `corrupt encrypted token is deleted and returned as absent`() {
    preferences.putString("auth_session", "not-base64")

    assertNull(store.readToken())
    assertNull(preferences.getString("auth_session"))
}
```

- [ ] **Step 2: Run tests RED**

Expected: missing `AuthSessionStore`.

- [ ] **Step 3: Implement storage abstraction**

Use one serialized value:

```text
v1:<base64 iv>:<base64 ciphertext-and-tag>
```

`AuthSessionStore` must delete malformed values before returning null. Password fields are not part of this interface.

- [ ] **Step 4: Implement Android Keystore cipher**

Use alias `STZBWatcher.AuthSession`, `KeyProperties.KEY_ALGORITHM_AES`, 256-bit key size, GCM mode, no padding, randomized encryption, and a new 12-byte IV for each save.

- [ ] **Step 5: Exclude auth preferences from backup**

Add:

```xml
<exclude domain="sharedpref" path="stzb_auth_session.xml"/>
```

to cloud backup and device transfer rules.

- [ ] **Step 6: Add Android round-trip test**

The instrumentation test creates `AndroidAuthSessionStore`, saves a canary token, reads it, deletes it, and asserts the preference XML does not contain the plaintext canary.

- [ ] **Step 7: Run Task 2 tests GREEN**

Run JVM focused tests and:

```bash
./astzb/gradlew -p astzb :app:assembleDebug :app:assembleAndroidTest
```

- [ ] **Step 8: Commit**

```bash
git add astzb/app/src/main/java/com/local/stzb/auth astzb/app/src/test/java/com/local/stzb/auth astzb/app/src/androidTest/java/com/local/stzb/auth astzb/app/src/main/res/xml
git commit -m "feat: secure Android auth sessions" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 3: Startup State Machine and Process Guard

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/auth/AuthAccessGuard.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/auth/AuthStartupCoordinator.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/auth/AuthViewModel.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/auth/AuthStartupCoordinatorTest.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/auth/AuthViewModelTest.kt`

**Interfaces:**
- Produces: `AuthGateState` sealed interface.
- Produces: `AuthGateUiState(state, username, password, registrationMode)` consumed by Compose.
- Produces: `AuthAccessGuard.grant()`, `revoke()`, and `isGranted`.
- Consumes: `AuthTransport`, `AuthSessionStore`, and `BuildConfig.VERSION_NAME`.

- [ ] **Step 1: Write failing coordinator tests**

Cover no token, valid token, invalid session, disabled account, disabled service, invalid response, transport failure, and process guard:

```kotlin
@Test
fun `service disabled keeps token but blocks startup`() = runTest {
    store.token = "saved"
    repository.verifyResult = AuthResult(false, AuthErrorCode.SERVICE_DISABLED)

    val state = coordinator.checkStartup()

    assertIs<AuthGateState.Blocked>(state)
    assertEquals("saved", store.token)
    assertFalse(guard.isGranted)
}
```

- [ ] **Step 2: Run coordinator tests RED**

Expected: missing coordinator and state classes.

- [ ] **Step 3: Implement coordinator and guard**

Define states exactly:

```kotlin
sealed interface AuthGateState {
    data object CheckingSession : AuthGateState
    data class LoginRequired(val message: String? = null) : AuthGateState
    data object SubmittingLogin : AuthGateState
    data object SubmittingRegistration : AuthGateState
    data class Blocked(val message: String) : AuthGateState
    data class Unavailable(val message: String) : AuthGateState
    data class Ready(val username: String?, val announcement: String?) : AuthGateState
}

data class AuthGateUiState(
    val state: AuthGateState = AuthGateState.CheckingSession,
    val username: String = "",
    val password: String = "",
    val registrationMode: Boolean = false,
)
```

The coordinator is the only class allowed to grant the process guard.

- [ ] **Step 4: Write failing ViewModel tests**

Assert trimmed username, password cleared after every request, successful login/register save token and grant access without verify, stable error messages, retry, and logout always revoking local access.

- [ ] **Step 5: Implement ViewModel**

Use `StateFlow<AuthGateUiState>` and `viewModelScope`; never expose the password as persistent state after an operation completes.

- [ ] **Step 6: Run Task 3 tests GREEN**

Run focused coordinator and ViewModel tests. Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add astzb/app/src/main/java/com/local/stzb/auth astzb/app/src/test/java/com/local/stzb/auth
git commit -m "feat: gate Android startup authentication" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 4: Compose Authentication Gate

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/feature/auth/AuthGateScreen.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/StzbAppActivity.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/StzbApplication.kt`
- Android Test: `astzb/app/src/androidTest/java/com/local/stzb/feature/auth/AuthGateScreenTest.kt`

**Interfaces:**
- Consumes: `AuthViewModel` and `AuthGateState`.
- Produces: `AuthGateScreen(state, username, password, registrationMode, on...)`.

- [ ] **Step 1: Write failing Compose UI tests**

Assert login copy, registration mode, retry state, disabled duplicate submits, password masking, and that business navigation is absent before `Ready`.

- [ ] **Step 2: Run Android test compilation RED**

Expected: missing `AuthGateScreen`.

- [ ] **Step 3: Implement Material 3 screen**

Use `AstzbTheme`, `OutlinedTextField`, `PasswordVisualTransformation`, one login/register mode switch, and exact required notices. Add a plain HTTP risk note without claiming encryption.

- [ ] **Step 4: Wire launcher Activity**

`StzbApplication` creates shared auth dependencies. `StzbAppActivity` renders:

```kotlin
when (val state = authState) {
    is AuthGateState.Ready -> StzbApp(...)
    else -> AuthGateScreen(...)
}
```

Do not instantiate business repositories in the composable until state is `Ready`.

- [ ] **Step 5: Run Task 4 tests GREEN**

Run unit tests, `assembleDebug`, and `assembleAndroidTest`.

- [ ] **Step 6: Commit**

```bash
git add astzb/app/src/main/java/com/local/stzb astzb/app/src/androidTest/java/com/local/stzb/feature/auth
git commit -m "feat: add Android login experience" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 5: Prevent Activity and Service Bypass

**Files:**
- Create: `astzb/app/src/main/java/com/local/stzb/auth/AuthEntryPolicy.kt`
- Create: `astzb/app/src/main/java/com/local/stzb/auth/AuthEntryGuard.kt`
- Modify: `astzb/app/src/main/java/com/example/myapplication/MainActivity.kt`
- Modify: `astzb/app/src/main/java/com/example/myapplication/DashboardActivity.kt`
- Modify: `astzb/app/src/main/java/com/example/myapplication/BattleDetailActivity.kt`
- Modify: `astzb/app/src/main/java/com/example/myapplication/CaptureVpnService.kt`
- Modify: `astzb/app/src/main/java/hev/sockstun/TProxyService.java`
- Modify: `astzb/app/src/main/java/com/local/stzb/feature/overlay/BattlefieldOverlayService.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/auth/AuthEntryGuardTest.kt`
- Android Test: `astzb/app/src/androidTest/java/com/local/stzb/AuthBypassTest.kt`

**Interfaces:**
- Produces: pure Kotlin `AuthEntryPolicy.canEnter(isGranted: Boolean): Boolean`.
- Produces: `AuthEntryGuard.redirectActivityIfDenied(activity): Boolean`.
- Produces: `AuthEntryGuard.stopServiceIfDenied(service): Boolean`.

- [ ] **Step 1: Write failing policy and Android guard tests**

In `AuthEntryGuardTest`, test only the pure Kotlin policy so the local JVM suite does not instantiate Android `Activity` or `Service` classes:

```kotlin
@Test
fun `entry is denied until process access has been granted`() {
    assertFalse(AuthEntryPolicy.canEnter(isGranted = false))
    assertTrue(AuthEntryPolicy.canEnter(isGranted = true))
}
```

In `AuthBypassTest`, use Android instrumentation to assert unauthenticated activities redirect to `StzbAppActivity` with `NEW_TASK | CLEAR_TASK`, authenticated entries continue, and denied services stop before starting VPN/overlay work.

- [ ] **Step 2: Run tests RED**

Expected: missing `AuthEntryPolicy` and `AuthEntryGuard`.

- [ ] **Step 3: Implement policy, Android guard, and apply at every entry**

`AuthEntryPolicy` contains no Android imports and returns the decision from the current `AuthAccessGuard.isGranted` value supplied by `AuthEntryGuard`. `AuthEntryGuard` owns redirects and service shutdown. Place activity checks immediately after `super.onCreate`. Place service checks before creating VPN interfaces, foreground overlays, writer threads, or SOCKS tunnels. Change the SOCKS notification PendingIntent target from `MainActivity` to `StzbAppActivity`.

- [ ] **Step 4: Run Task 5 tests GREEN**

Run:

```bash
./astzb/gradlew -p astzb :app:testDebugUnitTest --tests 'com.local.stzb.auth.AuthEntryGuardTest'
./astzb/gradlew -p astzb :app:assembleAndroidTest
```

Expected: the pure policy JVM test passes and Android bypass tests compile.

- [ ] **Step 5: Commit**

```bash
git add astzb/app/src/main/java astzb/app/src/test/java/com/local/stzb/auth astzb/app/src/androidTest/java/com/local/stzb/AuthBypassTest.kt
git commit -m "fix: prevent Android auth bypass" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 6: Logout and Background Shutdown

**Files:**
- Modify: `astzb/app/src/main/java/com/local/stzb/core/navigation/StzbApp.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/feature/tools/LegacyToolsScreen.kt`
- Modify: `astzb/app/src/main/java/com/local/stzb/auth/AuthViewModel.kt`
- Test: `astzb/app/src/test/java/com/local/stzb/auth/AuthLogoutTest.kt`
- Android Test: `astzb/app/src/androidTest/java/com/local/stzb/feature/auth/AuthLogoutScreenTest.kt`

**Interfaces:**
- `StzbApp` gains `onLogout: () -> Unit`.
- `LegacyToolsScreen` gains `onLogout: () -> Unit`.
- `AuthViewModel.logout()` always clears token and guard after best-effort server logout.

- [ ] **Step 1: Write failing logout tests**

Assert server success and server failure both clear token, revoke guard, stop `CaptureVpnService`, `TProxyService`, and `BattlefieldOverlayService`, and return to the auth gate.

- [ ] **Step 2: Run tests RED**

Expected: missing logout callback and lifecycle behavior.

- [ ] **Step 3: Implement logout**

Add an outlined “退出登录” button under “更多”. Stop the three services before resetting UI state to `LoginRequired`. Do not retain a session if `/v1/logout` fails.

- [ ] **Step 4: Run Task 6 tests GREEN**

Run focused JVM and Android test compilation.

- [ ] **Step 5: Commit**

```bash
git add astzb/app/src/main/java/com/local/stzb astzb/app/src/test astzb/app/src/androidTest
git commit -m "feat: add Android logout lifecycle" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 7: Remove the Ten-Day Trial

**Files:**
- Delete: `astzb/app/src/main/java/com/example/myapplication/LocalTrialManager.kt`
- Delete: `astzb/app/src/main/java/com/example/myapplication/ExpiredActivity.kt`
- Delete: `astzb/app/src/main/res/layout/activity_expired.xml`
- Delete: `astzb/app/src/test/java/com/example/myapplication/TrialPolicyTest.kt`
- Modify: `astzb/app/src/main/AndroidManifest.xml`
- Test: `astzb/app/src/test/java/com/local/stzb/auth/TrialRemovalTest.kt`

**Interfaces:**
- Removes all `LocalTrialManager`, `TrialPolicy`, `ExpiredActivity`, and `ensureAccessOrRedirect` references.

- [ ] **Step 1: Write failing source-contract test**

The test scans `src/main` and asserts the removed symbols and `activity_expired` are absent while auth gate symbols are present.

- [ ] **Step 2: Run test RED**

Expected: failures listing current trial files and references.

- [ ] **Step 3: Delete trial implementation and manifest entry**

Remove all old trial checks; do not read or migrate `local_trial_guard`.

- [ ] **Step 4: Run Task 7 tests GREEN**

Run focused test and:

```bash
rg -n "LocalTrialManager|TrialPolicy|ExpiredActivity|activity_expired" astzb/app/src
```

Expected: no matches.

- [ ] **Step 5: Commit**

```bash
git add -A astzb/app/src
git commit -m "refactor: replace Android trial gate" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```

---

### Task 8: Full Regression and Pixel_6 Acceptance

**Files:**
- Create: `docs/testing/android-auth-acceptance.md`
- Modify: `README.md`

**Interfaces:**
- Produces recorded JVM, build, instrumentation, emulator, and live-control evidence.

- [ ] **Step 1: Run the full Android unit suite**

```bash
./astzb/gradlew -p astzb :app:testDebugUnitTest
```

Expected: zero failed tests.

- [ ] **Step 2: Build debug and release variants**

```bash
./astzb/gradlew -p astzb :app:assembleDebug :app:assembleRelease :app:assembleAndroidTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Start and verify Pixel_6**

```bash
$HOME/Library/Android/sdk/emulator/emulator -avd Pixel_6 -no-snapshot-load
$HOME/Library/Android/sdk/platform-tools/adb wait-for-device
$HOME/Library/Android/sdk/platform-tools/adb shell getprop sys.boot_completed
```

Expected: `1`.

- [ ] **Step 4: Install and run instrumentation**

```bash
$HOME/Library/Android/sdk/platform-tools/adb install -r astzb/app/build/outputs/apk/debug/app-debug.apk
./astzb/gradlew -p astzb :app:connectedDebugAndroidTest
```

Expected: all tests pass.

- [ ] **Step 5: Perform manual cold-start acceptance**

Record: clean-data registration, login, force-stop/restart verify, offline fail-closed, retry, legacy intent denial, notification denial, logout, service shutdown, and absence of the trial expiry page. Use a disposable test account; do not record its password or token.

- [ ] **Step 6: Document live control as pending unless explicitly authorized**

Account disable and global service disable modify the public auth service. Record them as pending unless the user explicitly authorizes the live test; never silently change service state.

- [ ] **Step 7: Update README**

Document shared Windows/Android accounts, free/no-resale notice, no password recovery, startup-only verification, local-data privacy, logout, and HTTP risk.

- [ ] **Step 8: Commit**

```bash
git add docs/testing/android-auth-acceptance.md README.md
git commit -m "test: verify Android startup authentication" -m "Co-authored-by: TRAE CLI <noreply@bytedance.com>"
```
