package com.local.stzb.feature.simulator

import com.example.myapplication.LocalSimHeroOption
import com.example.myapplication.LocalSimSkillOption
import com.example.myapplication.LocalSimulationConfig
import com.example.myapplication.LocalSimulationRun
import com.example.myapplication.LocalSimulationSummary

enum class SimulatorCamp { BLUE, RED }

enum class TacticalSimulatorView { DUEL, REPORTS, DETAIL }

enum class TacticalReportTab(val label: String) {
    ROUND("回合"),
    STATUS("状态"),
    TRIGGER("触发"),
}

data class TacticalSimulationReport(
    val id: Long,
    val run: LocalSimulationRun,
)

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
    val reports: List<TacticalSimulationReport> = emptyList(),
    val selectedReportId: Long? = null,
    val selectedEventIndex: Int? = null,
    val tacticalView: TacticalSimulatorView = TacticalSimulatorView.DUEL,
    val reportTab: TacticalReportTab = TacticalReportTab.ROUND,
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
    data class SelectTacticalView(val view: TacticalSimulatorView) : BattleSimulatorIntent
    data class SelectReportTab(val tab: TacticalReportTab) : BattleSimulatorIntent
    data class SelectReport(val reportId: Long) : BattleSimulatorIntent
    data class SelectEvent(val index: Int?) : BattleSimulatorIntent
    data class ApplyResearchLineup(val camp: SimulatorCamp, val heroIds: List<Long>) : BattleSimulatorIntent
    data object DismissError : BattleSimulatorIntent
}
