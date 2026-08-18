package com.local.stzb.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthStartupCoordinatorTest {
    private val transport = FakeAuthTransport()
    private val store = FakeAuthSessionStore()
    private val guard = AuthAccessGuard()
    private val coordinator = AuthStartupCoordinator(transport, store, guard, "1.0.0")

    @Test
    fun `no saved token requires login without granting access`() = runTest {
        val state = coordinator.checkStartup()

        assertEquals(AuthGateState.LoginRequired(), state)
        assertFalse(guard.isGranted)
    }

    @Test
    fun `valid saved token grants current process`() = runTest {
        store.token = "saved"
        transport.verifyResult = AuthResult(
            isSuccess = true,
            username = "player",
            announcement = "公告",
        )

        val state = coordinator.checkStartup()

        assertEquals(AuthGateState.Ready("player", "公告"), state)
        assertEquals("saved", transport.verifiedToken)
        assertTrue(guard.isGranted)
    }

    @Test
    fun `invalid session deletes token and requires login`() = runTest {
        store.token = "saved"
        transport.verifyResult = AuthResult(false, AuthErrorCode.SESSION_INVALID)

        val state = coordinator.checkStartup()

        assertEquals(AuthGateState.LoginRequired("登录状态已失效，请重新登录"), state)
        assertNull(store.token)
        assertFalse(guard.isGranted)
    }

    @Test
    fun `disabled account deletes token and blocks startup`() = runTest {
        store.token = "saved"
        transport.verifyResult = AuthResult(false, AuthErrorCode.ACCOUNT_DISABLED)

        val state = coordinator.checkStartup()

        assertEquals(AuthGateState.Blocked("账号已禁用"), state)
        assertNull(store.token)
        assertFalse(guard.isGranted)
    }

    @Test
    fun `service disabled keeps token but blocks startup`() = runTest {
        store.token = "saved"
        transport.verifyResult = AuthResult(false, AuthErrorCode.SERVICE_DISABLED)

        val state = coordinator.checkStartup()

        assertEquals(AuthGateState.Blocked("服务暂不可用"), state)
        assertEquals("saved", store.token)
        assertFalse(guard.isGranted)
    }

    @Test
    fun `transport and invalid response keep token but fail closed`() = runTest {
        listOf(
            AuthResult.transportUnavailable() to "认证服务器暂时无法连接",
            AuthResult.invalidResponse() to "认证服务器返回了无效响应",
        ).forEach { (result, message) ->
            store.token = "saved"
            transport.verifyResult = result

            assertEquals(AuthGateState.Unavailable(message), coordinator.checkStartup())
            assertEquals("saved", store.token)
            assertFalse(guard.isGranted)
        }
    }
}

internal class FakeAuthTransport : AuthTransport {
    var loginResult = AuthResult(false, AuthErrorCode.INVALID_CREDENTIALS)
    var registerResult = AuthResult(false, AuthErrorCode.INVALID_INPUT)
    var verifyResult = AuthResult(false, AuthErrorCode.SESSION_INVALID)
    var logoutResult = AuthResult(isSuccess = true)
    var verifiedToken: String? = null
    var loginCredentials: Pair<String, String>? = null

    override suspend fun register(username: String, password: String, clientVersion: String) =
        registerResult

    override suspend fun login(username: String, password: String, clientVersion: String): AuthResult {
        loginCredentials = username to password
        return loginResult
    }

    override suspend fun verify(token: String, clientVersion: String): AuthResult {
        verifiedToken = token
        return verifyResult
    }

    override suspend fun logout(token: String) = logoutResult
}

internal class FakeAuthSessionStore : AuthSessionStore {
    var token: String? = null
    var username: String? = null

    override fun readToken() = token
    override fun saveToken(token: String) { this.token = token }
    override fun deleteToken() { token = null }
    override fun readUsername() = username
    override fun saveUsername(username: String) { this.username = username }
}
