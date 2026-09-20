package com.sspd.servicemgmt

import android.app.Application
import com.sspd.servicemgmt.core.network.ApiClient

class TechnicianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.initialize(this)
        TechnicianJobAlerts.createChannel(this)
    }
}
