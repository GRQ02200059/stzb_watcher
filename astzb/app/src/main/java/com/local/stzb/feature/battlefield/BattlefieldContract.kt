package com.local.stzb.feature.battlefield

import com.local.stzb.core.ui.LoadState
import com.local.stzb.domain.battlefield.BattlefieldSnapshot
import com.local.stzb.domain.battlefield.EventCategory

data class BattlefieldUiState(
    val loadState: LoadState<BattlefieldSnapshot> = LoadState.Loading,
)

sealed interface BattlefieldIntent {
    data class SetActive(val active: Boolean) : BattlefieldIntent
    data object Refresh : BattlefieldIntent
    data object TogglePaused : BattlefieldIntent
    data class ToggleCategory(val category: EventCategory) : BattlefieldIntent
    data object ConsumeBufferedEvents : BattlefieldIntent
}

sealed interface BattlefieldEffect {
    data class ShowMessage(val text: String) : BattlefieldEffect
}
