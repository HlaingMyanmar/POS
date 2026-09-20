package com.sspd.servicemgmt.feature.auth

enum class SessionStartAction {
    SHOW_LOGIN,
    LOGOUT_IDLE_SESSION,
    REQUIRE_BIOMETRIC,
    OPEN_SESSION
}

object CustomerAuthPolicy {
    fun isIdle(
        hasToken: Boolean,
        lastActiveAtMillis: Long,
        nowMillis: Long,
        timeoutMillis: Long
    ): Boolean {
        if (!hasToken || lastActiveAtMillis <= 0L) return false
        return (nowMillis - lastActiveAtMillis).coerceAtLeast(0L) >= timeoutMillis
    }

    fun startupAction(
        hasToken: Boolean,
        idle: Boolean,
        biometricEnabled: Boolean
    ): SessionStartAction = when {
        !hasToken -> SessionStartAction.SHOW_LOGIN
        idle -> SessionStartAction.LOGOUT_IDLE_SESSION
        biometricEnabled -> SessionStartAction.REQUIRE_BIOMETRIC
        else -> SessionStartAction.OPEN_SESSION
    }
}
