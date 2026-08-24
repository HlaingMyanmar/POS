package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.PaymentMethodDTO
import com.sspd.servicemgmt.api.PaymentTransactionDTO
import com.sspd.servicemgmt.api.ProductSerialDTO
import com.sspd.servicemgmt.api.SaleDTO
import com.sspd.servicemgmt.api.SalePaymentRequest
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class SaleDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs  = PreferenceManager(application)
    private val saleId: Int = checkNotNull(savedStateHandle["saleId"])

    private val _uiState = MutableStateFlow(SaleDetailUiState())
    val uiState: StateFlow<SaleDetailUiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Sale") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token   = ApiClient.bearer(prefs.authToken)
                val saleD   = async { ApiClient.service.getSaleById(token, saleId) }
                val pmD     = async { ApiClient.service.getActivePaymentMethods(token) }
                val txD     = async { ApiClient.service.getPaymentTransactions(token, saleId, "Sale") }
                val saleData = saleD.await().body()?.data
                val allSerials = (saleData?.details ?: emptyList()).flatMap { it.serialNumbers ?: emptyList() }
                val snMap: Map<String, ProductSerialDTO> = if (allSerials.isNotEmpty()) {
                    runCatching {
                        ApiClient.service.getProductSerialsBySerials(token, allSerials)
                            .body()?.data?.associateBy { it.serialNumber } ?: emptyMap()
                    }.getOrElse { emptyMap() }
                } else emptyMap()
                _uiState.update {
                    it.copy(
                        sale              = saleData,
                        paymentMethods    = pmD.await().body()?.data ?: emptyList(),
                        transactions      = txD.await().body()?.data ?: emptyList(),
                        serialWarrantyMap = snMap,
                        loading           = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun showPayDialog()    = _uiState.update { it.copy(showPayDialog = true) }
    fun dismissPayDialog() = _uiState.update { it.copy(showPayDialog = false, payError = null) }
    fun clearPayError()    = _uiState.update { it.copy(payError = null) }
    fun clearPaySuccess()  = _uiState.update { it.copy(paySuccess = false) }

    fun voidSale(reason: String, onSuccess: () -> Unit) {
        val trimmed = reason.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(payError = "Void reason is required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(voiding = true, payError = null) }
            try {
                val response = ApiClient.service.voidSale(ApiClient.bearer(prefs.authToken), saleId, trimmed)
                if (response.isSuccessful && response.body()?.data != null) {
                    _uiState.update { it.copy(sale = response.body()!!.data, voiding = false) }
                    onSuccess()
                } else {
                    _uiState.update { it.copy(voiding = false, payError = response.body()?.message ?: "Sale void failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(voiding = false, payError = e.message ?: "Network error") }
            }
        }
    }

    fun payDue(amount: Double, paymentMethodId: Int, transactionNo: String?, note: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(paying = true, payError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.payDue(
                    token, saleId,
                    SalePaymentRequest(
                        paidAmount      = amount,
                        paymentMethodId = paymentMethodId,
                        transactionNo   = transactionNo?.ifBlank { null },
                        note            = note?.ifBlank { null }
                    )
                )
                if (res.isSuccessful && res.body()?.data != null) {
                    // Reload transactions after payment
                    val txRes = ApiClient.service.getPaymentTransactions(token, saleId, "Sale")
                    _uiState.update {
                        it.copy(
                            sale          = res.body()!!.data,
                            transactions  = txRes.body()?.data ?: it.transactions,
                            paying        = false,
                            showPayDialog = false,
                            paySuccess    = true
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

    data class SaleDetailUiState(
        val sale:              SaleDTO?                       = null,
        val paymentMethods:    List<PaymentMethodDTO>         = emptyList(),
        val transactions:      List<PaymentTransactionDTO>    = emptyList(),
        val serialWarrantyMap: Map<String, ProductSerialDTO>  = emptyMap(),
        val loading:           Boolean                        = true,
        val showPayDialog:     Boolean                        = false,
        val paying:            Boolean                        = false,
        val voiding:           Boolean                        = false,
        val paySuccess:        Boolean                        = false,
        val payError:          String?                        = null
    )
}
