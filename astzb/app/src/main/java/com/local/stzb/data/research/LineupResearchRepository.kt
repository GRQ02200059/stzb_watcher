package com.local.stzb.data.research

import com.example.myapplication.LocalBattleSimulator
import com.example.myapplication.LocalHeroComboWinRate
import com.example.myapplication.LocalHeroUsage
import com.example.myapplication.LocalSimHeroOption
import com.example.myapplication.LocalStzbRepository

enum class EvidenceKind { CONFIG_FACT, HISTORICAL, SIMULATION }
data class ResearchEvidence(val kind: EvidenceKind, val label: String, val text: String)
data class LineupResearchRow(
    val heroNames: List<String>, val heroIds: List<Long>, val total: Int, val wins: Int, val losses: Int, val draws: Int, val winRate: Double,
    val configEvidence: ResearchEvidence, val historicalEvidence: ResearchEvidence, val simulationEvidence: ResearchEvidence,
    val canOpenSimulator: Boolean, val searchText: String,
)

interface LineupResearchSource {
    fun combos(): List<LocalHeroComboWinRate>
    fun usages(): List<LocalHeroUsage>
    fun heroes(): List<LocalSimHeroOption>
}
object AndroidLineupResearchSource : LineupResearchSource {
    override fun combos() = LocalStzbRepository.loadHeroComboWinRates(minCount = 1, limit = 200)
    override fun usages() = LocalStzbRepository.loadHeroUsage("atk", 200)
    override fun heroes() = LocalBattleSimulator.selectableHeroes(1000)
}

class LineupResearchRepository(private val source: LineupResearchSource = AndroidLineupResearchSource) {
    fun load(query: String = ""): List<LineupResearchRow> {
        val heroByName = source.heroes().groupBy(LocalSimHeroOption::name).mapValues { (_, rows) -> rows.minByOrNull(LocalSimHeroOption::id)!! }
        val usageByName = source.usages().associateBy(LocalHeroUsage::heroName)
        return source.combos().mapNotNull { combo ->
            val names = combo.combo.split('+', '/', '、').map(String::trim).filter(String::isNotBlank).take(3)
            if (names.size != 3) return@mapNotNull null
            val ids = names.mapNotNull { heroByName[it]?.id }
            val usageText = names.joinToString("；") { name -> usageByName[name]?.let { "$name 出场${it.count}" } ?: "$name 无独立样本" }
            LineupResearchRow(
                heroNames = names, heroIds = ids, total = combo.total, wins = combo.wins, losses = combo.losses, draws = combo.draws, winRate = combo.winRate,
                configEvidence = ResearchEvidence(EvidenceKind.CONFIG_FACT, "配置事实", names.mapNotNull { heroByName[it] }.joinToString(" / " ) { "${it.name}·${it.country}${it.armyType}" }.ifBlank { "静态武将资源未完整匹配" }),
                historicalEvidence = ResearchEvidence(EvidenceKind.HISTORICAL, "历史证据", "${combo.total} 场 · ${combo.wins}胜${combo.draws}平${combo.losses}负 · 胜率 ${"%.1f".format(combo.winRate)}% · $usageText"),
                simulationEvidence = ResearchEvidence(EvidenceKind.SIMULATION, "模拟验证", if (ids.size == 3) "可带入战斗模拟器验证候选对阵" else "武将资源未完整匹配，暂不能模拟"),
                canOpenSimulator = ids.size == 3 && ids.distinct().size == 3,
                searchText = (names + combo.total.toString() + "${combo.winRate}").joinToString(" "),
            )
        }.filter { query.isBlank() || it.searchText.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareByDescending<LineupResearchRow> { it.total }.thenByDescending { it.winRate })
    }
}
