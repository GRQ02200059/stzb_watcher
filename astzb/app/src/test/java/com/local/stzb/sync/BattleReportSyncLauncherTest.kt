package com.local.stzb.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleReportSyncLauncherTest {
    @Test
    fun authenticatedProcessSchedulesCurrentProfileOnlyOnce() {
        val scheduled = mutableListOf<String>()
        val launcher = BattleReportSyncLauncher(
            currentProfileId = { "profile-a" },
            scheduler = BattleReportWorkScheduler { scheduled += it },
        )

        repeat(5) { launcher.onAuthenticated() }

        assertEquals(listOf("profile-a"), scheduled)
    }

    @Test
    fun missingProfileDoesNotConsumeProcessGate() {
        var profileId: String? = null
        val scheduled = mutableListOf<String>()
        val launcher = BattleReportSyncLauncher(
            currentProfileId = { profileId },
            scheduler = BattleReportWorkScheduler { scheduled += it },
        )

        launcher.onAuthenticated()
        profileId = "profile-b"
        launcher.onAuthenticated()
        launcher.onAuthenticated()

        assertEquals(listOf("profile-b"), scheduled)
    }
}
