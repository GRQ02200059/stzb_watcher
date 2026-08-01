package com.local.stzb.feature.battlefield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.stzb.core.ui.LoadState
import com.local.stzb.domain.battlefield.BattlefieldRepository
import com.local.stzb.domain.battlefield.EventCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BattlefieldViewModel(
    private val repository: BattlefieldRepository,
) : ViewModel() {
    private var refreshJob: Job? = null
    private var commandCategories: Set<EventCategory>? = null
    private var commandPaused: Boolean? = null

    private val _effects = MutableSharedFlow<BattlefieldEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<BattlefieldEffect> = _effects.asSharedFlow()

    val state: StateFlow<BattlefieldUiState> = repository.observeSnapshot()
        .onEach { snapshot ->
            commandCategories = snapshot.selectedCategories
            commandPaused = snapshot.paused
        }
        .map { snapshot ->
            val loadState = if (!snapshot.capture.running && snapshot.events.isEmpty()) {
                LoadState.Empty("尚未收到战场动态", "启动抓包")
            } else {
                LoadState.Content(snapshot)
            }
            BattlefieldUiState(loadState)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = BattlefieldUiState(),
        )

    fun onIntent(intent: BattlefieldIntent) {
        when (intent) {
            is BattlefieldIntent.SetActive -> setActive(intent.active)
            BattlefieldIntent.Refresh -> viewModelScope.launch { refreshOnce() }
            BattlefieldIntent.TogglePaused -> togglePaused()
            is BattlefieldIntent.ToggleCategory -> toggleCategory(intent.category)
            BattlefieldIntent.ConsumeBufferedEvents -> setPaused(false)
        }
    }

    private fun setActive(active: Boolean) {
        if (!active) {
            refreshJob?.cancel()
            refreshJob = null
            return
        }
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun toggleCategory(category: EventCategory) {
        val snapshot = currentSnapshot() ?: return
        val currentCategories = commandCategories ?: snapshot.selectedCategories
        val categories = if (category in currentCategories) {
            currentCategories - category
        } else {
            currentCategories + category
        }
        if (categories.isEmpty()) {
            _effects.tryEmit(BattlefieldEffect.ShowMessage("至少保留一种动态类型"))
        } else {
            commandCategories = categories
            repository.setFilter(categories)
        }
    }

    private fun togglePaused() {
        val snapshot = currentSnapshot() ?: return
        setPaused(!(commandPaused ?: snapshot.paused))
    }

    private fun setPaused(paused: Boolean) {
        commandPaused = paused
        repository.setPaused(paused)
    }

    private fun currentSnapshot() =
        (state.value.loadState as? LoadState.Content)?.value

    private suspend fun refreshOnce() {
        try {
            repository.refresh()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            _effects.emit(
                BattlefieldEffect.ShowMessage(failure.message ?: "刷新失败"),
            )
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 2_000L
    }
}
