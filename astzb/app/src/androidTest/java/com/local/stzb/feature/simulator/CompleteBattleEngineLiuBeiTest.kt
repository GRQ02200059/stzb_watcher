package com.local.stzb.feature.simulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.LocalSimHeroConfig
import com.example.myapplication.LocalSimTeamConfig
import com.example.myapplication.LocalSimulationConfig
import com.example.myapplication.LocalSimulationEventKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompleteBattleEngineLiuBeiTest {
    @Test fun completeEngineLoadsFromApkAndEmitsLiuBeiEmergencyRecoveryAfterDamage() {
        val recoveredRun = (0..160).asSequence()
            .map { seed -> CompleteBattleSimulatorEngine.simulate(liuBeiMatch(seed)).firstRun }
            .firstOrNull { run ->
                val recoveryIndex = run.events.indexOfFirst {
                    it.kind == LocalSimulationEventKind.RECOVERY &&
                        it.skillName == "皇裔流离" &&
                        it.amount > 0
                }
                recoveryIndex > 0 && run.events.take(recoveryIndex).any { it.kind == LocalSimulationEventKind.DAMAGE }
            }

        assertTrue("APK 内的完整引擎应记录刘备受伤后的急救恢复", recoveredRun != null)
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
