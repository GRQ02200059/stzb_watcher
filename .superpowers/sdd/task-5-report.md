# Task 5 Report: Implement Battlefield ViewModel State Machine

Status: DONE

## Implemented

- Added the required `BattlefieldUiState`, `BattlefieldIntent`, and `BattlefieldEffect` public contract.
- Added `BattlefieldViewModel` with eagerly collected repository snapshots and `Loading`, `Empty`, and `Content` UI states.
- Kept pause and filter persistence repository-owned; the ViewModel only tracks the latest acknowledged/issued command values so multiple intents in one UI frame cannot be derived from stale `StateFlow` delivery.
- Added an idempotent lifecycle refresh state machine: first activation refreshes immediately, then every two seconds; repeated activation does not duplicate or restart the job; deactivation cancels it; later activation starts a new job.
- Preserved coroutine cancellation instead of translating it into an error message.
- Added non-replaying, one-shot `SharedFlow` message effects for refresh failures and rejecting an empty category selection.
- Added Turbine 1.2.0 to the version catalog and app unit-test dependencies.

## RED evidence

Initial command:

`./gradlew :app:testDebugUnitTest --tests '*BattlefieldViewModelTest'`

Result: failed during `compileDebugUnitTestKotlin` with missing `BattlefieldViewModel`, `BattlefieldIntent`, and `BattlefieldEffect` symbols. A test-only JUnit 4 assertion mismatch was corrected and RED was rerun, still failing on the required missing production symbols.

Self-review regression RED commands:

`./gradlew :app:testDebugUnitTest --tests '*BattlefieldViewModelTest.backToBackCategoryIntentsStillRejectRemovingTheLastCategory'`

Result: failed with a Turbine timeout because back-to-back category commands were derived from a stale `stateIn` snapshot and emitted no final-removal effect.

`./gradlew :app:testDebugUnitTest --tests '*BattlefieldViewModelTest.backToBackPauseIntentsUseTheLatestRepositoryOwnedValue'`

Result: failed its state assertion because two back-to-back pause toggles both requested the same stale-derived value.

## GREEN evidence

Focused fresh command:

`./gradlew :app:testDebugUnitTest --tests '*BattlefieldViewModelTest' --rerun-tasks`

Result: `BUILD SUCCESSFUL in 20s`; 24 actionable tasks executed. The focused suite has 9 tests with 0 failures/errors/skips.

Full fresh command:

`./gradlew :app:testDebugUnitTest --rerun-tasks`

Result: `BUILD SUCCESSFUL in 19s`; 24 actionable tasks executed. JUnit XML totals: 31 tests, 0 failures, 0 errors, 0 skipped.

## Self-review

- Lifecycle: the active refresh job is unique, immediate, periodic, cancellable, and restartable; its deterministic test always deactivates it before scheduler completion.
- Cancellation: `CancellationException` is rethrown, so `SetActive(false)` cannot produce a spurious failure effect.
- State ownership: all user mutations call `BattlefieldRepository.setPaused` or `setFilter`; UI state is mapped only from `observeSnapshot()`.
- Command ordering: ViewModel command mirrors are synchronized from repository emissions and updated immediately before mutations, preventing multiple same-frame intents from using stale asynchronous UI state.
- Filters: the repository never receives an empty selection; both single-category and rapid all-category paths are tested.
- Effects: the shared flow has no replay and the test proves a consumed failure message is not delivered to a new collector.
- Scope: only the two contract/ViewModel production files, one test file, exact Turbine catalog/module changes, and this report are intended for commit. The pre-existing untracked `astzb/third_party` symlink is untouched and excluded.

## Reconciliation and concerns

- The brief used `SharingStarted.WhileSubscribed(5_000)`. That can leave `state.value` at `Loading` when Task 6 sends an intent before collecting state. Eager collection makes intent handling safe without changing the required public interface or owning repository data in the ViewModel.
- The brief cancelled and recreated the refresh job for every `SetActive(true)`. Repeated lifecycle callbacks could therefore restart polling and cause duplicate immediate refreshes. The implementation treats repeated active/inactive values idempotently.
- The brief's `runCatching` pattern catches `CancellationException`; explicit `try/catch` preserves structured cancellation.
- `MutableSharedFlow(extraBufferCapacity = 1)` remains non-replaying. With no active collector, transient UI messages are intentionally not queued for a future screen instance, preventing repeated navigation/event behavior.

## Production review fix

Status: DONE

Resolved the review findings with one ViewModel-owned refresh coordinator and explicit state transitions:

- manual, lifecycle, and polling refresh requests now coalesce into one in-flight repository call;
- deactivation cancels both polling and a suspended manual refresh, while rapid stop/start waits for cancellation before starting the replacement call;
- `Content.refreshing` preserves the current snapshot during work, content failures preserve content and enqueue a message, empty failures become `Error`, and retry returns to repository-derived content/empty state;
- snapshot-flow failures become retryable `Error` state, and a successful refresh resubscribes to a newly created repository flow;
- effects use an unlimited single-consumer channel, so messages survive collector gaps and bursts, preserve order, and are delivered only once;
- pause/filter command mirrors remain correct even when repository snapshot acknowledgement is delayed;
- the cleanup regression obtains the ViewModel through a real `ViewModelStore` and calls `store.clear()`, proving both refresh and snapshot collection are cancelled.

Review RED evidence:

```text
./gradlew testDebugUnitTest --tests com.local.stzb.feature.battlefield.BattlefieldViewModelTest --stacktrace
11 tests completed, 7 failed
BUILD FAILED in 10s
```

The failures covered refresh progress, content/empty failure handling, snapshot retry, queued effects, inactive cancellation, and single-flight coalescing.

Review GREEN evidence:

```text
./gradlew testDebugUnitTest --tests com.local.stzb.feature.battlefield.BattlefieldViewModelTest
11 tests, 0 failures/errors/skips
BUILD SUCCESSFUL in 22s; 24 actionable tasks executed

./gradlew testDebugUnitTest --rerun-tasks
33 tests, 0 failures/errors/skips
BUILD SUCCESSFUL in 19s; 24 actionable tasks executed
```

Concern: the effect stream intentionally has single-consumer semantics. Multiple simultaneous UI collectors would divide events rather than duplicate them; this matches the requirement that each queued effect be consumed once.

## Final review fix

Status: DONE

- Replaced the unbounded, single-consumer public effect flow with a capacity-64 channel feeding a public `SharedFlow<BattlefieldEffect>` through `receiveAsFlow().shareIn(viewModelScope, SharingStarted.WhileSubscribed(0), replay = 0)`. Effects queued before the first collector are retained, active collectors receive the same broadcast, and consumed effects are not replayed.
- Handled channel overflow explicitly: validation messages remain distinct while capacity is available, and an overflow message is coalesced and retried as the queue drains.
- Coalesced identical refresh-failure messages until either a successful refresh or snapshot recovery. Persistent periodic failures therefore retain one message instead of growing the queue or flooding active collectors.
- Changed pause/filter command mirrors into pending acknowledgements. Mismatching stale snapshots no longer overwrite an issued command; only a matching snapshot clears it, and a second rapid intent derives from the pending value.
- Added delayed stale-emission/acknowledgement regressions for both pause and filter, plus SharedFlow broadcast, pre-collector queue, no-replay, and periodic-error coalescing coverage.
- Routed every test through a `ViewModelStore`/`ViewModelProvider` helper and clear every created store in `@After`, preventing ViewModel-owned collectors from leaking between tests.

Final review RED evidence:

```text
./gradlew :app:testDebugUnitTest --tests 'com.local.stzb.feature.battlefield.BattlefieldViewModelTest'
compileDebugUnitTestKotlin failed: expected SharedFlow<BattlefieldEffect>, actual Flow<BattlefieldEffect>
BUILD FAILED in 3s
```

Final review GREEN evidence:

```text
./gradlew :app:testDebugUnitTest --tests 'com.local.stzb.feature.battlefield.BattlefieldViewModelTest' --rerun-tasks
15 tests, 0 failures/errors/skips
BUILD SUCCESSFUL in 17s; 24 actionable tasks executed

./gradlew :app:testDebugUnitTest --rerun-tasks
37 tests, 0 failures/errors/skips
BUILD SUCCESSFUL in 25s; 24 actionable tasks executed
```

Concern: bounded overflow necessarily coalesces validation messages after the capacity-64 queue is full; messages already accepted into the queue remain ordered and distinct.
