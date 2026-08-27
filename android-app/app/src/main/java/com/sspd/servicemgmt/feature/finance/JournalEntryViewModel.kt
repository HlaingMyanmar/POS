package com.sspd.servicemgmt.feature.finance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.JournalEntryDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import com.sspd.servicemgmt.core.realtime.onDataEvent

class JournalEntryViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Journal", "Sale", "Purchase", "Expense", "Income", "StockAdj", "Return") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val res = ApiClient.service.getJournalEntries(ApiClient.bearer(prefs.authToken))
                val rows = res.body()?.data ?: emptyList()
                _uiState.update { it.copy(items = rows.sortedByDescending { row -> row.entryDate ?: "" }, loading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "ဂျာနယ်မှတ်တမ်း ဖတ်၍မရပါ") }
            }
        }
    }

    fun setSearch(value: String) {
        _uiState.update { it.copy(search = value) }
    }

    fun setSource(value: JournalSource) {
        _uiState.update { it.copy(source = value) }
    }

    fun setBalanceFilter(value: BalanceFilter) {
        _uiState.update { it.copy(balanceFilter = value) }
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

    enum class DateShortcut { TODAY, WEEK, MONTH, ALL }
    enum class BalanceFilter { ALL, BALANCED, CHECK }
    enum class JournalSource { ALL, SALE, PURCHASE, RETURN, EXPENSE, INCOME, STOCK, OPENING, MANUAL }

    data class UiState(
        val items: List<JournalEntryDTO> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val search: String = "",
        val source: JournalSource = JournalSource.ALL,
        val balanceFilter: BalanceFilter = BalanceFilter.ALL,
        val dateShortcut: DateShortcut = DateShortcut.MONTH,
        val dateFrom: String = LocalDate.now().withDayOfMonth(1).toString(),
        val dateTo: String = LocalDate.now().toString()
    )
}
