package com.sspd.servicemgmt.feature.purchasereturn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.PurchaseReturnDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import com.sspd.servicemgmt.core.realtime.onDataEvent

class PurchaseReturnListViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("PurchaseReturn", "Purchase", "Stock") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.getPurchaseReturns(token, search = _uiState.value.search)
                _uiState.update { it.copy(items = res.body()?.data?.content ?: emptyList(), loading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun setSearch(q: String) {
        _uiState.update { it.copy(search = q) }
        load()
    }

    fun applyDateShortcut(shortcut: DateShortcut) {
        val today = LocalDate.now()
        val range = when (shortcut) {
            DateShortcut.TODAY -> today to today
            DateShortcut.WEEK -> today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong()) to today
            DateShortcut.MONTH -> today.withDayOfMonth(1) to today
            DateShortcut.ALL -> null
        }
        _uiState.update {
            it.copy(
                dateShortcut = shortcut,
                dateFrom = range?.first?.toString() ?: "",
                dateTo = range?.second?.toString() ?: ""
            )
        }
    }

    fun findVoucher(keyword: String, onFound: (Int) -> Unit, onFallbackSearch: () -> Unit) {
        val query = keyword.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val rows = ApiClient.service.getPurchaseReturns(token, page = 0, size = 10, search = query)
                    .body()?.data?.content ?: emptyList()
                val normalized = query.removePrefix("#").trim().lowercase()
                val target = rows.firstOrNull {
                    (it.returnNo ?: "").lowercase() == query.lowercase() ||
                        (it.returnNo ?: "").lowercase() == normalized ||
                        (it.id?.toString() ?: "") == normalized
                } ?: rows.singleOrNull()
                val id = target?.id
                if (id != null) onFound(id) else {
                    _uiState.update { it.copy(search = query) }
                    load()
                    onFallbackSearch()
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(search = query) }
                load()
                onFallbackSearch()
            }
        }
    }

    enum class DateShortcut { TODAY, WEEK, MONTH, ALL }

    data class UiState(
        val items: List<PurchaseReturnDTO> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val search: String = "",
        val dateShortcut: DateShortcut = DateShortcut.MONTH,
        val dateFrom: String = LocalDate.now().withDayOfMonth(1).toString(),
        val dateTo: String = LocalDate.now().toString()
    )
}

