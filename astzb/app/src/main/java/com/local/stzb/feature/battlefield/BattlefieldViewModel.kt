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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
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
    private var pendingCategories: Set<EventCategory>? = null
    private var pendingPaused: Boolean? = null
    private var refreshFailureKey: String? = null
    private var coalescedOverflowEffect: BattlefieldEffect? = null
    private var refreshing = false

    private val _state = MutableStateFlow(BattlefieldUiState())
    val state: StateFlow<BattlefieldUiState> = _state.asStateFlow()

    private val effectQueue = Channel<BattlefieldEffect>(EFFECT_QUEUE_CAPACITY)
    val effects: SharedFlow<BattlefieldEffect> = effectQueue
        .receiveAsFlow()
        .onEach { flushCoalescedOverflowEffect() }
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            replay = 0,
        )

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
                refreshFailureKey = null
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
        if (pendingCategories == snapshot.selectedCategories) {
            pendingCategories = null
        }
        if (pendingPaused == snapshot.paused) {
            pendingPaused = null
        }
        refreshFailureKey = null
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
            if (refreshFailureKey != message) {
                refreshFailureKey = message
                enqueueEffect(BattlefieldEffect.ShowMessage(message))
            }
        } else {
            _state.value = BattlefieldUiState(LoadState.Error(message))
        }
    }

    private fun toggleCategory(category: EventCategory) {
        val snapshot = latestSnapshot ?: return
        val currentCategories = pendingCategories ?: snapshot.selectedCategories
        val categories = if (category in currentCategories) {
            currentCategories - category
        } else {
            currentCategories + category
        }
        if (categories.isEmpty()) {
            enqueueEffect(BattlefieldEffect.ShowMessage("至少保留一种动态类型"))
        } else {
            pendingCategories = categories
            repository.setFilter(categories)
        }
    }

    private fun togglePaused() {
        val snapshot = latestSnapshot ?: return
        setPaused(!(pendingPaused ?: snapshot.paused))
    }

    private fun setPaused(paused: Boolean) {
        pendingPaused = paused
        repository.setPaused(paused)
    }

    private fun enqueueEffect(effect: BattlefieldEffect) {
        val result = effectQueue.trySend(effect)
        when {
            result.isSuccess -> coalescedOverflowEffect = null
            result.isClosed -> Unit
            coalescedOverflowEffect != effect -> coalescedOverflowEffect = effect
        }
    }

    private fun flushCoalescedOverflowEffect() {
        val effect = coalescedOverflowEffect ?: return
        val result = effectQueue.trySend(effect)
        if (result.isSuccess || result.isClosed) {
            coalescedOverflowEffect = null
        }
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
        const val EFFECT_QUEUE_CAPACITY = 64
        const val REFRESH_INTERVAL_MS = 2_000L
    }
}
