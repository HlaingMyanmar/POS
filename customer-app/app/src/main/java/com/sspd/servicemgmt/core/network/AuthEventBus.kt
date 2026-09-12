package com.sspd.servicemgmt.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AuthEventBus {
    private val _tokenExpired = MutableStateFlow(false)
    val tokenExpired = _tokenExpired.asStateFlow()
    fun notifyTokenExpired() { _tokenExpired.value = true }
    fun reset() { _tokenExpired.value = false }
}
