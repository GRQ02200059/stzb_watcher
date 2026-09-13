package com.local.stzb.sync

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

interface BattleReportTransport {
    suspend fun manifest(
        token: String,
        version: String,
        profile: SyncProfile,
        digests: List<BattleReportDigest>,
    ): SyncHttpResult<Set<Long>>

    suspend fun upload(
        token: String,
        version: String,
        profile: SyncProfile,
        reports: List<HashedBattleReport>,
    ): SyncHttpResult<Int>

    fun uploadRequestBytes(
        token: String,
        version: String,
        profile: SyncProfile,
        reports: List<HashedBattleReport>,
    ): Int
}

class BattleReportSyncTransport(
    private val baseUrl: HttpUrl,
    private val client: OkHttpClient = defaultClient(),
) : BattleReportTransport {
    override suspend fun manifest(
        token: String,
        version: String,
        profile: SyncProfile,
        digests: List<BattleReportDigest>,
    ): SyncHttpResult<Set<Long>> = send(
        "v1/battle-reports/manifest",
        commonBody(token, version, profile).put(
            "reports",
            JSONArray().apply {
                digests.forEach { digest ->
                    put(JSONObject().put("battleId", digest.battleId).put("contentHash", digest.contentHash))
                }
            },
        ),
    ) { body ->
        val ids = body.optJSONArray("requiredBattleIds")
            ?: return@send SyncHttpResult.PermanentFailure
        val result = buildSet {
            for (index in 0 until ids.length()) {
                val value = ids.opt(index)
                if (value !is Number) return@send SyncHttpResult.PermanentFailure
                add(value.toLong())
            }
        }
        SyncHttpResult.Success(result)
    }

    override suspend fun upload(
        token: String,
        version: String,
        profile: SyncProfile,
        reports: List<HashedBattleReport>,
    ): SyncHttpResult<Int> = send(
        "v1/battle-reports/upload",
        JSONObject(uploadBody(token, version, profile, reports)),
    ) { body ->
        val accepted = body.opt("accepted")
        if (accepted !is Number) SyncHttpResult.PermanentFailure
        else SyncHttpResult.Success(accepted.toInt())
    }

    override fun uploadRequestBytes(
        token: String,
        version: String,
        profile: SyncProfile,
        reports: List<HashedBattleReport>,
    ): Int = uploadBody(token, version, profile, reports).toByteArray(Charsets.UTF_8).size

    internal fun uploadBody(
        token: String,
        version: String,
        profile: SyncProfile,
        reports: List<HashedBattleReport>,
    ): String = commonBody(token, version, profile).put(
        "reports",
        JSONArray().apply { reports.forEach { put(it.toJson()) } },
    ).toString()

    private suspend fun <T> send(
        path: String,
        body: JSONObject,
        parse: (JSONObject) -> SyncHttpResult<T>,
    ): SyncHttpResult<T> = withContext(Dispatchers.IO) {
        val url = baseUrl.resolve(path) ?: return@withContext SyncHttpResult.PermanentFailure
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.hasNoStore()) return@withContext SyncHttpResult.PermanentFailure
                when {
                    response.code == 429 -> SyncHttpResult.Retryable(
                        response.header("Retry-After")?.toLongOrNull(),
                    )
                    response.code >= 500 -> SyncHttpResult.Retryable()
                    response.code == 401 || response.code == 403 -> SyncHttpResult.SessionRejected
                    !response.isSuccessful -> SyncHttpResult.PermanentFailure
                    else -> {
                        val parsed = JSONObject(response.body?.readLimited() ?: return@withContext SyncHttpResult.PermanentFailure)
                        if (parsed.opt("ok") != true) SyncHttpResult.PermanentFailure else parse(parsed)
                    }
                }
            }
        } catch (_: IOException) {
            SyncHttpResult.Retryable()
        } catch (_: JSONException) {
            SyncHttpResult.PermanentFailure
        }
    }

    private fun commonBody(token: String, version: String, profile: SyncProfile) = JSONObject()
        .put("token", token)
        .put("clientVersion", version)
        .put("schemaVersion", BattleReportCanonicalizer.SCHEMA_VERSION)
        .put("profile", profile.toJson())

    private fun SyncProfile.toJson() = JSONObject()
        .put("profileId", profileId)
        .put("serverAddress", serverAddress)
        .put("roleId", roleId)
        .put("displayName", displayName)

    private fun HashedBattleReport.toJson() = JSONObject()
        .put("schemaVersion", BattleReportCanonicalizer.SCHEMA_VERSION)
        .put("battleId", payload.battleId)
        .put("contentHash", contentHash)
        .put("battle", payload.battle.toJsonObject())
        .put("heroes", payload.heroes.toJsonArray())
        .put("skills", payload.skills.toJsonArray())

    private fun Map<String, Any?>.toJsonObject() = JSONObject().apply {
        forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }

    private fun List<Map<String, Any?>>.toJsonArray() = JSONArray().apply {
        this@toJsonArray.forEach { put(it.toJsonObject()) }
    }

    private fun okhttp3.Response.hasNoStore(): Boolean = headers.values("Cache-Control")
        .flatMap { it.split(',') }
        .any { it.trim().equals("no-store", ignoreCase = true) }

    private fun okhttp3.ResponseBody.readLimited(): String {
        byteStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                if (output.size() > MAX_RESPONSE_BYTES) throw JSONException("response too large")
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 128 * 1024
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private fun defaultClient() = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
