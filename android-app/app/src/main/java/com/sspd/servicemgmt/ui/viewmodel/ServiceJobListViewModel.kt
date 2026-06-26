package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.ServiceJobDTO
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

class ServiceJobListViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState(fromDate = today(), toDate = today()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            DataEventBus.events
                .filter { it.entity.contains("Service Job", ignoreCase = true) }
                .debounce(600)
                .collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val s   = _uiState.value
                val res = ApiClient.service.getServiceJobs(
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

    fun setFilter(f: String) = _uiState.update { it.copy(filter = f) }

    fun setSearch(q: String) { _uiState.update { it.copy(search = q) }; load() }

    // ── Date filter ───────────────────────────────────────────────────────────
    fun setFromDate(d: String?) { _uiState.update { it.copy(fromDate = d) }; load() }
    fun setToDate(d: String?)   { _uiState.update { it.copy(toDate   = d) }; load() }
    fun clearDateFilter()       { _uiState.update { it.copy(fromDate = null, toDate = null) }; load() }

    // ── Delete ────────────────────────────────────────────────────────────────
    fun confirmDelete(job: ServiceJobDTO) = _uiState.update { it.copy(deleteTarget = job) }
    fun cancelDelete()                    = _uiState.update { it.copy(deleteTarget = null, deleteError = null) }

    fun delete() {
        val target = _uiState.value.deleteTarget ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true, deleteError = null) }
            try {
                val res = ApiClient.service.deleteServiceJob(ApiClient.bearer(prefs.authToken), target.id!!)
                if (res.isSuccessful) {
                    _uiState.update { it.copy(
                        items         = it.items.filter { j -> j.id != target.id },
                        deleting      = false,
                        deleteTarget  = null,
                        deleteSuccess = "Job ဖျက်မှု အောင်မြင်ပြီး"
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

    data class UiState(
        val items:         List<ServiceJobDTO> = emptyList(),
        val loading:       Boolean             = true,
        val filter:        String              = "ALL",
        val search:        String              = "",
        val fromDate:      String?             = null,
        val toDate:        String?             = null,
        val deleteTarget:  ServiceJobDTO?      = null,
        val deleting:      Boolean             = false,
        val deleteError:   String?             = null,
        val deleteSuccess: String?             = null
    )
}

private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
