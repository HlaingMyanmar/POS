package com.sspd.servicemgmt.core.util

import android.content.Context
import android.util.Base64
import com.sspd.servicemgmt.core.security.KeystoreSessionStore
import com.sspd.servicemgmt.feature.auth.CustomerAuthPolicy
import java.io.File

class PreferenceManager(context: Context) {
    private val app = context.applicationContext
    private val p = app.getSharedPreferences("sspd_customer", Context.MODE_PRIVATE)
    private val secrets = KeystoreSessionStore(app, p)
    private val logoFile get() = File(app.filesDir, "company_logo.bin")
    private val logoMeta get() = File(app.filesDir, "company_logo.meta")

    init {
        secrets.migrateLegacy("auth_token")
    }

    var serverUrl: String
        get() {
            val stored = p.getString("server_url", "") ?: ""
            val migrated = migrateLegacyServerUrl(stored)
            if (migrated != stored) p.edit().putString("server_url", migrated).apply()
            return migrated
        }
        set(v) { p.edit().putString("server_url", migrateLegacyServerUrl(v)).apply() }

    var authToken: String
        get() = secrets.get("auth_token")
        set(v) { secrets.put("auth_token", v) }

    var displayName: String
        get() = p.getString("display_name", "") ?: ""
        set(v) { p.edit().putString("display_name", v).apply() }

    var phone: String
        get() = p.getString("phone", "") ?: ""
        set(v) { p.edit().putString("phone", v).apply() }

    var address: String
        get() = p.getString("address", "") ?: ""
        set(v) { p.edit().putString("address", v).apply() }

    var email: String
        get() = p.getString("email", "") ?: ""
        set(v) { p.edit().putString("email", v).apply() }

    var needsProfile: Boolean
        get() = p.getBoolean("needs_profile", false)
        set(v) { p.edit().putBoolean("needs_profile", v).apply() }

    var customerId: Int
        get() = p.getInt("customer_id", 0)
        set(v) { p.edit().putInt("customer_id", v).apply() }

    var biometricEnabled: Boolean
        get() = p.getBoolean("biometric_enabled", false)
        set(v) { p.edit().putBoolean("biometric_enabled", v).apply() }

    /** Order to open on Activity tab after place / notification deep link. */
    var focusOrderId: Int
        get() = p.getInt("focus_order_id", 0)
        set(v) { p.edit().putInt("focus_order_id", v).apply() }

    /** Open order history after FCM / notification tap. */
    var openOrdersTab: Boolean
        get() = p.getBoolean("open_orders_tab", false)
        set(v) { p.edit().putBoolean("open_orders_tab", v).apply() }

    fun consumeOpenOrdersTab(): Boolean {
        val open = openOrdersTab
        if (open) openOrdersTab = false
        return open
    }

    fun clearFocusOrderId() {
        p.edit().remove("focus_order_id").apply()
    }

    /** Cached Company Settings name/tagline for login (survives logout). */
    var companyName: String
        get() = p.getString("company_name", "") ?: ""
        set(v) { p.edit().putString("company_name", v).apply() }

    var companyTagline: String
        get() = p.getString("company_tagline", "") ?: ""
        set(v) { p.edit().putString("company_tagline", v).apply() }

    var companyHasLogo: Boolean
        get() = p.getBoolean("company_has_logo", false) || logoFile.exists()
        set(v) { p.edit().putBoolean("company_has_logo", v).apply() }

    /** Absolute logo URL (optional cache-bust query already included by caller). */
    var companyLogoUrl: String
        get() = p.getString("company_logo_url", "") ?: ""
        set(v) { p.edit().putString("company_logo_url", v).apply() }

    /** Legacy text getter — returns data-URI only if binary cache missing. */
    var companyLogoBase64: String
        get() = ""
        set(v) {
            if (v.isBlank()) {
                clearCompanyLogo()
            } else {
                saveCompanyLogoBase64(v)
            }
        }

    fun companyLogoBytes(): ByteArray? {
        val cached = runCatching { if (logoFile.exists()) logoFile.readBytes() else null }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
        if (cached != null) return cached
        // Migrate old text cache (data-URI / base64) if present.
        val legacy = File(app.filesDir, "company_logo.b64")
        val migrated = runCatching {
            if (!legacy.exists()) return@runCatching null
            decodeLogoBase64(legacy.readText())
        }.getOrNull()
        if (migrated != null) {
            saveCompanyLogoBytes(migrated)
            runCatching { legacy.delete() }
            return migrated
        }
        return null
    }

    fun saveCompanyLogoBytes(bytes: ByteArray?) {
        runCatching {
            if (bytes == null || bytes.isEmpty()) {
                clearCompanyLogo()
            } else {
                logoFile.writeBytes(bytes)
                companyHasLogo = true
            }
        }
    }

    fun saveCompanyLogoBase64(raw: String) {
        val bytes = decodeLogoBase64(raw) ?: return
        saveCompanyLogoBytes(bytes)
    }

    fun clearCompanyLogo() {
        runCatching { logoFile.delete() }
        runCatching { logoMeta.delete() }
        companyHasLogo = false
        companyLogoUrl = ""
    }

    /** Epoch millis of last user interaction (for idle auto-logout). */
    var lastActiveAt: Long
        get() = p.getLong("last_active_at", 0L)
        set(v) { p.edit().putLong("last_active_at", v).apply() }

    fun touchSession() {
        lastActiveAt = System.currentTimeMillis()
    }

    fun isSessionIdle(timeoutMs: Long = IDLE_TIMEOUT_MS): Boolean {
        return CustomerAuthPolicy.isIdle(
            hasToken = authToken.isNotBlank(),
            lastActiveAtMillis = lastActiveAt,
            nowMillis = System.currentTimeMillis(),
            timeoutMillis = timeoutMs
        )
    }

    /** Clears login session but keeps server URL and company branding. */
    fun clearSession() {
        val url = serverUrl
        val name = companyName
        val tagline = companyTagline
        val hasLogo = companyHasLogo
        val logoUrl = companyLogoUrl
        val logoBytes = companyLogoBytes()
        secrets.clear()
        check(p.edit().clear().commit()) { "Unable to clear customer session" }
        if (url.isNotBlank()) serverUrl = url
        if (name.isNotBlank()) companyName = name
        if (tagline.isNotBlank()) companyTagline = tagline
        companyHasLogo = hasLogo
        companyLogoUrl = logoUrl
        saveCompanyLogoBytes(logoBytes)
    }

    fun clear() = clearSession()

    private fun migrateLegacyServerUrl(raw: String): String {
        val t = raw.trim()
        if (t.isBlank()) return t
        val host = t.removePrefix("https://").removePrefix("http://")
            .substringBefore("/").substringBefore(":")
        if (host == "118.27.151.89" || host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) {
            return "https://sspdmyanmar.com"
        }
        return t
    }

    companion object {
        /** Customer app idle timeout — 10 minutes is a good balance for shopping + account safety. */
        const val IDLE_TIMEOUT_MS = 10L * 60L * 1000L
        const val IDLE_TIMEOUT_MINUTES = 10

        fun decodeLogoBase64(raw: String): ByteArray? {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null
            val payload = if (trimmed.startsWith("data:", ignoreCase = true)) {
                trimmed.substringAfter(',', missingDelimiterValue = "").ifBlank { return null }
            } else trimmed
            return runCatching {
                Base64.decode(payload.replace("\\s".toRegex(), ""), Base64.DEFAULT)
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
    }
}
