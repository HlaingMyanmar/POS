package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.LoginRequest
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Username နှင့် Password ဖြည့်ပါ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = "") }
            try {
                val res = ApiClient.service.login(LoginRequest(username.trim(), password.trim()))
                if (res.isSuccessful && res.body()?.success == true) {
                    val auth = res.body()!!.data!!
                    prefs.authToken      = auth.accessToken
                    prefs.username       = auth.username
                    prefs.displayName    = auth.name ?: auth.username
                    prefs.permissionsStr = auth.permissions.joinToString(",")
                    _uiState.update { it.copy(loading = false, loginSuccess = true) }
                } else {
                    val code = res.code()
                    val msg  = res.body()?.message
                    _uiState.update { it.copy(
                        loading = false,
                        error   = when {
                            code == 401          -> msg ?: "Username သို့မဟုတ် Password မှားနေပါသည်"
                            msg != null          -> msg
                            else                 -> "HTTP $code — Login မအောင်မြင်ပါ"
                        }
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    loading = false,
                    error   = when {
                        e.message?.contains("timeout", true) == true               -> "Server မတုံ့ပြန်ပါ — Network စစ်ကြည့်ပါ"
                        e.message?.contains("Unable to resolve host", true) == true -> "Server IP မတွေ့ — WiFi စစ်ကြည့်ပါ"
                        else -> "${e.javaClass.simpleName}: ${e.message}"
                    }
                ) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = "") }
    }

    data class LoginUiState(
        val loading:      Boolean = false,
        val error:        String  = "",
        val loginSuccess: Boolean = false
    )
}
