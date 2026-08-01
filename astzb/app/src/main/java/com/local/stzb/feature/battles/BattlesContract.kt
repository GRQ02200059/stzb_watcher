package com.local.stzb.feature.battles

import com.local.stzb.domain.battles.BattleDetail
import com.local.stzb.domain.battles.BattleFilters
import com.local.stzb.domain.battles.BattleSummary

data class BattlesUiState(
    val loading: Boolean = true,
    val battles: List<BattleSummary> = emptyList(),
    val filters: BattleFilters = BattleFilters(),
    val selected: BattleDetail? = null,
    val error: String? = null,
)

sealed interface BattlesIntent {
    data object Refresh : BattlesIntent
    data class SetQuickFilter(val filter: QuickBattleFilter) : BattlesIntent
    data class SetQuery(val query: String) : BattlesIntent
    data class OpenBattle(val id: Int) : BattlesIntent
    data object CloseBattle : BattlesIntent
}

enum class QuickBattleFilter(val label: String) {
    ALL("全部"), VICTORY("胜利"), DEFEAT("失败"), SIEGE("攻城")
}
