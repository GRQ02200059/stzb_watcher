package com.local.stzb.feature.simulator

import com.example.myapplication.LocalSimHeroConfig
import com.example.myapplication.LocalSimTeamConfig
import com.example.myapplication.LocalSimulationConfig
import com.example.myapplication.LocalSimulationEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteBattleSimulatorEngineTest {
    @Test fun mapsCompleteEnginePreparationRoundsAndResultIntoExistingReportModel() {
        val result = CompleteBattleSimulatorEngine.simulate(liuBeiMatch(seed = 42))
        val run = result.firstRun

        assertEquals(3, run.attackerHeroes.size)
        assertEquals(3, run.defenderHeroes.size)
        assertTrue(run.events.any { it.kind == LocalSimulationEventKind.PREPARATION && it.skillName == "皇裔流离" })
        assertTrue(run.events.any { it.kind == LocalSimulationEventKind.ROUND_START })
        assertTrue(run.events.any { it.kind == LocalSimulationEventKind.RESULT })
    }

    @Test fun liuBeiEmergencyRecoveryIsRecordedAfterDamageForADeterministicSeed() {
        val run = (0..160).asSequence()
            .map { seed -> CompleteBattleSimulatorEngine.simulate(liuBeiMatch(seed)).firstRun }
            .firstOrNull { report ->
                val recoveryIndex = report.events.indexOfFirst {
                    it.kind == LocalSimulationEventKind.RECOVERY && it.skillName == "皇裔流离" && it.amount > 0
                }
                recoveryIndex > 0 && report.events.take(recoveryIndex).any { it.kind == LocalSimulationEventKind.DAMAGE }
            }

        assertTrue("刘备的皇裔流离应在友军受伤后产生恢复事件", run != null)
    }

    private fun liuBeiMatch(seed: Int) = LocalSimulationConfig(
        blue = LocalSimTeamConfig(100, listOf(
            LocalSimHeroConfig(100016, level = 40, advance = 5),
            LocalSimHeroConfig(100027, level = 40, advance = 5),
            LocalSimHeroConfig(100090, level = 40, advance = 5),
        )),
        red = LocalSimTeamConfig(100, listOf(
            LocalSimHeroConfig(100013, level = 40, advance = 5),
            LocalSimHeroConfig(100649, level = 40, advance = 5),
            LocalSimHeroConfig(100023, level = 40, advance = 5),
        )),
        repeat = 1,
        seed = seed,
    )
}
