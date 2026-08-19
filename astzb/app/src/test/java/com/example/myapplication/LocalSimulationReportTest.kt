package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSimulationReportTest {
    @Test
    fun reportRunKeepsSnapshotsAndInspectableCombatEvents() {
        val run = LocalSimulationRun(
            winner = "攻方",
            blueRemain = 8_000,
            redRemain = 0,
            records = listOf("第1回合"),
            attackerHeroes = listOf(
                LocalSimulationHeroSnapshot(100027, "张辽", "大营", 9_000, 8_000, 40, 5),
            ),
            defenderHeroes = listOf(
                LocalSimulationHeroSnapshot(100013, "吕蒙", "前锋", 9_000, 0, 40, 5),
            ),
            events = listOf(
                LocalSimulationEvent(
                    round = 1,
                    kind = LocalSimulationEventKind.DAMAGE,
                    sourceName = "张辽",
                    targetName = "吕蒙",
                    skillName = "普通攻击",
                    amount = 1_200,
                    targetRemaining = 0,
                ),
                LocalSimulationEvent(
                    round = 1,
                    kind = LocalSimulationEventKind.STATUS,
                    sourceName = "吕蒙",
                    targetName = "吕蒙",
                    skillName = "犹豫",
                    amount = 0,
                    targetRemaining = 0,
                ),
            ),
            roundsPlayed = 1,
            seed = 42,
        )

        assertEquals(1, run.roundsPlayed)
        assertEquals(42, run.seed)
        assertEquals("张辽", run.attackerHeroes.single().name)
        assertEquals(1_200, run.events.single { it.kind == LocalSimulationEventKind.DAMAGE }.amount)
        assertTrue(run.events.any { it.kind == LocalSimulationEventKind.STATUS })
        assertFalse(run.defenderHeroes.single().alive)
    }
}
