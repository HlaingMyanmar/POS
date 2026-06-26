package com.sspd.servicemgmt.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Signals the UI layer when the server returns 401 (token expired / invalid).
 * The OkHttp interceptor in ApiClient calls notifyTokenExpired() on 401.
 * AppNavigation observes tokenExpired and redirects to the login screen.
 */
object AuthEventBus {
    private val _tokenExpired = MutableStateFlow(false)
    val tokenExpired: StateFlow<Boolean> = _tokenExpired.asStateFlow()

    fun notifyTokenExpired() {
        _tokenExpired.value = true
    }

    fun reset() {
        _tokenExpired.value = false
    }
}
