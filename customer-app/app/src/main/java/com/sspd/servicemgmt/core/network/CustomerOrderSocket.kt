package com.sspd.servicemgmt.core.network

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.sspd.servicemgmt.core.util.PreferenceManager
import okhttp3.*
import java.util.concurrent.TimeUnit

/** One authenticated STOMP socket while the customer home is mounted. */
class CustomerOrderSocket(
    private val prefs: PreferenceManager,
    private val onUpdate: (CustomerNotification) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit = {},
    private val onChatMessage: ((ChatMessage) -> Unit)? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private val client = ApiClient.socketClient()
    private var socket: WebSocket? = null
    private var stopped = true
    private val reconnect = Runnable { connect() }

    fun start() { stopped = false; connect() }
    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        val old = socket
        socket = null
        old?.close(1000, "Customer session closed")
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun connect() {
        if (stopped || socket != null || prefs.authToken.isBlank()) return
        val token = prefs.authToken
        val request = Request.Builder().url(ApiClient.socketUrl()).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            private val frames = StringBuilder()
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("CONNECT\naccept-version:1.2\nhost:customer\nAuthorization:Bearer " + token + "\nheart-beat:0,0\n\n\u0000")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                frames.append(text)
                if (frames.length > 262144) { webSocket.cancel(); return }
                while (true) {
                    val end = frames.indexOf("\u0000")
                    if (end < 0) break
                    val frame = frames.substring(0, end).trimStart('\n', '\r')
                    frames.delete(0, end + 1)
                    val normalized = frame.replace("\r\n", "\n")
                    when (normalized.substringBefore('\n')) {
                        "CONNECTED" -> {
                            webSocket.send("SUBSCRIBE\nid:customer-orders\ndestination:/user/topic/customer-orders\nack:auto\n\n\u0000")
                            if (onChatMessage != null) {
                                webSocket.send("SUBSCRIBE\nid:customer-chat\ndestination:/user/topic/chat\nack:auto\n\n\u0000")
                            }
                            handler.post { if (!stopped && socket === webSocket) onConnected() }
                        }
                        "MESSAGE" -> {
                            val destination = normalized.lineSequence()
                                .firstOrNull { it.startsWith("destination:") }
                                ?.removePrefix("destination:")
                                ?.trim()
                                .orEmpty()
                            val payload = normalized.substringAfter("\n\n", "")
                            handler.post {
                                if (stopped || socket !== webSocket) return@post
                                if (destination.contains("/chat")) {
                                    val chat = CustomerChatSocket.parseChatPayload(payload)
                                    if (chat != null) onChatMessage?.invoke(chat)
                                    else onConnected()
                                } else {
                                    val notification = runCatching {
                                        Gson().fromJson(payload, CustomerNotification::class.java)
                                    }.getOrNull()
                                    if (notification?.orderId != null) onUpdate(notification)
                                    else onConnected()
                                }
                            }
                        }
                        "ERROR" -> webSocket.close(1000, "STOMP error")
                    }
                }
                // Heartbeat-only frames must not accumulate indefinitely.
                if (frames.all { it == '\n' || it == '\r' }) frames.clear()
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = retry(webSocket)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = retry(webSocket)
        })
    }

    private fun retry(failedSocket: WebSocket) {
        handler.post {
            if (!stopped && socket === failedSocket) {
                socket = null
                onDisconnected()
                handler.removeCallbacks(reconnect)
                handler.postDelayed(reconnect, 5000)
            }
        }
    }
}
