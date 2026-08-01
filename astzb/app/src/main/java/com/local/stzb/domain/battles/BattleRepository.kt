package com.local.stzb.domain.battles

interface BattleRepository {
    fun loadBattles(filters: BattleFilters = BattleFilters()): List<BattleSummary>
    fun loadBattle(id: Int): BattleDetail?
}
