package com.local.stzb.data.battlefield

import com.example.myapplication.LocalBattleField
import com.example.myapplication.LocalFullBattle
import com.example.myapplication.LocalTeamMove
import com.local.stzb.domain.battlefield.EventCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LegacyBattlefieldRepositoryTest {
    @Test
    fun refreshReadsOneConsistentSourceSnapshotAndPublishesMappedEvents() = runBlocking {
        val source = FakeBattlefieldSource().apply {
            moves = listOf(move(teamId = 42, arriveTime = 1_700_000_600L))
        }
        val repository = LegacyBattlefieldRepository(source, Dispatchers.Unconfined) { 1_700_000_500L }

        repository.refresh()
        val snapshot = repository.observeSnapshot().first()

        assertEquals(listOf("march:42:1700000600"), snapshot.events.map { it.id })
        assertEquals(1, snapshot.metrics.activeMarches)
        assertEquals(1, snapshot.metrics.arrivingSoon)
        assertTrue(snapshot.capture.running)
        assertEquals(1, source.reads.get())
    }

    @Test
    fun pausedRefreshBuffersNewEventsAndResumePublishesThemOnce() = runBlocking {
        val source = FakeBattlefieldSource().apply {
            moves = listOf(move(teamId = 1, arriveTime = 101))
        }
        val repository = LegacyBattlefieldRepository(source, Dispatchers.Unconfined) { 100 }
        repository.refresh()
        repository.setPaused(true)
        source.moves = source.moves + move(teamId = 2, arriveTime = 102)

        repository.refresh()
        val paused = repository.observeSnapshot().first()

        assertTrue(paused.paused)
        assertEquals(listOf("march:1:101"), paused.events.map { it.id })
        assertEquals(1, paused.bufferedEventCount)

        repository.setPaused(false)
        repository.setPaused(false)
        val resumed = repository.observeSnapshot().first()

        assertFalse(resumed.paused)
        assertEquals(listOf("march:2:102", "march:1:101"), resumed.events.map { it.id })
        assertEquals(0, resumed.bufferedEventCount)
        assertEquals(2, resumed.events.map { it.id }.distinct().size)
    }

    @Test
    fun filterOnlyChangesVisibilityAndCanBeRestored() = runBlocking {
        val source = FakeBattlefieldSource().apply {
            moves = listOf(move(teamId = 1, arriveTime = 101))
            sieges = listOf(siege(wid = 100020, sourceId = "siege-1"))
        }
        val repository = LegacyBattlefieldRepository(source, Dispatchers.Unconfined) { 100 }
        repository.refresh()

        repository.setFilter(setOf(EventCategory.SIEGE))
        assertEquals(listOf(EventCategory.SIEGE), repository.observeSnapshot().first().events.map { it.category })

        repository.setFilter(setOf(EventCategory.MARCH, EventCategory.SIEGE))
        assertEquals(2, repository.observeSnapshot().first().events.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyFilterIsRejected() {
        LegacyBattlefieldRepository(FakeBattlefieldSource(), Dispatchers.Unconfined)
            .setFilter(emptySet())
    }

    @Test
    fun visibleAndBufferedCollectionsAreIndependentlyCappedAtTwoHundredNewestEvents() = runBlocking {
        val source = FakeBattlefieldSource().apply {
            moves = (1..250).map { move(teamId = it, arriveTime = it.toLong()) }
        }
        val repository = LegacyBattlefieldRepository(source, Dispatchers.Unconfined) { 0 }

        repository.refresh()
        var snapshot = repository.observeSnapshot().first()
        assertEquals(200, snapshot.events.size)
        assertEquals(250L, snapshot.events.first().occurredAt)
        assertEquals(51L, snapshot.events.last().occurredAt)

        repository.setPaused(true)
        source.moves = (1..500).map { move(teamId = it, arriveTime = it.toLong()) }
        repository.refresh()
        snapshot = repository.observeSnapshot().first()
        assertEquals(200, snapshot.events.size)
        assertEquals(200, snapshot.bufferedEventCount)

        repository.setPaused(false)
        snapshot = repository.observeSnapshot().first()
        assertEquals(200, snapshot.events.size)
        assertEquals(500L, snapshot.events.first().occurredAt)
        assertEquals(301L, snapshot.events.last().occurredAt)
    }

    @Test
    fun pausingDoesNotBufferUnchangedHistoryTrimmedFromTheVisibleWindow() = runBlocking {
        val source = FakeBattlefieldSource().apply {
            moves = (1..250).map { move(teamId = it, arriveTime = it.toLong()) }
        }
        val repository = LegacyBattlefieldRepository(source, Dispatchers.Unconfined) { 0 }
        repository.refresh()

        repository.setPaused(true)
        repository.refresh()
        val snapshot = repository.observeSnapshot().first()

        assertEquals(200, snapshot.events.size)
        assertEquals(0, snapshot.bufferedEventCount)
    }

    @Test
    fun pausingDoesNotBufferUnchangedHistoryBeyondSeenLimit() = runBlocking {
        val source = FakeBattlefieldSource().apply {
            moves = (1..2_501).map { move(teamId = it, arriveTime = it.toLong()) }
        }
        val repository = LegacyBattlefieldRepository(source, Dispatchers.Unconfined) { 0 }
        repository.refresh()

        repository.setPaused(true)
        repository.refresh()
        val snapshot = repository.observeSnapshot().first()

        assertEquals(200, snapshot.events.size)
        assertEquals(0, snapshot.bufferedEventCount)
    }

    @Test
    fun retainedEventPayloadUpdatesImmediatelyWhenRunning() = runBlocking {
        val source = FakeBattlefieldSource().apply {
            sieges = listOf(siege(wid = 100020, sourceId = "transport-1", nearbyCount = 2))
        }
        val repository = LegacyBattlefieldRepository(source, Dispatchers.Unconfined)
        repository.refresh()
        source.sieges = listOf(siege(wid = 100020, sourceId = "transport-2", nearbyCount = 5))

        repository.refresh()
        val snapshot = repository.observeSnapshot().first()

        assertEquals(1, snapshot.events.size)
        assertEquals("附近 5 人", snapshot.events.single().summary)
    }

    @Test
    fun retainedEventPayloadStaysVisibleAndBuffersLatestUpdateWhilePaused() = runBlocking {
        val source = FakeBattlefieldSource().apply {
            sieges = listOf(siege(wid = 100020, sourceId = "transport-1", nearbyCount = 2))
        }
        val repository = LegacyBattlefieldRepository(source, Dispatchers.Unconfined)
        repository.refresh()
        repository.setPaused(true)
        source.sieges = listOf(siege(wid = 100020, sourceId = "transport-2", nearbyCount = 5))
        repository.refresh()
        source.sieges = listOf(siege(wid = 100020, sourceId = "transport-3", nearbyCount = 9))
        repository.refresh()

        val paused = repository.observeSnapshot().first()
        assertEquals("附近 2 人", paused.events.single().summary)
        assertEquals(1, paused.bufferedEventCount)

        repository.setPaused(false)
        val resumed = repository.observeSnapshot().first()
        assertEquals(1, resumed.events.size)
        assertEquals("附近 9 人", resumed.events.single().summary)
        assertEquals(0, resumed.bufferedEventCount)
    }

    @Test
    fun duplicateMoveIdsKeepNewestSourceOccurrenceForPublishingAndChangeDetection() = runBlocking {
        val newest = move(
            teamId = 7,
            arriveTime = 200,
            ownerName = "新玩家",
            fromXy = "20,20",
            toXy = "30,30",
        )
        val older = move(
            teamId = 7,
            arriveTime = 200,
            ownerName = "旧玩家",
            fromXy = "10,10",
            toXy = "15,15",
        )
        val source = FakeBattlefieldSource().apply {
            moves = listOf(newest, older)
        }
        val repository = LegacyBattlefieldRepository(source, Dispatchers.Unconfined)

        repository.refresh()
        val published = repository.observeSnapshot().first().events.single()
        assertEquals("新玩家 · 测试盟", published.title)
        assertEquals("地图队伍 · 20,20 → 30,30", published.summary)

        repository.setPaused(true)
        source.moves = listOf(newest)
        repository.refresh()

        assertEquals(0, repository.observeSnapshot().first().bufferedEventCount)
    }

    @Test
    fun captureLastEventTimestampIsNormalizedToEpochSeconds() = runBlocking {
        val source = FakeBattlefieldSource().apply {
            reportedLastEventAt = 1_700_000_000_123L
        }
        val repository = LegacyBattlefieldRepository(source, Dispatchers.Unconfined)

        repository.refresh()

        assertEquals(1_700_000_000L, repository.observeSnapshot().first().capture.lastEventAt)
    }

    @Test
    fun arrivingSoonMetricAcceptsLegacyMillisecondTimestamps() = runBlocking {
        val source = FakeBattlefieldSource().apply {
            moves = listOf(move(teamId = 1, arriveTime = 1_700_000_100_000L))
        }
        val repository = LegacyBattlefieldRepository(source, Dispatchers.Unconfined) { 1_700_000_000L }

        repository.refresh()

        assertEquals(1, repository.observeSnapshot().first().metrics.arrivingSoon)
    }

    @Test
    fun concurrentRefreshesRemainDeduplicatedAndBounded() = runBlocking {
        val source = FakeBattlefieldSource().apply {
            moves = (1..250).map { move(teamId = it, arriveTime = it.toLong()) }
        }
        val repository = LegacyBattlefieldRepository(source, Dispatchers.Default) { 0 }

        (1..12).map { async(Dispatchers.Default) { repository.refresh() } }.awaitAll()
        val snapshot = repository.observeSnapshot().first()

        assertEquals(200, snapshot.events.size)
        assertEquals(200, snapshot.events.map { it.id }.distinct().size)
        assertEquals((250L downTo 51L).toList(), snapshot.events.map { it.occurredAt })
    }

    private class FakeBattlefieldSource : LegacyBattlefieldSource {
        var moves = emptyList<LocalTeamMove>()
        var battles = emptyList<LocalFullBattle>()
        var sieges = emptyList<LocalBattleField>()
        var reportedLastEventAt: Long? = null
        val reads = AtomicInteger()

        override fun captureRunning() = true
        override fun lastEventAt(): Long? = reportedLastEventAt ?: moves.maxOfOrNull { it.arriveTime }
        override fun moves() = moves
        override fun battles() = battles
        override fun sieges() = sieges

        override fun read(): LegacyBattlefieldData {
            reads.incrementAndGet()
            return LegacyBattlefieldData(
                captureRunning = true,
                lastEventAt = lastEventAt(),
                moves = moves.toList(),
                battles = battles.toList(),
                sieges = sieges.toList(),
            )
        }
    }

    private fun move(
        teamId: Int,
        arriveTime: Long,
        ownerName: String = "玩家$teamId",
        fromXy: String = "10,10",
        toXy: String = "10,20",
    ) = LocalTeamMove(
        teamId = teamId,
        moveType = 5,
        subjectId = teamId,
        ownerUid = teamId,
        ownerName = ownerName,
        ownerUnion = "测试盟",
        fromWid = 100010,
        toWid = 100020,
        currentWid = 100015,
        fromXy = fromXy,
        toXy = toXy,
        currentXy = "10,15",
        startTime = arriveTime - 10,
        arriveTime = arriveTime,
        speed = 100,
    )

    private fun siege(wid: Int, sourceId: String, nearbyCount: Int = 2) = LocalBattleField(
        wid = wid,
        attackerUid = 1,
        nearbyUids = "",
        nearbyCount = nearbyCount,
        sourceMsgId = sourceId,
    )
}
