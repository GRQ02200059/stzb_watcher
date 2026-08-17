package com.local.stzb.data.battles

import com.example.myapplication.LocalBattleFilter
import com.example.myapplication.LocalFullBattle
import com.example.myapplication.LocalStzbRepository
import com.local.stzb.domain.battles.BattleDetail
import com.local.stzb.domain.battles.BattleFilters
import com.local.stzb.domain.battles.BattleHero
import com.local.stzb.domain.battles.BattleOutcome
import com.local.stzb.domain.battles.BattleRepository
import com.local.stzb.domain.battles.BattleSide
import com.local.stzb.domain.battles.BattleSummary

interface LegacyBattleSource {
    fun loadBattles(filter: LocalBattleFilter): List<LocalFullBattle>
    fun loadBattle(battleId: Int): LocalFullBattle?
}

object AndroidLegacyBattleSource : LegacyBattleSource {
    override fun loadBattles(filter: LocalBattleFilter) = LocalStzbRepository.loadFullBattles(filter)
    override fun loadBattle(battleId: Int) = LocalStzbRepository.loadFullBattle(battleId)
}

class LegacyBattleRepository(
    private val source: LegacyBattleSource = AndroidLegacyBattleSource,
) : BattleRepository {
    override fun loadBattles(filters: BattleFilters): List<BattleSummary> = source.loadBattles(
        LocalBattleFilter(
            player = filters.query,
            unionName = filters.unionName,
            fightType = if (filters.siegeOnly) 1 else null,
            result = null,
            wid = filters.wid,
            limit = if (filters.outcome == null) filters.limit else maxOf(filters.limit * 3, 300),
        ),
    ).asSequence()
        .map(LocalFullBattle::toSummary)
        .filter { filters.outcome == null || it.outcome == filters.outcome }
        .take(filters.limit)
        .toList()

    override fun loadBattle(id: Int): BattleDetail? = source.loadBattle(id)?.toDetail()
}

private fun LocalFullBattle.toSummary() = BattleSummary(
    id = battleId,
    occurredAt = if (time >= 100_000_000_000L) time / 1_000 else time,
    outcome = result.toOutcome(),
    outcomeLabel = resultLabel(result),
    title = "${attackerName.ifBlank { "未知攻方" }} vs ${defenderName.ifBlank { "未知守方" }}",
    locationAndType = listOf(widName.ifBlank { wid.toCoordinates() }, fightTypeLabel(fightType))
        .filter(String::isNotBlank).joinToString(" · "),
    attackerWuxun = attackerGongxun,
    attackerHp = attackerHp,
    defenderHp = defenderHp,
    heroNames = (attackerHeroes + defenderHeroes).map { it.heroName.ifBlank { "武将${it.heroId}" } }.take(6),
)

private fun LocalFullBattle.toDetail() = BattleDetail(
    summary = toSummary(),
    attacker = BattleSide("攻方", attackerName, attackerUnion, attackerPower, attackerGongxun, attackerHp, attackerHeroes.map { it.toHero() }),
    defender = BattleSide("守方", defenderName, defenderUnion, defenderPower, defenderGongxun, defenderHp, defenderHeroes.map { it.toHero() }),
    weather = weather,
    nightBattle = inNight != 0,
    rawJson = rawJson,
)

private fun com.example.myapplication.LocalBattleHero.toHero() = BattleHero(
    name = heroName.ifBlank { "武将$heroId" }, level = level, star = star,
    remainHp = remainHp, maxHp = maxHp,
)

private fun Int.toOutcome() = when (this) {
    1, 7, 11 -> BattleOutcome.VICTORY
    0, 10 -> BattleOutcome.DRAW
    2, 3, 4, 5, 8, 9 -> BattleOutcome.DEFEAT
    else -> BattleOutcome.OTHER
}

private fun resultLabel(result: Int) = when (result.toOutcome()) {
    BattleOutcome.VICTORY -> "胜利"
    BattleOutcome.DEFEAT -> "失败"
    BattleOutcome.DRAW -> "平局"
    BattleOutcome.OTHER -> "其他"
}

private fun fightTypeLabel(type: Int) = when (type) {
    1 -> "攻城"
    2 -> "驻守"
    3 -> "扫荡"
    4 -> "练兵"
    else -> "普通"
}

private fun Int.toCoordinates() = if (this > 0) "${this / 10_000},${this % 10_000}" else "未知地块"
