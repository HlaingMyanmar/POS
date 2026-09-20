package com.sspd.servicemgmt.core.security

object InactivityPolicy {
    const val LOCK_TIMEOUT_MILLIS = 5 * 60 * 1000L
    const val LOGOUT_TIMEOUT_MILLIS = 20 * 60 * 1000L

    fun action(
        backgroundAtMillis: Long,
        nowMillis: Long,
        lockEnabled: Boolean
    ): InactivityAction {
        if (backgroundAtMillis <= 0L) return InactivityAction.NONE
        val elapsed = (nowMillis - backgroundAtMillis).coerceAtLeast(0L)
        return when {
            elapsed >= LOGOUT_TIMEOUT_MILLIS -> InactivityAction.LOGOUT
            lockEnabled && elapsed >= LOCK_TIMEOUT_MILLIS -> InactivityAction.LOCK
            else -> InactivityAction.NONE
        }
    }
}
