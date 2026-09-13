package com.local.stzb.sync

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BattleReportSyncTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var transport: BattleReportSyncTransport
    private val profile = SyncProfile("p1", "s1", "r1", "档案甲")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        transport = BattleReportSyncTransport(
            server.url("/"),
            OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun manifestPostsExactSafeContractAndParsesRequiredIds() = runTest {
        server.enqueue(ok("""{"ok":true,"requiredBattleIds":[42],"requestId":"r1"}"""))
        val result = transport.manifest(
            "secret-token",
            "1.2.0",
            profile,
            listOf(BattleReportDigest(42, "a".repeat(64))),
        )

        assertEquals(SyncHttpResult.Success(setOf(42L)), result)
        val request = server.takeRequest()
        assertEquals("/v1/battle-reports/manifest", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals(1, body.getInt("schemaVersion"))
        assertEquals("secret-token", body.getString("token"))
        assertEquals("p1", body.getJSONObject("profile").getString("profileId"))
        assertFalse(request.toString().contains("secret-token"))
    }

    @Test
    fun uploadOmitsForbiddenFieldsAndChecksAcceptedCount() = runTest {
        server.enqueue(ok("""{"ok":true,"accepted":1,"requestId":"r2"}"""))
        val report = hashedReport(42)
        val result = transport.upload("token", "1.2.0", profile, listOf(report))

        assertEquals(SyncHttpResult.Success(1), result)
        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("raw_json"))
        assertFalse(body.contains("source_msg_id"))
        assertEquals(report.contentHash, JSONObject(body).getJSONArray("reports").getJSONObject(0).getString("contentHash"))
    }

    @Test
    fun mapsRetrySessionPermanentAndInvalidResponses() = runTest {
        server.enqueue(error(429, "RATE_LIMITED").addHeader("Retry-After", "75"))
        server.enqueue(error(401, "SESSION_INVALID"))
        server.enqueue(error(400, "INVALID_INPUT"))
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Cache-Control", "public").setBody("{}"))

        assertEquals(SyncHttpResult.Retryable(75), transport.manifest("t", "1.2.0", profile, emptyList()))
        assertEquals(SyncHttpResult.SessionRejected, transport.manifest("t", "1.2.0", profile, emptyList()))
        assertEquals(SyncHttpResult.PermanentFailure, transport.manifest("t", "1.2.0", profile, emptyList()))
        assertEquals(SyncHttpResult.PermanentFailure, transport.manifest("t", "1.2.0", profile, emptyList()))
    }

    @Test
    fun computesExactUploadRequestSize() {
        val report = hashedReport(42)
        val size = transport.uploadRequestBytes("token", "1.2.0", profile, listOf(report))
        assertTrue(size > report.encodedBytes)
        assertEquals(size, transport.uploadBody("token", "1.2.0", profile, listOf(report)).toByteArray().size)
    }

    private fun hashedReport(id: Long): HashedBattleReport = BattleReportCanonicalizer.hash(
        BattleReportPayload(
            id,
            mapOf("battle_id" to id, "atk_name" to "玩家甲"),
            emptyList(),
            emptyList(),
        ),
    )

    private fun ok(body: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Cache-Control", "no-store")
        .setBody(body)

    private fun error(code: Int, name: String) = MockResponse()
        .setResponseCode(code)
        .addHeader("Cache-Control", "no-store")
        .setBody("""{"ok":false,"error":{"code":"$name","message":"x"},"requestId":"r"}""")
}
