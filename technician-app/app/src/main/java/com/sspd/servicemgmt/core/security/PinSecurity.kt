package com.sspd.servicemgmt.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PREFIX = "pbkdf2_sha256"
    const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun hash(pin: CharArray, random: SecureRandom = SecureRandom()): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val derived = derive(pin, salt, ITERATIONS)
        return listOf(
            PREFIX,
            ITERATIONS.toString(),
            Base64.getUrlEncoder().withoutPadding().encodeToString(salt),
            Base64.getUrlEncoder().withoutPadding().encodeToString(derived)
        ).joinToString("$")
    }

    fun verify(pin: CharArray, encoded: String): Boolean = runCatching {
        val parts = encoded.split('$')
        require(parts.size == 4 && parts[0] == PREFIX)
        val iterations = parts[1].toInt()
        require(iterations in 100_000..1_000_000)
        val salt = Base64.getUrlDecoder().decode(parts[2])
        val expected = Base64.getUrlDecoder().decode(parts[3])
        require(salt.size >= SALT_BYTES && expected.size == KEY_BITS / 8)
        MessageDigest.isEqual(expected, derive(pin, salt, iterations))
    }.getOrDefault(false)

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

data class PinThrottleState(val failedAttempts: Int = 0, val lockedUntilMillis: Long = 0L)

sealed interface PinAttemptDecision {
    data object Allowed : PinAttemptDecision
    data class Locked(val untilMillis: Long) : PinAttemptDecision
}

object PinThrottlePolicy {
    const val MAX_ATTEMPTS = 5
    const val LOCKOUT_MILLIS = 5L * 60L * 1000L

    fun beforeAttempt(state: PinThrottleState, nowMillis: Long): PinAttemptDecision =
        if (state.lockedUntilMillis > nowMillis) {
            PinAttemptDecision.Locked(state.lockedUntilMillis)
        } else {
            PinAttemptDecision.Allowed
        }

    fun afterFailure(state: PinThrottleState, nowMillis: Long): PinThrottleState {
        val failures = if (state.lockedUntilMillis in 1..nowMillis) 1 else state.failedAttempts + 1
        return if (failures >= MAX_ATTEMPTS) {
            PinThrottleState(0, nowMillis + LOCKOUT_MILLIS)
        } else {
            PinThrottleState(failures, 0L)
        }
    }

    fun remainingAttempts(state: PinThrottleState): Int =
        (MAX_ATTEMPTS - state.failedAttempts).coerceAtLeast(0)
}

sealed interface PinVerification {
    data object Verified : PinVerification
    data class Invalid(val remainingAttempts: Int) : PinVerification
    data class Locked(val untilMillis: Long) : PinVerification
    data object Unavailable : PinVerification
}
