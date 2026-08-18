package com.local.stzb.auth

import java.nio.charset.StandardCharsets
import java.util.Base64

interface AuthSessionStore {
    fun readToken(): String?
    fun saveToken(token: String)
    fun deleteToken()
    fun readUsername(): String?
    fun saveUsername(username: String)
}

interface SessionPreferences {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

data class EncryptedSession(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

interface SessionCipher {
    fun encrypt(plaintext: ByteArray): EncryptedSession
    fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray
}

class EncryptedAuthSessionStore(
    private val preferences: SessionPreferences,
    private val cipher: SessionCipher,
) : AuthSessionStore {
    override fun readToken(): String? {
        val serialized = preferences.getString(SESSION_KEY) ?: return null
        return try {
            val parts = serialized.split(':')
            require(parts.size == 3 && parts[0] == FORMAT_VERSION)
            val iv = Base64.getDecoder().decode(parts[1])
            val ciphertext = Base64.getDecoder().decode(parts[2])
            require(iv.isNotEmpty() && ciphertext.isNotEmpty())
            cipher.decrypt(iv, ciphertext)
                .toString(StandardCharsets.UTF_8)
                .takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Empty token")
        } catch (_: Exception) {
            deleteToken()
            null
        }
    }

    override fun saveToken(token: String) {
        require(token.isNotBlank())
        val encrypted = cipher.encrypt(token.toByteArray(StandardCharsets.UTF_8))
        require(encrypted.iv.isNotEmpty() && encrypted.ciphertext.isNotEmpty())
        val serialized = listOf(
            FORMAT_VERSION,
            Base64.getEncoder().encodeToString(encrypted.iv),
            Base64.getEncoder().encodeToString(encrypted.ciphertext),
        ).joinToString(":")
        preferences.putString(SESSION_KEY, serialized)
    }

    override fun deleteToken() {
        preferences.remove(SESSION_KEY)
    }

    override fun readUsername(): String? =
        preferences.getString(USERNAME_KEY)?.takeIf(String::isNotBlank)

    override fun saveUsername(username: String) {
        val normalized = username.trim()
        if (normalized.isEmpty()) {
            preferences.remove(USERNAME_KEY)
        } else {
            preferences.putString(USERNAME_KEY, normalized)
        }
    }

    companion object {
        private const val FORMAT_VERSION = "v1"
        private const val SESSION_KEY = "auth_session"
        private const val USERNAME_KEY = "auth_username"
    }
}
