package com.local.stzb.auth

import android.app.Activity
import android.app.Service
import android.content.Intent
import com.local.stzb.StzbAppActivity
import com.local.stzb.StzbApplication

object AuthEntryGuard {
    @JvmStatic
    fun redirectActivityIfDenied(activity: Activity): Boolean {
        if (isGranted(activity.application as StzbApplication)) {
            return false
        }
        activity.startActivity(
            Intent(activity, StzbAppActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        activity.finish()
        return true
    }

    @JvmStatic
    fun stopServiceIfDenied(service: Service): Boolean {
        if (isGranted(service.application as StzbApplication)) {
            return false
        }
        service.stopSelf()
        return true
    }

    private fun isGranted(application: StzbApplication): Boolean =
        AuthEntryPolicy.canEnter(application.authAccessGuard.isGranted)
}
