package com.local.stzb.data.livearmy

import com.example.myapplication.Local13A2HeroLineup
import com.example.myapplication.Local13A2Lineup
import com.example.myapplication.Local13A2TeamInsight
import com.example.myapplication.Local13A2TeamStats
import com.example.myapplication.LocalTeamMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLiveArmyRepositoryTest {
    @Test
    fun projectsStateFreshnessLocationAndExactLineupEvidence() {
        val source = FakeSource(
            observedAtMs = 1_800_000_000_000L - 30_000L,
            moves = listOf(move()),
            insights = mapOf(42 to insight()),
        )
        val repository = LocalLiveArmyRepository(source) { 1_800_000_000_000L }

        val snapshot = repository.load()
        val army = snapshot.current.single()

        assertEquals("出征中", army.stateLabel)
        assertTrue(army.isMoving)
        assertEquals(LiveArmyFreshness.FRESH, army.freshness)
        assertEquals("10,15", army.currentLocation)
        assertEquals("10,20", army.targetLocation)
        assertEquals(listOf("陆逊", "周瑜", "吕蒙"), army.heroes.map { it.name })
        assertEquals(LineupEvidence.EXACT_BATTLE, army.lineupEvidence)
    }

    @Test
    fun searchMatchesTeamPlayerHeroAndWid() {
        val repository = LocalLiveArmyRepository(
            FakeSource(1_800_000_000_000L, listOf(move()), mapOf(42 to insight())),
        ) { 1_800_000_000_000L }

        listOf("42", "测试玩家", "陆逊", "100020", "10,20").forEach { query ->
            assertEquals(1, repository.load(query).current.size)
        }
        assertTrue(repository.load("不存在").current.isEmpty())
    }

    @Test
    fun oldSourceIsStaleEvenWhenArrivalIsInFuture() {
        val repository = LocalLiveArmyRepository(
            FakeSource(1_800_000_000_000L - 700_000L, listOf(move()), mapOf(42 to insight())),
        ) { 1_800_000_000_000L }

        val army = repository.load().current.single()

        assertEquals(LiveArmyFreshness.STALE, army.freshness)
        assertFalse(army.usable)
    }

    private class FakeSource(
        private val observedAtMs: Long,
        private val moves: List<LocalTeamMove>,
        private val insights: Map<Int, Local13A2TeamInsight>,
    ) : LiveArmySource {
        override fun observedAtMs(): Long = observedAtMs
        override fun moves(): List<LocalTeamMove> = moves
        override fun insight(move: LocalTeamMove): Local13A2TeamInsight = insights[move.teamId] ?: Local13A2TeamInsight.empty()
    }

    private fun move() = LocalTeamMove(
        teamId = 42, moveType = 1, subjectId = 42, ownerUid = 7, ownerName = "测试玩家", ownerUnion = "测试同盟",
        fromWid = 100010, toWid = 100020, currentWid = 100015, fromXy = "10,10", toXy = "10,20", currentXy = "10,15",
        startTime = 1_799_999_900L, arriveTime = 1_800_000_060L, speed = 100, morale = 88,
    )

    private fun insight() = Local13A2TeamInsight.empty().copy(
        stats = Local13A2TeamStats(12, 8, 1, 3, 70.8),
        lineup = Local13A2Lineup(99, "atk", "", listOf(
            Local13A2HeroLineup(1, 101, "陆逊", 50, 5, emptyList()),
            Local13A2HeroLineup(2, 102, "周瑜", 50, 5, emptyList()),
            Local13A2HeroLineup(3, 103, "吕蒙", 50, 5, emptyList()),
        )),
    )
}
