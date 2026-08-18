package com.local.stzb.auth

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = AuthRepository(
            baseUrl = server.url("/"),
            httpClient = OkHttpClient.Builder()
                .callTimeout(500, TimeUnit.MILLISECONDS)
                .connectTimeout(500, TimeUnit.MILLISECONDS)
                .readTimeout(500, TimeUnit.MILLISECONDS)
                .writeTimeout(500, TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login posts exact contract and parses success`() = runTest {
        server.enqueue(successResponse())

        val result = repository.login("player", "secret-value", "1.0.0")

        val request = server.takeRequest()
        assertEquals("/v1/login", request.path)
        assertEquals("POST", request.method)
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertJsonEquals(
            """{"username":"player","password":"secret-value","clientVersion":"1.0.0"}""",
            request.body.readUtf8(),
        )
        assertTrue(result.isSuccess)
        assertEquals(AuthErrorCode.NONE, result.errorCode)
        assertEquals("player", result.username)
        assertEquals("token", result.sessionToken)
        assertEquals("公告", result.announcement)
        assertEquals("1.0.0", result.minimumVersion)
        assertEquals("r1", result.requestId)
    }

    @Test
    fun `register verify and logout post exact contracts`() = runTest {
        server.enqueue(successResponse())
        server.enqueue(successResponse())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Cache-Control", "no-store")
                .setBody("""{"ok":true,"requestId":"r2"}"""),
        )

        assertTrue(repository.register("new-player", "new-secret", "1.0.0").isSuccess)
        assertJsonRequest(
            "/v1/register",
            """{"username":"new-player","password":"new-secret","clientVersion":"1.0.0"}""",
        )

        assertTrue(repository.verify("saved-token", "1.0.0").isSuccess)
        assertJsonRequest(
            "/v1/session/verify",
            """{"token":"saved-token","clientVersion":"1.0.0"}""",
        )

        assertTrue(repository.logout("saved-token").isSuccess)
        assertJsonRequest("/v1/logout", """{"token":"saved-token"}""")
    }

    @Test
    fun `missing no store and mismatched status are invalid responses`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true,"sessionToken":"secret-response-token"}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .addHeader("Cache-Control", "private, NO-STORE")
                .setBody("""{"ok":true,"sessionToken":"secret-response-token"}"""),
        )

        val missingHeader = repository.login("player", "secret", "1.0.0")
        val mismatchedStatus = repository.login("player", "secret", "1.0.0")

        assertInvalidResponse(missingHeader)
        assertInvalidResponse(mismatchedStatus)
    }

    @Test
    fun `malformed and incomplete envelopes are invalid responses`() = runTest {
        server.enqueue(noStoreResponse(200, """not-json-secret-response-token"""))
        server.enqueue(noStoreResponse(401, """{"ok":false,"requestId":"r3"}"""))

        assertInvalidResponse(repository.verify("secret-token", "1.0.0"))
        assertInvalidResponse(repository.verify("secret-token", "1.0.0"))
    }

    @Test
    fun `stable and unknown server errors map without exposing body data`() = runTest {
        val codes = linkedMapOf(
            "INVALID_INPUT" to AuthErrorCode.INVALID_INPUT,
            "USERNAME_TAKEN" to AuthErrorCode.USERNAME_TAKEN,
            "INVALID_CREDENTIALS" to AuthErrorCode.INVALID_CREDENTIALS,
            "ACCOUNT_DISABLED" to AuthErrorCode.ACCOUNT_DISABLED,
            "SERVICE_DISABLED" to AuthErrorCode.SERVICE_DISABLED,
            "SESSION_INVALID" to AuthErrorCode.SESSION_INVALID,
            "CLIENT_UNSUPPORTED" to AuthErrorCode.CLIENT_UNSUPPORTED,
            "RATE_LIMITED" to AuthErrorCode.RATE_LIMITED,
            "INTERNAL_ERROR" to AuthErrorCode.INTERNAL_ERROR,
            "FUTURE_SECRET_RESPONSE_TOKEN" to AuthErrorCode.UNKNOWN,
        )
        codes.forEach { (code, _) ->
            server.enqueue(
                noStoreResponse(
                    401,
                    """{"ok":false,"error":{"code":"$code","message":"公开提示"},"requestId":"r-error"}""",
                ),
            )
        }

        codes.forEach { (_, expected) ->
            val result = repository.login("player", "secret", "1.0.0")

            assertFalse(result.isSuccess)
            assertEquals(expected, result.errorCode)
            assertEquals("公开提示", result.message)
            assertEquals("r-error", result.requestId)
            assertNull(result.sessionToken)
        }
    }

    @Test
    fun `timeout returns transport failure without exception details`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val result = repository.verify("secret-token", "1.0.0")

        assertFalse(result.isSuccess)
        assertEquals(AuthErrorCode.TRANSPORT_UNAVAILABLE, result.errorCode)
        assertEquals("认证服务器暂时无法连接", result.message)
        assertNull(result.sessionToken)
        assertFalse(result.toString().contains("secret-token"))
    }

    private fun successResponse() = noStoreResponse(
        200,
        """{"ok":true,"user":{"username":"player"},"sessionToken":"token","service":{"enabled":true,"announcement":"公告"},"client":{"minimumVersion":"1.0.0"},"requestId":"r1"}""",
    )

    private fun noStoreResponse(status: Int, body: String) = MockResponse()
        .setResponseCode(status)
        .addHeader("Cache-Control", "no-store")
        .setBody(body)

    private fun assertJsonRequest(path: String, expectedBody: String) {
        val request = server.takeRequest()
        assertEquals(path, request.path)
        assertEquals("POST", request.method)
        assertJsonEquals(expectedBody, request.body.readUtf8())
    }

    private fun assertJsonEquals(expected: String, actual: String) {
        assertEquals(JSONObject(expected).toString(), JSONObject(actual).toString())
    }

    private fun assertInvalidResponse(result: AuthResult) {
        assertFalse(result.isSuccess)
        assertEquals(AuthErrorCode.INVALID_RESPONSE, result.errorCode)
        assertEquals("认证服务器返回了无效响应", result.message)
        assertNull(result.sessionToken)
        assertFalse(result.toString().contains("secret-response-token"))
    }
}
