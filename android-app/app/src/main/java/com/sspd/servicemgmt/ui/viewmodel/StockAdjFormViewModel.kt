package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.ProductDTO
import com.sspd.servicemgmt.api.StockAdjItemDTO
import com.sspd.servicemgmt.api.StockAdjustmentDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StockAdjFormViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ── Product search ────────────────────────────────────────────────────────

    fun setProductQuery(q: String) {
        _uiState.update { it.copy(productQuery = q, productResults = emptyList()) }
        if (q.length >= 1) searchProducts(q)
    }

    private fun searchProducts(q: String) {
        viewModelScope.launch {
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.getProducts(token)
                val filtered = (res.body()?.data ?: emptyList())
                    .filter {
                        it.name.contains(q, ignoreCase = true) ||
                        it.productCode.contains(q, ignoreCase = true)
                    }
                    .take(20)
                _uiState.update { it.copy(productResults = filtered) }
            } catch (_: Exception) {}
        }
    }

    fun dismissProductPicker() = _uiState.update { it.copy(productQuery = "", productResults = emptyList(), showProductPicker = false) }
    fun openProductPicker()    = _uiState.update { it.copy(showProductPicker = true, productQuery = "", productResults = emptyList()) }

    fun selectProduct(product: ProductDTO) {
        val existing = _uiState.value.lines.any { it.productId == product.id }
        if (existing) {
            _uiState.update { it.copy(saveError = "\"${product.name}\" ထပ်နေပြီ", showProductPicker = false, productQuery = "", productResults = emptyList()) }
            return
        }
        val newLine = AdjLine(
            productId   = product.id,
            productName = product.name,
            productCode = product.productCode
        )
        _uiState.update { it.copy(
            lines             = it.lines + newLine,
            showProductPicker = false,
            productQuery      = "",
            productResults    = emptyList(),
            saveError         = null
        ) }
    }

    // ── Line editing ──────────────────────────────────────────────────────────

    fun setLineQty(index: Int, qty: String) {
        _uiState.update { s ->
            val lines = s.lines.toMutableList()
            if (index < lines.size) lines[index] = lines[index].copy(qtyStr = qty)
            s.copy(lines = lines)
        }
    }

    fun setLineType(index: Int, type: String) {
        _uiState.update { s ->
            val lines = s.lines.toMutableList()
            if (index < lines.size) lines[index] = lines[index].copy(type = type)
            s.copy(lines = lines)
        }
    }

    fun setLineRemark(index: Int, remark: String) {
        _uiState.update { s ->
            val lines = s.lines.toMutableList()
            if (index < lines.size) lines[index] = lines[index].copy(remark = remark)
            s.copy(lines = lines)
        }
    }

    fun removeLine(index: Int) {
        _uiState.update { s ->
            val lines = s.lines.toMutableList()
            if (index < lines.size) lines.removeAt(index)
            s.copy(lines = lines)
        }
    }

    // ── Header fields ─────────────────────────────────────────────────────────

    fun setReason(v: String) = _uiState.update { it.copy(reason = v) }
    fun clearError()         = _uiState.update { it.copy(saveError = null) }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun save(onSuccess: (StockAdjustmentDTO) -> Unit) {
        val s = _uiState.value
        if (s.lines.isEmpty()) { _uiState.update { it.copy(saveError = "ပစ္စည်း အနည်းဆုံး တစ်ခု ထည့်ပါ") }; return }
        if (s.reason.isBlank()) { _uiState.update { it.copy(saveError = "အကြောင်းအရင်း ဖြည့်ပါ") }; return }

        for ((i, line) in s.lines.withIndex()) {
            val qty = line.qtyStr.toIntOrNull() ?: 0
            if (qty <= 0) { _uiState.update { it.copy(saveError = "ပစ္စည်း ${i + 1}: Qty မှန်ကန်သော ဂဏာန်း ထည့်ပါ") }; return }
        }

        val dto = StockAdjustmentDTO(
            reason = s.reason.ifBlank { null },
            items  = s.lines.map { line ->
                StockAdjItemDTO(
                    productId   = line.productId,
                    productName = line.productName,
                    productCode = line.productCode,
                    qty         = line.qtyStr.toIntOrNull() ?: 0,
                    type        = line.type,
                    remark      = line.remark.ifBlank { null }
                )
            }
        )

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.createStockAdjustment(token, dto)
                if (res.isSuccessful && res.body()?.data != null) {
                    _uiState.update { it.copy(saving = false) }
                    onSuccess(res.body()!!.data!!)
                } else {
                    _uiState.update { it.copy(saving = false, saveError = res.body()?.message ?: "မအောင်မြင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, saveError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    // ── Data ─────────────────────────────────────────────────────────────────

    data class AdjLine(
        val productId:   Int,
        val productName: String,
        val productCode: String,
        val qtyStr:      String = "",
        val type:        String = "GAIN",
        val remark:      String = ""
    )

    data class UiState(
        val lines:             List<AdjLine>    = emptyList(),
        val reason:            String           = "",
        val saving:            Boolean          = false,
        val saveError:         String?          = null,
        val showProductPicker: Boolean          = false,
        val productQuery:      String           = "",
        val productResults:    List<ProductDTO> = emptyList()
    )
}
