package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.PrintPreviewRequest
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookingPrintViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs     = PreferenceManager(application)
    val bookingId: Int    = checkNotNull(savedStateHandle["bookingId"])

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadHtml("A4") }

    fun loadHtml(paper: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, htmlContent = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.getBookingPrintHtml(
                    token,
                    PrintPreviewRequest(
                        documentType = "BOOKING",
                        documentId   = bookingId,
                        paperSize    = paper
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

    data class UiState(
        val htmlContent: String?  = null,
        val loading:     Boolean  = true,
        val error:       String?  = null
    )
}
