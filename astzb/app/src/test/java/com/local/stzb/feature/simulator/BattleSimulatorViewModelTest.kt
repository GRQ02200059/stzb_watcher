package com.local.stzb.feature.simulator

import com.example.myapplication.LocalSimHeroConfig
import com.example.myapplication.LocalSimHeroOption
import com.example.myapplication.LocalSimSkillOption
import com.example.myapplication.LocalSimTeamConfig
import com.example.myapplication.LocalSimulationConfig
import com.example.myapplication.LocalSimulationRun
import com.example.myapplication.LocalSimulationSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BattleSimulatorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loadsDefaultConfigurationAndResources() = runTest(dispatcher) {
        val engine = FakeEngine()
        val viewModel = BattleSimulatorViewModel(engine, dispatcher) { 123 }
        advanceUntilIdle()

        assertEquals(engine.defaultConfig, viewModel.state.value.config)
        assertEquals(engine.heroOptions, viewModel.state.value.heroOptions)
        assertEquals(engine.skillOptions, viewModel.state.value.skillOptions)
        assertFalse(viewModel.state.value.loading)
    }

    @Test fun clampsNumericFieldsAndUpdatesOnlyRequestedHero() = runTest(dispatcher) {
        val vm = BattleSimulatorViewModel(FakeEngine(), dispatcher) { 123 }
        advanceUntilIdle()

        vm.onIntent(BattleSimulatorIntent.SetMorale(SimulatorCamp.BLUE, 999))
        vm.onIntent(BattleSimulatorIntent.SetLevel(SimulatorCamp.BLUE, 1, 0))
        vm.onIntent(BattleSimulatorIntent.SetAdvance(SimulatorCamp.BLUE, 1, 9))

        val config = vm.state.value.config!!
        assertEquals(100, config.blue.morale)
        assertEquals(1, config.blue.heroes[1].level)
        assertEquals(5, config.blue.heroes[1].advance)
        assertEquals(40, config.blue.heroes[0].level)
    }

    @Test fun selectingHeroRejectsDuplicateAndClearsPreviousSkills() = runTest(dispatcher) {
        val vm = BattleSimulatorViewModel(FakeEngine(), dispatcher) { 123 }
        advanceUntilIdle()
        vm.onIntent(BattleSimulatorIntent.OpenHeroPicker(SimulatorCamp.BLUE, 1))

        vm.onIntent(BattleSimulatorIntent.SelectHero(1L))
        assertEquals("同一队伍不能重复选择武将", vm.state.value.error)

        vm.onIntent(BattleSimulatorIntent.SelectHero(7L))
        val hero = vm.state.value.config!!.blue.heroes[1]
        assertEquals(7L, hero.heroId)
        assertEquals(emptyList<Long>(), hero.equipSkillIds)
        assertEquals(40, hero.level)
        assertEquals(5, hero.advance)
    }

    @Test fun maintainsThreeSkillSlotsAndRejectsDuplicates() = runTest(dispatcher) {
        val vm = BattleSimulatorViewModel(FakeEngine(), dispatcher) { 123 }
        advanceUntilIdle()

        vm.onIntent(BattleSimulatorIntent.OpenSkillPicker(SimulatorCamp.BLUE, 0, 1))
        vm.onIntent(BattleSimulatorIntent.SelectSkill(102L))
        assertEquals(listOf(101L, 102L), vm.state.value.config!!.blue.heroes[0].equipSkillIds)

        vm.onIntent(BattleSimulatorIntent.OpenSkillPicker(SimulatorCamp.BLUE, 0, 2))
        vm.onIntent(BattleSimulatorIntent.SelectSkill(102L))
        assertEquals("同一武将不能重复装备战法", vm.state.value.error)

        vm.onIntent(BattleSimulatorIntent.SelectSkill(103L))
        vm.onIntent(BattleSimulatorIntent.OpenSkillPicker(SimulatorCamp.BLUE, 0, 1))
        vm.onIntent(BattleSimulatorIntent.SelectSkill(null))
        assertEquals(listOf(101L, 103L), vm.state.value.config!!.blue.heroes[0].equipSkillIds)
    }

    @Test fun runsSupportedRepeatCountAndPublishesSummary() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = BattleSimulatorViewModel(engine, dispatcher) { 456 }
        advanceUntilIdle()

        vm.onIntent(BattleSimulatorIntent.Run(100))
        assertTrue(vm.state.value.running)
        vm.onIntent(BattleSimulatorIntent.Run(1000))
        advanceUntilIdle()

        assertEquals(1, engine.calls)
        assertEquals(100, engine.lastConfig!!.repeat)
        assertEquals(456, engine.lastConfig!!.seed)
        assertEquals(engine.summary.copy(repeat = 100), vm.state.value.result)
        assertFalse(vm.state.value.running)
    }

    @Test fun rejectsUnsupportedRepeatAndRecoversFromFailureWithoutLosingConfig() = runTest(dispatcher) {
        val engine = FakeEngine().apply { failure = IllegalStateException("内核错误") }
        val vm = BattleSimulatorViewModel(engine, dispatcher) { 1 }
        advanceUntilIdle()

        vm.onIntent(BattleSimulatorIntent.Run(2))
        assertEquals("仅支持单次、100 次或 1000 次模拟", vm.state.value.error)
        assertEquals(0, engine.calls)

        val before = vm.state.value.config
        vm.onIntent(BattleSimulatorIntent.Run(1))
        advanceUntilIdle()
        assertEquals("模拟失败：内核错误", vm.state.value.error)
        assertEquals(before, vm.state.value.config)
        assertNotNull(vm.state.value.config)
        assertFalse(vm.state.value.running)
    }

    @Test fun researchLineupPrefillsThreeHeroesAndRejectsInvalidInput() = runTest(dispatcher) {
        val vm = BattleSimulatorViewModel(FakeEngine(), dispatcher) { 1 }
        advanceUntilIdle()
        vm.onIntent(BattleSimulatorIntent.Run(1))
        advanceUntilIdle()
        assertNotNull(vm.state.value.result)

        vm.onIntent(BattleSimulatorIntent.ApplyResearchLineup(SimulatorCamp.BLUE, listOf(7L, 8L, 9L)))

        assertEquals(listOf(7L, 8L, 9L), vm.state.value.config!!.blue.heroes.map { it.heroId })
        assertEquals(null, vm.state.value.result)
        assertEquals(listOf(40, 40, 40), vm.state.value.config!!.blue.heroes.map { it.level })

        vm.onIntent(BattleSimulatorIntent.ApplyResearchLineup(SimulatorCamp.RED, listOf(7L, 7L, 9L)))
        assertEquals("研究阵容需要三名不重复武将", vm.state.value.error)
    }

    @Test fun singleRunIsSavedAsSelectableTacticalReportButAggregateRunIsNot() = runTest(dispatcher) {
        val vm = BattleSimulatorViewModel(FakeEngine(), dispatcher) { 99 }
        advanceUntilIdle()

        vm.onIntent(BattleSimulatorIntent.Run(1))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.reports.size)
        assertEquals(vm.state.value.reports.single().id, vm.state.value.selectedReportId)
        vm.onIntent(BattleSimulatorIntent.SelectReport(vm.state.value.reports.single().id))
        vm.onIntent(BattleSimulatorIntent.SelectEvent(0))
        assertEquals(0, vm.state.value.selectedEventIndex)

        vm.onIntent(BattleSimulatorIntent.Run(100))
        advanceUntilIdle()
        assertEquals(1, vm.state.value.reports.size)
    }

    private class FakeEngine : BattleSimulatorEngine {
        val defaultConfig = LocalSimulationConfig(
            blue = LocalSimTeamConfig(90, listOf(hero(1), hero(2), hero(3))),
            red = LocalSimTeamConfig(80, listOf(hero(4), hero(5), hero(6))),
        )
        val heroOptions = (1L..9L).map { LocalSimHeroOption(it, "武将$it", "群", "步", it) }
        val skillOptions = (101L..105L).map { LocalSimSkillOption(it, "战法$it", "主动", true, 100.0, 5, "") }
        val summary = LocalSimulationSummary(
            repeat = 1,
            blueWins = 1,
            redWins = 0,
            draws = 0,
            blueWinRate = 100.0,
            redWinRate = 0.0,
            drawRate = 0.0,
            firstRun = LocalSimulationRun("攻方", 1000, 0, listOf("战斗开始")),
        )
        var lastConfig: LocalSimulationConfig? = null
        var calls = 0
        var failure: Throwable? = null

        override fun defaultConfig() = defaultConfig
        override fun heroes() = heroOptions
        override fun skills() = skillOptions
        override fun simulate(config: LocalSimulationConfig): LocalSimulationSummary {
            calls += 1
            lastConfig = config
            failure?.let { throw it }
            return summary.copy(repeat = config.repeat)
        }
        override fun heroName(id: Long) = "武将$id"
        override fun heroIconId(id: Long) = id
        override fun skillName(id: Long) = "战法$id"

        companion object {
            private fun hero(id: Long) = LocalSimHeroConfig(id, level = 40, advance = 5, equipSkillIds = listOf(101L))
        }
    }
}
