package com.sspd.servicemgmt.core.tracking

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationClient(context: Context) {
    private val fused = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    suspend fun current(): LocationFix = suspendCancellableCoroutine { cont ->
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location == null) {
                    cont.resumeWithException(IllegalStateException("GPS တည်နေရာ မရပါ"))
                } else {
                    cont.resume(
                        LocationFix(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy.toDouble()
                        )
                    )
                }
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
