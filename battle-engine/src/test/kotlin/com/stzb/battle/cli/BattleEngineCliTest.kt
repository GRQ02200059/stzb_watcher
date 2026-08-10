package com.stzb.battle.cli

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
