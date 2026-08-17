package com.local.stzb.feature.rankings

import com.local.stzb.domain.rankings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RankingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loadsRankingsAndReloadsReportControls() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = RankingsViewModel(repository, dispatcher)
        assertTrue(viewModel.state.value.loading)
        runCurrent()
        assertEquals("战功", viewModel.state.value.rankings!!.battle.single().name)
        viewModel.setPage(RankingPage.TEAM_REPORT)
        viewModel.setDimension(ReportDimension.PLAYER)
        viewModel.setPeriod(ReportPeriod.WEEK)
        viewModel.setGroup("一团")
        runCurrent()
        assertFalse(viewModel.state.value.loading)
        assertEquals(ReportDimension.PLAYER, repository.dimension)
        assertEquals(ReportPeriod.WEEK, repository.period)
        assertEquals("一团", repository.group)
        assertEquals("成员", viewModel.state.value.report!!.rows.single().name)
    }

    @Test fun reportsRepositoryFailure() = runTest(dispatcher) {
        val viewModel = RankingsViewModel(FakeRepository(failure = true), dispatcher)
        runCurrent()
        assertEquals("读取失败", viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
    }

    @Test fun staleRequestCannotOverwriteLatestSelection() = runTest(dispatcher) {
        val firstGate = java.util.concurrent.CountDownLatch(1)
        val io = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val repository = object : RankingRepository {
            override fun loadRankings() = RankingSnapshot(emptyList(), emptyList(), emptyList())
            override fun loadTeamReport(dimension: ReportDimension, period: ReportPeriod, group: String): TeamReportSnapshot {
                if (period == ReportPeriod.ALL) firstGate.await()
                return TeamReportSnapshot(listOf(TeamReportRow(1, period.label, "", 1, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0, 0.0)), emptyList())
            }
        }
        try {
            val viewModel = RankingsViewModel(repository, io)
            runCurrent()
            viewModel.setPage(RankingPage.TEAM_REPORT)
            runCurrent()
            viewModel.setPeriod(ReportPeriod.WEEK)
            repeat(100) { if (viewModel.state.value.report == null) { Thread.sleep(5); runCurrent() } }
            assertEquals("本周", viewModel.state.value.report!!.rows.single().name)
            firstGate.countDown()
            repeat(20) { Thread.sleep(5); runCurrent() }
            assertEquals("本周", viewModel.state.value.report!!.rows.single().name)
        } finally {
            firstGate.countDown()
            io.close()
        }
    }

    private class FakeRepository(private val failure: Boolean = false) : RankingRepository {
        var dimension = ReportDimension.GROUP; var period = ReportPeriod.ALL; var group = ""
        override fun loadRankings(): RankingSnapshot {
            if (failure) error("读取失败")
            return RankingSnapshot(listOf(RankingRow(1, "战功", value = 1)), emptyList(), emptyList())
        }
        override fun loadTeamReport(dimension: ReportDimension, period: ReportPeriod, group: String): TeamReportSnapshot {
            this.dimension = dimension; this.period = period; this.group = group
            return TeamReportSnapshot(listOf(TeamReportRow(1, "成员", "一团", 1, 1, 1, 0, 0, 0, 0, 2, 2.0, 3.0, 3, 100.0)), listOf("一团"))
        }
    }
}
