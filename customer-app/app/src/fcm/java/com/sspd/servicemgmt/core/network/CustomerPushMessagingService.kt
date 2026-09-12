package com.sspd.servicemgmt.core.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.*

object CustomerPushRegistration {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun initialize(context: Context) {
        val app = context.applicationContext
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("customer_orders", "Order updates", NotificationManager.IMPORTANCE_HIGH)
        )
        if (FirebaseApp.getApps(app).isEmpty()) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { register(app, it) }
    }
    fun register(context: Context, token: String) {
        val prefs = PreferenceManager(context.applicationContext); val auth = prefs.authToken
        if (auth.isBlank() || token.isBlank()) return
        scope.launch { runCatching { ApiClient.service.registerPushDevice(ApiClient.bearer(auth), PushTokenRequest(token)) } }
    }
    fun unregister(context: Context) {
        if (FirebaseApp.getApps(context.applicationContext).isEmpty()) return
        val auth = PreferenceManager(context).authToken; if (auth.isBlank()) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            scope.launch { runCatching { ApiClient.service.unregisterPushDevice(ApiClient.bearer(auth), PushTokenRequest(token)) } }
        }
    }
}

class CustomerPushMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = CustomerPushRegistration.register(applicationContext, token)
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data; val orderId = data["orderId"]?.toIntOrNull()
        CustomerOrderAlerts.show(applicationContext, CustomerNotification(
            id = orderId?.let { -it } ?: -1, channel = "CUSTOMER_ORDER",
            note = data["note"] ?: message.notification?.body, orderId = orderId,
            orderNo = data["orderNo"] ?: message.notification?.title, status = data["status"]
        ))
    }
}
