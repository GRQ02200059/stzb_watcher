package com.example.myapplication

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class StzbApiClient(private val baseUrl: String) {

    fun loadBattles(page: Int = 1, size: Int = 20): BattlesResult {
        val root = getJsonObject("/api/battles_v2?page=$page&size=$size")
        val rows = root.optJSONArray("data") ?: JSONArray()
        val battles = (0 until rows.length()).mapNotNull { idx ->
            rows.optJSONObject(idx)?.toBattleSummary()
        }
        return BattlesResult(
            total = root.optInt("total", battles.size),
            page = root.optInt("page", page),
            size = root.optInt("size", size),
            data = battles,
        )
    }

    fun loadRanking(
        period: String = "24h",
        dim: String = "player",
        metric: String = "wuxun",
    ): List<RankingRow> {
        val arr = getJsonArray("/api/ranking_v2?period=$period&dim=$dim&metric=$metric")
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optJSONObject(idx)?.toRankingRow()
        }
    }

    fun loadBattleDetail(battleId: Int): BattleDetail {
        val root = getJsonObject("/api/battles_v2/$battleId")
        val battle = root.optJSONObject("battle") ?: JSONObject()
        val heroes = root.optJSONArray("heroes") ?: JSONArray()
        val extra = root.optJSONObject("extra") ?: JSONObject()
        return BattleDetail(
            summary = battle.toBattleSummary(),
            attackerPower = battle.optInt("atk_power", 0),
            defenderPower = battle.optInt("def_power", 0),
            attackerHp = extra.optInt("atk_hp", battle.optInt("atk_hp", 0)),
            defenderHp = extra.optInt("def_hp", battle.optInt("def_hp", 0)),
            widName = extra.optString("wid_name", battle.optString("wid_name", "")),
            weather = extra.optInt("weather", 0),
            inNight = extra.optInt("in_night", 0) != 0,
            heroes = (0 until heroes.length()).mapNotNull { idx ->
                heroes.optJSONObject(idx)?.toBattleHero()
            },
            rawExtra = extra.toString(2),
        )
    }

    fun getBaseUrl(): String = baseUrl.trim().trimEnd('/')

    fun loadBattleMonitor(): BattleMonitorSummary {
        val root = getJsonObject("/api/battle_monitor")
        val items = root.optJSONArray("items") ?: JSONArray()
        val summary = root.optJSONObject("summary") ?: JSONObject()
        return BattleMonitorSummary(
            ok = root.optBoolean("ok", false),
            updatedAt = root.optString("updated_at", ""),
            latestFile = root.optString("latest_file", ""),
            teams = summary.optInt("teams", items.length()),
            matchedMembers = summary.optInt(
                "matched_members",
                summary.optInt("matched_battles", 0)
            ),
            firstOwner = items.optJSONObject(0)?.optString("owner_name", "").orEmpty(),
            firstTarget = items.optJSONObject(0)?.optString("to_xy", "").orEmpty(),
        )
    }

    fun loadRecentEvents(limit: Int = 20): List<String> {
        val arr = getJsonArray("/api/recent_events")
        val start = (arr.length() - limit).coerceAtLeast(0)
        return (start until arr.length()).mapNotNull { idx ->
            val item = arr.optJSONObject(idx) ?: return@mapNotNull null
            val type = item.optString("type", "event")
            val ts = item.optString("ts", item.optString("time", ""))
            val msg = item.optString("msg", item.optString("message", item.toString()))
            listOf(ts, type, msg).filter { it.isNotBlank() }.joinToString(" | ")
        }.asReversed()
    }

    fun ping(): String {
        val result = loadBattles(page = 1, size = 1)
        return "连接成功：战报总数 ${result.total}"
    }

    private fun getJsonObject(path: String): JSONObject {
        return JSONObject(getText(path))
    }

    private fun getJsonArray(path: String): JSONArray {
        return JSONArray(getText(path))
    }

    private fun getText(path: String): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        require(normalizedBase.isNotBlank()) { "服务地址不能为空" }
        val url = URL(normalizedBase + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val input = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
            if (code !in 200..299) {
                error("HTTP $code: $body")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }
}

data class BattlesResult(
    val total: Int,
    val page: Int,
    val size: Int,
    val data: List<BattleSummary>,
)

data class BattleSummary(
    val id: Int,
    val time: Long,
    val attacker: String,
    val defender: String,
    val attackerUnion: String,
    val defenderUnion: String,
    val result: String,
    val fightType: String,
    val gongxun: Int,
)

data class BattleDetail(
    val summary: BattleSummary,
    val attackerPower: Int,
    val defenderPower: Int,
    val attackerHp: Int,
    val defenderHp: Int,
    val widName: String,
    val weather: Int,
    val inNight: Boolean,
    val heroes: List<BattleHero>,
    val rawExtra: String,
)

data class BattleHero(
    val side: Int,
    val pos: Int,
    val heroId: Long,
    val name: String,
    val level: Int,
    val star: Int,
    val soldiers: Int,
)

data class RankingRow(
    val name: String,
    val groupName: String,
    val value: Long,
    val battles: Int,
    val winRate: Double,
)

data class BattleMonitorSummary(
    val ok: Boolean,
    val updatedAt: String,
    val latestFile: String,
    val teams: Int,
    val matchedMembers: Int,
    val firstOwner: String,
    val firstTarget: String,
)

private fun JSONObject.toBattleSummary(): BattleSummary {
    return BattleSummary(
        id = optInt("battle_id", optInt("id", optInt("bid", 0))),
        time = optLong("time", 0L),
        attacker = optString("atk_name", optString("attacker", "未知")),
        defender = optString("def_name", optString("defender", "未知")),
        attackerUnion = optString("atk_union", ""),
        defenderUnion = optString("def_union", ""),
        result = resultText(optInt("result", -1)),
        fightType = fightTypeText(optInt("fight_type", -1)),
        gongxun = optInt("atk_gongxun", optInt("gongxun", 0)),
    )
}

private fun JSONObject.toBattleHero(): BattleHero {
    return BattleHero(
        side = optInt("side", -1),
        pos = optInt("pos", -1),
        heroId = optLong("hero_id", 0L),
        name = optString("hero_name", optString("name", "")),
        level = optInt("level", 0),
        star = optInt("star", 0),
        soldiers = optInt("soldiers", optInt("兵力", 0)),
    )
}

private fun JSONObject.toRankingRow(): RankingRow {
    return RankingRow(
        name = optString("name", "未知"),
        groupName = optString("group_name", ""),
        value = optLong("value", 0L),
        battles = optInt("battles", 0),
        winRate = optDouble("win_rate", 0.0),
    )
}

private fun resultText(result: Int): String {
    return when (result) {
        1, 7, 11 -> "胜"
        2, 8, 12 -> "负"
        0, 3, 9 -> "平"
        else -> "未知"
    }
}

private fun fightTypeText(fightType: Int): String {
    return when (fightType) {
        0 -> "野战"
        1, 2 -> "援军"
        27 -> "宝物"
        33 -> "大城"
        80 -> "攻城"
        else -> if (fightType >= 0) "类型$fightType" else "未知"
    }
}
