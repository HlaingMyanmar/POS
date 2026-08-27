package com.sspd.servicemgmt.feature.purchase

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.PaymentMethodDTO
import com.sspd.servicemgmt.core.network.PurchaseDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.sspd.servicemgmt.core.realtime.onDataEvent

class PurchaseDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val purchaseId: Int = checkNotNull(savedStateHandle["purchaseId"])

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        loadPaymentMethods()
        onDataEvent("Purchase", "Stock", "Product") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.getPurchaseById(token, purchaseId)
                _uiState.update {
                    it.copy(
                        purchase = res.body()?.data,
                        loading = false,
                        busy = false,
                        error = if (res.isSuccessful) null else "ဝယ်ယူမှုကို မတွေ့ပါ (${res.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun confirmDraft() {
        if (!prefs.hasPermission("CAN_ACCESS_PURCHASE_UPDATE")) {
            _uiState.update { it.copy(error = "ဝယ်ယူမှုအတည်ပြုရန် ခွင့်ပြုချက် မရှိပါ") }
            return
        }
        val purchase = _uiState.value.purchase ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.confirmPurchase(token, purchase.id ?: purchaseId)
                if (res.isSuccessful) load() else _uiState.update { it.copy(busy = false, error = res.body()?.message ?: "အတည်ပြုမရပါ") }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun cancel(reason: String, refundPaymentMethodId: Int?) {
        if (!prefs.hasPermission("CAN_ACCESS_PURCHASE_DELETE")) {
            _uiState.update { it.copy(error = "ဝယ်ယူမှုပယ်ဖျက်ရန် ခွင့်ပြုချက် မရှိပါ") }
            return
        }
        val purchase = _uiState.value.purchase ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.cancelPurchase(token, purchase.id ?: purchaseId, reason, refundPaymentMethodId)
                if (res.isSuccessful) load() else _uiState.update { it.copy(busy = false, error = res.body()?.message ?: "ပယ်ဖျက်မရပါ") }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun loadPaymentMethods() {
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val methods = ApiClient.service.getActivePaymentMethods(token).body()?.data ?: emptyList()
                _uiState.update { it.copy(paymentMethods = methods) }
            } catch (_: Exception) {}
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    data class UiState(
        val purchase: PurchaseDTO? = null,
        val paymentMethods: List<PaymentMethodDTO> = emptyList(),
        val loading: Boolean = true,
        val busy: Boolean = false,
        val error: String? = null
    )
}
