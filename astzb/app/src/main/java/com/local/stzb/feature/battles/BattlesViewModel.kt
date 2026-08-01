package com.local.stzb.feature.battles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.stzb.domain.battles.BattleFilters
import com.local.stzb.domain.battles.BattleOutcome
import com.local.stzb.domain.battles.BattleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BattlesViewModel(private val repository: BattleRepository) : ViewModel() {
    private val _state = MutableStateFlow(BattlesUiState())
    val state: StateFlow<BattlesUiState> = _state.asStateFlow()

    init { refresh() }

    fun onIntent(intent: BattlesIntent) {
        when (intent) {
            BattlesIntent.Refresh -> refresh()
            is BattlesIntent.SetQuickFilter -> updateFilters(intent.filter.toFilters(_state.value.filters))
            is BattlesIntent.SetQuery -> updateFilters(_state.value.filters.copy(query = intent.query))
            is BattlesIntent.OpenBattle -> loadDetail(intent.id)
            BattlesIntent.CloseBattle -> _state.value = _state.value.copy(selected = null)
        }
    }

    private fun updateFilters(filters: BattleFilters) {
        _state.value = _state.value.copy(filters = filters)
        refresh()
    }

    private fun refresh() {
        val filters = _state.value.filters
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.loadBattles(filters) } }
                .onSuccess { _state.value = _state.value.copy(loading = false, battles = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "战报加载失败") }
        }
    }

    private fun loadDetail(id: Int) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.loadBattle(id) } }
                .onSuccess { _state.value = _state.value.copy(selected = it) }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "战报详情加载失败") }
        }
    }
}

private fun QuickBattleFilter.toFilters(current: BattleFilters) = when (this) {
    QuickBattleFilter.ALL -> current.copy(outcome = null, siegeOnly = false)
    QuickBattleFilter.VICTORY -> current.copy(outcome = BattleOutcome.VICTORY, siegeOnly = false)
    QuickBattleFilter.DEFEAT -> current.copy(outcome = BattleOutcome.DEFEAT, siegeOnly = false)
    QuickBattleFilter.SIEGE -> current.copy(outcome = null, siegeOnly = true)
}
