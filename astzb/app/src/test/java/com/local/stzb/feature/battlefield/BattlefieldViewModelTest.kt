package com.local.stzb.feature.battlefield

import app.cash.turbine.test
import com.local.stzb.core.ui.LoadState
import com.local.stzb.domain.battlefield.BattlefieldMetrics
import com.local.stzb.domain.battlefield.BattlefieldRepository
import com.local.stzb.domain.battlefield.BattlefieldSnapshot
import com.local.stzb.domain.battlefield.CaptureStatus
import com.local.stzb.domain.battlefield.EventCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun snapshotIsExposedAsContentAndRepositoryRemainsStateOwner() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot(paused = true))
        val viewModel = BattlefieldViewModel(repository)

        runCurrent()

        val content = viewModel.state.value.loadState
        assertTrue(content is LoadState.Content<BattlefieldSnapshot>)
        content as LoadState.Content<BattlefieldSnapshot>
        assertTrue(content.value.paused)
    }

    @Test
    fun stoppedCaptureWithoutEventsIsExposedAsEmpty() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot(captureRunning = false))
        val viewModel = BattlefieldViewModel(repository)

        runCurrent()

        assertEquals(
            LoadState.Empty("尚未收到战场动态", "启动抓包"),
            viewModel.state.value.loadState,
        )
    }

    @Test
    fun pauseAndConsumeIntentsDelegateToRepositoryOwnedState() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot())
        val viewModel = BattlefieldViewModel(repository)
        runCurrent()

        viewModel.onIntent(BattlefieldIntent.TogglePaused)
        assertTrue(repository.snapshot.value.paused)

        viewModel.onIntent(BattlefieldIntent.ConsumeBufferedEvents)
        assertFalse(repository.snapshot.value.paused)
        assertEquals(listOf(true, false), repository.pauseRequests)
    }

    @Test
    fun backToBackPauseIntentsUseTheLatestRepositoryOwnedValue() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot())
        val viewModel = BattlefieldViewModel(repository)
        runCurrent()

        viewModel.onIntent(BattlefieldIntent.TogglePaused)
        viewModel.onIntent(BattlefieldIntent.TogglePaused)

        assertFalse(repository.snapshot.value.paused)
        assertEquals(listOf(true, false), repository.pauseRequests)
    }

    @Test
    fun categoryIntentDelegatesEveryNonEmptySelection() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot())
        val viewModel = BattlefieldViewModel(repository)
        runCurrent()

        viewModel.onIntent(BattlefieldIntent.ToggleCategory(EventCategory.SYSTEM))

        assertEquals(
            EventCategory.entries.toSet() - EventCategory.SYSTEM,
            repository.snapshot.value.selectedCategories,
        )
    }

    @Test
    fun categoryIntentNeverAllowsAnEmptySelection() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(
            snapshot(selectedCategories = setOf(EventCategory.MARCH)),
        )
        val viewModel = BattlefieldViewModel(repository)
        runCurrent()

        viewModel.effects.test {
            viewModel.onIntent(BattlefieldIntent.ToggleCategory(EventCategory.MARCH))
            runCurrent()

            assertEquals(
                BattlefieldEffect.ShowMessage("至少保留一种动态类型"),
                awaitItem(),
            )
            assertEquals(setOf(EventCategory.MARCH), repository.snapshot.value.selectedCategories)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun backToBackCategoryIntentsStillRejectRemovingTheLastCategory() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot())
        val viewModel = BattlefieldViewModel(repository)
        runCurrent()

        viewModel.effects.test {
            EventCategory.entries.forEach { category ->
                viewModel.onIntent(BattlefieldIntent.ToggleCategory(category))
            }
            runCurrent()

            assertEquals(
                BattlefieldEffect.ShowMessage("至少保留一种动态类型"),
                awaitItem(),
            )
            assertEquals(1, repository.snapshot.value.selectedCategories.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun activeLifecycleOwnsOneImmediateTwoSecondRefreshLoopAndCancelsIt() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot())
        val viewModel = BattlefieldViewModel(repository)

        viewModel.onIntent(BattlefieldIntent.SetActive(true))
        runCurrent()
        assertEquals(1, repository.refreshCount)

        viewModel.onIntent(BattlefieldIntent.SetActive(true))
        runCurrent()
        assertEquals(1, repository.refreshCount)

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(1, repository.refreshCount)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.refreshCount)

        viewModel.onIntent(BattlefieldIntent.SetActive(false))
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(2, repository.refreshCount)

        viewModel.onIntent(BattlefieldIntent.SetActive(true))
        runCurrent()
        assertEquals(3, repository.refreshCount)
        viewModel.onIntent(BattlefieldIntent.SetActive(false))
    }

    @Test
    fun refreshFailureIsAOneShotEffectAndDoesNotReplay() = runTest(dispatcher) {
        val repository = FakeBattlefieldRepository(snapshot()).apply {
            refreshFailure = IllegalStateException("网络不可用")
        }
        val viewModel = BattlefieldViewModel(repository)

        viewModel.effects.test {
            viewModel.onIntent(BattlefieldIntent.Refresh)
            runCurrent()

            assertEquals(BattlefieldEffect.ShowMessage("网络不可用"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.effects.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeBattlefieldRepository(
        initialSnapshot: BattlefieldSnapshot,
    ) : BattlefieldRepository {
        val snapshot = MutableStateFlow(initialSnapshot)
        val pauseRequests = mutableListOf<Boolean>()
        var refreshCount = 0
        var refreshFailure: Throwable? = null

        override fun observeSnapshot(): Flow<BattlefieldSnapshot> = snapshot

        override suspend fun refresh() {
            refreshCount += 1
            refreshFailure?.let { throw it }
        }

        override fun setPaused(paused: Boolean) {
            pauseRequests += paused
            snapshot.value = snapshot.value.copy(paused = paused)
        }

        override fun setFilter(categories: Set<EventCategory>) {
            require(categories.isNotEmpty())
            snapshot.value = snapshot.value.copy(selectedCategories = categories)
        }
    }

    private fun snapshot(
        captureRunning: Boolean = true,
        paused: Boolean = false,
        selectedCategories: Set<EventCategory> = EventCategory.entries.toSet(),
    ) = BattlefieldSnapshot(
        capture = CaptureStatus(
            running = captureRunning,
            label = if (captureRunning) "抓包中" else "未启动",
            lastEventAt = null,
        ),
        metrics = BattlefieldMetrics(
            activeMarches = 0,
            arrivingSoon = 0,
            todayBattles = 0,
            siegeEvents = 0,
        ),
        events = emptyList(),
        selectedCategories = selectedCategories,
        paused = paused,
    )
}
