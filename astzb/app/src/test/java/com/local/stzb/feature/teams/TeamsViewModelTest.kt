package com.local.stzb.feature.teams

import com.local.stzb.domain.teams.PlayerTeam
import com.local.stzb.domain.teams.TeamHero
import com.local.stzb.domain.teams.TeamsRepository
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
class TeamsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loadsSortedTeamsAndFiltersAcrossAllTextAndSideFields() = runTest(dispatcher) {
        val teams = listOf(
            team("乙", "二盟", "def", "吕蒙", "反计之策", 9, 8, 88.8),
            team("甲", "一盟", "atk", "陆逊", "深谋远虑", 12, 6, 50.0),
            team("丙", "三盟", "atk", "周瑜", "神兵天降", 12, 8, 66.7),
        )
        val viewModel = TeamsViewModel(object : TeamsRepository { override fun loadTeams() = teams }, dispatcher)
        advanceUntilIdle()
        assertEquals(listOf("丙", "甲", "乙"), viewModel.state.value.visibleTeams.map { it.player })

        listOf("乙", "二盟", "吕蒙", "反计").forEach { query ->
            viewModel.onIntent(TeamsIntent.QueryChanged(query))
            assertEquals(listOf("乙"), viewModel.state.value.visibleTeams.map { it.player })
        }
        viewModel.onIntent(TeamsIntent.QueryChanged(""))
        viewModel.onIntent(TeamsIntent.SideChanged(TeamSide.ATTACK))
        assertEquals(listOf("丙", "甲"), viewModel.state.value.visibleTeams.map { it.player })
    }

    private fun team(player: String, union: String, side: String, hero: String, skill: String, battles: Int, wins: Int, rate: Double) =
        PlayerTeam(player, union, side, listOf(TeamHero(1, 1, hero)), listOf(skill), battles, wins, rate)
}
