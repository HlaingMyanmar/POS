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
        _uiState.update {
            it.copy(
                purchaseQuery = q,
                selectedPurchase = null,
                purchaseReturns = emptyList(),
                items = emptyList(),
                maxRefundAmount = 0.0,
                returnContextLoaded = false,
                refundAmountStr = "",
                splitRefunds = emptyList(),
                saveError = null
            )
        }
        if (q.length >= 2) searchPurchases(q)
    }

    private fun searchPurchases(q: String) {
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val rows = ApiClient.service.getPurchases(token, search = q, size = 20).body()?.data?.content
                    ?.filter { row ->
                        val status = row.status?.uppercase()
                        status != "CANCELLED" && status != "DRAFT"
                    }
                    ?: emptyList()
                _uiState.update { it.copy(purchaseResults = rows) }
            } catch (_: Exception) {}
        }
    }

    fun selectPurchase(purchase: PurchaseDTO) {
        val status = purchase.status?.uppercase()
        if (status == "CANCELLED") {
            _uiState.update {
                it.copy(
                    selectedPurchase = null,
                    purchaseQuery = purchase.purchaseCode ?: "",
                    purchaseResults = emptyList(),
                    saveError = "ပယ်ဖျက်ပြီး ဘောင်ချာကို ဝယ်ပြန်ပို့ မလုပ်နိုင်ပါ"
                )
            }
            return
        }
        if (status == "DRAFT") {
            _uiState.update {
                it.copy(
                    selectedPurchase = null,
                    purchaseQuery = purchase.purchaseCode ?: "",
                    purchaseResults = emptyList(),
                    saveError = "မူကြမ်းကို အရင်အတည်ပြုပြီးမှ ဝယ်ပြန်ပို့ လုပ်ပါ"
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedPurchase = purchase,
                purchaseQuery = purchase.purchaseCode ?: "",
                purchaseResults = emptyList(),
                purchaseReturns = emptyList(),
                refundAmountStr = "",
                splitRefunds = emptyList(),
                items = buildItems(purchase, emptyList()),
                returnContextLoading = purchase.id != null,
                returnContextLoaded = purchase.id == null,
                saveError = null
            )
        }
        loadPurchaseReturnContext(purchase)
    }

    private fun loadPurchaseReturnContext(purchase: PurchaseDTO) {
        val purchaseId = purchase.id ?: return
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val returns = ApiClient.service.getPurchaseReturnsByPurchase(token, purchaseId).body()?.data ?: emptyList()
                val confirmedReturns = returns.filterNot { it.status.equals("VOIDED", ignoreCase = true) }
                _uiState.update {
                    it.copy(
                        purchaseReturns = confirmedReturns,
                        items = buildItems(purchase, confirmedReturns),
                        maxRefundAmount = calculateMaxRefund(purchase, confirmedReturns),
                        returnContextLoading = false,
                        returnContextLoaded = true,
                        saveError = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        returnContextLoading = false,
                        returnContextLoaded = false,
                        saveError = e.message ?: "Unable to load previous purchase returns"
                    )
                }
            }
        }
    }

    private fun buildItems(purchase: PurchaseDTO, returns: List<PurchaseReturnDTO>): List<ReturnItem> {
        val gross = purchase.totalAmount ?: 0.0
        val originalNet = maxOf(0.0, gross - (purchase.discountAmount ?: 0.0)).takeIf { it > 0.0 }
            ?: ((purchase.netAmount ?: 0.0) + (purchase.returnAmount ?: 0.0))
        val discountRatio = if (gross > 0.0) originalNet / gross else 1.0
        val returnedByProduct = returnedQtyByProduct(returns)
        val returnedSerialsByProduct = returnedSerialsByProduct(returns)

        return (purchase.details ?: emptyList()).mapNotNull { line ->
            val productId = line.productId ?: return@mapNotNull null
            val originalQty = line.qty ?: 0
            val alreadyReturned = returnedByProduct[productId] ?: 0
            val purchasedSerials = line.serialNumbers ?: emptyList()
            val returnedSerials = returnedSerialsByProduct[productId] ?: emptySet()
            val availableSerials = purchasedSerials.filterNot { returnedSerials.contains(it.trim().uppercase()) }
            val maxQty = if (purchasedSerials.isNotEmpty()) availableSerials.size else (originalQty - alreadyReturned).coerceAtLeast(0)
            val grossUnit = if (originalQty > 0 && (line.subtotal ?: 0.0) > 0.0) {
                (line.subtotal ?: 0.0) / originalQty
            } else {
                line.unitCost ?: 0.0
            }
            val effectiveUnit = grossUnit * discountRatio
            ReturnItem(
                productId = productId,
                productName = line.productName ?: "",
                originalQty = originalQty,
                alreadyReturned = alreadyReturned,
                maxQty = maxQty,
                unitPrice = effectiveUnit,
                purchaseSerialNums = availableSerials,
                hasSerial = purchasedSerials.isNotEmpty()
            )
        }
    }

    private fun returnedQtyByProduct(returns: List<PurchaseReturnDTO>): Map<Int, Int> =
        returns.flatMap { it.details ?: emptyList() }
            .groupBy { it.productId ?: 0 }
            .filterKeys { it > 0 }
            .mapValues { entry -> entry.value.sumOf { it.qty ?: 0 } }

    private fun returnedSerialsByProduct(returns: List<PurchaseReturnDTO>): Map<Int, Set<String>> =
        returns.flatMap { it.details ?: emptyList() }
            .filter { it.productId != null }
            .groupBy { it.productId!! }
            .mapValues { entry ->
                entry.value.flatMap { it.serialNumbers ?: emptyList() }
                    .map { it.trim().uppercase() }
                    .filter { it.isNotBlank() }
                    .toSet()
            }

    private fun calculateMaxRefund(purchase: PurchaseDTO, returns: List<PurchaseReturnDTO>): Double {
        val originalNet = maxOf(0.0, (purchase.totalAmount ?: 0.0) - (purchase.discountAmount ?: 0.0)).takeIf { it > 0.0 }
            ?: ((purchase.netAmount ?: 0.0) + (purchase.returnAmount ?: 0.0))
        val previousReturnTotal = returns.sumOf { it.totalReturnAmount ?: 0.0 }
        val previousRefund = returns.sumOf { it.refundAmount ?: 0.0 }
        val currentNetAfterReturns = maxOf(0.0, originalNet - previousReturnTotal)
        val supplierCredit = maxOf(0.0, (purchase.paidAmount ?: 0.0) - currentNetAfterReturns - previousRefund)
        return supplierCredit
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
            val suggestedRefund = minOf(total, s.maxRefundAmount.takeIf { it > 0.0 } ?: total)
            s.copy(items = items, refundAmountStr = suggestedRefund.formatMoneyInput(), saveError = null)
        }
    }

    fun setReason(v: String) = _uiState.update { it.copy(reason = v) }
    fun setRefundAmount(v: String) = _uiState.update { it.copy(refundAmountStr = v) }
    fun setTransactionNo(v: String) = _uiState.update { it.copy(transactionNo = v) }
    fun selectPm(pm: PaymentMethodDTO?) = _uiState.update { it.copy(selectedPm = pm) }

    fun addSplitRefund() {
        val s = _uiState.value
        val method = s.selectedPm ?: return _uiState.update { it.copy(saveError = "Choose refund method") }
        val amount = s.refundAmountStr.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) return _uiState.update { it.copy(saveError = "Enter split refund amount") }
        val currentSplitTotal = splitTotal(s.splitRefunds)
        if (currentSplitTotal + amount > s.maxRefundAmount && s.maxRefundAmount > 0.0) {
            return _uiState.update { it.copy(saveError = "Refund exceeds supplier credit: ${s.maxRefundAmount.formatMoneyInput()} Ks") }
        }
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

        if (purchase == null) { _uiState.update { it.copy(saveError = "Choose purchase invoice") }; return }
        val purchaseStatus = purchase.status?.uppercase()
        if (purchaseStatus == "CANCELLED") { _uiState.update { it.copy(saveError = "ပယ်ဖျက်ပြီး ဘောင်ချာကို ဝယ်ပြန်ပို့ မလုပ်နိုင်ပါ") }; return }
        if (purchaseStatus == "DRAFT") { _uiState.update { it.copy(saveError = "မူကြမ်းကို အရင်အတည်ပြုပြီးမှ ဝယ်ပြန်ပို့ လုပ်ပါ") }; return }
        if (s.returnContextLoading || !s.returnContextLoaded) { _uiState.update { it.copy(saveError = "Please wait until return history is loaded") }; return }
        if (selectedItems.isEmpty()) { _uiState.update { it.copy(saveError = "Choose return items") }; return }
        if (s.reason.isBlank()) { _uiState.update { it.copy(saveError = "Enter return reason") }; return }

        val total = selectedItems.sumOf { it.qty * it.unitPrice }
        val splitRefunds = normalizePayments(s.splitRefunds)
        val refund = if (splitRefunds.isNotEmpty()) splitTotal(splitRefunds) else (s.refundAmountStr.toDoubleOrNull() ?: minOf(total, s.maxRefundAmount))
        if (refund < 0.0) { _uiState.update { it.copy(saveError = "Refund cannot be negative") }; return }
        if (refund > s.maxRefundAmount && s.maxRefundAmount >= 0.0) {
            _uiState.update { it.copy(saveError = "Refund exceeds supplier credit: ${s.maxRefundAmount.formatMoneyInput()} Ks") }
            return
        }
        if (refund > 0.0 && s.selectedPm == null && splitRefunds.isEmpty()) {
            _uiState.update { it.copy(saveError = "Choose refund method") }
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
                    _uiState.update { it.copy(saving = false, saveError = res.body()?.message ?: "Unable to save (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, saveError = e.message ?: "Connection failed") }
            }
        }
    }

    data class ReturnItem(
        val productId: Int,
        val productName: String,
        val originalQty: Int,
        val alreadyReturned: Int,
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
        val returnContextLoading: Boolean = false,
        val returnContextLoaded: Boolean = false,
        val saveError: String? = null,
        val paymentMethods: List<PaymentMethodDTO> = emptyList(),
        val purchaseQuery: String = "",
        val purchaseResults: List<PurchaseDTO> = emptyList(),
        val selectedPurchase: PurchaseDTO? = null,
        val purchaseReturns: List<PurchaseReturnDTO> = emptyList(),
        val items: List<ReturnItem> = emptyList(),
        val reason: String = "",
        val refundAmountStr: String = "",
        val maxRefundAmount: Double = 0.0,
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