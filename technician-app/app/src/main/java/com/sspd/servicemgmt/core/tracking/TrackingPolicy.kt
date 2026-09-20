package com.sspd.servicemgmt.core.tracking

object TrackingPolicy {
    const val TRAVEL_UPDATE_INTERVAL_MILLIS = 45_000L
    const val ON_SITE_UPDATE_INTERVAL_MILLIS = 180_000L
    const val HEARTBEAT_INTERVAL_MILLIS = 20_000L

    fun updateIntervalMillis(onSite: Boolean): Long =
        if (onSite) ON_SITE_UPDATE_INTERVAL_MILLIS else TRAVEL_UPDATE_INTERVAL_MILLIS

    fun shouldFlush(lastHeartbeatAtMillis: Long, nowMillis: Long): Boolean =
        lastHeartbeatAtMillis <= 0L ||
            nowMillis - lastHeartbeatAtMillis >= HEARTBEAT_INTERVAL_MILLIS
}
