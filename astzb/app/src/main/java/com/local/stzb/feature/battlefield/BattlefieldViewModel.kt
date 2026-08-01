package com.local.stzb.feature.battlefield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.stzb.core.ui.LoadState
import com.local.stzb.domain.battlefield.BattlefieldRepository
import com.local.stzb.domain.battlefield.BattlefieldSnapshot
import com.local.stzb.domain.battlefield.EventCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BattlefieldViewModel(
    private val repository: BattlefieldRepository,
) : ViewModel() {
    private var pageActive = true
    private var pollingJob: Job? = null
    private var refreshJob: Job? = null
    private var snapshotJob: Job? = null
    private var latestSnapshot: BattlefieldSnapshot? = null
    private var commandCategories: Set<EventCategory>? = null
    private var commandPaused: Boolean? = null
    private var refreshing = false

    private val _state = MutableStateFlow(BattlefieldUiState())
    val state: StateFlow<BattlefieldUiState> = _state.asStateFlow()

    private val effectQueue = Channel<BattlefieldEffect>(Channel.UNLIMITED)
    val effects: Flow<BattlefieldEffect> = effectQueue.receiveAsFlow()

    init {
        startSnapshotCollection()
    }

    fun onIntent(intent: BattlefieldIntent) {
        when (intent) {
            is BattlefieldIntent.SetActive -> setActive(intent.active)
            BattlefieldIntent.Refresh -> ensureRefresh()
            BattlefieldIntent.TogglePaused -> togglePaused()
            is BattlefieldIntent.ToggleCategory -> toggleCategory(intent.category)
            BattlefieldIntent.ConsumeBufferedEvents -> setPaused(false)
        }
    }

    private fun setActive(active: Boolean) {
        pageActive = active
        if (!active) {
            pollingJob?.cancel()
            pollingJob = null
            refreshJob?.cancel()
            return
        }
        if (pollingJob?.isActive == true) return

        val refreshBeingCancelled = refreshJob
        pollingJob = viewModelScope.launch {
            refreshBeingCancelled?.cancelAndJoin()
            while (isActive && pageActive) {
                ensureRefresh()?.join()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun ensureRefresh(): Job? {
        if (!pageActive) return null
        refreshJob?.takeIf { it.isActive }?.let { return it }

        setRefreshing(true)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                repository.refresh()
                if (snapshotJob?.isActive != true) {
                    startSnapshotCollection()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                setRefreshing(false)
                handleRefreshFailure(failure)
            } finally {
                if (refreshJob === coroutineContext[Job]) {
                    refreshJob = null
                    if (refreshing) setRefreshing(false)
                }
            }
        }
        refreshJob = job
        job.start()
        return job
    }

    private fun startSnapshotCollection() {
        snapshotJob?.cancel()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                repository.observeSnapshot().collect(::acceptSnapshot)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _state.value = BattlefieldUiState(
                    LoadState.Error(failure.message ?: "加载失败"),
                )
            }
        }
        snapshotJob = job
        job.start()
    }

    private fun acceptSnapshot(snapshot: BattlefieldSnapshot) {
        latestSnapshot = snapshot
        commandCategories = snapshot.selectedCategories
        commandPaused = snapshot.paused
        _state.value = BattlefieldUiState(snapshot.toLoadState(refreshing))
    }

    private fun setRefreshing(value: Boolean) {
        refreshing = value
        val snapshot = latestSnapshot ?: return
        _state.value = BattlefieldUiState(snapshot.toLoadState(value))
    }

    private fun handleRefreshFailure(failure: Throwable) {
        val message = failure.message ?: "刷新失败"
        val snapshot = latestSnapshot
        if (snapshot != null && snapshot.hasContent()) {
            effectQueue.trySend(BattlefieldEffect.ShowMessage(message))
        } else {
            _state.value = BattlefieldUiState(LoadState.Error(message))
        }
    }

    private fun toggleCategory(category: EventCategory) {
        val snapshot = latestSnapshot ?: return
        val currentCategories = commandCategories ?: snapshot.selectedCategories
        val categories = if (category in currentCategories) {
            currentCategories - category
        } else {
            currentCategories + category
        }
        if (categories.isEmpty()) {
            effectQueue.trySend(BattlefieldEffect.ShowMessage("至少保留一种动态类型"))
        } else {
            commandCategories = categories
            repository.setFilter(categories)
        }
    }

    private fun togglePaused() {
        val snapshot = latestSnapshot ?: return
        setPaused(!(commandPaused ?: snapshot.paused))
    }

    private fun setPaused(paused: Boolean) {
        commandPaused = paused
        repository.setPaused(paused)
    }

    private fun BattlefieldSnapshot.toLoadState(refreshing: Boolean): LoadState<BattlefieldSnapshot> =
        if (!hasContent()) {
            LoadState.Empty("尚未收到战场动态", "启动抓包")
        } else {
            LoadState.Content(this, refreshing)
        }

    private fun BattlefieldSnapshot.hasContent() = capture.running || events.isNotEmpty()

    override fun onCleared() {
        effectQueue.close()
        super.onCleared()
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 2_000L
    }
}
