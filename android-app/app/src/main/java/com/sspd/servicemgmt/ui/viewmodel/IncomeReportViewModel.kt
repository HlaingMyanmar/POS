package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.PeriodSummaryDTO
import com.sspd.servicemgmt.api.YearlySummaryDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class IncomeReportViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val cal   = Calendar.getInstance()

    private val _uiState = MutableStateFlow(
        UiState(
            fromDate      = today(),
            toDate        = today(),
            selectedYear  = cal.get(Calendar.YEAR),
            selectedMonth = cal.get(Calendar.MONTH) + 1
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Sale", "Income", "Expense", "Purchase") { load() }
    }

    fun selectMode(mode: ReportMode) {
        when (mode) {
            ReportMode.TODAY -> {
                val t = today()
                _uiState.update { it.copy(mode = mode, fromDate = t, toDate = t) }
                load()
            }
            ReportMode.MONTHLY -> {
                val y = _uiState.value.selectedYear
                val m = _uiState.value.selectedMonth
                _uiState.update { it.copy(mode = mode, fromDate = monthStart(y, m), toDate = monthEnd(y, m)) }
                load()
            }
            ReportMode.YEARLY -> {
                _uiState.update { it.copy(mode = mode) }
                loadYearly()
            }
            ReportMode.CUSTOM -> {
                _uiState.update { it.copy(mode = mode) }
            }
        }
    }

    fun prevMonth() {
        var y = _uiState.value.selectedYear
        var m = _uiState.value.selectedMonth - 1
        if (m < 1) { m = 12; y-- }
        _uiState.update { it.copy(selectedYear = y, selectedMonth = m, fromDate = monthStart(y, m), toDate = monthEnd(y, m)) }
        load()
    }

    fun nextMonth() {
        var y = _uiState.value.selectedYear
        var m = _uiState.value.selectedMonth + 1
        if (m > 12) { m = 1; y++ }
        _uiState.update { it.copy(selectedYear = y, selectedMonth = m, fromDate = monthStart(y, m), toDate = monthEnd(y, m)) }
        load()
    }

    fun prevYear() {
        val y = _uiState.value.selectedYear - 1
        _uiState.update { it.copy(selectedYear = y) }
        loadYearly()
    }

    fun nextYear() {
        val y = _uiState.value.selectedYear + 1
        _uiState.update { it.copy(selectedYear = y) }
        loadYearly()
    }

    fun setFromDate(date: String) {
        _uiState.update { it.copy(fromDate = date) }
        if (_uiState.value.toDate != null) load()
    }

    fun setToDate(date: String) {
        _uiState.update { it.copy(toDate = date) }
        load()
    }

    fun load() {
        val from = _uiState.value.fromDate ?: return
        val to   = _uiState.value.toDate   ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.getPeriodSummary(token, from, to)
                _uiState.update { it.copy(summary = res.body()?.data, loading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    private fun loadYearly() {
        val year = _uiState.value.selectedYear
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.getYearlySummary(token, year)
                _uiState.update { it.copy(yearlySummary = res.body()?.data, loading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    data class UiState(
        val mode:          ReportMode        = ReportMode.TODAY,
        val fromDate:      String?           = null,
        val toDate:        String?           = null,
        val selectedYear:  Int               = Calendar.getInstance().get(Calendar.YEAR),
        val selectedMonth: Int               = Calendar.getInstance().get(Calendar.MONTH) + 1,
        val summary:       PeriodSummaryDTO? = null,
        val yearlySummary: YearlySummaryDTO? = null,
        val loading:       Boolean           = true
    )
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

private fun monthStart(year: Int, month: Int) = "%d-%02d-01".format(year, month)

private fun monthEnd(year: Int, month: Int): String {
    val c = Calendar.getInstance()
    c.set(year, month - 1, 1)
    return "%d-%02d-%02d".format(year, month, c.getActualMaximum(Calendar.DAY_OF_MONTH))
}
