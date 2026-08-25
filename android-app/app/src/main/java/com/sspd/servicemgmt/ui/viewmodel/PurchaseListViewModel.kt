package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.PurchaseDTO
import com.sspd.servicemgmt.api.ReorderSuggestionDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

class PurchaseListViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Purchase", "Stock", "Product") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val current = _uiState.value
                val res = ApiClient.service.getPurchases(token, search = current.search, dateFrom = current.dateFrom, dateTo = current.dateTo)
                val overdue = runCatching { ApiClient.service.getOverduePurchases(token).body()?.data.orEmpty() }.getOrDefault(emptyList())
                val reorder = runCatching { ApiClient.service.getReorderSuggestions(token).body()?.data.orEmpty() }.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        items = res.body()?.data?.content ?: emptyList(),
                        overdueItems = overdue,
                        reorderSuggestions = reorder,
                        loading = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun setSearch(q: String) {
        _uiState.update { it.copy(search = q) }
        load()
    }

    fun setStatusFilter(v: StatusFilter) {
        _uiState.update { it.copy(statusFilter = v) }
    }

    fun setDateFrom(v: String) {
        _uiState.update { it.copy(dateFrom = v, dateShortcut = DateShortcut.CUSTOM) }
    }

    fun setDateTo(v: String) {
        _uiState.update { it.copy(dateTo = v, dateShortcut = DateShortcut.CUSTOM) }
    }

    fun applyDateShortcut(shortcut: DateShortcut) {
        val today = LocalDate.now()
        val range = when (shortcut) {
            DateShortcut.TODAY -> today to today
            DateShortcut.WEEK -> today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong()) to today
            DateShortcut.MONTH -> today.withDayOfMonth(1) to today
            DateShortcut.ALL -> null
            DateShortcut.CUSTOM -> null
        }
        _uiState.update {
            it.copy(
                dateShortcut = shortcut,
                dateFrom = range?.first?.toString() ?: "",
                dateTo = range?.second?.toString() ?: ""
            )
        }
        load()
    }

    fun findVoucher(keyword: String, onFound: (Int) -> Unit, onFallbackSearch: () -> Unit) {
        val query = keyword.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.getPurchases(token, page = 0, size = 10, search = query)
                val rows = res.body()?.data?.content ?: emptyList()
                val normalized = query.removePrefix("#").trim().lowercase()
                val exact = rows.firstOrNull {
                    (it.purchaseCode ?: "").lowercase() == query.lowercase() ||
                        (it.purchaseCode ?: "").lowercase() == normalized ||
                        (it.id?.toString() ?: "") == normalized
                }
                val target = exact ?: rows.singleOrNull()
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

    enum class StatusFilter { ALL, PAID, PARTIAL, DUE, OVERDUE }
    enum class DateShortcut { TODAY, WEEK, MONTH, ALL, CUSTOM }

    data class UiState(
        val items: List<PurchaseDTO> = emptyList(),
        val overdueItems: List<PurchaseDTO> = emptyList(),
        val reorderSuggestions: List<ReorderSuggestionDTO> = emptyList(),
        val loading: Boolean = true,
        val search: String = "",
        val statusFilter: StatusFilter = StatusFilter.ALL,
        val dateShortcut: DateShortcut = DateShortcut.MONTH,
        val dateFrom: String = LocalDate.now().withDayOfMonth(1).toString(),
        val dateTo: String = LocalDate.now().toString()
    )
}
