package com.local.stzb.auth

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val transport = FakeAuthTransport()
    private val store = FakeAuthSessionStore()
    private val guard = AuthAccessGuard()
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = AuthViewModel(
            AuthStartupCoordinator(transport, store, guard, "1.0.0"),
            transport,
            store,
            guard,
            "1.0.0",
        )
    }

    @After
    fun tearDown() {
        ViewModelStore().clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `startup loads saved username and requires login without token`() = runTest(dispatcher) {
        store.username = "player"

        viewModel.start()
        runCurrent()

        assertEquals(AuthGateState.LoginRequired(), viewModel.uiState.value.state)
        assertEquals("player", viewModel.uiState.value.username)
    }

    @Test
    fun `successful login trims username saves token and grants without verify`() = runTest(dispatcher) {
        transport.loginResult = AuthResult(
            isSuccess = true,
            username = "player",
            sessionToken = "new-token",
            announcement = "公告",
        )
        viewModel.updateUsername("  player  ")
        viewModel.updatePassword("secret")

        viewModel.submit()
        runCurrent()

        assertEquals("player" to "secret", transport.loginCredentials)
        assertEquals("new-token", store.token)
        assertEquals("player", store.username)
        assertTrue(guard.isGranted)
        assertEquals(AuthGateState.Ready("player", "公告"), viewModel.uiState.value.state)
        assertEquals("", viewModel.uiState.value.password)
        assertEquals(null, transport.verifiedToken)
    }

    @Test
    fun `registration success enters ready and failed request clears password`() = runTest(dispatcher) {
        viewModel.setRegistrationMode(true)
        viewModel.updateUsername("player")
        viewModel.updatePassword("secret")
        transport.registerResult = AuthResult(
            isSuccess = true,
            username = "player",
            sessionToken = "registered-token",
        )

        viewModel.submit()
        runCurrent()
        assertEquals(AuthGateState.Ready("player", null), viewModel.uiState.value.state)
        assertEquals("", viewModel.uiState.value.password)

        viewModel.logout()
        runCurrent()
        viewModel.updatePassword("wrong")
        transport.loginResult = AuthResult(false, AuthErrorCode.INVALID_CREDENTIALS)
        viewModel.submit()
        runCurrent()
        assertEquals(
            AuthGateState.LoginRequired("用户名或密码错误"),
            viewModel.uiState.value.state,
        )
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun `retry runs startup verification again`() = runTest(dispatcher) {
        store.token = "saved"
        transport.verifyResult = AuthResult.transportUnavailable()
        viewModel.start()
        runCurrent()
        assertTrue(viewModel.uiState.value.state is AuthGateState.Unavailable)

        transport.verifyResult = AuthResult(isSuccess = true, username = "player")
        viewModel.retry()
        runCurrent()

        assertEquals(AuthGateState.Ready("player", null), viewModel.uiState.value.state)
        assertTrue(guard.isGranted)
    }

    @Test
    fun `logout clears local access even when server fails`() = runTest(dispatcher) {
        store.token = "saved"
        guard.grant()
        transport.logoutResult = AuthResult.transportUnavailable()

        viewModel.logout()
        runCurrent()

        assertEquals(null, store.token)
        assertFalse(guard.isGranted)
        assertEquals(AuthGateState.LoginRequired(), viewModel.uiState.value.state)
    }
}
