package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.StaffReportDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

class StaffReportViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(StaffReportUiState(month = nowYM()))
    val uiState: StateFlow<StaffReportUiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Sale", "Service Job") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val res = ApiClient.service.getStaffReport(
                    ApiClient.bearer(prefs.authToken), _uiState.value.month
                )
                if (res.isSuccessful) _uiState.update { it.copy(items = res.body()?.data ?: emptyList()) }
            } catch (_: Exception) {}
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun prevMonth() {
        _uiState.update { it.copy(month = prevMonth(it.month)) }
        load()
    }

    fun nextMonth() {
        val cur = _uiState.value.month
        if (cur < nowYM()) {
            _uiState.update { it.copy(month = nextMonth(cur)) }
            load()
        }
    }

    data class StaffReportUiState(
        val items:   List<StaffReportDTO> = emptyList(),
        val loading: Boolean              = true,
        val month:   String               = ""
    )

    companion object {
        fun nowYM(): String {
            val c = Calendar.getInstance()
            return "%d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        }
        fun prevMonth(ym: String): String {
            val (y, m) = ym.split("-").map { it.toInt() }
            val c = Calendar.getInstance().apply { set(y, m - 2, 1) }
            return "%d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        }
        fun nextMonth(ym: String): String {
            val (y, m) = ym.split("-").map { it.toInt() }
            val c = Calendar.getInstance().apply { set(y, m, 1) }
            return "%d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        }
    }
}
