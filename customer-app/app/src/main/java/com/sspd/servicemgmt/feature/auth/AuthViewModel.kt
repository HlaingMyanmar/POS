package com.sspd.servicemgmt.feature.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.ApiResponse
import com.sspd.servicemgmt.core.network.CustomerAuthResponse
import com.sspd.servicemgmt.core.network.ForgotPasswordRequest
import com.sspd.servicemgmt.core.network.GoogleLoginRequest
import com.sspd.servicemgmt.core.network.LoginRequest
import com.sspd.servicemgmt.core.network.ProfileUpdateRequest
import com.sspd.servicemgmt.core.network.RegisterRequest
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = PreferenceManager(app)
    private var loginGeneration = 0
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    var success by mutableStateOf(false); private set
    var profileSaved by mutableStateOf(false); private set
    var companyLogo by mutableStateOf(prefs.companyLogoUrl); private set
    var companyLogoBytes by mutableStateOf(prefs.companyLogoBytes()); private set
    var companyName by mutableStateOf(prefs.companyName.ifBlank { BuildConfig.APP_DISPLAY_NAME }); private set
    var companyTagline by mutableStateOf(
        prefs.companyTagline.ifBlank { "Service နှင့် Shopping ကို တစ်နေရာတည်းမှာ" }
    ); private set

    fun fail(message: String) { error = message; notice = null }
    fun clearFeedback() { error = null; notice = null }

    /** Clears stale login success so AuthScreen does not auto-skip after logout. */
    fun resetForLoginScreen() {
        loginGeneration++
        loading = false
        error = null
        notice = null
        success = false
        profileSaved = false
    }

    fun consumeSuccess() { success = false }

    fun loadBranding(server: String) {
        viewModelScope.launch {
            try {
                ApiClient.setBaseUrl(server)
                val res = ApiClient.service.branding()
                val data = res.body()?.data
                if (res.isSuccessful && data != null) {
                    val name = data.companyName.orEmpty().trim()
                    val tagline = data.taglineMm.orEmpty().trim()
                    if (name.isNotBlank()) {
                        prefs.companyName = name
                        companyName = name
                    }
                    if (tagline.isNotBlank()) {
                        prefs.companyTagline = tagline
                        companyTagline = tagline
                    }

                    val hasLogo = data.hasLogo == true || !data.logoBase64.isNullOrBlank()
                    if (hasLogo) {
                        val url = ApiClient.brandingLogoUrl()
                        prefs.companyLogoUrl = url
                        prefs.companyHasLogo = true
                        companyLogo = url
                        // Prefer binary image endpoint; fall back to embedded base64 if present.
                        val downloaded = runCatching {
                            val logoRes = ApiClient.service.brandingLogo()
                            if (logoRes.isSuccessful) logoRes.body()?.bytes() else null
                        }.getOrNull()
                        val bytes = downloaded?.takeIf { it.isNotEmpty() }
                            ?: PreferenceManager.decodeLogoBase64(data.logoBase64.orEmpty())
                        if (bytes != null) {
                            prefs.saveCompanyLogoBytes(bytes)
                            companyLogoBytes = bytes
                        }
                    } else if (!data.logoBase64.isNullOrBlank()) {
                        prefs.saveCompanyLogoBase64(data.logoBase64)
                        companyLogoBytes = prefs.companyLogoBytes()
                        companyLogo = ApiClient.brandingLogoUrl()
                    }
                }
            } catch (_: Exception) {
                // Keep cached / default branding
                companyLogo = prefs.companyLogoUrl.ifBlank { ApiClient.brandingLogoUrl() }
                companyLogoBytes = prefs.companyLogoBytes()
            }
        }
    }

    fun rememberedUrl() = prefs.serverUrl.ifBlank { BuildConfig.DEFAULT_BASE_URL }
    fun googleConfigured() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    fun applySession(
        data: CustomerAuthResponse,
        fallbackName: String = "",
        fallbackPhone: String = "",
        fallbackAddress: String = ""
    ) {
        prefs.authToken = data.accessToken ?: return
        prefs.displayName = data.name ?: fallbackName
        prefs.phone = data.phone ?: fallbackPhone
        prefs.address = data.address ?: fallbackAddress
        prefs.email = data.email ?: ""
        prefs.needsProfile = data.needsProfile == true
        prefs.customerId = data.customerId ?: 0
        success = true
    }

    fun submit(
        register: Boolean,
        name: String,
        phone: String,
        password: String,
        address: String,
        email: String,
        server: String
    ) {
        val attemptGeneration = loginGeneration
        viewModelScope.launch {
            loading = true
            error = null
            notice = null
            try {
                ApiClient.setBaseUrl(server)
                prefs.serverUrl = server
                val res = if (register) {
                    ApiClient.service.register(RegisterRequest(name, phone, password, address, email))
                } else {
                    val id = phone.trim()
                    ApiClient.service.login(LoginRequest(phone = id, login = id, password = password))
                }
                val body = res.body()
                val data = body?.data
                if (res.isSuccessful && body?.success == true && data?.resetSent == true) {
                    notice = body.message.ifBlank {
                        "ဤ email ဖြင့် အကောင့်ရှိပြီးသား။ Password ပြန်သတ်မှတ်ရန် link ကို email သို့ ပို့ပြီးပါပြီ"
                    }
                } else if (res.isSuccessful && body?.success == true && !data?.accessToken.isNullOrBlank()) {
                    if (attemptGeneration == loginGeneration) applySession(data!!, name, phone, address)
                } else {
                    error = apiMessage(res, if (register) "အကောင့်ဖွင့်မရပါ" else "အကောင့်ဝင်မရပါ")
                }
            } catch (e: Exception) {
                error = e.message ?: "ချိတ်ဆက်မရပါ"
            } finally {
                loading = false
            }
        }
    }

    fun forgot(email: String, server: String) {
        viewModelScope.launch {
            loading = true
            error = null
            notice = null
            try {
                ApiClient.setBaseUrl(server)
                prefs.serverUrl = server
                val res = ApiClient.service.forgotPassword(ForgotPasswordRequest(email))
                val body = res.body()
                if (res.isSuccessful && body?.success == true) {
                    notice = body.message.ifBlank { "Password ပြန်သတ်မှတ်ရန် link ကို email သို့ ပို့ပြီးပါပြီ" }
                } else {
                    error = apiMessage(res, "Email မပို့နိုင်ပါ")
                }
            } catch (e: Exception) {
                error = e.message ?: "ချိတ်ဆက်မရပါ"
            } finally {
                loading = false
            }
        }
    }

    fun google(idToken: String, server: String) {
        val attemptGeneration = loginGeneration
        viewModelScope.launch {
            loading = true
            error = null
            try {
                ApiClient.setBaseUrl(server)
                prefs.serverUrl = server
                val res = ApiClient.service.googleLogin(GoogleLoginRequest(idToken))
                val body = res.body()
                val data = body?.data
                if (res.isSuccessful && body?.success == true && !data?.accessToken.isNullOrBlank()) {
                    if (attemptGeneration == loginGeneration) applySession(data!!)
                } else {
                    error = apiMessage(res, "Gmail ဝင်မရပါ")
                }
            } catch (e: Exception) {
                error = e.message ?: "Gmail ဝင်မရပါ"
            } finally {
                loading = false
            }
        }
    }

    fun completeProfile(name: String, phone: String, address: String) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val res = ApiClient.service.updateProfile(
                    ApiClient.bearer(prefs.authToken),
                    ProfileUpdateRequest(name.ifBlank { null }, phone, address)
                )
                val body = res.body()
                val data = body?.data
                if (res.isSuccessful && body?.success == true && data != null) {
                    applySession(data, name, phone, address)
                    prefs.needsProfile = false
                    profileSaved = true
                } else {
                    error = apiMessage(res, "သိမ်းမရပါ")
                }
            } catch (e: Exception) {
                error = e.message ?: "သိမ်းမရပါ"
            } finally {
                loading = false
            }
        }
    }

    private fun apiMessage(res: Response<*>, fallback: String): String {
        res.body()?.let { body ->
            if (body is ApiResponse<*> && body.message.isNotBlank()) {
                return body.message
            }
        }
        val raw = res.errorBody()?.string().orEmpty()
        if (raw.isNotBlank()) {
            runCatching { JSONObject(raw).optString("message") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return fallback
    }
}
