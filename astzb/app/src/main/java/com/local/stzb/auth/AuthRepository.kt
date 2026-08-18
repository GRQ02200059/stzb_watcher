package com.local.stzb.auth

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject

class AuthRepository(
    private val baseUrl: HttpUrl,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : AuthTransport {
    override suspend fun register(
        username: String,
        password: String,
        clientVersion: String,
    ): AuthResult = send(
        path = "v1/register",
        body = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("clientVersion", clientVersion),
    )

    override suspend fun login(
        username: String,
        password: String,
        clientVersion: String,
    ): AuthResult = send(
        path = "v1/login",
        body = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("clientVersion", clientVersion),
    )

    override suspend fun verify(token: String, clientVersion: String): AuthResult = send(
        path = "v1/session/verify",
        body = JSONObject()
            .put("token", token)
            .put("clientVersion", clientVersion),
    )

    override suspend fun logout(token: String): AuthResult = send(
        path = "v1/logout",
        body = JSONObject().put("token", token),
    )

    private suspend fun send(path: String, body: JSONObject): AuthResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl.resolve(path) ?: return@withContext AuthResult.invalidResponse())
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.hasNoStore()) {
                        return@withContext AuthResult.invalidResponse()
                    }
                    val responseBody = response.body?.string()
                        ?: return@withContext AuthResult.invalidResponse()
                    parseResponse(response.isSuccessful, responseBody)
                }
            } catch (_: IOException) {
                AuthResult.transportUnavailable()
            } catch (_: JSONException) {
                AuthResult.invalidResponse()
            }
        }

    private fun parseResponse(httpSuccess: Boolean, responseBody: String): AuthResult {
        val envelope = JSONObject(responseBody)
        if (!envelope.has("ok") || envelope.opt("ok") !is Boolean) {
            return AuthResult.invalidResponse()
        }
        val ok = envelope.getBoolean("ok")
        if (ok != httpSuccess) {
            return AuthResult.invalidResponse()
        }
        val requestId = envelope.optionalString("requestId")
        if (ok) {
            return AuthResult(
                isSuccess = true,
                username = envelope.optJSONObject("user")?.optionalString("username"),
                sessionToken = envelope.optionalString("sessionToken"),
                announcement = envelope.optJSONObject("service")?.optionalString("announcement"),
                minimumVersion = envelope.optJSONObject("client")?.optionalString("minimumVersion"),
                requestId = requestId,
            )
        }

        val error = envelope.optJSONObject("error") ?: return AuthResult.invalidResponse()
        val code = error.optionalString("code") ?: return AuthResult.invalidResponse()
        return AuthResult(
            isSuccess = false,
            errorCode = mapErrorCode(code),
            message = error.optionalString("message"),
            requestId = requestId,
        )
    }

    private fun okhttp3.Response.hasNoStore(): Boolean =
        headers.values("Cache-Control")
            .flatMap { value -> value.split(',') }
            .any { directive -> directive.trim().equals("no-store", ignoreCase = true) }

    private fun JSONObject.optionalString(name: String): String? =
        if (has(name) && !isNull(name) && opt(name) is String) {
            getString(name).takeIf(String::isNotBlank)
        } else {
            null
        }

    private fun mapErrorCode(code: String): AuthErrorCode = when (code) {
        "INVALID_INPUT" -> AuthErrorCode.INVALID_INPUT
        "USERNAME_TAKEN" -> AuthErrorCode.USERNAME_TAKEN
        "INVALID_CREDENTIALS" -> AuthErrorCode.INVALID_CREDENTIALS
        "ACCOUNT_DISABLED" -> AuthErrorCode.ACCOUNT_DISABLED
        "SERVICE_DISABLED" -> AuthErrorCode.SERVICE_DISABLED
        "SESSION_INVALID" -> AuthErrorCode.SESSION_INVALID
        "CLIENT_UNSUPPORTED" -> AuthErrorCode.CLIENT_UNSUPPORTED
        "RATE_LIMITED" -> AuthErrorCode.RATE_LIMITED
        "INTERNAL_ERROR" -> AuthErrorCode.INTERNAL_ERROR
        else -> AuthErrorCode.UNKNOWN
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultHttpClient() = OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
