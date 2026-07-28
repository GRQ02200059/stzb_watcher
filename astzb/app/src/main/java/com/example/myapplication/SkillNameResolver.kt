package com.example.myapplication

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SkillNameResolver {
    private var loaded = false
    private val names = mutableMapOf<Long, String>()
    private var simulatorHeroCount = 0
    private var simulatorSkillCount = 0

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            context.assets.open("skillcfg.json").bufferedReader(Charsets.UTF_8).use { reader ->
                val root = JSONObject(reader.readText())
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val id = key.toLongOrNull() ?: continue
                    val obj = root.optJSONObject(key) ?: continue
                    val name = obj.optString("name", "")
                    if (name.isNotBlank()) names[id] = name
                }
            }
            simulatorHeroCount = countArrayAsset(context, "simulator_hero_extra.json")
            simulatorSkillCount = countArrayAsset(context, "simulator_skill_extra.json")
            PacketLogStore.add("战法配置已加载：${names.size} 条")
        }.onFailure {
            PacketLogStore.add("未加载战法配置，使用战法ID兜底：${it.message}")
        }
    }

    @Synchronized
    fun nameOf(skillId: Long): String {
        return names[skillId] ?: "战法$skillId"
    }

    @Synchronized
    fun summary(): LocalSkillResourceSummary {
        return LocalSkillResourceSummary(
            skillCount = names.size,
            simulatorHeroCount = simulatorHeroCount,
            simulatorSkillCount = simulatorSkillCount,
        )
    }

    private fun countArrayAsset(context: Context, assetName: String): Int {
        return runCatching {
            context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { reader ->
                JSONArray(reader.readText()).length()
            }
        }.getOrDefault(0)
    }
}

data class LocalSkillResourceSummary(
    val skillCount: Int,
    val simulatorHeroCount: Int,
    val simulatorSkillCount: Int,
)
