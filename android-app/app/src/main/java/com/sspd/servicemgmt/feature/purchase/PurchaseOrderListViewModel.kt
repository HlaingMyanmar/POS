package com.sspd.servicemgmt.feature.purchase

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.PurchaseOrderDTO
import com.sspd.servicemgmt.core.network.PurchaseOrderReceiveLineDTO
import com.sspd.servicemgmt.core.network.PurchaseOrderReceiveRequest
import com.sspd.servicemgmt.core.network.PurchaseOrderRejectRequest
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.sspd.servicemgmt.core.realtime.onDataEvent

class PurchaseOrderListViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("PurchaseOrder", "Purchase") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val page = ApiClient.service.getPurchaseOrders(token, page = 0, size = 50).body()?.data?.content ?: emptyList()
                val late = runCatching { ApiClient.service.getLatePurchaseOrders(token).body()?.data.orEmpty() }.getOrDefault(emptyList())
                _uiState.update { it.copy(items = page, lateItems = late, loading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun approve(po: PurchaseOrderDTO) {
        val status = (po.status ?: "").uppercase()
        val permitted = if (status == "PENDING_FINAL_APPROVAL")
            prefs.hasPermission("CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE")
        else prefs.hasPermission("CAN_ACCESS_PURCHASE_ORDER_APPROVE")
        if (!permitted) return denyMutation()
        val id = po.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.approvePurchaseOrder(token, id)
                if (res.isSuccessful) {
                    load()
                } else {
                    _uiState.update { it.copy(busy = false, error = res.body()?.message ?: "Approve မရပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun reject(po: PurchaseOrderDTO, reason: String?) {
        if (!prefs.hasPermission("CAN_ACCESS_PURCHASE_ORDER_APPROVE")
            && !prefs.hasPermission("CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE")) return denyMutation()
        val id = po.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.rejectPurchaseOrder(
                    token,
                    id,
                    PurchaseOrderRejectRequest(reason = reason?.trim()?.ifBlank { null })
                )
                if (res.isSuccessful) {
                    load()
                } else {
                    _uiState.update { it.copy(busy = false, error = res.body()?.message ?: "Reject မရပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun startReceive(po: PurchaseOrderDTO) {
        if (!prefs.hasPermission("CAN_ACCESS_PURCHASE_ORDER_RECEIVE")) return denyMutation()
        val id = po.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val full = ApiClient.service.getPurchaseOrderById(token, id).body()?.data ?: po
                val products = runCatching { ApiClient.service.getProducts(token).body()?.data.orEmpty() }.getOrDefault(emptyList())
                val serialByProduct = products.associate { (it.id ?: 0) to (it.hasSerial != false) }
                val lines = (full.details ?: emptyList()).mapNotNull { d ->
                    val pending = (d.qty ?: 0) - (d.receivedQty ?: 0)
                    if (pending <= 0 || d.id == null) null
                    else ReceiveLineDraft(
                        detailId = d.id,
                        productId = d.productId ?: 0,
                        productName = d.productName ?: "ပစ္စည်း",
                        qty = pending,
                        hasSerial = d.hasSerial ?: serialByProduct[d.productId ?: 0] ?: false
                    )
                }
                if (lines.isEmpty()) {
                    _uiState.update { it.copy(busy = false, error = "လက်ခံရန် ကျန်မရှိပါ") }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        busy = false,
                        receiveDraft = ReceiveDraft(poId = id, poCode = full.poCode ?: po.poCode, lines = lines)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun cancelReceive() = _uiState.update { it.copy(receiveDraft = null, error = null) }

    fun setReceiveInvoiceNo(v: String) = _uiState.update { s ->
        s.copy(receiveDraft = s.receiveDraft?.copy(invoiceNo = v))
    }

    fun setReceiveLine(detailId: Int, serialText: String? = null) {
        _uiState.update { s ->
            val draft = s.receiveDraft ?: return@update s
            s.copy(receiveDraft = draft.copy(lines = draft.lines.map { line ->
                if (line.detailId != detailId) line
                else line.copy(serialText = serialText ?: line.serialText)
            }))
        }
    }

    fun confirmReceive() {
        if (!prefs.hasPermission("CAN_ACCESS_PURCHASE_ORDER_RECEIVE")) return denyMutation()
        val draft = _uiState.value.receiveDraft ?: return
        val parsed = draft.lines.map { line ->
            val serials = parseSerials(line.serialText)
            Triple(line, serials, line.hasSerial && serials.size != line.qty)
        }
        val bad = parsed.firstOrNull { it.third }
        if (bad != null) {
            _uiState.update { it.copy(error = "${bad.first.productName} အတွက် serial ${bad.first.qty} ခု လိုအပ်သည်") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val lines = parsed.map { (line, serials, _) ->
                    PurchaseOrderReceiveLineDTO(
                        detailId = line.detailId,
                        qty = line.qty,
                        serialNumbers = if (line.hasSerial) serials else null
                    )
                }
                val res = ApiClient.service.receivePurchaseOrder(
                    token,
                    draft.poId,
                    PurchaseOrderReceiveRequest(
                        lines = lines,
                        supplierInvoiceNo = draft.invoiceNo.trim().ifBlank { null }
                    )
                )
                if (res.isSuccessful) {
                    _uiState.update { it.copy(receiveDraft = null) }
                    load()
                } else {
                    _uiState.update { it.copy(busy = false, error = res.body()?.message ?: "လက်ခံမရပါ") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    private fun parseSerials(raw: String): List<String> =
        raw.split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }

    private fun denyMutation() {
        _uiState.update { it.copy(error = "ဤလုပ်ဆောင်ချက်အတွက် ခွင့်ပြုချက် မရှိပါ") }
    }

    data class ReceiveLineDraft(
        val detailId: Int,
        val productId: Int,
        val productName: String,
        val qty: Int,
        val hasSerial: Boolean,
        val serialText: String = ""
    )

    data class ReceiveDraft(
        val poId: Int,
        val poCode: String? = null,
        val invoiceNo: String = "",
        val lines: List<ReceiveLineDraft> = emptyList()
    )

    data class UiState(
        val items: List<PurchaseOrderDTO> = emptyList(),
        val lateItems: List<PurchaseOrderDTO> = emptyList(),
        val receiveDraft: ReceiveDraft? = null,
        val loading: Boolean = true,
        val busy: Boolean = false,
        val error: String? = null
    )
}
