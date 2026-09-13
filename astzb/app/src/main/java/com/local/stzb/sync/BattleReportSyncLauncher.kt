package com.local.stzb.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import java.util.concurrent.atomic.AtomicBoolean

fun interface BattleReportWorkScheduler {
    fun enqueue(profileId: String)
}

class BattleReportSyncLauncher(
    private val currentProfileId: () -> String?,
    private val scheduler: BattleReportWorkScheduler,
) {
    private val scheduled = AtomicBoolean(false)

    fun onAuthenticated() {
        val profileId = currentProfileId()?.takeIf(String::isNotBlank) ?: return
        if (scheduled.compareAndSet(false, true)) scheduler.enqueue(profileId)
    }
}

class AndroidBattleReportWorkScheduler(
    private val workManager: WorkManager,
) : BattleReportWorkScheduler {
    constructor(context: Context) : this(WorkManager.getInstance(context))

    override fun enqueue(profileId: String) {
        workManager.enqueueUniqueWork(
            workName(profileId),
            ExistingWorkPolicy.REPLACE,
            BattleReportSyncWorker.request(profileId),
        )
    }

    fun enqueueRateLimitRetry(profileId: String, delaySeconds: Long) {
        workManager.enqueueUniqueWork(
            workName(profileId),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            BattleReportSyncWorker.request(
                profileId,
                initialDelaySeconds = delaySeconds.coerceAtLeast(30),
            ),
        )
    }

    companion object {
        fun workName(profileId: String) = "stzb-battle-report-sync-" + profileId
    }
}
