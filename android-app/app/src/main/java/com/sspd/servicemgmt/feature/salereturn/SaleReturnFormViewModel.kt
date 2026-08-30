package com.sspd.servicemgmt.feature.salereturn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.PaymentMethodDTO
import com.sspd.servicemgmt.core.network.PaymentTransactionDTO
import com.sspd.servicemgmt.core.network.SaleDTO
import com.sspd.servicemgmt.core.network.SaleReturnDTO
import com.sspd.servicemgmt.core.network.SaleReturnDetailDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SaleReturnFormViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs    = PreferenceManager(application)
    private val returnId: Int? = savedStateHandle.get<Int>("returnId")?.takeIf { it != -1 }

    val isEdit get() = returnId != null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadInit() }

    private fun loadInit() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val pmRes = ApiClient.service.getActivePaymentMethods(token)
                val pms   = pmRes.body()?.data ?: emptyList()

                if (returnId != null) {
                    val retRes = ApiClient.service.getSaleReturnById(token, returnId)
                    retRes.body()?.data?.let { r ->
                        val saleRes = r.saleId?.let { ApiClient.service.getSaleById(token, it) }
                        val sale    = saleRes?.body()?.data
                        _uiState.update { it.copy(
                            paymentMethods  = pms,
                            selectedSale    = sale,
                            saleQuery       = r.saleCode ?: "",
                            items           = buildItems(sale, r),
                            reason          = r.reason ?: "",
                            refundAmountStr = r.refundAmount?.let { v -> String.format("%.0f", v) } ?: "",
                            selectedPm      = pms.find { pm -> pm.id == r.paymentMethodId },
                            transactionNo   = r.transactionNo ?: "",
                            loading         = false
                        ) }
                        return@launch
                    }
                }
                _uiState.update { it.copy(paymentMethods = pms, loading = false) }
            } catch (_: Exception) { _uiState.update { it.copy(loading = false) } }
        }
    }

    private fun buildItems(sale: SaleDTO?, existingReturn: SaleReturnDTO?): List<ReturnItem> {
        val details = sale?.details ?: return emptyList()
        val lineNetSubtotal = details.sumOf { item ->
            val qty = item.qty ?: 0
            val gross = (item.unitPrice ?: 0.0) * qty
            maxOf(0.0, gross - (item.discountAmount ?: 0.0))
        }
        val overallDiscount = sale.discountAmount ?: 0.0
        return details.mapNotNull { saleItem ->
            val productId    = saleItem.productId ?: return@mapNotNull null
            val retDetail    = existingReturn?.details?.find { it.productId == productId }
            val saleSerials  = saleItem.serialNumbers ?: emptyList()
            val retQty       = retDetail?.qty ?: 0
            val saleQty      = saleItem.qty ?: 1
            val lineGross    = (saleItem.unitPrice ?: 0.0) * saleQty
            val lineNet      = maxOf(0.0, lineGross - (saleItem.discountAmount ?: 0.0))
            val lineShareDiscount = if (lineNetSubtotal > 0.0) overallDiscount * (lineNet / lineNetSubtotal) else 0.0
            val netUnitPrice = if (saleQty > 0) maxOf(0.0, lineNet - lineShareDiscount) / saleQty else 0.0
            // Auto-populate serials for edit mode from sale's serials up to retQty
            val autoSerials  = if (saleSerials.isNotEmpty()) saleSerials.take(retQty)
                               else retDetail?.serialNumbers ?: emptyList()
            ReturnItem(
                productId      = productId,
                productName    = saleItem.productName ?: "",
                maxQty         = saleItem.qty ?: 1,
                unitPrice      = netUnitPrice,
                qty            = retQty,
                serialNumbers  = autoSerials,
                saleSerialNums = saleSerials,
                hasSerial      = saleSerials.isNotEmpty()
            )
        }
    }

    // ── Sale search ───────────────────────────────────────────────────────────

    fun setSaleQuery(q: String) {
        _uiState.update { it.copy(saleQuery = q, selectedSale = null, items = emptyList()) }
        if (q.length >= 2) searchSales(q)
    }

    private fun searchSales(q: String) {
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.getSales(token, search = q, size = 20)
                _uiState.update { it.copy(saleResults = res.body()?.data?.content ?: emptyList()) }
            } catch (_: Exception) {}
        }
    }

    fun selectSale(sale: SaleDTO) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                selectedSale = sale,
                saleQuery    = sale.saleCode ?: "",
                saleResults  = emptyList(),
                items        = buildItems(sale, null),
                refundAmountStr = ""
            ) }
        }
    }

    // ── Item qty ──────────────────────────────────────────────────────────────

    fun setItemQty(index: Int, qty: Int) {
        _uiState.update { s ->
            val items   = s.items.toMutableList()
            if (index < items.size) {
                val item    = items[index]
                val clamped = qty.coerceIn(0, item.maxQty)
                // Auto-fill serial numbers from original sale serials up to selected qty
                val serials = if (item.hasSerial) item.saleSerialNums.take(clamped) else emptyList()
                items[index] = item.copy(qty = clamped, serialNumbers = serials)
            }
            val total = items.sumOf { it.qty * it.unitPrice }
            s.copy(items = items, refundAmountStr = String.format("%.0f", total))
        }
    }

    fun addSerial(index: Int, serial: String) {
        _uiState.update { s ->
            val items = s.items.toMutableList()
            val item  = items.getOrNull(index) ?: return@update s
            if (item.serialNumbers.contains(serial)) return@update s.copy(saveError = "\"$serial\" ထပ်နေသည်")
            if (item.serialNumbers.size >= item.qty) return@update s.copy(saveError = "Qty ${item.qty} ပြည့်ပြီ")
            items[index] = item.copy(serialNumbers = item.serialNumbers + serial)
            s.copy(items = items, saveError = null)
        }
    }

    fun removeSerial(index: Int, serial: String) {
        _uiState.update { s ->
            val items = s.items.toMutableList()
            val item  = items.getOrNull(index) ?: return@update s
            items[index] = item.copy(serialNumbers = item.serialNumbers.filter { it != serial })
            s.copy(items = items)
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    fun setReason(v: String)         = _uiState.update { it.copy(reason = v) }
    fun setRefundAmount(v: String)   = _uiState.update { it.copy(refundAmountStr = v) }
    fun setTransactionNo(v: String)  = _uiState.update { it.copy(transactionNo = v) }
    fun selectPm(pm: PaymentMethodDTO?) = _uiState.update { it.copy(selectedPm = pm) }
    fun clearError()                 = _uiState.update { it.copy(saveError = null) }

    fun addSplitRefund() {
        val s = _uiState.value
        val method = s.selectedPm ?: return _uiState.update { it.copy(saveError = "ငွေပြန်ပေးနည်း ရွေးပါ") }
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

    // ── Save ──────────────────────────────────────────────────────────────────

    fun save(onSuccess: (SaleReturnDTO) -> Unit) {
        val s = _uiState.value
        val selectedItems = s.items.filter { it.qty > 0 }

        if (s.selectedSale == null) { _uiState.update { it.copy(saveError = "Sale ရွေးပါ") }; return }
        if (selectedItems.isEmpty()) { _uiState.update { it.copy(saveError = "ပြန်ပေးမည့် ပစ္စည်း တစ်ခုမျှ မရွေးရသေးပါ") }; return }
        if (s.reason.isBlank())     { _uiState.update { it.copy(saveError = "အကြောင်းအရင်း ဖြည့်ပါ") }; return }

        val totalReturn = selectedItems.sumOf { it.qty * it.unitPrice }
        val splitRefunds = normalizePayments(s.splitRefunds)
        val refund      = if (splitRefunds.isNotEmpty()) splitTotal(splitRefunds) else (s.refundAmountStr.toDoubleOrNull() ?: totalReturn)
        if (refund > 0 && s.selectedPm == null) { _uiState.update { it.copy(saveError = "ငွေပြန်ပေးနည်း ရွေးပါ") }; return }

        val dto = SaleReturnDTO(
            saleId              = s.selectedSale.id,
            reason              = s.reason.ifBlank { null },
            totalReturnAmount   = totalReturn,
            refundAmount        = refund,
            paymentMethodId     = if (refund > 0.0) (splitRefunds.firstOrNull()?.paymentMethodId ?: s.selectedPm?.id) else null,
            transactionNo       = s.transactionNo.ifBlank { null },
            payments            = splitRefunds.ifEmpty { null },
            details             = selectedItems.map { item ->
                SaleReturnDetailDTO(
                    productId     = item.productId,
                    productName   = item.productName,
                    qty           = item.qty,
                    unitPrice     = item.unitPrice,
                    subtotal      = item.qty * item.unitPrice,
                    serialNumbers = item.serialNumbers.ifEmpty { null }
                )
            }
        )

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = if (returnId != null)
                    ApiClient.service.updateSaleReturn(token, returnId, dto)
                else
                    ApiClient.service.createSaleReturn(token, dto)

                val saved = res.body()?.data
                if (res.isSuccessful && saved != null) {
                    _uiState.update { it.copy(saving = false) }
                    onSuccess(saved)
                } else {
                    _uiState.update { it.copy(saving = false, saveError = res.body()?.message ?: "မအောင်မြင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, saveError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    // ── Data ─────────────────────────────────────────────────────────────────

    data class ReturnItem(
        val productId:      Int,
        val productName:    String,
        val maxQty:         Int,
        val unitPrice:      Double,
        val qty:            Int          = 0,
        val serialNumbers:  List<String> = emptyList(),
        val saleSerialNums: List<String> = emptyList(), // original serials from the sale
        val hasSerial:      Boolean      = false
    )

    data class UiState(
        val loading:        Boolean              = true,
        val saving:         Boolean              = false,
        val saveError:      String?              = null,
        val paymentMethods: List<PaymentMethodDTO> = emptyList(),
        val saleQuery:      String               = "",
        val saleResults:    List<SaleDTO>        = emptyList(),
        val selectedSale:   SaleDTO?             = null,
        val items:          List<ReturnItem>     = emptyList(),
        val reason:         String               = "",
        val refundAmountStr: String              = "",
        val selectedPm:     PaymentMethodDTO?    = null,
        val splitRefunds:   List<PaymentTransactionDTO> = emptyList(),
        val transactionNo:  String               = ""
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
