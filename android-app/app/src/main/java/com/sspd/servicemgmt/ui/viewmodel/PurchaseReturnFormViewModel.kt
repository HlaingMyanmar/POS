package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.PaymentMethodDTO
import com.sspd.servicemgmt.api.PaymentTransactionDTO
import com.sspd.servicemgmt.api.PurchaseDTO
import com.sspd.servicemgmt.api.PurchaseReturnDTO
import com.sspd.servicemgmt.api.PurchaseReturnDetailDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PurchaseReturnFormViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadInit() }

    private fun loadInit() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val pms = ApiClient.service.getActivePaymentMethods(token).body()?.data ?: emptyList()
                _uiState.update { it.copy(paymentMethods = pms, loading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, saveError = e.message) }
            }
        }
    }

    fun setPurchaseQuery(q: String) {
        _uiState.update { it.copy(purchaseQuery = q, selectedPurchase = null, items = emptyList()) }
        if (q.length >= 2) searchPurchases(q)
    }

    private fun searchPurchases(q: String) {
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val rows = ApiClient.service.getPurchases(token, search = q, size = 20).body()?.data?.content ?: emptyList()
                _uiState.update { it.copy(purchaseResults = rows) }
            } catch (_: Exception) {}
        }
    }

    fun selectPurchase(purchase: PurchaseDTO) {
        _uiState.update {
            it.copy(
                selectedPurchase = purchase,
                purchaseQuery = purchase.purchaseCode ?: "",
                purchaseResults = emptyList(),
                refundAmountStr = "",
                items = buildItems(purchase)
            )
        }
    }

    private fun buildItems(purchase: PurchaseDTO): List<ReturnItem> {
        val gross = purchase.totalAmount ?: 0.0
        val net = purchase.netAmount ?: maxOf(0.0, gross - (purchase.discountAmount ?: 0.0))
        val discountRatio = if (gross > 0.0) net / gross else 1.0
        return (purchase.details ?: emptyList()).mapNotNull { line ->
            val productId = line.productId ?: return@mapNotNull null
            val qty = line.qty ?: 0
            val grossUnit = if (qty > 0 && (line.subtotal ?: 0.0) > 0.0) {
                (line.subtotal ?: 0.0) / qty
            } else {
                line.unitCost ?: 0.0
            }
            val effectiveUnit = grossUnit * discountRatio
            ReturnItem(
                productId = productId,
                productName = line.productName ?: "",
                maxQty = qty,
                unitPrice = effectiveUnit,
                purchaseSerialNums = line.serialNumbers ?: emptyList(),
                hasSerial = !(line.serialNumbers ?: emptyList()).isEmpty()
            )
        }
    }

    fun setItemQty(index: Int, qty: Int) {
        _uiState.update { s ->
            val items = s.items.toMutableList()
            val item = items.getOrNull(index) ?: return@update s
            val clamped = qty.coerceIn(0, item.maxQty)
            items[index] = item.copy(
                qty = clamped,
                serialNumbers = if (item.hasSerial) item.purchaseSerialNums.take(clamped) else emptyList()
            )
            val total = items.sumOf { it.qty * it.unitPrice }
            s.copy(items = items, refundAmountStr = String.format("%.0f", total), saveError = null)
        }
    }

    fun setReason(v: String) = _uiState.update { it.copy(reason = v) }
    fun setRefundAmount(v: String) = _uiState.update { it.copy(refundAmountStr = v) }
    fun setTransactionNo(v: String) = _uiState.update { it.copy(transactionNo = v) }
    fun selectPm(pm: PaymentMethodDTO?) = _uiState.update { it.copy(selectedPm = pm) }

    fun addSplitRefund() {
        val s = _uiState.value
        val method = s.selectedPm ?: return _uiState.update { it.copy(saveError = "ငွေလက်ခံနည်း ရွေးပါ") }
        val amount = s.refundAmountStr.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) return _uiState.update { it.copy(saveError = "Split refund amount ထည့်ပါ") }
        val next = s.splitRefunds + PaymentTransactionDTO(
            paymentMethodId = method.id,
            paymentMethodName = method.methodName,
            amount = amount,
            transactionNo = s.transactionNo.ifBlank { null }
        )
        _uiState.update { it.copy(splitRefunds = next, refundAmountStr = splitTotal(next).formatMoneyInput(), transactionNo = "", saveError = null) }
    }

    fun removeSplitRefund(index: Int) = _uiState.update { s ->
        val next = s.splitRefunds.filterIndexed { i, _ -> i != index }
        s.copy(splitRefunds = next, refundAmountStr = if (next.isEmpty()) "" else splitTotal(next).formatMoneyInput())
    }

    fun save(onSuccess: (PurchaseReturnDTO) -> Unit) {
        val s = _uiState.value
        val purchase = s.selectedPurchase
        val selectedItems = s.items.filter { it.qty > 0 }

        if (purchase == null) { _uiState.update { it.copy(saveError = "ဝယ်ယူမှုဘောင်ချာ ရွေးပါ") }; return }
        if (selectedItems.isEmpty()) { _uiState.update { it.copy(saveError = "ပြန်ပို့မည့် ပစ္စည်း ရွေးပါ") }; return }
        if (s.reason.isBlank()) { _uiState.update { it.copy(saveError = "အကြောင်းအရင်း ဖြည့်ပါ") }; return }

        val total = selectedItems.sumOf { it.qty * it.unitPrice }
        val splitRefunds = normalizePayments(s.splitRefunds)
        val refund = if (splitRefunds.isNotEmpty()) splitTotal(splitRefunds) else (s.refundAmountStr.toDoubleOrNull() ?: total)
        if (refund > 0.0 && s.selectedPm == null) {
            _uiState.update { it.copy(saveError = "Supplier ထံမှ ငွေလက်ခံနည်း ရွေးပါ") }
            return
        }

        val dto = PurchaseReturnDTO(
            purchaseId = purchase.id,
            totalReturnAmount = total,
            refundAmount = refund,
            paymentMethodId = if (refund > 0.0) (splitRefunds.firstOrNull()?.paymentMethodId ?: s.selectedPm?.id) else null,
            transactionNo = s.transactionNo.ifBlank { null },
            payments = splitRefunds.ifEmpty { null },
            reason = s.reason.trim(),
            details = selectedItems.map {
                PurchaseReturnDetailDTO(
                    productId = it.productId,
                    productName = it.productName,
                    qty = it.qty,
                    unitPrice = it.unitPrice,
                    subtotal = it.qty * it.unitPrice,
                    serialNumbers = it.serialNumbers.ifEmpty { null }
                )
            }
        )

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.createPurchaseReturn(token, dto)
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update { it.copy(saving = false) }
                    onSuccess(res.body()!!.data!!)
                } else {
                    _uiState.update { it.copy(saving = false, saveError = res.body()?.message ?: "သိမ်းမရပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, saveError = e.message ?: "ချိတ်ဆက်မှု မအောင်မြင်ပါ") }
            }
        }
    }

    data class ReturnItem(
        val productId: Int,
        val productName: String,
        val maxQty: Int,
        val unitPrice: Double,
        val qty: Int = 0,
        val serialNumbers: List<String> = emptyList(),
        val purchaseSerialNums: List<String> = emptyList(),
        val hasSerial: Boolean = false
    )

    data class UiState(
        val loading: Boolean = true,
        val saving: Boolean = false,
        val saveError: String? = null,
        val paymentMethods: List<PaymentMethodDTO> = emptyList(),
        val purchaseQuery: String = "",
        val purchaseResults: List<PurchaseDTO> = emptyList(),
        val selectedPurchase: PurchaseDTO? = null,
        val items: List<ReturnItem> = emptyList(),
        val reason: String = "",
        val refundAmountStr: String = "",
        val selectedPm: PaymentMethodDTO? = null,
        val splitRefunds: List<PaymentTransactionDTO> = emptyList(),
        val transactionNo: String = ""
    )
}

private fun normalizePayments(payments: List<PaymentTransactionDTO>): List<PaymentTransactionDTO> =
    payments.mapNotNull { p ->
        val methodId = p.paymentMethodId ?: 0
        val amount = p.amount ?: 0.0
        if (methodId <= 0 || amount <= 0.0) null else p.copy(amount = amount, transactionNo = p.transactionNo?.ifBlank { null })
    }

private fun splitTotal(payments: List<PaymentTransactionDTO>): Double = payments.sumOf { it.amount ?: 0.0 }

private fun Double.formatMoneyInput(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
