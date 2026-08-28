package com.sspd.servicemgmt.core.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sspd.servicemgmt.R
import com.sspd.servicemgmt.TechnicianMainActivity
import com.sspd.servicemgmt.core.util.PreferenceManager

class TrackingBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED
            && intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = PreferenceManager(context)
        if (prefs.activeVisitId <= 0L || prefs.authToken.isBlank()) return

        // Android 12–15 may block a location foreground service started directly
        // from the background. Preserve the visit and let the user resume by tap.
        prefs.trackingPaused = true
        PendingPingSyncWorker.enqueue(context)
        showResumeNotification(context, prefs)
    }

    private fun showResumeNotification(context: Context, prefs: PreferenceManager) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                TechnicianLocationService.CHANNEL_ID,
                "Outdoor tracking",
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        val openApp = Intent(context, TechnicianMainActivity::class.java).apply {
            action = ACTION_RESUME_AFTER_REBOOT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            RESUME_REQUEST_CODE,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val detail = listOf(prefs.activeVisitJobNo, prefs.activeVisitCustomerName)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        val notification = NotificationCompat.Builder(
            context,
            TechnicianLocationService.CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("Outdoor tracking ပြန်စရန် လိုအပ်ပါသည်")
            .setContentText(detail.ifBlank { "Active visit ကိုဆက်ရန် နှိပ်ပါ" })
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${detail.ifBlank { "Active visit" }}\nဖုန်းပြန်ဖွင့်ထားသဖြင့် GPS tracking ပြန်စရန် နှိပ်ပါ"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(
            TechnicianLocationService.NOTIFICATION_ID + 1,
            notification
        )
    }

    companion object {
        const val ACTION_RESUME_AFTER_REBOOT =
            "com.sspd.servicemgmt.action.RESUME_TRACKING_AFTER_REBOOT"
        private const val RESUME_REQUEST_CODE = 43
    }
}
