package com.sspd.servicemgmt.core.tracking

import com.sspd.servicemgmt.core.network.LocationPingRequest
import java.time.OffsetDateTime
import java.util.UUID

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val recordedAt: String = OffsetDateTime.now().toString()
) {
    fun toPing(): LocationPingRequest = LocationPingRequest(
        clientPingId = UUID.randomUUID().toString(),
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        recordedAt = recordedAt
    )
}
