package com.sspd.servicemgmt.feature.service.job

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.PrintPreviewRequest
import com.sspd.servicemgmt.core.network.VoucherSettingDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ServiceJobPrintViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    val jobId: Int = checkNotNull(savedStateHandle["jobId"])

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var cachedSetting: VoucherSettingDTO? = null

    init {
        viewModelScope.launch {
            val setting = loadVoucherSetting()
            cachedSetting = setting
            val preferred = setting?.paperSize?.takeIf { it.isNotBlank() } ?: "A5"
            _uiState.update { it.copy(preferredPaper = preferred) }
            loadHtml(preferred)
        }
    }

    fun loadHtml(paper: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, htmlContent = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val s = cachedSetting
                val res = ApiClient.service.getPrintPreviewHtml(
                    token,
                    PrintPreviewRequest(
                        documentType = "SERVICE_JOB",
                        documentId = jobId,
                        paperSize = paper,
                        showLogo = s?.showLogo ?: true,
                        showSerial = s?.showSerial ?: true,
                        showSignatures = s?.showSignatures ?: true,
                        showQrCode = s?.showQrCode ?: false,
                        showPaymentHistory = s?.showPaymentHistory ?: true,
                        copyType = "CUSTOMER",
                    )
                )
                if (res.isSuccessful) {
                    val html = res.body()?.string()
                    if (html != null) {
                        _uiState.update { it.copy(loading = false, htmlContent = html) }
                    } else {
                        _uiState.update { it.copy(loading = false, error = "HTML ဒေတာ မရပါ") }
                    }
                } else {
                    _uiState.update { it.copy(loading = false, error = "Server error ${res.code()}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    private suspend fun loadVoucherSetting(): VoucherSettingDTO? {
        return try {
            val token = ApiClient.bearer(prefs.authToken)
            val res = ApiClient.service.getVoucherSetting(token, "SERVICE_JOB")
            if (res.isSuccessful) res.body()?.data else null
        } catch (_: Exception) {
            null
        }
    }

    data class UiState(
        val htmlContent: String? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val preferredPaper: String? = null,
    )
}
