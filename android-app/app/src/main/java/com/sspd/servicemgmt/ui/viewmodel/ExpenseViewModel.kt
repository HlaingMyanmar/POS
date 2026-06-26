package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.ExpenseDTO
import com.sspd.servicemgmt.api.IncomeDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.text.SimpleDateFormat
import java.util.*

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(
        ExpenseUiState(
            fromDate = LocalDate.now().withDayOfMonth(1).toString(),
            toDate = LocalDate.now().toString(),
            dateShortcut = DateShortcut.MONTH
        )
    )
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Expense", "Income") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token    = ApiClient.bearer(prefs.authToken)
                val expD     = async { ApiClient.service.getExpenses(token) }
                val incD     = async { ApiClient.service.getIncomes(token) }
                _uiState.update {
                    it.copy(
                        expenses = expD.await().body()?.data ?: emptyList(),
                        incomes  = incD.await().body()?.data ?: emptyList(),
                        loading  = false
                    )
                }
            } catch (_: Exception) { _uiState.update { it.copy(loading = false) } }
        }
    }

    fun setFromDate(date: String?) = _uiState.update { it.copy(fromDate = date, dateShortcut = DateShortcut.CUSTOM) }
    fun setToDate(date: String?)   = _uiState.update { it.copy(toDate = date, dateShortcut = DateShortcut.CUSTOM) }
    fun clearDateFilter()          = _uiState.update { it.copy(fromDate = null, toDate = null, dateShortcut = DateShortcut.ALL) }
    fun setToday()                 = applyDateShortcut(DateShortcut.TODAY)

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
                fromDate = range?.first?.toString(),
                toDate = range?.second?.toString()
            )
        }
    }

    enum class DateShortcut { TODAY, WEEK, MONTH, ALL, CUSTOM }

    data class ExpenseUiState(
        val expenses: List<ExpenseDTO> = emptyList(),
        val incomes:  List<IncomeDTO>  = emptyList(),
        val loading:  Boolean          = true,
        val fromDate: String?          = null,
        val toDate:   String?          = null,
        val dateShortcut: DateShortcut = DateShortcut.MONTH
    )
}

private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
