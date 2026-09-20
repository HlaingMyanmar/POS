package com.sspd.servicemgmt.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

class CryptoEnvelopeTest {
    @Test
    fun `AES GCM round trip does not expose plaintext`() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = AesGcmCipher(key)

        val encrypted = cipher.encrypt("test-session-value")

        assertFalse(encrypted.contains("test-session-value"))
        assertEquals("test-session-value", cipher.decrypt(encrypted))
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = AesGcmCipher(key)
        val original = CryptoEnvelope.decode(cipher.encrypt("session"))
        original.ciphertext[0] = (original.ciphertext[0].toInt() xor 1).toByte()

        assertThrows(AEADBadTagException::class.java) {
            cipher.decrypt(original.encode())
        }
    }
}
