package com.local.stzb.feature.research

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.stzb.data.research.SharedLineupRankingsSource
import com.local.stzb.data.research.SharedLineupSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SharedRankingTab(val label: String) { POPULAR("热门"), WIN_RATE("胜率"), COUNTERS("克制") }
data class SharedLineupUiState(val loading: Boolean = false, val snapshot: SharedLineupSnapshot? = null, val tab: SharedRankingTab = SharedRankingTab.POPULAR, val query: String = "", val error: String? = null)
class SharedLineupRankingsViewModel(private val source: SharedLineupRankingsSource, private val io: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val mutableState = MutableStateFlow(SharedLineupUiState(loading = true))
    val state: StateFlow<SharedLineupUiState> = mutableState.asStateFlow()
    init { refresh() }
    fun setTab(tab: SharedRankingTab) { mutableState.value = mutableState.value.copy(tab = tab) }
    fun setQuery(query: String) { mutableState.value = mutableState.value.copy(query = query) }
    fun refresh() {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { withContext(io) { source.load(50) } }
                .onSuccess { mutableState.value = mutableState.value.copy(loading = false, snapshot = it) }
                .onFailure { mutableState.value = mutableState.value.copy(loading = false, error = "共享榜暂不可用") }
        }
    }
}
