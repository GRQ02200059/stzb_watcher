package com.local.stzb.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.local.stzb.StzbApplication
import java.util.concurrent.TimeUnit

enum class WorkerDecision { SUCCESS, RETRY, DELAYED_RETRY }

fun workerDecision(outcome: SyncOutcome): WorkerDecision = when (outcome) {
    SyncOutcome.Success,
    SyncOutcome.SessionRejected,
    SyncOutcome.PermanentFailure -> WorkerDecision.SUCCESS
    is SyncOutcome.Retryable -> if (outcome.retryAfterSeconds == null) {
        WorkerDecision.RETRY
    } else {
        WorkerDecision.DELAYED_RETRY
    }
}

class BattleReportSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val profileId = inputData.getString(PROFILE_ID)?.takeIf(String::isNotBlank)
            ?: return Result.success()
        val app = applicationContext as? StzbApplication ?: return Result.success()
        val outcome = app.runBattleReportSync(profileId)
        return when (workerDecision(outcome)) {
            WorkerDecision.SUCCESS -> Result.success()
            WorkerDecision.RETRY -> Result.retry()
            WorkerDecision.DELAYED_RETRY -> {
                val retryAfter = (outcome as SyncOutcome.Retryable).retryAfterSeconds ?: 30
                AndroidBattleReportWorkScheduler(applicationContext)
                    .enqueueRateLimitRetry(profileId, retryAfter)
                Result.success()
            }
        }
    }

    companion object {
        private const val PROFILE_ID = "profileId"

        fun request(
            profileId: String,
            initialDelaySeconds: Long = 0,
        ): OneTimeWorkRequest = OneTimeWorkRequestBuilder<BattleReportSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(workDataOf(PROFILE_ID to profileId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .apply {
                if (initialDelaySeconds > 0) {
                    setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
                }
            }
            .build()
    }
}
