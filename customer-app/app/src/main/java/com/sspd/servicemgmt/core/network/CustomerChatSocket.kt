package com.sspd.servicemgmt.core.network

import android.os.Handler
import android.os.Looper
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.sspd.servicemgmt.core.util.PreferenceManager
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

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
                            val message = parseChatPayload(payload)
                            handler.post {
                                if (stopped || socket !== webSocket) return@post
                                if (message != null) onMessageReceived(message)
                                else onConnected() // parse miss → reload history
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

    companion object {
        fun parseChatPayload(payload: String): ChatMessage? = runCatching {
            val value = JsonParser.parseString(payload).asJsonObject
            val text = firstString(value.get("text"), value.get("content")).orEmpty().trim()
            if (text.isEmpty()) return@runCatching null
            val role = firstString(value.get("senderRole"))
            val isAdmin = when {
                value.has("isFromAdmin") && value.get("isFromAdmin").isJsonPrimitive ->
                    value.get("isFromAdmin").asBoolean
                else -> !role.equals("CUSTOMER", ignoreCase = true)
            }
            ChatMessage(
                id = firstInt(value.get("id")),
                senderId = firstInt(value.get("customerId"), value.get("senderId")),
                text = text,
                createdAt = firstTime(value.get("createdAt"), value.get("sentAt")),
                isFromAdmin = isAdmin,
                senderName = firstString(value.get("senderName"))
            )
        }.getOrNull()

        private fun firstString(vararg elements: JsonElement?): String? {
            for (el in elements) {
                if (el == null || el.isJsonNull || !el.isJsonPrimitive) continue
                val text = el.asString
                if (text.isNotBlank()) return text
            }
            return null
        }

        private fun firstInt(vararg elements: JsonElement?): Int {
            for (el in elements) {
                if (el == null || el.isJsonNull || !el.isJsonPrimitive || !el.asJsonPrimitive.isNumber) continue
                return el.asLong.toInt()
            }
            return 0
        }

        private fun firstTime(vararg elements: JsonElement?): String? {
            for (el in elements) {
                if (el == null || el.isJsonNull) continue
                when {
                    el.isJsonPrimitive -> return el.asString
                    el.isJsonArray -> {
                        val parts = el.asJsonArray.mapNotNull {
                            if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asInt else null
                        }
                        if (parts.size >= 3) {
                            val y = parts[0]
                            val m = parts[1]
                            val d = parts[2]
                            val h = parts.getOrElse(3) { 0 }
                            val mi = parts.getOrElse(4) { 0 }
                            val s = parts.getOrElse(5) { 0 }
                            return "%04d-%02d-%02dT%02d:%02d:%02d".format(y, m, d, h, mi, s)
                        }
                    }
                }
            }
            return null
        }
    }
}
