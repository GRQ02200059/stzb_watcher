package com.local.stzb.profile

import java.security.MessageDigest

data class LocalProfile(
    val profileId: String,
    val serverAddress: String,
    val roleId: String,
    val displayName: String,
    val databaseName: String,
    val lastUsedAt: Long,
)

data class ProfileSnapshot(
    val profiles: List<LocalProfile>,
    val current: LocalProfile?,
)

interface ProfileStorage {
    fun loadProfiles(): List<LocalProfile>
    fun saveProfiles(profiles: List<LocalProfile>)
    fun loadCurrentProfileId(): String?
    fun saveCurrentProfileId(profileId: String)
}

class ProfileManager(
    private val storage: ProfileStorage,
    private val clock: () -> Long = System::currentTimeMillis,
    private val captureRunning: () -> Boolean,
) {
    fun ensureDefault(): ProfileSnapshot {
        if (storage.loadProfiles().isEmpty()) {
            val default = LocalProfile(
                profileId = DEFAULT_PROFILE_ID,
                serverAddress = "local",
                roleId = "default",
                displayName = "本机默认档案",
                databaseName = DEFAULT_DATABASE_NAME,
                lastUsedAt = clock(),
            )
            storage.saveProfiles(listOf(default))
            storage.saveCurrentProfileId(default.profileId)
        } else if (storage.loadCurrentProfileId().isNullOrBlank()) {
            storage.saveCurrentProfileId(storage.loadProfiles().first().profileId)
        }
        return snapshot()
    }

    fun register(serverAddress: String, roleId: String, displayName: String): LocalProfile {
        val normalizedServer = serverAddress.trim()
        val normalizedRole = roleId.trim()
        require(normalizedServer.isNotBlank()) { "区服地址不能为空" }
        require(normalizedRole.isNotBlank()) { "角色 ID 不能为空" }
        val profileId = stableProfileId(normalizedServer, normalizedRole)
        val current = storage.loadProfiles().toMutableList()
        val index = current.indexOfFirst { it.profileId == profileId }
        val old = current.getOrNull(index)
        val profile = LocalProfile(
            profileId = profileId,
            serverAddress = normalizedServer,
            roleId = normalizedRole,
            displayName = displayName.trim().ifBlank { old?.displayName ?: "未命名档案" },
            databaseName = old?.databaseName ?: "astzb_profile_${profileId.take(16)}.db",
            lastUsedAt = clock(),
        )
        if (index >= 0) current[index] = profile else current += profile
        storage.saveProfiles(current.sortedByDescending(LocalProfile::lastUsedAt))
        if (storage.loadCurrentProfileId().isNullOrBlank()) storage.saveCurrentProfileId(profile.profileId)
        return profile
    }

    fun switchTo(profileId: String): Result<LocalProfile> = runCatching {
        check(!captureRunning()) { "抓包运行中，请先停止抓包再切换档案" }
        val profiles = storage.loadProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.profileId == profileId }
        require(index >= 0) { "档案不存在" }
        val selected = profiles[index].copy(lastUsedAt = clock())
        profiles[index] = selected
        storage.saveProfiles(profiles.sortedByDescending(LocalProfile::lastUsedAt))
        storage.saveCurrentProfileId(selected.profileId)
        selected
    }

    fun snapshot(): ProfileSnapshot {
        val profiles = storage.loadProfiles()
        val currentId = storage.loadCurrentProfileId()
        return ProfileSnapshot(
            profiles = profiles,
            current = profiles.firstOrNull { it.profileId == currentId },
        )
    }

    companion object {
        const val DEFAULT_PROFILE_ID = "default"
        const val DEFAULT_DATABASE_NAME = "astzb_local.db"

        fun stableProfileId(serverAddress: String, roleId: String): String =
            "${serverAddress.trim().lowercase()}:${roleId.trim()}".sha256().take(24)
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
