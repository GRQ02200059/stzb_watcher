package com.local.stzb.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileManagerTest {
    @Test
    fun registersStableIsolatedProfilesAndPersistsCurrentSelection() {
        val storage = FakeProfileStorage()
        val manager = ProfileManager(storage) { false }

        val first = manager.register("s1.example:8001", "role-1", "一服主号")
        val duplicate = manager.register("s1.example:8001", "role-1", "一服主号新名称")
        val second = manager.register("s2.example:8001", "role-2", "二服小号")

        assertEquals(first.profileId, duplicate.profileId)
        assertEquals(2, manager.snapshot().profiles.size)
        assertEquals("一服主号新名称", manager.snapshot().profiles.first { it.profileId == first.profileId }.displayName)
        assertFalse(first.databaseName == second.databaseName)

        assertTrue(manager.switchTo(second.profileId).isSuccess)
        assertEquals(second.profileId, storage.currentProfileId)
        assertEquals(second.databaseName, manager.snapshot().current?.databaseName)
    }

    @Test
    fun switchingIsRejectedWhileCaptureIsRunning() {
        val storage = FakeProfileStorage()
        var captureRunning = false
        val manager = ProfileManager(storage) { captureRunning }
        val first = manager.register("s1", "r1", "主号")
        val second = manager.register("s2", "r2", "小号")
        manager.switchTo(first.profileId)
        captureRunning = true

        val result = manager.switchTo(second.profileId)

        assertTrue(result.isFailure)
        assertEquals(first.profileId, manager.snapshot().current?.profileId)
    }

    @Test
    fun defaultProfileKeepsLegacyDatabaseName() {
        val manager = ProfileManager(FakeProfileStorage()) { false }

        val snapshot = manager.ensureDefault()

        assertEquals("astzb_local.db", snapshot.current?.databaseName)
        assertEquals("本机默认档案", snapshot.current?.displayName)
    }

    private class FakeProfileStorage : ProfileStorage {
        var profiles = emptyList<LocalProfile>()
        var currentProfileId: String? = null

        override fun loadProfiles(): List<LocalProfile> = profiles
        override fun saveProfiles(profiles: List<LocalProfile>) { this.profiles = profiles }
        override fun loadCurrentProfileId(): String? = currentProfileId
        override fun saveCurrentProfileId(profileId: String) { currentProfileId = profileId }
    }
}
