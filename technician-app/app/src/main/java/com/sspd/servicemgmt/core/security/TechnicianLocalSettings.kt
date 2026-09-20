package com.sspd.servicemgmt.core.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

private val Context.securityDataStore by preferencesDataStore(name = "technician_security")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class InactivityAction { NONE, LOCK, LOGOUT }

class TechnicianLocalSettings(private val context: Context) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val lockEnabled = booleanPreferencesKey("app_lock_enabled")
        val backgroundAt = longPreferencesKey("background_at")
        val legacyAppPin = stringPreferencesKey("app_pin")
        val appPinHash = stringPreferencesKey("app_pin_hash")
        val failedPinAttempts = longPreferencesKey("pin_failed_attempts")
        val pinLockedUntil = longPreferencesKey("pin_locked_until")
    }

    val themeMode: Flow<ThemeMode> = context.securityDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.themeMode] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val lockEnabled: Flow<Boolean> = context.securityDataStore.data.map { prefs ->
        prefs[Keys.lockEnabled] ?: true
    }

    val hasPin: Flow<Boolean> = context.securityDataStore.data
        .onStart { migrateLegacyPin() }
        .map { prefs ->
            !prefs[Keys.appPinHash].isNullOrEmpty() || !prefs[Keys.legacyAppPin].isNullOrEmpty()
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.securityDataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.lockEnabled] = enabled }
    }

    /** Idempotent startup migration; plaintext is removed only in the hash write transaction. */
    suspend fun migrateLegacyPinIfNeeded() {
        migrateLegacyPin()
    }

    suspend fun setAppPin(pin: String) {
        require(pin.length == 4 && pin.all(Char::isDigit)) { "PIN must contain exactly four digits" }
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin.toCharArray()) }
        context.securityDataStore.edit {
            it[Keys.appPinHash] = hash
            it.remove(Keys.legacyAppPin)
            resetPinThrottle(it)
        }
    }

    suspend fun clearAppPin() {
        context.securityDataStore.edit {
            it.remove(Keys.appPinHash)
            it.remove(Keys.legacyAppPin)
            resetPinThrottle(it)
        }
    }

    suspend fun verifyPin(
        enteredPin: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PinVerification {
        val hash = migrateLegacyPin() ?: return PinVerification.Unavailable
        val prefs = context.securityDataStore.data.first()
        val state = PinThrottleState(
            failedAttempts = (prefs[Keys.failedPinAttempts] ?: 0L).toInt(),
            lockedUntilMillis = prefs[Keys.pinLockedUntil] ?: 0L
        )
        when (val decision = PinThrottlePolicy.beforeAttempt(state, nowMillis)) {
            is PinAttemptDecision.Locked -> return PinVerification.Locked(decision.untilMillis)
            PinAttemptDecision.Allowed -> Unit
        }

        val valid = withContext(Dispatchers.Default) {
            PinHasher.verify(enteredPin.toCharArray(), hash)
        }
        if (valid) {
            context.securityDataStore.edit { resetPinThrottle(it) }
            return PinVerification.Verified
        }

        val failed = PinThrottlePolicy.afterFailure(state, nowMillis)
        context.securityDataStore.edit {
            it[Keys.failedPinAttempts] = failed.failedAttempts.toLong()
            it[Keys.pinLockedUntil] = failed.lockedUntilMillis
        }
        return if (failed.lockedUntilMillis > nowMillis) {
            PinVerification.Locked(failed.lockedUntilMillis)
        } else {
            PinVerification.Invalid(PinThrottlePolicy.remainingAttempts(failed))
        }
    }

    suspend fun markBackgrounded(atMillis: Long = System.currentTimeMillis()) {
        context.securityDataStore.edit { prefs ->
            if ((prefs[Keys.backgroundAt] ?: 0L) == 0L) prefs[Keys.backgroundAt] = atMillis
        }
    }

    suspend fun clearBackgroundTime() {
        context.securityDataStore.edit { it.remove(Keys.backgroundAt) }
    }

    suspend fun inactivityAction(nowMillis: Long = System.currentTimeMillis()): InactivityAction {
        var action = InactivityAction.NONE
        context.securityDataStore.edit { prefs ->
            val backgroundAt = prefs[Keys.backgroundAt] ?: 0L
            val lockEnabled = prefs[Keys.lockEnabled] ?: true
            action = InactivityPolicy.action(backgroundAt, nowMillis, lockEnabled)
            if (action != InactivityAction.LOCK) prefs.remove(Keys.backgroundAt)
        }
        return action
    }

    private suspend fun migrateLegacyPin(): String? {
        val current = context.securityDataStore.data.first()
        current[Keys.appPinHash]?.takeIf(String::isNotBlank)?.let { return it }
        val legacy = current[Keys.legacyAppPin]?.takeIf(String::isNotBlank) ?: return null
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(legacy.toCharArray()) }
        context.securityDataStore.edit { prefs ->
            if (prefs[Keys.appPinHash].isNullOrBlank() && prefs[Keys.legacyAppPin] == legacy) {
                prefs[Keys.appPinHash] = hash
                prefs.remove(Keys.legacyAppPin)
            }
        }
        return context.securityDataStore.data.first()[Keys.appPinHash]
    }

    private fun resetPinThrottle(
        prefs: androidx.datastore.preferences.core.MutablePreferences
    ) {
        prefs.remove(Keys.failedPinAttempts)
        prefs.remove(Keys.pinLockedUntil)
    }

    companion object {
        const val LOCK_TIMEOUT_MILLIS = InactivityPolicy.LOCK_TIMEOUT_MILLIS
        const val LOGOUT_TIMEOUT_MILLIS = InactivityPolicy.LOGOUT_TIMEOUT_MILLIS
    }
}
