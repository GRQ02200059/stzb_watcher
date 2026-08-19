package com.local.stzb.data.research

import com.example.myapplication.LocalHeroComboWinRate
import com.example.myapplication.LocalHeroUsage
import com.example.myapplication.LocalSimHeroOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineupResearchRepositoryTest {
    @Test
    fun separatesConfigHistoryAndSimulationEvidenceAndResolvesHeroIds() {
        val repository = LineupResearchRepository(FakeSource())

        val rows = repository.load("陆逊")
        val row = rows.single()

        assertEquals(listOf(101L, 102L, 103L), row.heroIds)
        assertEquals(listOf("陆逊", "周瑜", "吕蒙"), row.heroNames)
        assertEquals(EvidenceKind.CONFIG_FACT, row.configEvidence.kind)
        assertEquals(EvidenceKind.HISTORICAL, row.historicalEvidence.kind)
        assertEquals(EvidenceKind.SIMULATION, row.simulationEvidence.kind)
        assertTrue(row.canOpenSimulator)
        assertTrue(row.historicalEvidence.text.contains("12 场"))
        assertTrue(row.historicalEvidence.text.contains("70.8%"))
    }

    @Test fun unresolvedHeroDisablesSimulatorHandoff() {
        val repository = LineupResearchRepository(FakeSource(resolveThird = false))
        assertFalse(repository.load().single().canOpenSimulator)
    }

    private class FakeSource(private val resolveThird: Boolean = true) : LineupResearchSource {
        override fun combos() = listOf(LocalHeroComboWinRate("陆逊+周瑜+吕蒙", 12, 8, 3, 1, 70.8))
        override fun usages() = listOf(LocalHeroUsage("陆逊", 20, 12, 2, 50, 65.0))
        override fun heroes() = buildList {
            add(LocalSimHeroOption(101, "陆逊", "吴", "弓", 101))
            add(LocalSimHeroOption(102, "周瑜", "吴", "弓", 102))
            if (resolveThird) add(LocalSimHeroOption(103, "吕蒙", "吴", "步", 103))
        }
    }
}
