package com.local.stzb.domain.battles

enum class BattleOutcome { VICTORY, DEFEAT, DRAW, OTHER }

data class BattleFilters(
    val query: String = "",
    val unionName: String = "",
    val outcome: BattleOutcome? = null,
    val siegeOnly: Boolean = false,
    val wid: Int? = null,
    val limit: Int = 100,
)

data class BattleSummary(
    val id: Int,
    val occurredAt: Long,
    val outcome: BattleOutcome,
    val outcomeLabel: String,
    val title: String,
    val locationAndType: String,
    val attackerWuxun: Int,
    val attackerHp: Int,
    val defenderHp: Int,
    val heroNames: List<String>,
)

data class BattleSide(
    val label: String,
    val name: String,
    val unionName: String,
    val power: Int,
    val wuxun: Int,
    val hp: Int,
    val heroes: List<BattleHero>,
)

data class BattleHero(
    val name: String,
    val level: Int,
    val star: Int,
    val remainHp: Int,
    val maxHp: Int,
)

data class BattleDetail(
    val summary: BattleSummary,
    val attacker: BattleSide,
    val defender: BattleSide,
    val weather: Int,
    val nightBattle: Boolean,
    val rawJson: String,
)
