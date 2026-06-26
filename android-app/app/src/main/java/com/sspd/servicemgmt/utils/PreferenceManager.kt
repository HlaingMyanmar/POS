package com.sspd.servicemgmt.utils

import android.content.Context

class PreferenceManager(context: Context) {
    private val p = context.getSharedPreferences("sspd_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = p.getString("server_url", "") ?: ""
        set(v) { p.edit().putString("server_url", v).apply() }

    var authToken: String
        get() = p.getString("auth_token", "") ?: ""
        set(v) { p.edit().putString("auth_token", v).apply() }

    var username: String
        get() = p.getString("username", "") ?: ""
        set(v) { p.edit().putString("username", v).apply() }

    var displayName: String
        get() = p.getString("display_name", "") ?: ""
        set(v) { p.edit().putString("display_name", v).apply() }

    var permissionsStr: String
        get() = p.getString("permissions", "") ?: ""
        set(v) { p.edit().putString("permissions", v).apply() }

    fun hasPermission(perm: String) = permissionsStr.contains(perm)

    fun clear() = p.edit().clear().apply()
}
