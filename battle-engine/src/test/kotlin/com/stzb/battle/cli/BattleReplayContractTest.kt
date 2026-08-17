package com.stzb.battle.cli

import com.stzb.battle.core.BattleEffectValueUnit
import com.stzb.battle.core.BattleEvent
import com.stzb.battle.core.BattleHero
import com.stzb.battle.core.BattleHeroId
import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleOutcome
import com.stzb.battle.core.BattleResult
import com.stzb.battle.core.BattleStat
import com.stzb.battle.core.BattleStats
import com.stzb.battle.core.BattleStatus
import com.stzb.battle.core.BattleTeam
import com.stzb.battle.core.ClientBattleTextReplayAdapter
import com.stzb.battle.core.ClientReportAction
import com.stzb.battle.core.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BattleReplayContractTest {
    @Test
    fun `contract preserves every semantic event in order`() {
        val result = fixtureBattleResult()

        val payload = BattleReplayContract.from(result)

        assertEquals(result.events.size, payload.events.size)
        assertEquals(
            result.events.indices.toList(),
            payload.events.map { it.eventSeq },
        )
        assertTrue(payload.events.any { it.type == "HeroActionStart" })
        assertTrue(payload.events.any { it.type == "EffectBlocked" })
        assertTrue(payload.events.any { it.type == "StatChanged" })
        assertEquals(
            12.5,
            payload.events.single { it.type == "StatChanged" }.deltaExact,
        )
    }

    @Test
    fun `contract includes entry round and final snapshots`() {
        val payload = BattleReplayContract.from(fixtureBattleResult())

        assertEquals(6, payload.entrySnapshots.size)
        assertEquals(6, payload.roundSnapshots.size)
        assertEquals(6, payload.finalSnapshots.size)
        val defenderFront = payload.finalSnapshots.single {
            it.side == "DEFENDER" && it.position == 2
        }
        assertEquals(70, defenderFront.troops)
        assertEquals(30, defenderFront.cumulativeDamageTaken)
    }

    @Test
    fun `server action stream matches text replay adapter order`() {
        val result = fixtureBattleResult()

        val payload = BattleReplayContract.from(result)
        val expected = ClientBattleTextReplayAdapter.adapt(result)
            .map(ClientReportAction::encode)

        assertEquals(expected, payload.replayActions.map { it.encoded })
        assertEquals(expected.joinToString("#"), payload.replayText)
    }

    @Test
    fun `diagnostics expose unsupported effects without dropping events`() {
        val payload = BattleReplayContract.from(fixtureBattleResult())

        assertEquals(1, payload.diagnostics.unsupportedSkillEffects.size)
        assertEquals(1, payload.diagnostics.unsupportedEquipmentEffects.size)
        assertEquals(payload.events.size, payload.diagnostics.semanticEventCount)
        assertEquals(
            payload.replayActions.size,
            payload.diagnostics.replayActionCount,
        )
        assertTrue(payload.diagnostics.unprojectedReplayEvents.isNotEmpty())
    }

    private fun fixtureBattleResult(): BattleResult {
        val attackerEntry = BattleTeam(
            listOf(
                hero(1, 0, 100),
                hero(2, 1, 100),
                hero(3, 2, 100),
            ),
        )
        val defenderEntry = BattleTeam(
            listOf(
                hero(4, 0, 100),
                hero(5, 1, 100),
                hero(6, 2, 100),
            ),
        )
        val attacker = attackerEntry.copy(
            heroes = attackerEntry.heroes.map {
                if (it.position == 0) it.copy(troops = 90) else it
            },
        )
        val defender = defenderEntry.copy(
            heroes = defenderEntry.heroes.map {
                if (it.position == 2) it.copy(troops = 70) else it
            },
        )
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val target = BattleHeroRef(Side.DEFENDER, 2, BattleHeroId(6))
        return BattleResult(
            outcome = BattleOutcome.ATTACKER_WIN,
            attacker = attacker,
            defender = defender,
            entryAttacker = attackerEntry,
            entryDefender = defenderEntry,
            events = listOf(
                BattleEvent.BattleStart,
                BattleEvent.RoundStart(1),
                BattleEvent.HeroActionStart(1, source),
                BattleEvent.StatChanged(
                    round = 1,
                    source = source,
                    target = source,
                    stat = BattleStat.ATTACK,
                    delta = 12,
                    durationRounds = 2,
                    skillId = 200001,
                    effectId = 101,
                    valueAfter = 112,
                    deltaExact = 12.5,
                    valueAfterExact = 112.5,
                    unit = BattleEffectValueUnit.FLAT,
                ),
                BattleEvent.EffectBlocked(
                    round = 1,
                    source = source,
                    target = target,
                    skillId = 200001,
                    effectId = 401,
                    blockingEffectId = 207,
                ),
                BattleEvent.StatusApplied(
                    round = 1,
                    source = source,
                    target = target,
                    status = BattleStatus.BURN,
                    durationRounds = 2,
                    skillId = 200001,
                    effectId = 305,
                ),
                BattleEvent.SkillDamage(
                    round = 1,
                    skillId = 200001,
                    effectId = 301,
                    source = source,
                    target = target,
                    damage = 30,
                    targetTroopsAfter = 70,
                ),
                BattleEvent.UnsupportedSkillEffect(
                    round = 1,
                    skillId = 299999,
                    effectId = 999,
                    source = source,
                    rawDescription = "fixture unsupported skill",
                ),
                BattleEvent.UnsupportedEquipmentEffect(
                    round = 1,
                    equipmentId = 499999,
                    source = source,
                    rawDescription = "fixture unsupported equipment",
                ),
                BattleEvent.HeroActionEnd(1, source),
                BattleEvent.RoundEnd(1),
                BattleEvent.BattleEnd(BattleOutcome.ATTACKER_WIN),
            ),
        )
    }

    private fun hero(id: Int, position: Int, troops: Int): BattleHero =
        BattleHero(
            id = BattleHeroId(id),
            position = position,
            stats = BattleStats(100, 100, 100, 100, 0, 5),
            troops = troops,
            maxTroops = 100,
        )
}
