package com.local.stzb.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidAuthSessionStore(context: Context) : AuthSessionStore {
    private val delegate = EncryptedAuthSessionStore(
        preferences = SharedPreferencesSessionPreferences(
            context.getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE),
        ),
        cipher = AndroidKeystoreSessionCipher(),
    )

    override fun readToken(): String? = delegate.readToken()

    override fun saveToken(token: String) = delegate.saveToken(token)

    override fun deleteToken() = delegate.deleteToken()

    override fun readUsername(): String? = delegate.readUsername()

    override fun saveUsername(username: String) = delegate.saveUsername(username)

    companion object {
        const val PREFERENCE_FILE = "stzb_auth_session"
    }
}

private class SharedPreferencesSessionPreferences(
    private val preferences: SharedPreferences,
) : SessionPreferences {
    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit())
    }

    override fun remove(key: String) {
        check(preferences.edit().remove(key).commit())
    }
}

private class AndroidKeystoreSessionCipher : SessionCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedSession {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return EncryptedSession(
            iv = cipher.iv,
            ciphertext = cipher.doFinal(plaintext),
        )
    }

    override fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "STZBWatcher.AuthSession"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
    }
}
