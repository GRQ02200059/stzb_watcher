package com.local.stzb.auth

object AuthEntryPolicy {
    fun canEnter(isGranted: Boolean): Boolean = isGranted
}
