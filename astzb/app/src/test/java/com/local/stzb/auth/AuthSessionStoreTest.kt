package com.local.stzb.auth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionStoreTest {
    private val preferences = FakeSessionPreferences()
    private val cipher = FakeSessionCipher()
    private val store = EncryptedAuthSessionStore(preferences, cipher)

    @Test
    fun `token round trip uses versioned base64 storage without plaintext`() {
        store.saveToken("session-secret")

        val serialized = preferences.values.getValue("auth_session")
        assertTrue(serialized.startsWith("v1:"))
        assertTrue(!serialized.contains("session-secret"))
        assertEquals("session-secret", store.readToken())
        assertEquals(byteArrayOf(1, 2, 3).toList(), cipher.lastDecryptIv?.toList())
    }

    @Test
    fun `delete removes token and username is stored separately`() {
        store.saveToken("session-secret")
        store.saveUsername("player")

        store.deleteToken()

        assertNull(store.readToken())
        assertEquals("player", store.readUsername())
    }

    @Test
    fun `blank username is normalized to absent`() {
        store.saveUsername("  ")

        assertNull(store.readUsername())
        assertNull(preferences.values["auth_username"])
    }

    @Test
    fun `empty token is rejected without modifying existing session`() {
        store.saveToken("existing")

        assertThrows(IllegalArgumentException::class.java) {
            store.saveToken("")
        }

        assertEquals("existing", store.readToken())
    }

    @Test
    fun `corrupt encrypted token is deleted and returned as absent`() {
        listOf(
            "not-versioned",
            "v2:AQID:BAUG",
            "v1:not-base64:BAUG",
            "v1:AQID:",
            "v1:AQID:BAUG:extra",
        ).forEach { value ->
            preferences.values["auth_session"] = value

            assertNull(store.readToken())
            assertNull(preferences.values["auth_session"])
        }
    }

    @Test
    fun `decrypt failure and blank plaintext delete stored token`() {
        store.saveToken("session-secret")
        cipher.failDecrypt = true
        assertNull(store.readToken())
        assertNull(preferences.values["auth_session"])

        cipher.failDecrypt = false
        cipher.decryptValue = byteArrayOf()
        store.saveToken("session-secret")
        assertNull(store.readToken())
        assertNull(preferences.values["auth_session"])
    }

    private class FakeSessionPreferences : SessionPreferences {
        val values = mutableMapOf<String, String>()

        override fun getString(key: String): String? = values[key]

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private class FakeSessionCipher : SessionCipher {
        var failDecrypt = false
        var decryptValue: ByteArray? = null
        var lastDecryptIv: ByteArray? = null

        override fun encrypt(plaintext: ByteArray): EncryptedSession {
            val iv = byteArrayOf(1, 2, 3)
            return EncryptedSession(
                iv = iv,
                ciphertext = Base64.getEncoder().encode(plaintext),
            )
        }

        override fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray {
            lastDecryptIv = iv
            if (failDecrypt) error("decrypt failed")
            return decryptValue ?: Base64.getDecoder().decode(ciphertext)
        }
    }
}
