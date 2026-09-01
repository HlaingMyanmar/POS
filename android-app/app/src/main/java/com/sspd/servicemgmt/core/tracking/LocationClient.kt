package com.sspd.servicemgmt.core.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationClient(context: Context) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)

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

    @SuppressLint("MissingPermission")
    fun startUpdates(
        intervalMs: Long,
        minDistanceMeters: Float,
        onFix: (LocationFix) -> Unit
    ): LocationCallback {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs * 0.6).toLong())
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                if (location.accuracy > 100f) return
                onFix(
                    LocationFix(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy.toDouble()
                    )
                )
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        return callback
    }

    fun stopUpdates(callback: LocationCallback) {
        fused.removeLocationUpdates(callback)
    }
}
