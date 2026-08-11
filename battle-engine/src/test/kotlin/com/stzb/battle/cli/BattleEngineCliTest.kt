package com.stzb.battle.cli

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BattleEngineCliTest {
    @Test
    fun fixtureProducesJsonResult() {
        val input = """
            {
              "seed": 1,
              "repeat": 1,
              "attacker": {"morale": 100, "heroes": []},
              "defender": {"morale": 100, "heroes": []}
            }
        """.trimIndent()
        val result = runBattleEngineCli(input)
        assertTrue(result.contains("\"ok\""))
        assertTrue(result.contains("\"repeat\""))
        assertEquals(1, result.substringAfter("\"repeat\":").substringBefore(",").trim().toInt())
    }

    @Test
    fun fullTeamsProduceRealBattleEvents() {
        val input = """
            {
              "seed": 20260810,
              "repeat": 1,
              "attacker": {"morale": 100, "heroes": [
                {"heroId":100027,"position":0,"level":40,"advanceLevel":5,"troops":9000},
                {"heroId":100016,"position":1,"level":40,"advanceLevel":5,"troops":9000},
                {"heroId":100090,"position":2,"level":40,"advanceLevel":5,"troops":9000}
              ]},
              "defender": {"morale": 100, "heroes": [
                {"heroId":100013,"position":0,"level":40,"advanceLevel":5,"troops":9000},
                {"heroId":100649,"position":1,"level":40,"advanceLevel":5,"troops":9000},
                {"heroId":100023,"position":2,"level":40,"advanceLevel":5,"troops":9000}
              ]}
            }
        """.trimIndent()

        val result = jacksonObjectMapper().readTree(runBattleEngineCli(input))
        val textLog = result["firstRun"]["textLog"].map { it.asText() }

        assertTrue(result["ok"].asBoolean())
        assertTrue(textLog.any { it.contains("BattleStart") })
        assertTrue(textLog.any { it.contains("RoundStart") || it.contains("HeroActionStart") })
        assertFalse(textLog.any { it.contains("battle-engine skeleton") })
    }

    @Test
    fun firstRunExposesPerHeroSnapshotsAndRounds() {
        val input = """
            {
              "seed": 20260810,
              "repeat": 1,
              "attacker": {"morale": 100, "heroes": [
                {"heroId":100027,"position":0,"level":40,"advanceLevel":5,"troops":9000},
                {"heroId":100016,"position":1,"level":40,"advanceLevel":5,"troops":9000},
                {"heroId":100090,"position":2,"level":40,"advanceLevel":5,"troops":9000}
              ]},
              "defender": {"morale": 100, "heroes": [
                {"heroId":100013,"position":0,"level":40,"advanceLevel":5,"troops":9000},
                {"heroId":100649,"position":1,"level":40,"advanceLevel":5,"troops":9000},
                {"heroId":100023,"position":2,"level":40,"advanceLevel":5,"troops":9000}
              ]}
            }
        """.trimIndent()

        val result = jacksonObjectMapper().readTree(runBattleEngineCli(input))
        val firstRun = result["firstRun"]

        assertTrue(firstRun["roundsPlayed"].asInt() >= 1)
        val attackerHeroes = firstRun["attackerHeroes"]
        assertEquals(3, attackerHeroes.size())
        val leader = attackerHeroes[0]
        assertEquals(100027, leader["heroId"].asInt())
        assertEquals(0, leader["position"].asInt())
        assertEquals(9000, leader["initialTroops"].asInt())
        assertTrue(leader.has("troops"))
        assertEquals(3, firstRun["defenderHeroes"].size())
    }
}
