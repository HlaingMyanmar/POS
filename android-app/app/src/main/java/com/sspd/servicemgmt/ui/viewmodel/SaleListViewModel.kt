package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.PaymentMethodDTO
import com.sspd.servicemgmt.api.SaleDTO
import com.sspd.servicemgmt.api.SalePaymentRequest
import com.sspd.servicemgmt.utils.PreferenceManager
import com.sspd.servicemgmt.websocket.DataEventBus
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SaleListViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(SaleListUiState(fromDate = today(), toDate = today()))
    val uiState: StateFlow<SaleListUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            DataEventBus.events
                .filter { it.entity.contains("Sale", ignoreCase = true)
                       && !it.entity.contains("Return", ignoreCase = true) }
                .debounce(600)
                .collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val salesD = async { ApiClient.service.getSales(token, page = 0, size = 200, search = _uiState.value.search) }
                val pmD    = async { ApiClient.service.getActivePaymentMethods(token) }
                val items  = salesD.await().body()?.data?.content ?: emptyList()
                _uiState.update {
                    it.copy(
                        items          = items,
                        paymentMethods = pmD.await().body()?.data ?: it.paymentMethods
                    )
                }
            } catch (_: Exception) {}
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun setSearch(q: String) = _uiState.update { it.copy(search = q) }

    // ── Date filter ───────────────────────────────────────────────────────────

    fun setFromDate(date: String?) = _uiState.update { it.copy(fromDate = date) }
    fun setToDate(date: String?)   = _uiState.update { it.copy(toDate = date) }
    fun clearDateFilter()          = _uiState.update { it.copy(fromDate = null, toDate = null) }

    // ── Pay Due ───────────────────────────────────────────────────────────────

    fun showPayDialog(sale: SaleDTO) =
        _uiState.update { it.copy(payTargetSale = sale) }

    fun dismissPayDialog() =
        _uiState.update { it.copy(payTargetSale = null, payError = null) }

    fun clearPaySuccess() = _uiState.update { it.copy(paySuccess = null) }
    fun clearPayError()   = _uiState.update { it.copy(payError = null) }

    fun payDue(saleId: Int, amount: Double, paymentMethodId: Int, note: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(paying = true, payError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.payDue(
                    token, saleId,
                    SalePaymentRequest(
                        paidAmount      = amount,
                        paymentMethodId = paymentMethodId,
                        note            = note?.ifBlank { null }
                    )
                )
                if (res.isSuccessful && res.body()?.data != null) {
                    val updated = res.body()!!.data!!
                    _uiState.update { state ->
                        state.copy(
                            items         = state.items.map { if (it.id == saleId) updated else it },
                            paying        = false,
                            payTargetSale = null,
                            paySuccess    = updated.saleCode ?: "#$saleId"
                        )
                    }
                } else {
                    val msg = res.body()?.message ?: "ငွေဆပ်မှု မအောင်မြင်ပါ (${res.code()})"
                    _uiState.update { it.copy(paying = false, payError = msg) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(paying = false, payError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    data class SaleListUiState(
        val items:          List<SaleDTO>          = emptyList(),
        val paymentMethods: List<PaymentMethodDTO> = emptyList(),
        val loading:        Boolean                = true,
        val search:         String                 = "",
        val fromDate:       String?                = null,
        val toDate:         String?                = null,
        val payTargetSale:  SaleDTO?               = null,
        val paying:         Boolean                = false,
        val paySuccess:     String?                = null,
        val payError:       String?                = null
    )
}

private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
