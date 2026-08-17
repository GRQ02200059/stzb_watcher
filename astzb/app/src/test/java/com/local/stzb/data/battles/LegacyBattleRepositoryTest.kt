package com.local.stzb.data.battles

import com.example.myapplication.LocalBattleFilter
import com.example.myapplication.LocalBattleHero
import com.example.myapplication.LocalFullBattle
import com.local.stzb.domain.battles.BattleFilters
import com.local.stzb.domain.battles.BattleOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyBattleRepositoryTest {
    @Test
    fun mapsLocalBattleIntoReadableSummaryAndStructuredDetail() {
        val source = FakeBattleSource(fullBattle())
        val repository = LegacyBattleRepository(source)

        val summary = repository.loadBattles(BattleFilters()).single()
        val detail = repository.loadBattle(9001)!!

        assertEquals(BattleOutcome.VICTORY, summary.outcome)
        assertEquals("前锋 vs 守军", summary.title)
        assertEquals("洛阳 · 攻城", summary.locationAndType)
        assertEquals("关羽", summary.heroNames.single())
        assertEquals(100, detail.attacker.power)
        assertEquals(80, detail.defender.hp)
    }

    private class FakeBattleSource(private val battle: LocalFullBattle) : LegacyBattleSource {
        override fun loadBattles(filter: LocalBattleFilter) = listOf(battle)
        override fun loadBattle(battleId: Int) = battle.takeIf { battleId == it.battleId }
    }

    private fun fullBattle() = LocalFullBattle(
        battleId = 9001, time = 1_700_000_000L, result = 1, fightType = 1,
        wid = 100020, widName = "洛阳", widCode = "", attackerName = "前锋",
        attackerUid = "11", attackerUnion = "测试盟", attackerUnionId = 12,
        attackerPower = 100, attackerGongxun = 20, attackerHp = 90,
        defenderName = "守军", defenderUid = "21", defenderUnion = "守方盟",
        defenderUnionId = 22, defenderLevel = 10, defenderPower = 90,
        defenderGongxun = 10, defenderHp = 80, weather = 0, inNight = 0,
        isNpc = 0, isAi = 0, blockId = 0, cityType = 1, borrowLand = 0,
        garrison = 1, firstOccupyLvnLand = 0, attackerTeamId = 31,
        defenderTeamId = 32, attackerAdvance = "", defenderAdvance = "",
        attackerHeroType = "", defenderHeroType = "", attackerGearInfo = "",
        defenderGearInfo = "", allSkillInfo = "", attackAllHeroInfo = "",
        defendAllHeroInfo = "", attackAllSubHeroInfo = "", defendAllSubHeroInfo = "",
        attackSupportUserInfo = "", defendSupportUserInfo = "", sourceMsgId = "10",
        rawJson = "{}",
        attackerHeroes = listOf(LocalBattleHero(9001, "atk", 0, 1, "关羽", 40, 5, 100, 90, 10)),
        defenderHeroes = emptyList(),
    )
}
