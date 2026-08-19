package com.local.stzb.feature.livearmy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.stzb.data.livearmy.LiveArmySnapshot
import com.local.stzb.data.livearmy.LocalLiveArmyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LiveArmyUiState(
    val loading: Boolean = true,
    val query: String = "",
    val snapshot: LiveArmySnapshot? = null,
    val error: String? = null,
)

class LiveArmyViewModel(private val repository: LocalLiveArmyRepository = LocalLiveArmyRepository()) : ViewModel() {
    private val mutableState = MutableStateFlow(LiveArmyUiState())
    val state: StateFlow<LiveArmyUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun setQuery(value: String) {
        mutableState.value = mutableState.value.copy(query = value)
        refresh()
    }

    fun refresh() {
        val query = mutableState.value.query
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.load(query) } }
                .onSuccess { mutableState.value = mutableState.value.copy(loading = false, snapshot = it) }
                .onFailure { mutableState.value = mutableState.value.copy(loading = false, error = it.message ?: "实时部队加载失败") }
        }
    }
}
