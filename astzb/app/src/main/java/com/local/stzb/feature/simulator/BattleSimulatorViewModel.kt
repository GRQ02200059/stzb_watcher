package com.local.stzb.feature.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.LocalSimHeroConfig
import com.example.myapplication.LocalSimTeamConfig
import com.example.myapplication.LocalSimulationConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BattleSimulatorViewModel(
    private val engine: BattleSimulatorEngine = LocalBattleSimulatorEngine,
    private val io: CoroutineDispatcher = Dispatchers.Default,
    private val seed: () -> Int = { System.currentTimeMillis().toInt() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(BattleSimulatorUiState())
    val state: StateFlow<BattleSimulatorUiState> = mutableState.asStateFlow()
    private var nextReportId = 1L

    init {
        viewModelScope.launch {
            runCatching {
                withContext(io) { Triple(engine.defaultConfig(), engine.heroes(), engine.skills()) }
            }.onSuccess { (config, heroes, skills) ->
                mutableState.value = BattleSimulatorUiState(
                    loading = false,
                    config = config.normalize(),
                    heroOptions = heroes,
                    skillOptions = skills,
                )
            }.onFailure { error ->
                mutableState.value = BattleSimulatorUiState(
                    loading = false,
                    error = "模拟器资源加载失败：${error.message ?: "未知错误"}",
                )
            }
        }
    }

    fun onIntent(intent: BattleSimulatorIntent) {
        when (intent) {
            is BattleSimulatorIntent.SetMorale -> updateConfig { config ->
                config.updateTeam(intent.camp) { it.copy(morale = intent.value.coerceIn(0, 100)) }
            }
            is BattleSimulatorIntent.SetLevel -> updateConfig { config ->
                config.updateTeam(intent.camp) { team ->
                    team.updateHero(intent.position) { it.copy(level = intent.value.coerceIn(1, 50)) }
                }
            }
            is BattleSimulatorIntent.SetAdvance -> updateConfig { config ->
                config.updateTeam(intent.camp) { team ->
                    team.updateHero(intent.position) { it.copy(advance = intent.value.coerceIn(0, 5)) }
                }
            }
            is BattleSimulatorIntent.OpenHeroPicker -> mutableState.value = mutableState.value.copy(
                picker = SimulatorPicker.Hero(intent.camp, intent.position), error = null,
            )
            is BattleSimulatorIntent.OpenSkillPicker -> mutableState.value = mutableState.value.copy(
                picker = SimulatorPicker.Skill(intent.camp, intent.position, intent.slot), error = null,
            )
            is BattleSimulatorIntent.PickerQuery -> updatePickerQuery(intent.value)
            is BattleSimulatorIntent.SelectHero -> selectHero(intent.heroId)
            is BattleSimulatorIntent.SelectSkill -> selectSkill(intent.skillId)
            BattleSimulatorIntent.ClosePicker -> mutableState.value = mutableState.value.copy(picker = null)
            is BattleSimulatorIntent.Run -> runSimulation(intent.repeat)
            is BattleSimulatorIntent.SelectTacticalView -> mutableState.value = mutableState.value.copy(
                tacticalView = intent.view,
                selectedEventIndex = null,
            )
            is BattleSimulatorIntent.SelectReportTab -> mutableState.value = mutableState.value.copy(
                reportTab = intent.tab,
                selectedEventIndex = null,
            )
            is BattleSimulatorIntent.SelectReport -> selectReport(intent.reportId)
            is BattleSimulatorIntent.SelectEvent -> mutableState.value = mutableState.value.copy(
                selectedEventIndex = intent.index,
            )
            is BattleSimulatorIntent.ApplyResearchLineup -> applyResearchLineup(intent.camp, intent.heroIds)
            BattleSimulatorIntent.DismissError -> mutableState.value = mutableState.value.copy(error = null)
        }
    }

    private fun updateConfig(transform: (LocalSimulationConfig) -> LocalSimulationConfig) {
        val current = mutableState.value.config ?: return
        mutableState.value = mutableState.value.copy(config = transform(current), result = null, error = null)
    }

    private fun updatePickerQuery(value: String) {
        val picker = mutableState.value.picker ?: return
        mutableState.value = mutableState.value.copy(
            picker = when (picker) {
                is SimulatorPicker.Hero -> picker.copy(query = value)
                is SimulatorPicker.Skill -> picker.copy(query = value)
            },
        )
    }

    private fun selectHero(heroId: Long) {
        val picker = mutableState.value.picker as? SimulatorPicker.Hero ?: return
        val config = mutableState.value.config ?: return
        val team = config.team(picker.camp)
        if (team.heroes.withIndex().any { (index, hero) -> index != picker.position && hero.heroId == heroId }) {
            mutableState.value = mutableState.value.copy(error = "同一队伍不能重复选择武将")
            return
        }
        updateConfig { current ->
            current.updateTeam(picker.camp) { currentTeam ->
                currentTeam.updateHero(picker.position) { hero ->
                    hero.copy(heroId = heroId, equipSkillIds = emptyList())
                }
            }
        }
        mutableState.value = mutableState.value.copy(picker = null)
    }

    private fun selectSkill(skillId: Long?) {
        val picker = mutableState.value.picker as? SimulatorPicker.Skill ?: return
        val config = mutableState.value.config ?: return
        val hero = config.team(picker.camp).heroes.getOrNull(picker.position) ?: return
        val currentAtSlot = hero.equipSkillIds.getOrNull(picker.slot)
        if (skillId != null && hero.equipSkillIds.any { it == skillId } && currentAtSlot != skillId) {
            mutableState.value = mutableState.value.copy(error = "同一武将不能重复装备战法")
            return
        }
        updateConfig { current ->
            current.updateTeam(picker.camp) { team ->
                team.updateHero(picker.position) { it.copy(equipSkillIds = it.equipSkillIds.withSkill(picker.slot, skillId)) }
            }
        }
        mutableState.value = mutableState.value.copy(picker = null)
    }

    private fun runSimulation(repeat: Int) {
        if (mutableState.value.running) return
        if (repeat !in SUPPORTED_REPEATS) {
            mutableState.value = mutableState.value.copy(error = "仅支持单次、100 次或 1000 次模拟")
            return
        }
        val config = mutableState.value.config ?: return
        val validationError = config.validationError()
        if (validationError != null) {
            mutableState.value = mutableState.value.copy(error = validationError)
            return
        }
        val runConfig = config.copy(repeat = repeat, seed = seed())
        mutableState.value = mutableState.value.copy(running = true, error = null)
        viewModelScope.launch {
            runCatching { withContext(io) { engine.simulate(runConfig) } }
                .onSuccess { result ->
                    val report = if (repeat == 1) TacticalSimulationReport(nextReportId++, result.firstRun) else null
                    val current = mutableState.value
                    mutableState.value = current.copy(
                        running = false,
                        result = result,
                        reports = if (report == null) current.reports else listOf(report) + current.reports,
                        selectedReportId = report?.id ?: current.selectedReportId,
                        selectedEventIndex = null,
                        tacticalView = if (report == null) current.tacticalView else TacticalSimulatorView.DETAIL,
                    )
                }
                .onFailure { error -> mutableState.value = mutableState.value.copy(
                    running = false,
                    error = "模拟失败：${error.message ?: "未知错误"}",
                ) }
        }
    }

    private fun selectReport(reportId: Long) {
        if (mutableState.value.reports.none { it.id == reportId }) return
        mutableState.value = mutableState.value.copy(
            selectedReportId = reportId,
            selectedEventIndex = null,
            tacticalView = TacticalSimulatorView.DETAIL,
        )
    }

    private fun applyResearchLineup(camp: SimulatorCamp, heroIds: List<Long>) {
        val ids = heroIds.filter { it > 0 }
        if (ids.size != 3 || ids.distinct().size != 3) {
            mutableState.value = mutableState.value.copy(error = "研究阵容需要三名不重复武将")
            return
        }
        val available = mutableState.value.heroOptions.map { it.id }.toSet()
        if (!available.containsAll(ids)) {
            mutableState.value = mutableState.value.copy(error = "研究阵容包含模拟器未收录的武将")
            return
        }
        updateConfig { config ->
            config.updateTeam(camp) { team ->
                team.copy(heroes = ids.map { id -> LocalSimHeroConfig(heroId = id, level = 40, advance = 5) })
            }
        }
        mutableState.value = mutableState.value.copy(error = null, picker = null, result = null)
    }

    private companion object {
        val SUPPORTED_REPEATS = setOf(1, 100, 1000)
    }
}

private fun LocalSimulationConfig.normalize() = copy(
    blue = blue.normalize(),
    red = red.normalize(),
)

private fun LocalSimTeamConfig.normalize() = copy(
    morale = morale.coerceIn(0, 100),
    heroes = heroes.take(3).map { it.copy(
        level = it.level.coerceIn(1, 50),
        advance = it.advance.coerceIn(0, 5),
        equipSkillIds = it.equipSkillIds.filter { id -> id > 0 }.distinct().take(3),
    ) },
)

private fun LocalSimulationConfig.team(camp: SimulatorCamp): LocalSimTeamConfig =
    if (camp == SimulatorCamp.BLUE) blue else red

private fun LocalSimulationConfig.updateTeam(
    camp: SimulatorCamp,
    transform: (LocalSimTeamConfig) -> LocalSimTeamConfig,
): LocalSimulationConfig = if (camp == SimulatorCamp.BLUE) copy(blue = transform(blue)) else copy(red = transform(red))

private fun LocalSimTeamConfig.updateHero(
    position: Int,
    transform: (LocalSimHeroConfig) -> LocalSimHeroConfig,
): LocalSimTeamConfig = copy(heroes = heroes.mapIndexed { index, hero -> if (index == position) transform(hero) else hero })

private fun List<Long>.withSkill(slot: Int, skillId: Long?): List<Long> {
    if (slot !in 0..2) return this
    val slots = take(3).map<Long, Long?> { it }.toMutableList()
    while (slots.size < 3) slots += null
    slots[slot] = skillId?.takeIf { it > 0 }
    return slots.filterNotNull()
}

private fun LocalSimulationConfig.validationError(): String? = when {
    blue.heroes.size != 3 || blue.heroes.any { it.heroId <= 0 } -> "攻方需要选择三名武将"
    red.heroes.size != 3 || red.heroes.any { it.heroId <= 0 } -> "守方需要选择三名武将"
    blue.heroes.map { it.heroId }.distinct().size != 3 -> "攻方队伍存在重复武将"
    red.heroes.map { it.heroId }.distinct().size != 3 -> "守方队伍存在重复武将"
    else -> null
}
