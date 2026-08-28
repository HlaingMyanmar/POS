package com.sspd.servicemgmt.core.tracking

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.util.PreferenceManager

class PendingPingSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = PendingPingStore(applicationContext)
        val visitIds = store.visitIds()
        if (visitIds.isEmpty()) return Result.success()

        val prefs = PreferenceManager(applicationContext)
        if (prefs.authToken.isBlank()) return Result.retry()
        if (prefs.serverUrl.isNotBlank()) ApiClient.setBaseUrl(prefs.serverUrl)

        val allUploaded = visitIds.all { visitId ->
            VisitTracker.flushPending(applicationContext, visitId)
        }
        return if (allUploaded) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "technician-pending-location-pings"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PendingPingSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
