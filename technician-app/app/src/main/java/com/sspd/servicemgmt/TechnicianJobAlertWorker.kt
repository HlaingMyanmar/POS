package com.sspd.servicemgmt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import java.util.concurrent.TimeUnit

object TechnicianJobAlerts {
    private const val CHANNEL_ID = "technician_new_jobs"
    private const val SEEN_PREFS = "technician_job_alerts"
    private const val SEEN_IDS = "seen_job_ids"

    fun createChannel(context: Context) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ဝန်ဆောင်မှုအလုပ်အသစ်",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Booking မှ Service Job ပြောင်းပြီး assign ရောက်လာသောအခါ"
            enableVibration(true)
            setSound(sound, audio)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    suspend fun checkAndNotify(context: Context): Boolean {
        val prefs = PreferenceManager(context)
        if (prefs.authToken.isBlank() || prefs.staffId <= 0) return true
        if (prefs.serverUrl.isNotBlank()) ApiClient.setBaseUrl(prefs.serverUrl)

        val response = ApiClient.service.getServiceJobs(
            auth = ApiClient.bearer(prefs.authToken),
            size = 100,
            dateFrom = "",
            dateTo = ""
        )
        if (!response.isSuccessful) return false

        val assignedBookingJobs = response.body()?.data?.content.orEmpty()
            .filter { it.id != null && it.assignedStaffId == prefs.staffId && !it.bookingNo.isNullOrBlank() }
        val store = context.getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE)
        val seen = store.getStringSet(SEEN_IDS, emptySet()).orEmpty().toMutableSet()
        val newJobs = assignedBookingJobs.filter { it.id.toString() !in seen }

        newJobs.takeLast(5).forEach { showNotification(context, it) }
        seen += assignedBookingJobs.mapNotNull { it.id?.toString() }
        // Bound stored history while retaining the newest server results.
        store.edit().putStringSet(SEEN_IDS, seen.toList().takeLast(500).toSet()).apply()
        return true
    }

    private fun showNotification(context: Context, job: ServiceJobDTO) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val intent = Intent(context, TechnicianMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("service_job_id", job.id)
        }
        val pending = PendingIntent.getActivity(
            context, job.id ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = "ဝန်ဆောင်မှုအလုပ်အသစ် ရောက်ရှိပါပြီ"
        val detail = listOfNotNull(
            job.jobNo,
            job.customerName,
            job.itemName ?: job.deviceType
        ).joinToString(" • ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$detail\nBooking: ${job.bookingNo}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(10_000 + (job.id ?: 0), notification)
    }
}

class TechnicianJobAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (TechnicianJobAlerts.checkAndNotify(applicationContext)) Result.success()
        else Result.retry()
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TechnicianJobAlertWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "technician-new-job-alert",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
