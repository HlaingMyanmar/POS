package com.sspd.servicemgmt.feature.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.LoginRequest
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun rememberedUsername(): String = prefs.rememberedUsername

    fun login(username: String, password: String, rememberMe: Boolean = false) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "အသုံးပြုသူအမည်နှင့် စကားဝှက် ဖြည့်ပါ") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = "") }
            try {
                val response = ApiClient.service.login(
                    LoginRequest(username.trim(), password.trim())
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val auth = response.body()?.data
                    if (auth == null) {
                        _uiState.update {
                            it.copy(loading = false, error = "အကောင့်အချက်အလက် ရယူ၍မရပါ။ ပြန်ကြိုးစားပါ")
                        }
                        return@launch
                    }

                    prefs.authToken = auth.accessToken
                    prefs.refreshToken = auth.refreshToken ?: ""
                    prefs.username = auth.username
                    prefs.displayName = auth.name ?: auth.username
                    prefs.phone = auth.phone ?: ""
                    prefs.staffId = auth.staffId ?: 0
                    prefs.rolesStr = auth.roles.joinToString(",")
                    prefs.permissionsStr = auth.permissions.joinToString(",")

                    if (BuildConfig.TECHNICIAN_ONLY && !prefs.isTechnician()) {
                        prefs.clear()
                        _uiState.update {
                            it.copy(
                                loading = false,
                                error = "ဤအက်ပ်သည် Technician အကောင့်အတွက်သာ ဖြစ်ပါသည်"
                            )
                        }
                        return@launch
                    }

                    prefs.rememberedUsername = if (rememberMe) username.trim() else ""
                    _uiState.update { it.copy(loading = false, loginSuccess = true) }
                } else {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = when (response.code()) {
                                401 -> "အသုံးပြုသူအမည် သို့မဟုတ် စကားဝှက် မှားနေပါသည်"
                                403 -> "ဤအကောင့်ဖြင့် ဝင်ရောက်ခွင့်မရှိပါ"
                                in 500..599 -> "ဆာဗာတွင် ခေတ္တပြဿနာရှိနေပါသည်။ ခဏနောက် ပြန်ကြိုးစားပါ"
                                else -> "ဝင်ရောက်မှု မအောင်မြင်ပါ။ ထပ်မံကြိုးစားပါ"
                            }
                        )
                    }
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(loading = false, error = friendlyLoginError(error))
                }
            }
        }
    }

    private fun friendlyLoginError(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.toList()
        return when {
            causes.any { it is SocketTimeoutException } ->
                "ဆာဗာတုံ့ပြန်မှု ကြာနေပါသည်။ အင်တာနက်လိုင်းစစ်ပြီး ပြန်ကြိုးစားပါ"
            causes.any { it is UnknownHostException } ->
                "ဆာဗာလိပ်စာကို ရှာမတွေ့ပါ။ Wi-Fi သို့မဟုတ် အင်တာနက်လိုင်းကို စစ်ဆေးပါ"
            causes.any { it is SSLException } ->
                "လုံခြုံသော ဆက်သွယ်မှု မပြုလုပ်နိုင်ပါ။ စနစ်တာဝန်ခံကို ဆက်သွယ်ပါ"
            causes.any { it is ConnectException || it is NoRouteToHostException || it is IOException } ->
                "ဆာဗာနှင့် ချိတ်ဆက်၍မရပါ။ ကွန်ရက်ချိတ်ဆက်မှုကို စစ်ဆေးပါ"
            else ->
                "ဝင်ရောက်မှု မအောင်မြင်ပါ။ ခဏနောက် ပြန်ကြိုးစားပါ"
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = "") }
    }

    data class LoginUiState(
        val loading: Boolean = false,
        val error: String = "",
        val loginSuccess: Boolean = false
    )
}