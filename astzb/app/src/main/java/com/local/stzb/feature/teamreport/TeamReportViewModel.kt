package com.local.stzb.feature.teamreport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.stzb.domain.rankings.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TeamReportUiState(
    val loading: Boolean = true,
    val dimension: ReportDimension = ReportDimension.GROUP,
    val period: ReportPeriod = ReportPeriod.ALL,
    val group: String = "",
    val report: TeamReportSnapshot? = null,
    val error: String? = null,
)

class TeamReportViewModel(
    private val repository: RankingRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(TeamReportUiState())
    val state: StateFlow<TeamReportUiState> = _state.asStateFlow()
    private var generation = 0L
    init { refresh() }

    fun setDimension(value: ReportDimension) { _state.value = _state.value.copy(dimension = value, group = if (value == ReportDimension.GROUP) "" else _state.value.group); refresh() }
    fun setPeriod(value: ReportPeriod) { _state.value = _state.value.copy(period = value); refresh() }
    fun setGroup(value: String) { _state.value = _state.value.copy(group = value); refresh() }
    fun refresh() {
        val requested = _state.value
        val request = ++generation
        _state.value = requested.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { withContext(io) { repository.loadTeamReport(requested.dimension, requested.period, requested.group) } }
                .onSuccess { if (request == generation) _state.value = _state.value.copy(loading = false, report = it) }
                .onFailure { if (request == generation) _state.value = _state.value.copy(loading = false, error = it.message ?: "团队报表加载失败") }
        }
    }
}
