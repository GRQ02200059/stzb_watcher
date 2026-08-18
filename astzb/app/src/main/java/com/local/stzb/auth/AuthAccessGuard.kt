package com.local.stzb.auth

class AuthAccessGuard {
    @Volatile
    var isGranted: Boolean = false
        private set

    internal fun grant() {
        isGranted = true
    }

    fun revoke() {
        isGranted = false
    }
}
