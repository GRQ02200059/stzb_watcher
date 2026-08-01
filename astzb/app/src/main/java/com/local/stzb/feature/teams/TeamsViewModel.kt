package com.local.stzb.feature.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.stzb.domain.teams.PlayerTeam
import com.local.stzb.domain.teams.TeamsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TeamsViewModel(
    private val repository: TeamsRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(TeamsUiState())
    val state: StateFlow<TeamsUiState> = _state.asStateFlow()
    private var generation = 0L

    init { refresh() }

    fun onIntent(intent: TeamsIntent) = when (intent) {
        TeamsIntent.Refresh -> refresh()
        is TeamsIntent.QueryChanged -> publish(_state.value.copy(query = intent.value))
        is TeamsIntent.SideChanged -> publish(_state.value.copy(side = intent.value))
    }

    private fun refresh() {
        val request = ++generation
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { withContext(io) { repository.loadTeams() } }
                .onSuccess { teams ->
                    if (request == generation) publish(_state.value.copy(loading = false, allTeams = teams, error = null))
                }
                .onFailure { error ->
                    if (request == generation) _state.value = _state.value.copy(loading = false, error = error.message ?: "队伍数据加载失败")
                }
        }
    }

    private fun publish(state: TeamsUiState) {
        _state.value = state.copy(visibleTeams = state.allTeams
            .asSequence()
            .filter { state.side.value == null || it.side == state.side.value }
            .filter { state.query.isBlank() || it.searchText().contains(state.query.trim(), ignoreCase = true) }
            .sortedWith(compareByDescending<PlayerTeam> { it.battles }.thenByDescending { it.winRate })
            .toList())
    }

    private fun PlayerTeam.searchText(): String = buildString {
        append(player); append(' '); append(unionName); append(' ')
        append(heroes.joinToString(" ") { it.name }); append(' '); append(skillNames.joinToString(" "))
    }
}
