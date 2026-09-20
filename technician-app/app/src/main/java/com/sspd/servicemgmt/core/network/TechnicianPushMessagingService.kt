package com.sspd.servicemgmt.core.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sspd.servicemgmt.R
import com.sspd.servicemgmt.TechnicianMainActivity
import com.sspd.servicemgmt.core.util.PreferenceManager

object TechnicianPushRegistration {
    private const val PREFS = "technician_fcm"
    private const val TOPIC_PREFIX = "technician_staff_"

    fun sync(context: Context) {
        val app = context.applicationContext
        if (FirebaseApp.getApps(app).isEmpty()) return
        val staffId = PreferenceManager(app).staffId
        val store = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldTopic = store.getString("topic", null)
        val newTopic = staffId.takeIf { it > 0 }?.let { "$TOPIC_PREFIX$it" }
        if (oldTopic == newTopic) return
        if (!oldTopic.isNullOrBlank()) FirebaseMessaging.getInstance().unsubscribeFromTopic(oldTopic)
        if (newTopic != null) {
            FirebaseMessaging.getInstance().subscribeToTopic(newTopic).addOnSuccessListener {
                store.edit().putString("topic", newTopic).apply()
            }
        } else {
            store.edit().remove("topic").apply()
        }
    }

    fun unregister(context: Context) {
        val app = context.applicationContext
        if (FirebaseApp.getApps(app).isEmpty()) return
        val store = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        store.getString("topic", null)?.let { FirebaseMessaging.getInstance().unsubscribeFromTopic(it) }
        store.edit().clear().apply()
    }
}

class TechnicianPushMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = TechnicianPushRegistration.sync(applicationContext)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val jobId = data["jobId"]?.toIntOrNull()
        val title = message.notification?.title ?: data["title"] ?: "New service job"
        val body = message.notification?.body ?: data["body"] ?: data["jobNo"] ?: "Open Technician App to view"
        showNotification(jobId, title, body)
    }

    private fun showNotification(jobId: Int?, title: String, body: String) {
        val channelId = "technician_new_jobs"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "Service job updates", NotificationManager.IMPORTANCE_HIGH)
        )
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(this, TechnicianMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            jobId?.let { putExtra("service_job_id", it) }
        }
        val pending = PendingIntent.getActivity(
            this, jobId ?: 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(this).notify(20_000 + (jobId ?: 0), notification)
    }
}
