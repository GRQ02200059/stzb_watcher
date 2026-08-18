package com.local.stzb.auth

enum class AuthErrorCode {
    NONE,
    INVALID_INPUT,
    USERNAME_TAKEN,
    INVALID_CREDENTIALS,
    ACCOUNT_DISABLED,
    SERVICE_DISABLED,
    SESSION_INVALID,
    CLIENT_UNSUPPORTED,
    RATE_LIMITED,
    INTERNAL_ERROR,
    INVALID_RESPONSE,
    TRANSPORT_UNAVAILABLE,
    UNKNOWN,
}

data class AuthResult(
    val isSuccess: Boolean,
    val errorCode: AuthErrorCode = AuthErrorCode.NONE,
    val message: String? = null,
    val username: String? = null,
    val sessionToken: String? = null,
    val announcement: String? = null,
    val minimumVersion: String? = null,
    val requestId: String? = null,
) {
    override fun toString(): String =
        "AuthResult(isSuccess=$isSuccess, errorCode=$errorCode, requestId=$requestId)"

    companion object {
        fun invalidResponse() = AuthResult(
            isSuccess = false,
            errorCode = AuthErrorCode.INVALID_RESPONSE,
            message = "认证服务器返回了无效响应",
        )

        fun transportUnavailable() = AuthResult(
            isSuccess = false,
            errorCode = AuthErrorCode.TRANSPORT_UNAVAILABLE,
            message = "认证服务器暂时无法连接",
        )
    }
}

interface AuthTransport {
    suspend fun register(
        username: String,
        password: String,
        clientVersion: String,
    ): AuthResult

    suspend fun login(
        username: String,
        password: String,
        clientVersion: String,
    ): AuthResult

    suspend fun verify(token: String, clientVersion: String): AuthResult

    suspend fun logout(token: String): AuthResult
}
