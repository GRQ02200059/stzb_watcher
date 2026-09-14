package com.local.stzb.data.research

import android.content.Context
import com.local.stzb.auth.AndroidAuthSessionStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class SharedLineupSummary(val eligibleBattles: Int, val uniqueLineups: Int, val uniqueMatchups: Int, val latestBattleAt: Long, val generatedAt: String)
data class SharedLineupRow(
    val lineupKey: String, val heroIds: List<Long>, val samples: Int, val wins: Int,
    val draws: Int, val losses: Int, val rawWinRate: Double, val conservativeWinRate: Double,
    val usageRate: Double, val confidence: String, val lastSeenAt: Long,
)
data class SharedCounterRow(val lineup: SharedLineupRow, val opponentHeroIds: List<Long>, val opponentKey: String)
data class SharedLineupSnapshot(val summary: SharedLineupSummary, val popular: List<SharedLineupRow>, val winRate: List<SharedLineupRow>, val counters: List<SharedCounterRow>)

interface SharedLineupRankingsSource { suspend fun load(limit: Int = 50): SharedLineupSnapshot }

class HttpSharedLineupRankingsRepository(
    private val baseUrl: HttpUrl,
    private val client: OkHttpClient = defaultClient(),
    private val token: () -> String?,
    private val clientVersion: String,
) : SharedLineupRankingsSource {
    override suspend fun load(limit: Int): SharedLineupSnapshot = withContext(Dispatchers.IO) {
        val session = token()?.takeIf(String::isNotBlank) ?: error("登录状态已失效")
        val body = JSONObject().put("token", session).put("clientVersion", clientVersion).put("limit", limit.coerceIn(1, 100))
        val request = Request.Builder().url(requireNotNull(baseUrl.resolve("v1/lineup-rankings/query"))).post(body.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
        try {
            client.newCall(request).execute().use { response ->
                check(response.headers.values("Cache-Control").flatMap { it.split(',') }.any { it.trim().equals("no-store", true) })
                val root = JSONObject(response.body?.readLimited() ?: error("共享榜响应为空"))
                check(response.isSuccessful && root.opt("ok") == true) { "共享榜暂不可用" }
                parseSnapshot(root)
            }
        } catch (error: IOException) { throw IllegalStateException("共享榜暂不可用", error) }
    }

    private fun parseSnapshot(root: JSONObject): SharedLineupSnapshot {
        requireExact(root, setOf("ok", "summary", "popular", "winRate", "counters", "requestId"))
        val summary = root.getJSONObject("summary")
        requireExact(summary, setOf("eligibleBattles", "uniqueLineups", "uniqueMatchups", "latestBattleAt", "generatedAt"))
        return SharedLineupSnapshot(
            SharedLineupSummary(summary.getInt("eligibleBattles"), summary.getInt("uniqueLineups"), summary.getInt("uniqueMatchups"), summary.getLong("latestBattleAt"), summary.getString("generatedAt")),
            parseRows(root.getJSONArray("popular")), parseRows(root.getJSONArray("winRate")), parseCounters(root.getJSONArray("counters")),
        )
    }

    private fun parseRows(array: JSONArray) = (0 until array.length()).map { parseRow(array.getJSONObject(it), false) }
    private fun parseCounters(array: JSONArray) = (0 until array.length()).map { index ->
        val value = array.getJSONObject(index)
        SharedCounterRow(parseRow(value, true), ids(value.getJSONArray("opponentHeroIds")), value.getString("opponentKey"))
    }
    private fun parseRow(value: JSONObject, counter: Boolean): SharedLineupRow {
        val keys = mutableSetOf("lineupKey", "heroIds", "samples", "wins", "draws", "losses", "rawWinRate", "conservativeWinRate", "confidence")
        if (counter) keys += setOf("opponentKey", "opponentHeroIds") else keys += setOf("usageRate", "lastSeenAt")
        requireExact(value, keys)
        return SharedLineupRow(
            value.getString("lineupKey"), ids(value.getJSONArray("heroIds")), value.getInt("samples"), value.getInt("wins"), value.getInt("draws"), value.getInt("losses"),
            value.getDouble("rawWinRate"), value.getDouble("conservativeWinRate"), if (counter) 0.0 else value.getDouble("usageRate"),
            value.getString("confidence"), if (counter) 0L else value.getLong("lastSeenAt"),
        ).also { check(it.heroIds.size == 3 && it.heroIds.distinct().size == 3) }
    }
    private fun ids(array: JSONArray) = (0 until array.length()).map { array.getLong(it) }
    private fun requireExact(value: JSONObject, keys: Set<String>) { check(value.keys().asSequence().toSet() == keys) { "共享榜响应字段不匹配" } }
    private fun okhttp3.ResponseBody.readLimited(): String = byteStream().use { input ->
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val read = input.read(buffer); if (read < 0) break; output.write(buffer, 0, read); check(output.size() <= 512 * 1024) { "共享榜响应过大" } }
        output.toString(Charsets.UTF_8.name())
    }
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private fun defaultClient() = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS).build()
        fun production(context: Context, clientVersion: String): HttpSharedLineupRankingsRepository {
            val store = AndroidAuthSessionStore(context.applicationContext)
            return HttpSharedLineupRankingsRepository("http://152.136.236.184:9080".toHttpUrl(), token = store::readToken, clientVersion = clientVersion)
        }
    }
}
