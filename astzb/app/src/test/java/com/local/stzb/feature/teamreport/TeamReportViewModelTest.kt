package com.local.stzb.feature.teamreport

import com.local.stzb.domain.rankings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun defaultsToGroupAllAndRefreshesForDimensionPeriodAndGroup() = runTest(dispatcher) {
        val requests = mutableListOf<Triple<ReportDimension, ReportPeriod, String>>()
        val repository = object : RankingRepository {
            override fun loadRankings() = RankingSnapshot(emptyList(), emptyList(), emptyList())
            override fun loadTeamReport(dimension: ReportDimension, period: ReportPeriod, group: String): TeamReportSnapshot {
                requests += Triple(dimension, period, group)
                return TeamReportSnapshot(emptyList(), listOf("一团"))
            }
        }
        val viewModel = TeamReportViewModel(repository, dispatcher)
        advanceUntilIdle()
        assertEquals(Triple(ReportDimension.GROUP, ReportPeriod.ALL, ""), requests.last())

        viewModel.setDimension(ReportDimension.PLAYER); advanceUntilIdle()
        viewModel.setPeriod(ReportPeriod.WEEK); advanceUntilIdle()
        viewModel.setGroup("一团"); advanceUntilIdle()
        assertEquals(Triple(ReportDimension.PLAYER, ReportPeriod.WEEK, "一团"), requests.last())
    }
}
