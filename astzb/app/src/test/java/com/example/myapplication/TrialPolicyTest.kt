package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialPolicyTest {

    @Test
    fun `less than ten days remains available`() {
        val result = TrialPolicy.evaluate(
            firstActivatedAt = 0L,
            nowWallTime = 9L * DAY_MS,
            lastWallTime = 8L * DAY_MS,
            nowElapsedTime = 9L * DAY_MS,
            lastElapsedTime = 8L * DAY_MS,
        )
        assertFalse(result.blocked)
    }

    @Test
    fun `ten days becomes blocked`() {
        val result = TrialPolicy.evaluate(
            firstActivatedAt = 0L,
            nowWallTime = 10L * DAY_MS,
            lastWallTime = 9L * DAY_MS,
            nowElapsedTime = 10L * DAY_MS,
            lastElapsedTime = 9L * DAY_MS,
        )
        assertTrue(result.blocked)
    }

    @Test
    fun `wall clock rollback becomes blocked`() {
        val result = TrialPolicy.evaluate(
            firstActivatedAt = DAY_MS,
            nowWallTime = 2L * DAY_MS,
            lastWallTime = 3L * DAY_MS,
            nowElapsedTime = 5L * DAY_MS,
            lastElapsedTime = 4L * DAY_MS,
        )
        assertTrue(result.blocked)
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
