package com.local.stzb.sync

import com.local.stzb.profile.LocalProfile

class BattleReportSyncCoordinator(
    private val source: BattleReportSource,
    private val transport: BattleReportTransport,
) {
    suspend fun sync(
        profile: LocalProfile,
        token: String,
        clientVersion: String,
    ): SyncOutcome {
        val syncProfile = SyncProfile(
            profile.profileId,
            profile.serverAddress,
            profile.roleId,
            profile.displayName,
        )
        var afterBattleId: Long? = null
        while (true) {
            val localReports = source.readBatch(profile, afterBattleId, MANIFEST_BATCH)
            if (localReports.isEmpty()) return SyncOutcome.Success
            val hashed = localReports.map(BattleReportCanonicalizer::hash)
            val manifest = transport.manifest(
                token,
                clientVersion,
                syncProfile,
                hashed.map { BattleReportDigest(it.payload.battleId, it.contentHash) },
            )
            val required = when (manifest) {
                is SyncHttpResult.Success -> manifest.value
                is SyncHttpResult.Retryable -> return SyncOutcome.Retryable(manifest.retryAfterSeconds)
                SyncHttpResult.SessionRejected -> return SyncOutcome.SessionRejected
                SyncHttpResult.PermanentFailure -> return SyncOutcome.PermanentFailure
            }
            val toUpload = hashed.filter { it.payload.battleId in required }
            for (batch in uploadBatches(token, clientVersion, syncProfile, toUpload)) {
                when (val uploaded = transport.upload(token, clientVersion, syncProfile, batch)) {
                    is SyncHttpResult.Success -> if (uploaded.value != batch.size) return SyncOutcome.PermanentFailure
                    is SyncHttpResult.Retryable -> return SyncOutcome.Retryable(uploaded.retryAfterSeconds)
                    SyncHttpResult.SessionRejected -> return SyncOutcome.SessionRejected
                    SyncHttpResult.PermanentFailure -> return SyncOutcome.PermanentFailure
                }
            }
            afterBattleId = localReports.last().battleId
            if (localReports.size < MANIFEST_BATCH) return SyncOutcome.Success
        }
    }

    private fun uploadBatches(
        token: String,
        clientVersion: String,
        profile: SyncProfile,
        reports: List<HashedBattleReport>,
    ): List<List<HashedBattleReport>> {
        val result = mutableListOf<List<HashedBattleReport>>()
        var current = mutableListOf<HashedBattleReport>()
        for (report in reports) {
            if (transport.uploadRequestBytes(token, clientVersion, profile, listOf(report)) > MAX_UPLOAD_BYTES) {
                continue
            }
            val candidate = current + report
            if (current.isNotEmpty() &&
                (candidate.size > MAX_UPLOAD_REPORTS || transport.uploadRequestBytes(token, clientVersion, profile, candidate) > MAX_UPLOAD_BYTES)
            ) {
                result += current
                current = mutableListOf(report)
            } else {
                current.add(report)
            }
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    companion object {
        private const val MANIFEST_BATCH = 200
        private const val MAX_UPLOAD_REPORTS = 50
        private const val MAX_UPLOAD_BYTES = 2 * 1024 * 1024
    }
}
