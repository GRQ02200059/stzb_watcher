package com.local.stzb.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AndroidProfileStorage(context: Context) : ProfileStorage {
    private val preferences = context.getSharedPreferences("stzb_local_profiles", Context.MODE_PRIVATE)

    override fun loadProfiles(): List<LocalProfile> {
        val raw = preferences.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val profileId = item.optString("profileId")
                val databaseName = item.optString("databaseName")
                if (profileId.isBlank() || databaseName.isBlank()) return@mapNotNull null
                LocalProfile(
                    profileId = profileId,
                    serverAddress = item.optString("serverAddress"),
                    roleId = item.optString("roleId"),
                    displayName = item.optString("displayName", "未命名档案"),
                    databaseName = databaseName,
                    lastUsedAt = item.optLong("lastUsedAt", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    override fun saveProfiles(profiles: List<LocalProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(JSONObject().apply {
                put("profileId", profile.profileId)
                put("serverAddress", profile.serverAddress)
                put("roleId", profile.roleId)
                put("displayName", profile.displayName)
                put("databaseName", profile.databaseName)
                put("lastUsedAt", profile.lastUsedAt)
            })
        }
        preferences.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    override fun loadCurrentProfileId(): String? = preferences.getString(KEY_CURRENT, null)

    override fun saveCurrentProfileId(profileId: String) {
        preferences.edit().putString(KEY_CURRENT, profileId).apply()
    }

    private companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_CURRENT = "current_profile_id"
    }
}
