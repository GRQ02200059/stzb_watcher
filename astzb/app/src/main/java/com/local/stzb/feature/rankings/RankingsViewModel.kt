package com.local.stzb.feature.rankings

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

data class RankingsUiState(
    val loading: Boolean = true,
    val page: RankingPage = RankingPage.RANKINGS,
    val category: RankingCategory = RankingCategory.BATTLE,
    val dimension: ReportDimension = ReportDimension.GROUP,
    val period: ReportPeriod = ReportPeriod.ALL,
    val group: String = "",
    val rankings: RankingSnapshot? = null,
    val report: TeamReportSnapshot? = null,
    val error: String? = null,
)

class RankingsViewModel(private val repository: RankingRepository, private val io: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val _state = MutableStateFlow(RankingsUiState())
    val state: StateFlow<RankingsUiState> = _state.asStateFlow()
    init { refresh() }
    fun setPage(value: RankingPage) { _state.value = _state.value.copy(page = value); refresh() }
    fun setCategory(value: RankingCategory) { _state.value = _state.value.copy(category = value) }
    fun setDimension(value: ReportDimension) { _state.value = _state.value.copy(dimension = value, group = if (value == ReportDimension.GROUP) "" else _state.value.group); refresh() }
    fun setPeriod(value: ReportPeriod) { _state.value = _state.value.copy(period = value); refresh() }
    fun setGroup(value: String) { _state.value = _state.value.copy(group = value); refresh() }
    fun refresh() {
        val requested = _state.value
        _state.value = requested.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { withContext(io) { if (requested.page == RankingPage.RANKINGS) repository.loadRankings() else repository.loadTeamReport(requested.dimension, requested.period, requested.group) } }
                .onSuccess { result -> _state.value = if (result is RankingSnapshot) _state.value.copy(loading = false, rankings = result) else _state.value.copy(loading = false, report = result as TeamReportSnapshot) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "排行数据加载失败") }
        }
    }
}
