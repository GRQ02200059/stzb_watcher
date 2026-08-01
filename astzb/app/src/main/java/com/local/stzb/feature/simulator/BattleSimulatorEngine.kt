package com.local.stzb.feature.simulator

import com.example.myapplication.LocalBattleSimulator
import com.example.myapplication.LocalSimHeroOption
import com.example.myapplication.LocalSimSkillOption
import com.example.myapplication.LocalSimulationConfig
import com.example.myapplication.LocalSimulationSummary

interface BattleSimulatorEngine {
    fun defaultConfig(): LocalSimulationConfig
    fun heroes(): List<LocalSimHeroOption>
    fun skills(): List<LocalSimSkillOption>
    fun simulate(config: LocalSimulationConfig): LocalSimulationSummary
    fun heroName(id: Long): String
    fun heroIconId(id: Long): Long
    fun skillName(id: Long): String
}

object LocalBattleSimulatorEngine : BattleSimulatorEngine {
    override fun defaultConfig() = LocalBattleSimulator.defaultWebConfig()
    override fun heroes() = LocalBattleSimulator.selectableHeroes()
    override fun skills() = LocalBattleSimulator.selectableSkills()
    override fun simulate(config: LocalSimulationConfig) = LocalBattleSimulator.simulate(config)
    override fun heroName(id: Long) = LocalBattleSimulator.heroName(id)
    override fun heroIconId(id: Long) = LocalBattleSimulator.heroIconId(id)
    override fun skillName(id: Long) = LocalBattleSimulator.skillName(id)
}
