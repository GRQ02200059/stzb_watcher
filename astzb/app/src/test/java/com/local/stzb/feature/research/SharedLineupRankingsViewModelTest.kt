package com.local.stzb.feature.research

import com.local.stzb.data.research.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedLineupRankingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loadsChangesTabsSearchesAndRetries() = runTest(dispatcher) {
        val source = FakeSource()
        val vm = SharedLineupRankingsViewModel(source, dispatcher)
        runCurrent()
        assertEquals(80, vm.state.value.snapshot?.summary?.eligibleBattles)
        vm.setTab(SharedRankingTab.COUNTERS)
        vm.setQuery("武将1")
        assertEquals(SharedRankingTab.COUNTERS, vm.state.value.tab)
        assertEquals("武将1", vm.state.value.query)
        source.fail = true
        vm.refresh(); runCurrent()
        assertEquals("共享榜暂不可用", vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    private class FakeSource : SharedLineupRankingsSource {
        var fail = false
        override suspend fun load(limit: Int): SharedLineupSnapshot {
            if (fail) error("offline")
            val row = SharedLineupRow("1+2+3", listOf(1,2,3), 3,2,0,1,60.0,50.0,3.0,"低",1)
            return SharedLineupSnapshot(SharedLineupSummary(80,63,76,1,"now"), listOf(row), listOf(row), listOf(SharedCounterRow(row,listOf(4,5,6),"4+5+6")))
        }
    }
}
