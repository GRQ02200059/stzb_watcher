package com.stzb.battle.core

import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BattleReportCodecTest {
    @Test
    fun `serializes battle events to stable json`() {
        val result = sampleResult()

        val json = BattleReportCodec.toJson(result)

        assertTrue(json.contains("BattleStart"))
        assertTrue(json.contains("RoundStart"))
        assertTrue(json.contains("SkillDamage"))
        assertTrue(json.contains("ATTACKER_WIN"))
        assertTrue(json.contains("\"targetTroopsAfter\":0"))
    }

    @Test
    fun `compresses client report as report detail action text`() {
        val result = sampleResult()

        val compressed = BattleReportCodec.toCompressedClientReport(result)
        val unzipped = GZIPInputStream(ByteArrayInputStream(Base64.getDecoder().decode(compressed.removePrefix("zzz"))))
            .reader(Charsets.UTF_8)
            .readText()

        assertTrue(compressed.startsWith("zzz"))
        assertTrue(!unzipped.trimStart().startsWith("{"))
        assertTrue(unzipped.contains("#"))
        assertTrue(unzipped.split("#").none { it.startsWith("0u") })
        assertEquals(1, unzipped.split("#").count { it == "04" })
        assertTrue(unzipped.split("#").any { it.startsWith("1o") })
        assertTrue(unzipped.split("#").any { it.startsWith("09") })
    }

    @Test
    fun `serializes originating skill ids for attributed event types`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val target = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(2))
        val result = BattleResult(
            outcome = BattleOutcome.ATTACKER_WIN,
            attacker = BattleTeam(listOf(hero(1, 100))),
            defender = BattleTeam(listOf(hero(2, 0))),
            events = listOf(
                BattleEvent.Recovery(1, source, source, 10, 100, skillId = 200001),
                BattleEvent.StatusApplied(
                    1,
                    source,
                    target,
                    BattleStatus.BURN,
                    2,
                    power = 37,
                    statDelta = BattleStats(1, 2, 3, 4, 5, 6),
                    skillId = 200002,
                    effectId = 752,
                ),
                BattleEvent.OngoingDamage(2, source, target, BattleStatus.BURN, 10, 90, skillId = 200002),
                BattleEvent.StatChanged(1, source, source, BattleStat.ATTACK, 10, 2, skillId = 200036),
            ),
        )

        val json = BattleReportCodec.toJson(result)

        assertEquals(4, Regex("\"skillId\":").findAll(json).count())
        assertTrue(json.contains("\"skillId\":200001"))
        assertTrue(json.contains("\"skillId\":200002"))
        assertTrue(json.contains("\"skillId\":200036"))
        assertTrue(json.contains("\"effectId\":752"))
        assertTrue(json.contains("\"power\":37"))
        assertTrue(json.contains("\"statDelta\":{\"attack\":1"))
    }

    private fun sampleResult(): BattleResult =
        BattleResult(
            outcome = BattleOutcome.ATTACKER_WIN,
            attacker = BattleTeam(listOf(hero(1, 100))),
            defender = BattleTeam(listOf(hero(2, 0))),
            events = listOf(
                BattleEvent.BattleStart,
                BattleEvent.RoundStart(1),
                BattleEvent.SkillDamage(
                    round = 1,
                    skillId = 200012,
                    effectId = 301,
                    source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1)),
                    target = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(2)),
                    damage = 100,
                    targetTroopsAfter = 0,
                ),
                BattleEvent.BattleEnd(BattleOutcome.ATTACKER_WIN),
            ),
        )

    private fun hero(id: Int, troops: Int): BattleHero =
        BattleHero(
            id = BattleHeroId(id),
            position = 0,
            stats = BattleStats(1, 1, 1, 1, 0, 1),
            troops = troops,
        )
}
