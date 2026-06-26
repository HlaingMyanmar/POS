package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.SaleReturnDTO
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

class SaleReturnListViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState(fromDate = today(), toDate = today()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            DataEventBus.events
                .filter { it.entity.contains("Return", ignoreCase = true) }
                .debounce(600)
                .collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.getSaleReturns(token, search = _uiState.value.search)
                if (res.isSuccessful)
                    _uiState.update { it.copy(items = res.body()?.data?.content ?: emptyList()) }
            } catch (_: Exception) {}
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun setSearch(q: String) { _uiState.update { it.copy(search = q) }; load() }

    fun setFromDate(date: String?) = _uiState.update { it.copy(fromDate = date) }
    fun setToDate(date: String?)   = _uiState.update { it.copy(toDate = date) }
    fun clearDateFilter()          = _uiState.update { it.copy(fromDate = null, toDate = null) }
    fun setToday()                 = _uiState.update { it.copy(fromDate = today(), toDate = today()) }

    data class UiState(
        val items:    List<SaleReturnDTO> = emptyList(),
        val loading:  Boolean             = true,
        val search:   String              = "",
        val fromDate: String?             = null,
        val toDate:   String?             = null
    )
}

private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
