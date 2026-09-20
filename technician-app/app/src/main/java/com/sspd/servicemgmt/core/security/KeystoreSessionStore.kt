package com.sspd.servicemgmt.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Stores only session secrets. The AES key is non-exportable and generated in Android Keystore.
 * A legacy plaintext value is removed only after its encrypted replacement is committed.
 */
class KeystoreSessionStore(
    context: Context,
    private val legacy: SharedPreferences
) {
    private val encrypted = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val cipher by lazy { AesGcmCipher(getOrCreateKey()) }

    fun migrateLegacy(key: String) {
        if (legacy.contains(key)) get(key)
    }

    @Synchronized
    fun get(key: String): String {
        encrypted.getString(key, null)?.let { envelope ->
            return runCatching { cipher.decrypt(envelope) }
                .getOrElse {
                    // A restored/corrupt value or invalidated key cannot be recovered safely.
                    encrypted.edit().remove(key).commit()
                    legacy.edit().remove(key).commit()
                    ""
                }
        }

        val plaintext = legacy.getString(key, null) ?: return ""
        if (plaintext.isEmpty()) {
            legacy.edit().remove(key).commit()
            return ""
        }
        return runCatching {
            val persisted = encrypted.edit().putString(key, cipher.encrypt(plaintext)).commit()
            if (persisted) legacy.edit().remove(key).commit()
            if (persisted) plaintext else ""
        }.getOrDefault("")
    }

    @Synchronized
    fun put(key: String, value: String) {
        if (value.isBlank()) {
            remove(key)
            return
        }
        val envelope = cipher.encrypt(value)
        check(encrypted.edit().putString(key, envelope).commit()) {
            "Unable to persist encrypted session"
        }
        legacy.edit().remove(key).commit()
    }

    @Synchronized
    fun remove(key: String) {
        encrypted.edit().remove(key).commit()
        legacy.edit().remove(key).commit()
    }

    fun clear() {
        encrypted.edit().clear().commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sspd_technician_session_aes_v1"
        const val PREFERENCES_NAME = "sspd_secure_session"
    }
}
