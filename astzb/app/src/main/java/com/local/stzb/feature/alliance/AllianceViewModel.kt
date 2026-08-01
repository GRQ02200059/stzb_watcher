package com.local.stzb.feature.alliance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.stzb.domain.alliance.AllianceRepository
import com.local.stzb.domain.alliance.AllianceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AllianceUiState(
    val loading: Boolean = true,
    val query: String = "",
    val group: String = "",
    val snapshot: AllianceSnapshot? = null,
    val error: String? = null,
)

class AllianceViewModel(private val repository: AllianceRepository) : ViewModel() {
    private val _state = MutableStateFlow(AllianceUiState())
    val state: StateFlow<AllianceUiState> = _state.asStateFlow()
    init { refresh() }

    fun setQuery(query: String) { _state.value = _state.value.copy(query = query); refresh() }
    fun setGroup(group: String) { _state.value = _state.value.copy(group = group); refresh() }
    fun refresh() {
        val current = _state.value
        _state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.load(current.query, current.group) } }
                .onSuccess { _state.value = _state.value.copy(loading = false, snapshot = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "同盟数据加载失败") }
        }
    }
}
