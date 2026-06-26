package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.ChatMessageDTO
import com.sspd.servicemgmt.api.SendMessageRequest
import com.sspd.servicemgmt.utils.PreferenceManager
import com.sspd.servicemgmt.websocket.StompClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(ChatUiState(myUsername = prefs.username))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var stomp: StompClient? = null
    private var reconnectJob: Job? = null

    // Gson that handles LocalDateTime as both ISO string and [y,m,d,h,min,s] array
    private val gson = GsonBuilder()
        .registerTypeAdapter(ChatMessageDTO::class.java, JsonDeserializer { json, _, _ ->
            val obj = json.asJsonObject
            ChatMessageDTO(
                id             = obj.get("id")?.asLongOrNull(),
                senderUsername = obj.get("senderUsername")?.asStringOrNull() ?: "",
                senderName     = obj.get("senderName")?.asStringOrNull(),
                senderRole     = obj.get("senderRole")?.asStringOrNull(),
                content        = obj.get("content")?.asStringOrNull() ?: "",
                sentAt         = obj.get("sentAt")?.toDateString(),
            )
        })
        .create()

    init {
        viewModelScope.launch { loadInitialMessages() }
        connectWs()
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────

    private fun connectWs() {
        stomp?.disconnect()
        stomp = StompClient(
            client        = ApiClient.wsClient(),
            url           = ApiClient.wsNativeUrl,
            token         = prefs.authToken,
            onConnected   = {
                _uiState.update { it.copy(connected = true) }
                stomp?.subscribe("/topic/chat")
            },
            onMessage     = { dest, body ->
                if (dest == "/topic/chat") appendMessage(body)
            },
            onDisconnected = {
                _uiState.update { it.copy(connected = false) }
                scheduleReconnect()
            },
        )
        stomp?.connect()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(5_000)
            connectWs()
        }
    }

    private fun appendMessage(json: String) {
        runCatching {
            val msg = gson.fromJson(json, ChatMessageDTO::class.java)
            _uiState.update { state ->
                // Deduplicate by id; id-less messages (shouldn't happen) always append
                if (msg.id != null && state.messages.any { it.id == msg.id }) state
                else state.copy(messages = state.messages + msg)
            }
        }
    }

    // ── REST fallback (initial load + send) ───────────────────────────────────

    private suspend fun loadInitialMessages() {
        runCatching {
            val res = ApiClient.service.getChatMessages(ApiClient.bearer(prefs.authToken))
            if (res.isSuccessful) {
                _uiState.update { it.copy(messages = res.body()?.data ?: emptyList()) }
            }
        }
    }

    fun setInput(text: String) = _uiState.update { it.copy(input = text) }

    fun sendMessage() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _uiState.value.sending) return
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, input = "") }
            runCatching {
                ApiClient.service.sendMessage(
                    ApiClient.bearer(prefs.authToken),
                    SendMessageRequest(text),
                )
                // The backend broadcasts to /topic/chat; WebSocket picks it up automatically.
            }
            _uiState.update { it.copy(sending = false) }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        reconnectJob?.cancel()
        stomp?.disconnect()
    }

    // ── State ─────────────────────────────────────────────────────────────────

    data class ChatUiState(
        val messages:   List<ChatMessageDTO> = emptyList(),
        val input:      String               = "",
        val sending:    Boolean              = false,
        val myUsername: String               = "",
        val connected:  Boolean              = false,
    )
}

// ── Gson helpers ──────────────────────────────────────────────────────────────

private fun JsonElement?.asLongOrNull(): Long? =
    runCatching { if (this == null || isJsonNull) null else asLong }.getOrNull()

private fun JsonElement?.asStringOrNull(): String? =
    runCatching { if (this == null || isJsonNull) null else asString }.getOrNull()

/**
 * Converts a JsonElement that holds a date to an ISO-like string.
 *
 * Spring Boot may serialize LocalDateTime as:
 *   • ISO string  "2025-05-30T10:30:00"
 *   • Array       [2025, 5, 30, 10, 30, 0]
 *
 * Both are normalized to "2025-05-30T10:30:00".
 */
private fun JsonElement.toDateString(): String? = runCatching {
    when {
        isJsonNull -> null
        isJsonPrimitive -> asString
        isJsonArray -> {
            val a = asJsonArray
            if (a.size() < 6) null
            else "%04d-%02d-%02dT%02d:%02d:%02d".format(
                a[0].asInt, a[1].asInt, a[2].asInt,
                a[3].asInt, a[4].asInt, a[5].asInt,
            )
        }
        else -> null
    }
}.getOrNull()
