package com.sspd.servicemgmt.websocket

import com.google.gson.Gson
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.DataEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * App-level singleton that keeps ONE STOMP WebSocket connection for real-time
 * data-change events.
 *
 * Usage:
 *   // On login / app start:
 *   DataEventBus.connect(ApiClient.wsNativeUrl, token)
 *
 *   // On logout:
 *   DataEventBus.disconnect()
 *
 *   // In any ViewModel:
 *   viewModelScope.launch {
 *       DataEventBus.events
 *           .filter { it.entity.contains("Sale", ignoreCase = true) }
 *           .debounce(500)
 *           .collect { load() }
 *   }
 */
object DataEventBus {

    private val _events = MutableSharedFlow<DataEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<DataEvent> = _events.asSharedFlow()

    private val scope       = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson        = Gson()

    @Volatile private var stomp:         StompClient? = null
    @Volatile private var currentUrl:    String       = ""
    @Volatile private var currentToken:  String       = ""
    private var reconnectJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun connect(url: String, token: String) {
        if (url.isBlank() || token.isBlank()) return
        currentUrl   = url
        currentToken = token
        openSocket()
    }

    fun disconnect() {
        reconnectJob?.cancel()
        stomp?.disconnect()
        stomp = null
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun openSocket() {
        stomp?.disconnect()
        stomp = StompClient(
            client         = ApiClient.wsClient(),
            url            = currentUrl,
            token          = currentToken,
            onConnected    = { stomp?.subscribe("/topic/data-events") },
            onMessage      = { dest, body ->
                if (dest == "/topic/data-events") {
                    runCatching {
                        val event = gson.fromJson(body, DataEvent::class.java)
                        scope.launch { _events.emit(event) }
                    }
                }
            },
            onDisconnected = { scheduleReconnect() },
        )
        stomp?.connect()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5_000)
            if (currentUrl.isNotBlank() && currentToken.isNotBlank()) openSocket()
        }
    }
}
