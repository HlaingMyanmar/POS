package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.BookingDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import com.sspd.servicemgmt.websocket.DataEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BookingListViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(
        BookingListUiState(fromDate = today(), toDate = today())
    )
    val uiState: StateFlow<BookingListUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            DataEventBus.events
                .filter { it.entity.contains("Booking", ignoreCase = true) }
                .debounce(600)
                .collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val s   = _uiState.value
                val res = ApiClient.service.getBookings(
                    auth     = ApiClient.bearer(prefs.authToken),
                    search   = s.search,
                    dateFrom = s.fromDate ?: "",
                    dateTo   = s.toDate   ?: ""
                )
                if (res.isSuccessful)
                    _uiState.update { it.copy(items = res.body()?.data?.content ?: emptyList()) }
            } catch (_: Exception) {}
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun setSearch(q: String) {
        _uiState.update { it.copy(search = q) }
        load()
    }

    // ── Date filter ───────────────────────────────────────────────────────────

    fun setFromDate(date: String?) { _uiState.update { it.copy(fromDate = date) }; load() }
    fun setToDate(date: String?)   { _uiState.update { it.copy(toDate = date) };   load() }
    fun clearDateFilter()          { _uiState.update { it.copy(fromDate = null, toDate = null) }; load() }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun confirmDelete(booking: BookingDTO) =
        _uiState.update { it.copy(deleteTarget = booking) }

    fun cancelDelete() =
        _uiState.update { it.copy(deleteTarget = null, deleteError = null) }

    fun delete() {
        val target = _uiState.value.deleteTarget ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true, deleteError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.deleteBooking(token, target.id!!)
                if (res.isSuccessful) {
                    _uiState.update { it.copy(
                        items         = it.items.filter { b -> b.id != target.id },
                        deleting      = false,
                        deleteTarget  = null,
                        deleteSuccess = "Booking ဖျက်မှု အောင်မြင်ပြီး"
                    ) }
                } else {
                    _uiState.update { it.copy(deleting = false, deleteError = "ဖျက်မှု မအောင်မြင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(deleting = false, deleteError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun clearDeleteSuccess() = _uiState.update { it.copy(deleteSuccess = null) }

    data class BookingListUiState(
        val items:         List<BookingDTO> = emptyList(),
        val loading:       Boolean          = true,
        val search:        String           = "",
        val fromDate:      String?          = null,
        val toDate:        String?          = null,
        val deleteTarget:  BookingDTO?      = null,
        val deleting:      Boolean          = false,
        val deleteError:   String?          = null,
        val deleteSuccess: String?          = null
    )
}

private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
