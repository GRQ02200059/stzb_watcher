package com.stzb.battle.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BattleEngineTest {
    @Test
    fun `faster heroes act first and normal attacks reduce troops`() {
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(
                    listOf(
                        hero(pos = 0, heroId = 100479, hitRange = 5, speed = 120, attack = 90, defense = 20, troops = 100),
                        hero(pos = 1, heroId = 100017, speed = 60, attack = 30, defense = 20, troops = 100),
                    ),
                ),
                defender = BattleTeam(
                    listOf(
                        hero(pos = 0, heroId = 100023, hitRange = 5, speed = 80, attack = 40, defense = 10, troops = 100),
                    ),
                ),
                maxRounds = 1,
            ),
        )

        val attacks = result.events.filterIsInstance<BattleEvent.NormalAttack>()
        assertEquals(Side.ATTACKER, attacks[0].source.side)
        assertEquals(0, attacks[0].source.position)
        assertEquals(Side.DEFENDER, attacks[1].source.side)
        assertEquals(0, attacks[1].source.position)
        assertTrue(result.defender.heroes.single().troops < 100)
    }

    @Test
    fun `front heroes are distance one apart`() {
        val result = resolveOneRound(
            attacker = listOf(hero(pos = 2, hitRange = 1, speed = 100, attack = 100, defense = 0, troops = 100)),
            defender = listOf(hero(pos = 2, speed = 10, attack = 10, defense = 0, troops = 100)),
        )

        assertTrue(result.events.any { it is BattleEvent.NormalAttack && it.source.side == Side.ATTACKER })
    }

    @Test
    fun `base heroes need range five to hit each other`() {
        val shortRange = resolveOneRound(
            attacker = listOf(hero(pos = 0, hitRange = 4, speed = 100, attack = 100, defense = 0, troops = 100)),
            defender = listOf(hero(pos = 0, speed = 10, attack = 10, defense = 0, troops = 100)),
        )
        val fullRange = resolveOneRound(
            attacker = listOf(hero(pos = 0, hitRange = 5, speed = 100, attack = 100, defense = 0, troops = 100)),
            defender = listOf(hero(pos = 0, speed = 10, attack = 10, defense = 0, troops = 100)),
        )

        assertTrue(shortRange.events.none { it is BattleEvent.NormalAttack && it.source.side == Side.ATTACKER })
        assertTrue(fullRange.events.any { it is BattleEvent.NormalAttack && it.source.side == Side.ATTACKER })
    }

    @Test
    fun `normal attack selects nearest enemy front first`() {
        val result = resolveOneRound(
            attacker = listOf(hero(pos = 2, hitRange = 5, speed = 100, attack = 10, defense = 0, troops = 100)),
            defender = listOf(
                hero(pos = 0, speed = 10, attack = 1, defense = 0, troops = 100),
                hero(pos = 2, speed = 9, attack = 1, defense = 0, troops = 100),
            ),
        )

        val firstAttack = result.events.filterIsInstance<BattleEvent.NormalAttack>()
            .first { it.source.side == Side.ATTACKER }
        assertEquals(2, firstAttack.target.position)
    }

    @Test
    fun `all six heroes take turns and normal attack when targets are in range`() {
        val result = resolveOneRound(
            attacker = listOf(
                hero(pos = 0, heroId = 10, hitRange = 5, speed = 60, attack = 10, defense = 0, troops = 1_000),
                hero(pos = 1, heroId = 11, hitRange = 5, speed = 50, attack = 10, defense = 0, troops = 1_000),
                hero(pos = 2, heroId = 12, hitRange = 5, speed = 40, attack = 10, defense = 0, troops = 1_000),
            ),
            defender = listOf(
                hero(pos = 0, heroId = 20, hitRange = 5, speed = 30, attack = 10, defense = 0, troops = 1_000),
                hero(pos = 1, heroId = 21, hitRange = 5, speed = 20, attack = 10, defense = 0, troops = 1_000),
                hero(pos = 2, heroId = 22, hitRange = 5, speed = 10, attack = 10, defense = 0, troops = 1_000),
            ),
        )

        val actionSources = result.events.filterIsInstance<BattleEvent.HeroActionStart>().map { it.source }.toSet()
        val attackSources = result.events.filterIsInstance<BattleEvent.NormalAttack>().map { it.source }.toSet()

        assertEquals(6, actionSources.size)
        assertEquals(6, attackSources.size)
        assertEquals(setOf(0, 1, 2), attackSources.filter { it.side == Side.ATTACKER }.map { it.position }.toSet())
        assertEquals(setOf(0, 1, 2), attackSources.filter { it.side == Side.DEFENDER }.map { it.position }.toSet())
    }

    @Test
    fun `battle ends when one side is defeated`() {
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(
                    listOf(hero(pos = 0, hitRange = 5, speed = 100, attack = 500, defense = 0, troops = 100)),
                ),
                defender = BattleTeam(
                    listOf(hero(pos = 0, speed = 10, attack = 10, defense = 0, troops = 80)),
                ),
                maxRounds = 8,
            ),
        )

        assertEquals(BattleOutcome.ATTACKER_WIN, result.outcome)
        assertEquals(0, result.defender.heroes.single().troops)
        assertEquals(1, result.events.filterIsInstance<BattleEvent.RoundStart>().size)
        assertTrue(result.events.last() is BattleEvent.BattleEnd)
    }

    @Test
    fun `battle ends immediately when a base hero is defeated`() {
        val result = resolveOneRound(
            attacker = listOf(
                hero(pos = 0, heroId = 1, hitRange = 5, speed = 100, attack = 10, defense = 0, troops = 100),
            ),
            defender = listOf(
                hero(pos = 0, heroId = 2, hitRange = 5, speed = 10, attack = 10, defense = 0, troops = 1)
                    .copy(activeStatuses = setOf(BattleStatus.PANIC)),
                hero(pos = 2, heroId = 3, hitRange = 5, speed = 5, attack = 10, defense = 0, troops = 1_000),
            ),
        )

        assertEquals(BattleOutcome.ATTACKER_WIN, result.outcome)
        assertEquals(0, result.defender.heroes.first { it.position == 0 }.troops)
        assertEquals(1_000, result.defender.heroes.first { it.position == 2 }.troops)
        assertTrue(
            result.events.none {
                it is BattleEvent.HeroActionStart &&
                    it.source.heroId == BattleHeroId(3)
            },
        )
    }

    @Test
    fun `defeating a non-base hero does not decide the battle`() {
        val result = resolveOneRound(
            attacker = listOf(
                hero(pos = 0, heroId = 1, hitRange = 5, speed = 100, attack = 10, defense = 0, troops = 1_000),
                hero(pos = 2, heroId = 2, hitRange = 5, speed = 90, attack = 500, defense = 0, troops = 1_000),
            ),
            defender = listOf(
                hero(pos = 0, heroId = 3, hitRange = 5, speed = 10, attack = 10, defense = 0, troops = 1_000),
                hero(pos = 2, heroId = 4, hitRange = 5, speed = 5, attack = 10, defense = 0, troops = 1),
            ),
        )

        assertEquals(0, result.defender.heroes.first { it.position == 2 }.troops)
        assertTrue(result.defender.heroes.first { it.position == 0 }.troops > 0)
        assertEquals(BattleOutcome.DRAW, result.outcome)
    }

    @Test
    fun `losing the attacker base is a defender victory even when other attackers survive`() {
        val result = resolveOneRound(
            attacker = listOf(
                hero(pos = 0, heroId = 1, hitRange = 5, speed = 100, attack = 10, defense = 0, troops = 1)
                    .copy(activeStatuses = setOf(BattleStatus.PANIC)),
                hero(pos = 2, heroId = 2, hitRange = 5, speed = 90, attack = 10, defense = 0, troops = 1_000),
            ),
            defender = listOf(
                hero(pos = 0, heroId = 3, hitRange = 5, speed = 10, attack = 10, defense = 0, troops = 1_000),
            ),
        )

        assertEquals(0, result.attacker.heroes.first { it.position == 0 }.troops)
        assertEquals(1_000, result.attacker.heroes.first { it.position == 2 }.troops)
        assertEquals(BattleOutcome.DEFENDER_WIN, result.outcome)
    }

    @Test
    fun `eight rounds with both base heroes alive is a draw`() {
        val result = BattleEngine.resolve(
            BattleRequest(
                attacker = BattleTeam(
                    listOf(hero(pos = 0, heroId = 1, hitRange = 1, speed = 100, attack = 1, defense = 1_000, troops = 1_000)),
                ),
                defender = BattleTeam(
                    listOf(hero(pos = 0, heroId = 2, hitRange = 1, speed = 10, attack = 1, defense = 1_000, troops = 1_000)),
                ),
                maxRounds = 8,
            ),
        )

        assertEquals(8, result.events.filterIsInstance<BattleEvent.RoundStart>().size)
        assertTrue(result.attacker.heroes.single().troops > 0)
        assertTrue(result.defender.heroes.single().troops > 0)
        assertEquals(BattleOutcome.DRAW, result.outcome)
    }

    private fun resolveOneRound(
        attacker: List<BattleHero>,
        defender: List<BattleHero>,
    ): BattleResult = BattleEngine.resolve(
        BattleRequest(BattleTeam(attacker), BattleTeam(defender), maxRounds = 1),
    )

    private fun hero(
        pos: Int,
        heroId: Int = 1,
        hitRange: Int = 3,
        speed: Int,
        attack: Int,
        defense: Int,
        troops: Int,
    ): BattleHero =
        BattleHero(
            id = BattleHeroId(heroId),
            position = pos,
            stats = BattleStats(
                attack = attack,
                defense = defense,
                strategy = 0,
                speed = speed,
                siege = 0,
                hitRange = hitRange,
            ),
            troops = troops,
        )
}
