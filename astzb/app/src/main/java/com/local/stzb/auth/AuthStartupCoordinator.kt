package com.local.stzb.auth

sealed interface AuthGateState {
    data object CheckingSession : AuthGateState
    data class LoginRequired(val message: String? = null) : AuthGateState
    data object SubmittingLogin : AuthGateState
    data object SubmittingRegistration : AuthGateState
    data class Blocked(val message: String) : AuthGateState
    data class Unavailable(val message: String) : AuthGateState
    data class Ready(
        val username: String?,
        val announcement: String?,
    ) : AuthGateState
}

data class AuthGateUiState(
    val state: AuthGateState = AuthGateState.CheckingSession,
    val username: String = "",
    val password: String = "",
    val registrationMode: Boolean = false,
)

class AuthStartupCoordinator(
    private val transport: AuthTransport,
    private val sessionStore: AuthSessionStore,
    private val accessGuard: AuthAccessGuard,
    private val clientVersion: String,
) {
    suspend fun checkStartup(): AuthGateState {
        accessGuard.revoke()
        val token = sessionStore.readToken()
            ?: return AuthGateState.LoginRequired()
        val result = transport.verify(token, clientVersion)
        if (result.isSuccess) {
            return completeAuthentication(result)
        }
        return when (result.errorCode) {
            AuthErrorCode.SESSION_INVALID -> {
                sessionStore.deleteToken()
                AuthGateState.LoginRequired(AuthMessages.forError(result.errorCode))
            }
            AuthErrorCode.ACCOUNT_DISABLED -> {
                sessionStore.deleteToken()
                AuthGateState.Blocked(AuthMessages.forError(result.errorCode))
            }
            AuthErrorCode.SERVICE_DISABLED ->
                AuthGateState.Blocked(AuthMessages.forError(result.errorCode))
            else -> AuthGateState.Unavailable(AuthMessages.forError(result.errorCode))
        }
    }

    fun completeAuthentication(result: AuthResult): AuthGateState.Ready {
        require(result.isSuccess)
        accessGuard.grant()
        return AuthGateState.Ready(result.username, result.announcement)
    }
}

internal object AuthMessages {
    fun forError(errorCode: AuthErrorCode): String = when (errorCode) {
        AuthErrorCode.INVALID_INPUT -> "用户名或密码格式不正确"
        AuthErrorCode.USERNAME_TAKEN -> "用户名已被使用"
        AuthErrorCode.INVALID_CREDENTIALS -> "用户名或密码错误"
        AuthErrorCode.ACCOUNT_DISABLED -> "账号已禁用"
        AuthErrorCode.SERVICE_DISABLED -> "服务暂不可用"
        AuthErrorCode.SESSION_INVALID -> "登录状态已失效，请重新登录"
        AuthErrorCode.CLIENT_UNSUPPORTED -> "当前客户端版本过低"
        AuthErrorCode.RATE_LIMITED -> "请求过于频繁，请稍后重试"
        AuthErrorCode.TRANSPORT_UNAVAILABLE -> "认证服务器暂时无法连接"
        AuthErrorCode.INVALID_RESPONSE -> "认证服务器返回了无效响应"
        AuthErrorCode.INTERNAL_ERROR, AuthErrorCode.UNKNOWN, AuthErrorCode.NONE ->
            "认证服务暂时异常，请稍后重试"
    }
}
