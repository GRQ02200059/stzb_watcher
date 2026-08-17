package com.local.stzb.feature.battlefield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.local.stzb.core.ui.LoadState
import com.local.stzb.domain.battlefield.BattlefieldEvent
import com.local.stzb.domain.battlefield.BattlefieldMetrics
import com.local.stzb.domain.battlefield.BattlefieldRepository
import com.local.stzb.domain.battlefield.BattlefieldSnapshot
import com.local.stzb.domain.battlefield.CaptureStatus
import com.local.stzb.domain.battlefield.EventCategory
import com.local.stzb.domain.battlefield.EventPriority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BattlefieldViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelStores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        viewModelStores.forEach(ViewModelStore::clear)
        viewModelStores.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun snapshotAndEmptyStateComeFromRepository() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot(paused = true))
        val viewModel = viewModel(repository)
        runCurrent()

        assertTrue(content(viewModel).value.paused)

        repository.snapshot.value = snapshot(captureRunning = false)
        runCurrent()
        assertEquals(
            LoadState.Empty("尚未收到战场动态", "启动抓包"),
            viewModel.state.value.loadState,
        )
        viewModel.onIntent(BattlefieldIntent.SetActive(false))
    }

    @Test
    fun manualRefreshPreservesContentAndReportsProgressUntilSuccess() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeBattlefieldRepository(snapshot(events = listOf(event()))).apply {
            refreshHandler = { gate.await() }
        }
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.onIntent(BattlefieldIntent.Refresh)
        runCurrent()
        assertTrue(content(viewModel).refreshing)
        assertEquals(listOf(event()), content(viewModel).value.events)

        gate.complete(Unit)
        runCurrent()
        assertFalse(content(viewModel).refreshing)
        viewModel.onIntent(BattlefieldIntent.SetActive(false))
    }

    @Test
    fun refreshFailureWithContentPreservesContentAndQueuesOneShotEffect() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot(events = listOf(event()))).apply {
            refreshHandler = { error("网络不可用") }
        }
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.onIntent(BattlefieldIntent.Refresh)
        runCurrent()

        assertFalse(content(viewModel).refreshing)
        viewModel.effects.test {
            assertEquals(BattlefieldEffect.ShowMessage("网络不可用"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun refreshFailureWithoutContentShowsErrorAndSuccessfulRetryRecovers() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot(captureRunning = false)).apply {
            refreshHandler = { error("首次失败") }
        }
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.onIntent(BattlefieldIntent.Refresh)
        runCurrent()
        assertEquals(LoadState.Error("首次失败"), viewModel.state.value.loadState)

        repository.refreshHandler = {}
        viewModel.onIntent(BattlefieldIntent.Refresh)
        runCurrent()
        assertEquals(
            LoadState.Empty("尚未收到战场动态", "启动抓包"),
            viewModel.state.value.loadState,
        )
    }

    @Test
    fun snapshotFlowFailureShowsErrorAndRetryResubscribesAndRecovers() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot()).apply {
            observeFactory = { flow { error("数据流失败") } }
        }
        val viewModel = viewModel(repository)
        runCurrent()
        assertEquals(LoadState.Error("数据流失败"), viewModel.state.value.loadState)

        repository.observeFactory = { repository.snapshot }
        viewModel.onIntent(BattlefieldIntent.Refresh)
        runCurrent()
        assertTrue(viewModel.state.value.loadState is LoadState.Content)
    }

    @Test
    fun effectsSurviveNoCollectorPreserveBurstsAndDoNotReplayAfterConsumption() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(
            snapshot(selectedCategories = setOf(EventCategory.MARCH)),
        )
        val viewModel = viewModel(repository)
        runCurrent()

        repeat(3) {
            viewModel.onIntent(BattlefieldIntent.ToggleCategory(EventCategory.MARCH))
        }

        viewModel.effects.test {
            repeat(3) {
                assertEquals(
                    BattlefieldEffect.ShowMessage("至少保留一种动态类型"),
                    awaitItem(),
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.effects.test { expectNoEvents() }
        assertEquals(setOf(EventCategory.MARCH), repository.snapshot.value.selectedCategories)
    }

    @Test
    fun effectsAreSharedWithActiveCollectorsButNotReplayedAfterConsumption() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(
            snapshot(selectedCategories = setOf(EventCategory.MARCH)),
        )
        val viewModel = viewModel(repository)
        val effects: SharedFlow<BattlefieldEffect> = viewModel.effects
        val first = async { effects.first() }
        val second = async { effects.first() }
        runCurrent()

        viewModel.onIntent(BattlefieldIntent.ToggleCategory(EventCategory.MARCH))

        val expected = BattlefieldEffect.ShowMessage("至少保留一种动态类型")
        assertEquals(expected, first.await())
        assertEquals(expected, second.await())
        effects.test { expectNoEvents() }
    }

    @Test
    fun identicalPeriodicRefreshFailuresAreCoalescedUntilRecovery() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot(events = listOf(event()))).apply {
            refreshHandler = { error("网络不可用") }
        }
        val viewModel = viewModel(repository)

        viewModel.onIntent(BattlefieldIntent.SetActive(true))
        runCurrent()
        repeat(40) {
            advanceTimeBy(2_000)
            runCurrent()
        }

        viewModel.effects.test {
            assertEquals(BattlefieldEffect.ShowMessage("网络不可用"), awaitItem())
            expectNoEvents()
        }

        repository.refreshHandler = {}
        advanceTimeBy(2_000)
        runCurrent()
        repository.refreshHandler = { error("网络不可用") }
        advanceTimeBy(2_000)
        runCurrent()

        viewModel.effects.test {
            assertEquals(BattlefieldEffect.ShowMessage("网络不可用"), awaitItem())
            expectNoEvents()
        }
        viewModel.onIntent(BattlefieldIntent.SetActive(false))
    }

    @Test
    fun backToBackPauseAndCategoryIntentsUseLatestRepositoryOwnedCommands() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot()).apply {
            applyCommandsToSnapshot = false
        }
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.onIntent(BattlefieldIntent.TogglePaused)
        viewModel.onIntent(BattlefieldIntent.TogglePaused)
        assertFalse(repository.snapshot.value.paused)
        assertEquals(listOf(true, false), repository.pauseRequests)

        viewModel.effects.test {
            EventCategory.entries.forEach { category ->
                viewModel.onIntent(BattlefieldIntent.ToggleCategory(category))
            }
            assertEquals(BattlefieldEffect.ShowMessage("至少保留一种动态类型"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(EventCategory.entries.size - 1, repository.filterRequests.size)
        assertTrue(repository.filterRequests.all(Set<EventCategory>::isNotEmpty))
        assertEquals(1, repository.filterRequests.last().size)
    }

    @Test
    fun stalePauseSnapshotDoesNotOverwritePendingCommandAndMatchingSnapshotAcknowledgesIt() =
        runTest(dispatcher) {
            val repository = FakeBattlefieldRepository(snapshot()).apply {
                applyCommandsToSnapshot = false
            }
            val viewModel = viewModel(repository)
            runCurrent()

            viewModel.onIntent(BattlefieldIntent.TogglePaused)
            repository.snapshot.value = snapshot(paused = false, events = listOf(event("stale")))
            runCurrent()
            viewModel.onIntent(BattlefieldIntent.TogglePaused)
            assertEquals(listOf(true, false), repository.pauseRequests)

            repository.snapshot.value = snapshot(paused = false, events = listOf(event("ack")))
            runCurrent()
            repository.snapshot.value = snapshot(paused = true, events = listOf(event("external")))
            runCurrent()
            viewModel.onIntent(BattlefieldIntent.TogglePaused)
            assertEquals(listOf(true, false, false), repository.pauseRequests)
        }

    @Test
    fun staleFilterSnapshotDoesNotOverwritePendingCommandAndMatchingSnapshotAcknowledgesIt() =
        runTest(dispatcher) {
            val repository = FakeBattlefieldRepository(snapshot()).apply {
                applyCommandsToSnapshot = false
            }
            val viewModel = viewModel(repository)
            runCurrent()

            viewModel.onIntent(BattlefieldIntent.ToggleCategory(EventCategory.MARCH))
            repository.snapshot.value = snapshot(events = listOf(event("stale")))
            runCurrent()
            viewModel.onIntent(BattlefieldIntent.ToggleCategory(EventCategory.MARCH))
            assertEquals(
                listOf(EventCategory.entries.toSet() - EventCategory.MARCH, EventCategory.entries.toSet()),
                repository.filterRequests,
            )

            repository.snapshot.value = snapshot(events = listOf(event("ack")))
            runCurrent()
            repository.snapshot.value = snapshot(
                selectedCategories = setOf(EventCategory.MARCH),
                events = listOf(event("external")),
            )
            runCurrent()
            viewModel.onIntent(BattlefieldIntent.ToggleCategory(EventCategory.MARCH))
            assertEquals(2, repository.filterRequests.size)
        }

    @Test
    fun activeAndManualRefreshRequestsAreSingleFlightAndCoalesced() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeBattlefieldRepository(snapshot(events = listOf(event()))).apply {
            refreshHandler = { gate.await() }
        }
        val viewModel = viewModel(repository)

        viewModel.onIntent(BattlefieldIntent.SetActive(true))
        repeat(10) { viewModel.onIntent(BattlefieldIntent.Refresh) }
        viewModel.onIntent(BattlefieldIntent.SetActive(true))
        runCurrent()
        assertEquals(1, repository.refreshCount)
        assertTrue(content(viewModel).refreshing)

        gate.complete(Unit)
        runCurrent()
        assertEquals(1, repository.refreshCount)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2, repository.refreshCount)
        viewModel.onIntent(BattlefieldIntent.SetActive(false))
        runCurrent()
    }

    @Test
    fun inactiveCancelsSuspendedManualRefresh() = runTest(dispatcher) {
        val cancelled = CompletableDeferred<Unit>()
        val repository = FakeBattlefieldRepository(snapshot(events = listOf(event()))).apply {
            refreshHandler = {
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
        val viewModel = viewModel(repository)

        viewModel.onIntent(BattlefieldIntent.Refresh)
        runCurrent()
        viewModel.onIntent(BattlefieldIntent.SetActive(false))
        runCurrent()

        assertTrue(cancelled.isCompleted)
        assertFalse(content(viewModel).refreshing)

        viewModel.onIntent(BattlefieldIntent.Refresh)
        runCurrent()
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun rapidStopStartWaitsForCancellationBeforeStartingNextRefresh() = runTest(dispatcher) {
        val firstCancelled = CompletableDeferred<Unit>()
        val repository = FakeBattlefieldRepository(snapshot(events = listOf(event()))).apply {
            refreshHandler = {
                if (refreshCount == 1) {
                    try {
                        awaitCancellation()
                    } finally {
                        firstCancelled.complete(Unit)
                    }
                }
            }
        }
        val viewModel = viewModel(repository)

        viewModel.onIntent(BattlefieldIntent.SetActive(true))
        runCurrent()
        viewModel.onIntent(BattlefieldIntent.SetActive(false))
        viewModel.onIntent(BattlefieldIntent.SetActive(true))
        runCurrent()

        assertTrue(firstCancelled.isCompleted)
        assertEquals(2, repository.refreshCount)
        viewModel.onIntent(BattlefieldIntent.SetActive(false))
        runCurrent()
    }

    @Test
    fun clearingViewModelCancelsOwnedRefreshAndSnapshotCollection() = runTest(dispatcher) {
        val refreshCancelled = CompletableDeferred<Unit>()
        val snapshotCancelled = CompletableDeferred<Unit>()
        val repository = FakeBattlefieldRepository(snapshot()).apply {
            observeFactory = {
                flow {
                    try {
                        emit(snapshot.value)
                        awaitCancellation()
                    } finally {
                        snapshotCancelled.complete(Unit)
                    }
                }
            }
            refreshHandler = {
                try {
                    awaitCancellation()
                } finally {
                    refreshCancelled.complete(Unit)
                }
            }
        }
        val viewModel = viewModel(repository)
        viewModel.onIntent(BattlefieldIntent.SetActive(true))
        runCurrent()

        viewModelStores.single().clear()
        runCurrent()

        assertTrue(refreshCancelled.isCompleted)
        assertTrue(snapshotCancelled.isCompleted)
    }

    private fun factory(repository: BattlefieldRepository) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BattlefieldViewModel(repository) as T
    }

    private fun viewModel(repository: BattlefieldRepository): BattlefieldViewModel {
        val store = ViewModelStore().also(viewModelStores::add)
        return ViewModelProvider(store, factory(repository))[BattlefieldViewModel::class.java]
    }

    private class FakeBattlefieldRepository(initialSnapshot: BattlefieldSnapshot) : BattlefieldRepository {
        val snapshot = MutableStateFlow(initialSnapshot)
        val pauseRequests = mutableListOf<Boolean>()
        val filterRequests = mutableListOf<Set<EventCategory>>()
        var observeFactory: () -> Flow<BattlefieldSnapshot> = { snapshot }
        var refreshHandler: suspend () -> Unit = {}
        var applyCommandsToSnapshot = true
        var refreshCount = 0

        override fun observeSnapshot(): Flow<BattlefieldSnapshot> = observeFactory()

        override suspend fun refresh() {
            refreshCount += 1
            refreshHandler()
        }

        override fun setPaused(paused: Boolean) {
            pauseRequests += paused
            if (applyCommandsToSnapshot) {
                snapshot.value = snapshot.value.copy(paused = paused)
            }
        }

        override fun setFilter(categories: Set<EventCategory>) {
            require(categories.isNotEmpty())
            filterRequests += categories
            if (applyCommandsToSnapshot) {
                snapshot.value = snapshot.value.copy(selectedCategories = categories)
            }
        }
    }

    private fun content(viewModel: BattlefieldViewModel): LoadState.Content<BattlefieldSnapshot> {
        val loadState = viewModel.state.value.loadState
        assertTrue(loadState is LoadState.Content<BattlefieldSnapshot>)
        return loadState as LoadState.Content<BattlefieldSnapshot>
    }

    private fun event(id: String = "event-1") = BattlefieldEvent(
        id = id,
        occurredAt = 1L,
        category = EventCategory.MARCH,
        priority = EventPriority.NORMAL,
        title = "行军",
        summary = "测试",
    )

    private fun snapshot(
        captureRunning: Boolean = true,
        paused: Boolean = false,
        selectedCategories: Set<EventCategory> = EventCategory.entries.toSet(),
        events: List<BattlefieldEvent> = emptyList(),
    ) = BattlefieldSnapshot(
        capture = CaptureStatus(captureRunning, if (captureRunning) "抓包中" else "未启动", null),
        metrics = BattlefieldMetrics(0, 0, 0, 0),
        events = events,
        selectedCategories = selectedCategories,
        paused = paused,
    )
}
