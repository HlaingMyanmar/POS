package com.sspd.servicemgmt.core.tracking

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null
)
