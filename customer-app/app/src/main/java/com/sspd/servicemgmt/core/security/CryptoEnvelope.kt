package com.sspd.servicemgmt.core.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CryptoEnvelope(val iv: ByteArray, val ciphertext: ByteArray) {
    fun encode(): String = listOf(
        VERSION,
        ENCODER.encodeToString(iv),
        ENCODER.encodeToString(ciphertext)
    ).joinToString(":")

    companion object {
        private const val VERSION = "v1"
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()

        fun decode(value: String): CryptoEnvelope {
            val parts = value.split(':')
            require(parts.size == 3 && parts[0] == VERSION) { "Unsupported encrypted value" }
            val iv = DECODER.decode(parts[1])
            val ciphertext = DECODER.decode(parts[2])
            require(iv.size == 12 && ciphertext.size >= 16) { "Invalid encrypted value" }
            return CryptoEnvelope(iv, ciphertext)
        }
    }
}

class AesGcmCipher(
    private val key: SecretKey
) {
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        return CryptoEnvelope(iv, cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))).encode()
    }

    fun decrypt(envelope: String): String {
        val decoded = CryptoEnvelope.decode(envelope)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, decoded.iv))
        return cipher.doFinal(decoded.ciphertext).toString(Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
