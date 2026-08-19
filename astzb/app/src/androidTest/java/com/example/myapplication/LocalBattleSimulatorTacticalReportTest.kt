package com.example.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBattleSimulatorTacticalReportTest {
    @Test
    fun defaultSimulationEmitsInspectableTacticalReport() {
        LocalBattleSimulator.init(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )

        val run = LocalBattleSimulator.simulate(
            LocalBattleSimulator.defaultWebConfig().copy(seed = 42, repeat = 1),
        ).firstRun

        assertEquals(3, run.attackerHeroes.size)
        assertEquals(3, run.defenderHeroes.size)
        assertTrue(run.roundsPlayed > 0)
        assertTrue(run.events.any { it.kind == LocalSimulationEventKind.DAMAGE })
        assertTrue(run.events.any { it.kind == LocalSimulationEventKind.RESULT })
        assertFalse(run.events.any { it.description.isBlank() })
    }
}
