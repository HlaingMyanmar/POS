package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

enum class ServerStatus { CHECKING, ONLINE, OFFLINE }

class ServerStatusViewModel(application: Application) : AndroidViewModel(application) {

    private val _status = MutableStateFlow(ServerStatus.CHECKING)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    // Build once, reuse — avoids creating a new OkHttpClient every 10 s
    private val pingClient = ApiClient.buildPingClient()

    init { startPolling() }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                val online = withContext(Dispatchers.IO) {
                    try {
                        val req = Request.Builder().url(ApiClient.pingUrl).head().build()
                        pingClient.newCall(req).execute().use { it.code < 500 }
                    } catch (_: Exception) { false }
                }
                _status.value = if (online) ServerStatus.ONLINE else ServerStatus.OFFLINE
                delay(10_000)
            }
        }
    }
}
