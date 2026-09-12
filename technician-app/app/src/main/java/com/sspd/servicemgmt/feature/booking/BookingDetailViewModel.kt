package com.sspd.servicemgmt.feature.booking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.navigation.optionalId
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.BookingDTO
import com.sspd.servicemgmt.core.network.BookingItemDTO
import com.sspd.servicemgmt.core.realtime.onDataEvent
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        onDataEvent("Service Job") { load() }
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

    fun cancelBooking() = runAction("ပယ်ဖျက်ပြီးပါပြီ") {
        ApiClient.service.updateBookingStatus(ApiClient.bearer(prefs.authToken), bookingId, "CANCELED")
    }

    fun convertOutdoor() = runAction("Outdoor Job ဖန်တီးပြီးပါပြီ") {
        ApiClient.service.convertBookingOutdoor(ApiClient.bearer(prefs.authToken), bookingId)
    }

    fun convertIndoor() = runAction("Indoor Jobs ဖန်တီးပြီးပါပြီ") {
        ApiClient.service.convertBookingIndoor(ApiClient.bearer(prefs.authToken), bookingId)
    }

    fun receiveItems(items: List<BookingItemDTO>, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.addBookingItems(token, bookingId, items)
                val data  = res.body()?.data
                if (res.isSuccessful && data != null) {
                    _uiState.update {
                        it.copy(booking = data, actionLoading = false, actionSuccess = "ပစ္စည်းလက်ခံပြီးပါပြီ")
                    }
                    onSuccess()
                } else {
                    _uiState.update {
                        it.copy(actionLoading = false, actionError = res.body()?.message ?: "ပစ္စည်းလက်ခံမရပါ")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, actionError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun removeItem(itemId: Int) = runAction("ပစ္စည်း ဖယ်ပြီးပါပြီ") {
        ApiClient.service.removeBookingItem(ApiClient.bearer(prefs.authToken), bookingId, itemId)
    }

    private inline fun runAction(successMsg: String, crossinline call: suspend () -> retrofit2.Response<com.sspd.servicemgmt.core.network.ApiResponse<BookingDTO>>) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, actionError = null) }
            try {
                val res  = call()
                val data = res.body()?.data
                if (res.isSuccessful && data != null) {
                    _uiState.update { it.copy(booking = data, actionLoading = false, actionSuccess = successMsg) }
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
