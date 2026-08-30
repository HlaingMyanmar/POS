package com.sspd.servicemgmt.feature.booking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.BookingDTO
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.sspd.servicemgmt.core.navigation.optionalId
import com.sspd.servicemgmt.core.realtime.onDataEvent

class BookingDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs     = PreferenceManager(application)
    private val bookingId: Int = savedStateHandle.optionalId("bookingId")

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Booking") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.getBookingById(token, bookingId)
                _uiState.update { it.copy(booking = res.body()?.data, loading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    // ── Status Update ─────────────────────────────────────────────────────────

    fun updateStatus(status: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.updateBookingStatus(token, bookingId, status)
                val data = res.body()?.data
                if (res.isSuccessful && data != null) {
                    _uiState.update { it.copy(booking = data, actionLoading = false, actionSuccess = "အဆင့် ပြောင်းလဲပြီး") }
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    // ── Convert to Job ────────────────────────────────────────────────────────

    fun convertToJob(onSuccess: (List<ServiceJobDTO>) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.convertBookingToJob(token, bookingId)
                val jobs = res.body()?.data
                if (res.isSuccessful && jobs != null) {
                    _uiState.update { it.copy(actionLoading = false, actionSuccess = "Job များ ဖန်တီးပြီး") }
                    load()
                    onSuccess(jobs)
                } else {
                    _uiState.update { it.copy(actionLoading = false, actionError = res.body()?.message ?: "မအောင်မြင်ပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun clearActionSuccess() = _uiState.update { it.copy(actionSuccess = null) }
    fun clearActionError()   = _uiState.update { it.copy(actionError = null) }

    data class UiState(
        val booking:       BookingDTO? = null,
        val loading:       Boolean     = true,
        val actionLoading: Boolean     = false,
        val actionSuccess: String?     = null,
        val actionError:   String?     = null
    )
}
