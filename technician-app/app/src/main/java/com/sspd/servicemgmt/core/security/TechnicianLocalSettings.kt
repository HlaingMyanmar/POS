package com.sspd.servicemgmt.core.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.securityDataStore by preferencesDataStore(name = "technician_security")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class InactivityAction { NONE, LOCK, LOGOUT }

class TechnicianLocalSettings(private val context: Context) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val lockEnabled = booleanPreferencesKey("app_lock_enabled")
        val backgroundAt = longPreferencesKey("background_at")
        val appPin = stringPreferencesKey("app_pin")
    }

    val themeMode: Flow<ThemeMode> = context.securityDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.themeMode] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val lockEnabled: Flow<Boolean> = context.securityDataStore.data.map { prefs ->
        prefs[Keys.lockEnabled] ?: true
    }

    val hasPin: Flow<Boolean> = context.securityDataStore.data.map { prefs ->
        !prefs[Keys.appPin].isNullOrEmpty()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.securityDataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.lockEnabled] = enabled }
    }

    suspend fun setAppPin(pin: String) {
        context.securityDataStore.edit { it[Keys.appPin] = pin }
    }

    suspend fun clearAppPin() {
        context.securityDataStore.edit { it.remove(Keys.appPin) }
    }

    suspend fun verifyPin(enteredPin: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        val storedPin = prefs[Keys.appPin]
        return !storedPin.isNullOrEmpty() && storedPin == enteredPin
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

    companion object {
        const val LOCK_TIMEOUT_MILLIS = InactivityPolicy.LOCK_TIMEOUT_MILLIS
        const val LOGOUT_TIMEOUT_MILLIS = InactivityPolicy.LOGOUT_TIMEOUT_MILLIS
    }
}
