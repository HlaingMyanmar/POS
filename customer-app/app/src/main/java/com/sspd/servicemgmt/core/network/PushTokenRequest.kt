package com.sspd.servicemgmt.core.network

data class PushTokenRequest(val token: String, val platform: String = "ANDROID")
