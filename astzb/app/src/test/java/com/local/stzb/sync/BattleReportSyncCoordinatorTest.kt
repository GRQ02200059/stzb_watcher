package com.local.stzb.sync

import com.local.stzb.profile.LocalProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleReportSyncCoordinatorTest {
    private val profile = LocalProfile("p1", "s1", "r1", "档案", "p1.db", 0)

    @Test
    fun pagesManifestAtTwoHundredAndUploadsOnlyRequiredReports() = runTest {
        val source = FakeSource((1L..201L).map(::report))
        val transport = FakeTransport(required = setOf(2L, 201L))

        val result = BattleReportSyncCoordinator(source, transport).sync(profile, "token", "1.2.0")

        assertEquals(SyncOutcome.Success, result)
        assertEquals(listOf(200, 1), transport.manifestSizes)
        assertEquals(listOf(listOf(2L), listOf(201L)), transport.uploadIds)
    }

    @Test
    fun uploadBatchesStopAtFiftyReports() = runTest {
        val reports = (1L..51L).map(::report)
        val transport = FakeTransport(required = reports.map { it.battleId }.toSet())

        BattleReportSyncCoordinator(FakeSource(reports), transport).sync(profile, "token", "1.2.0")

        assertEquals(listOf(50, 1), transport.uploadIds.map(List<Long>::size))
    }

    @Test
    fun retryAndPermanentOutcomesStopFurtherPages() = runTest {
        val retry = FakeTransport(manifestResult = SyncHttpResult.Retryable(90))
        val permanent = FakeTransport(manifestResult = SyncHttpResult.PermanentFailure)
        assertEquals(
            SyncOutcome.Retryable(90),
            BattleReportSyncCoordinator(FakeSource(listOf(report(1))), retry).sync(profile, "t", "1.2.0"),
        )
        assertEquals(
            SyncOutcome.PermanentFailure,
            BattleReportSyncCoordinator(FakeSource(listOf(report(1))), permanent).sync(profile, "t", "1.2.0"),
        )
    }

    @Test
    fun oversizedSingleReportIsSkippedWithoutBlockingLaterReport() = runTest {
        val reports = listOf(report(1), report(2))
        val transport = FakeTransport(
            required = setOf(1, 2),
            sizeByIds = mapOf(listOf(1L) to 2_097_153, listOf(2L) to 512),
        )
        val result = BattleReportSyncCoordinator(FakeSource(reports), transport).sync(profile, "t", "1.2.0")
        assertEquals(SyncOutcome.Success, result)
        assertEquals(listOf(listOf(2L)), transport.uploadIds)
    }

    private fun report(id: Long) = BattleReportPayload(
        id,
        mapOf("battle_id" to id),
        emptyList(),
        emptyList(),
    )

    private class FakeSource(private val reports: List<BattleReportPayload>) : BattleReportSource {
        override fun readBatch(profile: LocalProfile, afterBattleId: Long?, limit: Int) =
            reports.filter { afterBattleId == null || it.battleId > afterBattleId }.take(limit)
    }

    private class FakeTransport(
        private val required: Set<Long> = emptySet(),
        private val manifestResult: SyncHttpResult<Set<Long>>? = null,
        private val sizeByIds: Map<List<Long>, Int> = emptyMap(),
    ) : BattleReportTransport {
        val manifestSizes = mutableListOf<Int>()
        val uploadIds = mutableListOf<List<Long>>()
        override suspend fun manifest(token: String, version: String, profile: SyncProfile, digests: List<BattleReportDigest>): SyncHttpResult<Set<Long>> {
            manifestSizes += digests.size
            return manifestResult ?: SyncHttpResult.Success(digests.map { it.battleId }.filter(required::contains).toSet())
        }
        override suspend fun upload(token: String, version: String, profile: SyncProfile, reports: List<HashedBattleReport>): SyncHttpResult<Int> {
            uploadIds += reports.map { it.payload.battleId }
            return SyncHttpResult.Success(reports.size)
        }
        override fun uploadRequestBytes(token: String, version: String, profile: SyncProfile, reports: List<HashedBattleReport>): Int =
            sizeByIds[reports.map { it.payload.battleId }] ?: reports.size * 100
    }
}
