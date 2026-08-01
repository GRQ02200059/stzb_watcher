package com.local.stzb.feature.simulator

import com.example.myapplication.LocalSimHeroOption
import com.example.myapplication.LocalSimSkillOption
import com.example.myapplication.LocalSimulationConfig
import com.example.myapplication.LocalSimulationSummary

enum class SimulatorCamp { BLUE, RED }

sealed interface SimulatorPicker {
    val query: String

    data class Hero(
        val camp: SimulatorCamp,
        val position: Int,
        override val query: String = "",
    ) : SimulatorPicker

    data class Skill(
        val camp: SimulatorCamp,
        val position: Int,
        val slot: Int,
        override val query: String = "",
    ) : SimulatorPicker
}

data class BattleSimulatorUiState(
    val loading: Boolean = true,
    val running: Boolean = false,
    val config: LocalSimulationConfig? = null,
    val heroOptions: List<LocalSimHeroOption> = emptyList(),
    val skillOptions: List<LocalSimSkillOption> = emptyList(),
    val result: LocalSimulationSummary? = null,
    val picker: SimulatorPicker? = null,
    val error: String? = null,
)

sealed interface BattleSimulatorIntent {
    data class SetMorale(val camp: SimulatorCamp, val value: Int) : BattleSimulatorIntent
    data class SetLevel(val camp: SimulatorCamp, val position: Int, val value: Int) : BattleSimulatorIntent
    data class SetAdvance(val camp: SimulatorCamp, val position: Int, val value: Int) : BattleSimulatorIntent
    data class OpenHeroPicker(val camp: SimulatorCamp, val position: Int) : BattleSimulatorIntent
    data class OpenSkillPicker(val camp: SimulatorCamp, val position: Int, val slot: Int) : BattleSimulatorIntent
    data class PickerQuery(val value: String) : BattleSimulatorIntent
    data class SelectHero(val heroId: Long) : BattleSimulatorIntent
    data class SelectSkill(val skillId: Long?) : BattleSimulatorIntent
    data object ClosePicker : BattleSimulatorIntent
    data class Run(val repeat: Int) : BattleSimulatorIntent
    data object DismissError : BattleSimulatorIntent
}
