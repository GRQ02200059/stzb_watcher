package com.local.stzb.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BattleReportSyncWorkerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun coldStartRequestIsNetworkConstrainedAndContainsOnlyProfileId() {
        val request = BattleReportSyncWorker.request("profile-a")
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(setOf("profileId"), request.workSpec.input.keyValueMap.keys)
        assertEquals("profile-a", request.workSpec.input.getString("profileId"))
        assertEquals(30, TimeUnit.MILLISECONDS.toSeconds(request.workSpec.backoffDelayDuration))
        assertFalse(request.workSpec.isPeriodic)
    }

    @Test
    fun coldStartReplacesExistingUniqueWork() {
        val scheduler = AndroidBattleReportWorkScheduler(workManager)
        scheduler.enqueue("profile-a")
        val first = workManager.getWorkInfosForUniqueWork(AndroidBattleReportWorkScheduler.workName("profile-a")).get().single()
        scheduler.enqueue("profile-a")
        val second = workManager.getWorkInfosForUniqueWork(AndroidBattleReportWorkScheduler.workName("profile-a")).get().single()
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun rateLimitContinuationUsesRequestedInitialDelay() {
        val request = BattleReportSyncWorker.request("profile-a", initialDelaySeconds = 75)
        assertEquals(75, TimeUnit.MILLISECONDS.toSeconds(request.workSpec.initialDelay))
        assertTrue(request.workSpec.input.keyValueMap.keys == setOf("profileId"))
    }

    @Test
    fun workerDecisionIsSilentAndRetriesOnlyTransientFailures() {
        assertEquals(WorkerDecision.SUCCESS, workerDecision(SyncOutcome.Success))
        assertEquals(WorkerDecision.RETRY, workerDecision(SyncOutcome.Retryable()))
        assertEquals(WorkerDecision.DELAYED_RETRY, workerDecision(SyncOutcome.Retryable(75)))
        assertEquals(WorkerDecision.SUCCESS, workerDecision(SyncOutcome.SessionRejected))
        assertEquals(WorkerDecision.SUCCESS, workerDecision(SyncOutcome.PermanentFailure))
    }
}
