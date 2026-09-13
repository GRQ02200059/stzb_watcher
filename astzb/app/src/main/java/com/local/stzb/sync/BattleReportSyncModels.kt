package com.local.stzb.sync

data class SyncProfile(
    val profileId: String,
    val serverAddress: String,
    val roleId: String,
    val displayName: String,
)

data class BattleReportPayload(
    val battleId: Long,
    val battle: Map<String, Any?>,
    val heroes: List<Map<String, Any?>>,
    val skills: List<Map<String, Any?>>,
)

data class BattleReportDigest(
    val battleId: Long,
    val contentHash: String,
)

data class HashedBattleReport(
    val payload: BattleReportPayload,
    val contentHash: String,
    val canonicalJson: String,
    val encodedBytes: Int,
)

sealed interface SyncHttpResult<out T> {
    data class Success<T>(val value: T) : SyncHttpResult<T>
    data class Retryable(val retryAfterSeconds: Long? = null) : SyncHttpResult<Nothing>
    data object SessionRejected : SyncHttpResult<Nothing>
    data object PermanentFailure : SyncHttpResult<Nothing>
}

sealed interface SyncOutcome {
    data object Success : SyncOutcome
    data class Retryable(val retryAfterSeconds: Long? = null) : SyncOutcome
    data object SessionRejected : SyncOutcome
    data object PermanentFailure : SyncOutcome
}
