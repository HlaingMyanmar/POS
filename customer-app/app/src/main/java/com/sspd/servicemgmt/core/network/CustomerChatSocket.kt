package com.sspd.servicemgmt.core.network

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.sspd.servicemgmt.core.util.PreferenceManager
import okhttp3.*
import java.util.concurrent.TimeUnit

/** Real-time chat socket for customer support. */
class CustomerChatSocket(
    private val prefs: PreferenceManager,
    private val onMessageReceived: (ChatMessage) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit = {}
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
        old?.close(1000, "Chat session closed")
    }

    fun sendMessage(text: String) {
        val msg = ChatRequest(text)
        val payload = Gson().toJson(msg)
        val frame = "SEND\nid:chat-send\ndestination:/app/chat/send\ncontent-type:application/json\n\n$payload\u0000"
        socket?.send(frame)
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
                            webSocket.send("SUBSCRIBE\nid:chat-sub\ndestination:/user/topic/chat\nack:auto\n\n\u0000")
                            handler.post { if (!stopped && socket === webSocket) onConnected() }
                        }
                        "MESSAGE" -> {
                            val payload = normalized.substringAfter("\n\n", "")
                            val message = runCatching {
                                Gson().fromJson(payload, ChatMessage::class.java)
                            }.getOrNull()
                            handler.post {
                                if (stopped || socket !== webSocket) return@post
                                message?.let { onMessageReceived(it) }
                            }
                        }
                        "ERROR" -> webSocket.close(1000, "STOMP error")
                    }
                }
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
