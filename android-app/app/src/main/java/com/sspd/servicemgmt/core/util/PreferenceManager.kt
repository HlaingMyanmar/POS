package com.sspd.servicemgmt.core.util

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

    var refreshToken: String
        get() = p.getString("refresh_token", "") ?: ""
        set(v) { p.edit().putString("refresh_token", v).apply() }

    var staffId: Int
        get() = p.getInt("staff_id", 0)
        set(v) { p.edit().putInt("staff_id", v).apply() }

    var rolesStr: String
        get() = p.getString("roles", "") ?: ""
        set(v) { p.edit().putString("roles", v).apply() }

    var phone: String
        get() = p.getString("phone", "") ?: ""
        set(v) { p.edit().putString("phone", v).apply() }

    fun hasPermission(perm: String) = permissionsStr
        .split(',')
        .map { it.trim() }
        .any { it == perm }

    fun hasRole(role: String): Boolean {
        val wanted = role.removePrefix("ROLE_").uppercase()
        return rolesStr.split(',')
            .map { it.trim().removePrefix("ROLE_").uppercase() }
            .any { it == wanted }
    }

    fun isTechnician() = hasRole("TECHNICIAN")

    fun clear() = p.edit().clear().apply()
}
