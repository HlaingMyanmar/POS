package com.sspd.servicemgmt.core.network

import android.content.Context

/** Keeps non-Firebase builds working until google-services.json is installed. */
object CustomerPushBridge {
    private fun call(context: Context, method: String) {
        runCatching {
            val type = Class.forName("com.sspd.servicemgmt.core.network.CustomerPushRegistration")
            val instance = type.getField("INSTANCE").get(null)
            type.getMethod(method, Context::class.java).invoke(instance, context.applicationContext)
        }
    }
    fun initialize(context: Context) = call(context, "initialize")
    fun unregister(context: Context) = call(context, "unregister")
}
