package com.stzb.battle.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BattleDamageCalculatorTest {
    @Test
    fun `troop counter profiles apply advantage disadvantage and source immunity`() {
        val archer = hero(heroType = 1)
        val spear = hero(heroType = 22)
        val neutral = BattleDamageCalculator.physical(archer, spear)

        val advantaged = BattleDamageCalculator.physical(
            source = archer.copy(
                modifiers = listOf(
                    BattleModifier.TroopCounterDealtPercent(
                        targetHeroType = 22,
                        percent = 30,
                    ),
                ),
            ),
            target = spear,
        )
        val archerCounterProfile = archer.copy(
            modifiers = listOf(
                BattleModifier.TroopCounterTakenPercent(
                    sourceHeroType = 22,
                    percent = -30,
                ),
            ),
        )
        val disadvantaged = BattleDamageCalculator.physical(
            source = spear,
            target = archerCounterProfile,
        )
        val immune = BattleDamageCalculator.physical(
            source = spear.copy(
                modifiers = listOf(BattleModifier.TroopCounterImmunity),
            ),
            target = archerCounterProfile,
        )

        assertTrue(advantaged > neutral)
        assertTrue(disadvantaged < neutral)
        assertEquals(neutral, immune)
    }

    @Test
    fun `skill scoped damage modifier does not leak to another active skill`() {
        val source = hero(heroType = 1).copy(
            modifiers = listOf(
                BattleModifier.DamageDealtPercent(
                    origin = DamageOrigin.ACTIVE,
                    percent = 30,
                    skillId = 200684,
                ),
            ),
        )
        val target = hero(heroType = 22)

        val selected = BattleDamageCalculator.strategy(
            source = source,
            target = target,
            ratePercent = 100,
            origin = DamageOrigin.ACTIVE,
            skillId = 200684,
        )
        val unselected = BattleDamageCalculator.strategy(
            source = source,
            target = target,
            ratePercent = 100,
            origin = DamageOrigin.ACTIVE,
            skillId = 200829,
        )

        assertTrue(selected > unselected)
    }

    @Test
    fun `damage taken modifier can require lower source inherent defense`() {
        val baselineSource = hero(heroType = 1)
        val lowDefenseSource = baselineSource.copy(
            inherentStats = baselineSource.inherentStats.copy(defense = 80),
        )
        val highDefenseSource = baselineSource.copy(
            inherentStats = baselineSource.inherentStats.copy(defense = 120),
        )
        val baselineTarget = hero(heroType = 22)
        val target = baselineTarget.copy(
            inherentStats = baselineTarget.inherentStats.copy(defense = 100),
            modifiers = listOf(
                BattleModifier.DamageTakenPercent(
                    percent = -20,
                    requiredSourceInherentStatBelowTarget = BattleStat.DEFENSE,
                ),
            ),
        )

        val lowBaseline = BattleDamageCalculator.physical(
            source = lowDefenseSource,
            target = baselineTarget,
        )
        val reduced = BattleDamageCalculator.physical(
            source = lowDefenseSource,
            target = target,
        )
        val highBaseline = BattleDamageCalculator.physical(
            source = highDefenseSource,
            target = baselineTarget,
        )
        val notReduced = BattleDamageCalculator.physical(
            source = highDefenseSource,
            target = target,
        )

        assertTrue(reduced < lowBaseline)
        assertEquals(highBaseline, notReduced)
    }

    private fun hero(heroType: Int) = BattleHero(
        id = BattleHeroId(heroType),
        position = 2,
        stats = BattleStats(
            attack = 140,
            defense = 100,
            strategy = 100,
            speed = 80,
            siege = 20,
            hitRange = 5,
        ),
        troops = 10_000,
        maxTroops = 10_000,
        heroType = heroType,
    )
}
