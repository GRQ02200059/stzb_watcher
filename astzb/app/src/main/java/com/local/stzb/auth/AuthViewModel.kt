package com.local.stzb.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val startupCoordinator: AuthStartupCoordinator,
    private val transport: AuthTransport,
    private val sessionStore: AuthSessionStore,
    private val accessGuard: AuthAccessGuard,
    private val clientVersion: String,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AuthGateUiState())
    val uiState: StateFlow<AuthGateUiState> = mutableUiState.asStateFlow()

    fun start() {
        mutableUiState.value = mutableUiState.value.copy(
            state = AuthGateState.CheckingSession,
            username = sessionStore.readUsername().orEmpty(),
            password = "",
        )
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                state = startupCoordinator.checkStartup(),
                password = "",
            )
        }
    }

    fun retry() = start()

    fun updateUsername(username: String) {
        mutableUiState.value = mutableUiState.value.copy(username = username)
    }

    fun updatePassword(password: String) {
        mutableUiState.value = mutableUiState.value.copy(password = password)
    }

    fun setRegistrationMode(enabled: Boolean) {
        mutableUiState.value = mutableUiState.value.copy(
            registrationMode = enabled,
            password = "",
            state = AuthGateState.LoginRequired(),
        )
    }

    fun submit() {
        val snapshot = mutableUiState.value
        if (snapshot.state is AuthGateState.SubmittingLogin ||
            snapshot.state is AuthGateState.SubmittingRegistration
        ) {
            return
        }
        val username = snapshot.username.trim()
        val password = snapshot.password
        mutableUiState.value = snapshot.copy(
            username = username,
            state = if (snapshot.registrationMode) {
                AuthGateState.SubmittingRegistration
            } else {
                AuthGateState.SubmittingLogin
            },
        )
        viewModelScope.launch {
            val result = if (snapshot.registrationMode) {
                transport.register(username, password, clientVersion)
            } else {
                transport.login(username, password, clientVersion)
            }
            val nextState = if (result.isSuccess && !result.sessionToken.isNullOrBlank()) {
                sessionStore.saveToken(result.sessionToken)
                sessionStore.saveUsername(result.username ?: username)
                startupCoordinator.completeAuthentication(result)
            } else if (result.isSuccess) {
                AuthGateState.Unavailable(AuthMessages.forError(AuthErrorCode.INVALID_RESPONSE))
            } else {
                stateForFailure(result.errorCode)
            }
            mutableUiState.value = mutableUiState.value.copy(
                state = nextState,
                password = "",
            )
        }
    }

    fun logout() {
        val token = sessionStore.readToken()
        viewModelScope.launch {
            if (token != null) {
                transport.logout(token)
            }
            sessionStore.deleteToken()
            accessGuard.revoke()
            mutableUiState.value = mutableUiState.value.copy(
                state = AuthGateState.LoginRequired(),
                password = "",
                registrationMode = false,
            )
        }
    }

    private fun stateForFailure(errorCode: AuthErrorCode): AuthGateState = when (errorCode) {
        AuthErrorCode.ACCOUNT_DISABLED, AuthErrorCode.SERVICE_DISABLED ->
            AuthGateState.Blocked(AuthMessages.forError(errorCode))
        AuthErrorCode.TRANSPORT_UNAVAILABLE, AuthErrorCode.INVALID_RESPONSE,
        AuthErrorCode.INTERNAL_ERROR, AuthErrorCode.UNKNOWN ->
            AuthGateState.Unavailable(AuthMessages.forError(errorCode))
        else -> AuthGateState.LoginRequired(AuthMessages.forError(errorCode))
    }
}
