package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import java.util.concurrent.TimeUnit

data class TrialCheckResult(
    val blocked: Boolean,
    val reason: String,
    val usedDays: Long,
    val firstActivatedAt: Long,
)

object TrialPolicy {
    private const val TRIAL_DAYS = 10L
    private const val BACKWARD_TOLERANCE_MS = 5 * 60 * 1000L
    private const val ELAPSED_SKEW_TOLERANCE_MS = 30 * 60 * 1000L

    fun evaluate(
        firstActivatedAt: Long,
        nowWallTime: Long,
        lastWallTime: Long,
        nowElapsedTime: Long,
        lastElapsedTime: Long,
    ): TrialCheckResult {
        val safeNow = nowWallTime.coerceAtLeast(firstActivatedAt)
        val usedDays = TimeUnit.MILLISECONDS.toDays(safeNow - firstActivatedAt)
        if (usedDays >= TRIAL_DAYS) {
            return TrialCheckResult(
                blocked = true,
                reason = "应用试用期已满 10 天，当前已不可继续使用。",
                usedDays = usedDays,
                firstActivatedAt = firstActivatedAt,
            )
        }

        val wallRolledBack = lastWallTime > 0 && nowWallTime + BACKWARD_TOLERANCE_MS < lastWallTime
        if (wallRolledBack) {
            return TrialCheckResult(
                blocked = true,
                reason = "检测到系统时间被回拨，应用已停止访问。",
                usedDays = usedDays,
                firstActivatedAt = firstActivatedAt,
            )
        }

        val elapsedAdvanced = if (lastElapsedTime > 0) nowElapsedTime - lastElapsedTime else 0L
        val wallAdvanced = if (lastWallTime > 0) nowWallTime - lastWallTime else 0L
        val elapsedMismatch = lastElapsedTime > 0 &&
            elapsedAdvanced > 0 &&
            wallAdvanced >= 0 &&
            wallAdvanced + ELAPSED_SKEW_TOLERANCE_MS < elapsedAdvanced
        if (elapsedMismatch) {
            return TrialCheckResult(
                blocked = true,
                reason = "检测到系统时间异常，应用已停止访问。",
                usedDays = usedDays,
                firstActivatedAt = firstActivatedAt,
            )
        }

        return TrialCheckResult(
            blocked = false,
            reason = "",
            usedDays = usedDays,
            firstActivatedAt = firstActivatedAt,
        )
    }
}

object LocalTrialManager {
    private const val PREFS_NAME = "local_trial_guard"
    private const val KEY_FIRST_ACTIVATED_AT = "first_activated_at"
    private const val KEY_LAST_WALL_TIME = "last_wall_time"
    private const val KEY_LAST_ELAPSED_TIME = "last_elapsed_time"
    private const val KEY_BOUND_ANDROID_ID = "bound_android_id"

    fun ensureAccessOrRedirect(activity: Activity): Boolean {
        val result = check(activity.applicationContext)
        if (!result.blocked) {
            return false
        }
        val intent = Intent(activity, ExpiredActivity::class.java).apply {
            putExtra(ExpiredActivity.EXTRA_REASON, result.reason)
            putExtra(ExpiredActivity.EXTRA_USED_DAYS, result.usedDays)
            putExtra(ExpiredActivity.EXTRA_FIRST_ACTIVATED_AT, result.firstActivatedAt)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        activity.startActivity(intent)
        activity.finishAffinity()
        return true
    }

    fun check(
        context: Context,
        nowWallTime: Long = System.currentTimeMillis(),
        nowElapsedTime: Long = SystemClock.elapsedRealtime(),
    ): TrialCheckResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentAndroidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty()
        val storedAndroidId = prefs.getString(KEY_BOUND_ANDROID_ID, null).orEmpty()
        val firstActivatedAt = ensureFirstActivatedAt(context, prefs, nowWallTime)
        val lastWallTime = prefs.getLong(KEY_LAST_WALL_TIME, 0L)
        val lastElapsedTime = prefs.getLong(KEY_LAST_ELAPSED_TIME, 0L)

        val result = TrialPolicy.evaluate(
            firstActivatedAt = firstActivatedAt,
            nowWallTime = nowWallTime,
            lastWallTime = lastWallTime,
            nowElapsedTime = nowElapsedTime,
            lastElapsedTime = lastElapsedTime,
        )

        if (result.blocked) {
            return result
        }

        prefs.edit()
            .putLong(KEY_LAST_WALL_TIME, maxOf(lastWallTime, nowWallTime))
            .putLong(KEY_LAST_ELAPSED_TIME, maxOf(lastElapsedTime, nowElapsedTime))
            .putString(KEY_BOUND_ANDROID_ID, if (storedAndroidId.isBlank()) currentAndroidId else storedAndroidId)
            .apply()

        return result
    }

    private fun ensureFirstActivatedAt(
        context: Context,
        prefs: android.content.SharedPreferences,
        nowWallTime: Long,
    ): Long {
        val stored = prefs.getLong(KEY_FIRST_ACTIVATED_AT, 0L)
        if (stored > 0L) {
            return stored
        }
        val firstInstallTime = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrNull() ?: nowWallTime
        val activatedAt = firstInstallTime.coerceAtLeast(0L).takeIf { it > 0L } ?: nowWallTime
        prefs.edit().putLong(KEY_FIRST_ACTIVATED_AT, activatedAt).apply()
        return activatedAt
    }
}
