package com.sspd.servicemgmt.core.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.sspd.servicemgmt.R
import com.sspd.servicemgmt.MainActivity
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TechnicianLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationClient: LocationClient
    private lateinit var store: PendingPingStore
    private var callback: LocationCallback? = null
    private var lastHeartbeatAt = 0L

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationClient(this)
        store = PendingPingStore(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            VisitTracker.stopServiceOnly(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val prefs = PreferenceManager(this)
        val visitId = prefs.activeVisitId
        if (visitId <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification(prefs.activeVisitJobNo, prefs.activeVisitCustomerName)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startUpdates(visitId, prefs.activeVisitStatus == "ON_SITE")
        return START_STICKY
    }

    private fun startUpdates(visitId: Long, onSite: Boolean) {
        callback?.let { locationClient.stopUpdates(it) }
        val interval = if (onSite) 180_000L else 45_000L
        callback = locationClient.startUpdates(interval, 30f) { fix ->
            scope.launch { handleFix(visitId, fix) }
        }
    }

    private suspend fun handleFix(visitId: Long, fix: LocationFix) {
        val ping = fix.toPing()
        store.enqueue(visitId, ping)
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatAt < 20_000L) return
        lastHeartbeatAt = now
        flush(visitId)
    }

    private suspend fun flush(visitId: Long) {
        val pending = store.pending(visitId)
        if (pending.isEmpty()) return
        val prefs = PreferenceManager(this)
        val token = ApiClient.bearer(prefs.authToken)
        runCatching {
            val res = if (pending.size == 1) {
                ApiClient.service.pingTechnicianVisit(token, visitId, pending.first())
            } else {
                ApiClient.service.pingTechnicianVisitBatch(token, visitId, pending)
            }
            if (res.isSuccessful) {
                store.remove(pending.map { it.clientPingId })
                res.body()?.data?.let { VisitTracker.onServerVisit(it) }
            }
        }
    }

    override fun onDestroy() {
        callback?.let { locationClient.stopUpdates(it) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Outdoor tracking", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(jobNo: String, customer: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TechnicianLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("$jobNo · $customer သို့ သွားနေသည်")
            .setContentText("Outdoor visit tracking ဖွင့်ထားသည်")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Tracking ရပ်ရန်", stop)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "technician_location"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "stop_tracking"

        fun start(context: Context) {
            val intent = Intent(context, TechnicianLocationService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TechnicianLocationService::class.java))
        }
    }
}
