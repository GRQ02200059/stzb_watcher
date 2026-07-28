package com.example.myapplication

import android.content.Context
import org.json.JSONObject

object HeroNameResolver {
    private var loaded = false
    private val names = mutableMapOf<Long, String>()
    private val iconIds = mutableMapOf<Long, Long>()
    private val bestIconByName = mutableMapOf<String, Pair<Long, Int>>()

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            context.assets.open("herocfg.json").bufferedReader(Charsets.UTF_8).use { reader ->
                val root = JSONObject(reader.readText())
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val id = key.toLongOrNull() ?: continue
                    val obj = root.optJSONObject(key) ?: continue
                    val name = obj.optString("name", "")
                    if (name.isNotBlank()) {
                        names[id] = name
                    }
                    val iconId = obj.optLong("iconId", id)
                    if (iconId > 0L) {
                        iconIds[id] = iconId
                    }
                    val quality = obj.optInt("quality", 0)
                    if (name.isNotBlank() && iconId > 0L) {
                        val current = bestIconByName[name]
                        if (current == null || quality > current.second) {
                            bestIconByName[name] = iconId to quality
                        }
                    }
                }
            }
            PacketLogStore.add("武将名配置已加载：${names.size} 条")
        }.onFailure {
            PacketLogStore.add("未加载武将名配置，使用武将ID兜底：${it.message}")
        }
    }

    @Synchronized
    fun nameOf(heroId: Long): String {
        return names[heroId] ?: "武将$heroId"
    }

    @Synchronized
    fun iconIdOf(heroId: Long): Long {
        return iconIds[heroId] ?: heroId
    }

    @Synchronized
    fun iconIdForName(name: String, fallback: Long): Long {
        return bestIconByName[name]?.first ?: fallback
    }
}
