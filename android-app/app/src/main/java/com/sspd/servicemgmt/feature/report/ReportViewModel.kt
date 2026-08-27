package com.sspd.servicemgmt.feature.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.BookingDTO
import com.sspd.servicemgmt.core.network.ExpenseDTO
import com.sspd.servicemgmt.core.network.IncomeDTO
import com.sspd.servicemgmt.core.network.PeriodSummaryDTO
import com.sspd.servicemgmt.core.network.ProductDTO
import com.sspd.servicemgmt.core.network.PurchaseDTO
import com.sspd.servicemgmt.core.network.SaleDTO
import com.sspd.servicemgmt.core.network.SalesRankingDTO
import com.sspd.servicemgmt.core.network.ServiceJobDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.sspd.servicemgmt.core.realtime.onDataEvent

/** Period selector shared by the mobile snapshot report. */
enum class ReportMode { TODAY, MONTHLY, YEARLY, CUSTOM }

class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val cal = Calendar.getInstance()

    private val _uiState = MutableStateFlow(
        ReportUiState(
            fromDate = today(),
            toDate = today(),
            selectedYear = cal.get(Calendar.YEAR),
            selectedMonth = cal.get(Calendar.MONTH) + 1
        )
    )
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Sale", "Service Job", "Booking", "Purchase", "Expense", "Income", "Product", "Stock") { load() }
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
                val y = _uiState.value.selectedYear
                _uiState.update { it.copy(mode = mode, fromDate = "$y-01-01", toDate = "$y-12-31") }
                load()
            }
            ReportMode.CUSTOM -> _uiState.update { it.copy(mode = mode) }
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
        _uiState.update { it.copy(selectedYear = y, fromDate = "$y-01-01", toDate = "$y-12-31") }
        load()
    }

    fun nextYear() {
        val y = _uiState.value.selectedYear + 1
        _uiState.update { it.copy(selectedYear = y, fromDate = "$y-01-01", toDate = "$y-12-31") }
        load()
    }

    fun setFromDate(date: String) {
        _uiState.update { it.copy(mode = ReportMode.CUSTOM, fromDate = date) }
        if (_uiState.value.toDate != null) load()
    }

    fun setToDate(date: String) {
        _uiState.update { it.copy(mode = ReportMode.CUSTOM, toDate = date) }
        load()
    }

    fun load() {
        val from = _uiState.value.fromDate ?: return
        val to = _uiState.value.toDate ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                supervisorScope {
                    val summaryD = async { runCatching { ApiClient.service.getPeriodSummary(token, from, to).body()?.data }.getOrNull() }
                    val salesD = async { runCatching { ApiClient.service.getSales(token, page = 0, size = 500, dateFrom = from, dateTo = to).body()?.data?.content.orEmpty() }.getOrDefault(emptyList()) }
                    val purchasesD = async { runCatching { ApiClient.service.getPurchases(token, page = 0, size = 500, dateFrom = from, dateTo = to).body()?.data?.content.orEmpty() }.getOrDefault(emptyList()) }
                    val jobsD = async { runCatching { ApiClient.service.getServiceJobs(token, page = 0, size = 500, dateFrom = from, dateTo = to).body()?.data?.content.orEmpty() }.getOrDefault(emptyList()) }
                    val bookingsD = async { runCatching { ApiClient.service.getBookings(token, page = 0, size = 500, dateFrom = from, dateTo = to).body()?.data?.content.orEmpty() }.getOrDefault(emptyList()) }
                    val expensesD = async { runCatching { ApiClient.service.getExpenses(token).body()?.data.orEmpty().filterByDate(from, to) { it.expenseDate } }.getOrDefault(emptyList()) }
                    val incomesD = async { runCatching { ApiClient.service.getIncomes(token).body()?.data.orEmpty().filterByDate(from, to) { it.incomeDate } }.getOrDefault(emptyList()) }
                    val productsD = async { runCatching { ApiClient.service.getProducts(token).body()?.data.orEmpty() }.getOrDefault(emptyList()) }
                    val rankD = async { runCatching { ApiClient.service.getSalesRanking(token, from.take(7)).body()?.data.orEmpty() }.getOrDefault(emptyList()) }

                    val sales = salesD.await().filterByDate(from, to) { it.saleDate }
                    val purchases = purchasesD.await().filterByDate(from, to) { it.purchaseDate }
                    val jobs = jobsD.await().filterByDate(from, to) { it.receivedDate ?: it.completedDate ?: it.deliveredDate }
                    val bookings = bookingsD.await().filterByDate(from, to) { it.bookingDate ?: it.appointmentDate }

                    _uiState.update {
                        it.copy(
                            summary = summaryD.await(),
                            sales = sales,
                            purchases = purchases,
                            serviceJobs = jobs,
                            bookings = bookings,
                            expenses = expensesD.await(),
                            incomes = incomesD.await(),
                            products = productsD.await(),
                            rankings = rankD.await(),
                            loading = false,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "Report data ဖတ်မရပါ") }
            }
        }
    }

    data class ReportUiState(
        val mode: ReportMode = ReportMode.TODAY,
        val fromDate: String? = null,
        val toDate: String? = null,
        val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
        val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
        val summary: PeriodSummaryDTO? = null,
        val sales: List<SaleDTO> = emptyList(),
        val purchases: List<PurchaseDTO> = emptyList(),
        val serviceJobs: List<ServiceJobDTO> = emptyList(),
        val bookings: List<BookingDTO> = emptyList(),
        val expenses: List<ExpenseDTO> = emptyList(),
        val incomes: List<IncomeDTO> = emptyList(),
        val products: List<ProductDTO> = emptyList(),
        val rankings: List<SalesRankingDTO> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null
    )
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

private fun monthStart(year: Int, month: Int) = "%d-%02d-01".format(year, month)

private fun monthEnd(year: Int, month: Int): String {
    val c = Calendar.getInstance()
    c.set(year, month - 1, 1)
    return "%d-%02d-%02d".format(year, month, c.getActualMaximum(Calendar.DAY_OF_MONTH))
}

private fun <T> List<T>.filterByDate(from: String, to: String, picker: (T) -> String?): List<T> = filter { row ->
    val d = picker(row)?.take(10).orEmpty()
    d.isNotBlank() && d >= from && d <= to
}
