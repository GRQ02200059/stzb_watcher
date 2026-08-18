package com.local.stzb.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthEntryGuardTest {
    @Test
    fun `entry is denied until process access has been granted`() {
        assertFalse(AuthEntryPolicy.canEnter(isGranted = false))
        assertTrue(AuthEntryPolicy.canEnter(isGranted = true))
    }
}
