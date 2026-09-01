package com.sspd.servicemgmt.feature.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.StaffDTO
import com.sspd.servicemgmt.core.network.StockAdjustmentDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class OpeningStockViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val draftStore = application.getSharedPreferences("sspd_opening_stock", 0)
    private val gson = Gson()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val products = ApiClient.service.getProducts(token).body()?.data.orEmpty()
                val staff = ApiClient.service.getActiveStaff(token).body()?.data.orEmpty()
                val draft = readDraft()
                val rows = products.map { p ->
                    StockRow(
                        productId = p.id,
                        productCode = p.productCode,
                        productName = p.name,
                        category = p.categoryName?.ifBlank { "-" } ?: "-",
                        brand = p.brandName?.ifBlank { "-" } ?: "-",
                        unit = p.unitName?.ifBlank { "pcs" } ?: "pcs",
                        currentStock = p.stockQty,
                        costPrice = p.costPrice ?: 0.0,
                        openingQty = draft.entries[p.id.toString()].orEmpty(),
                        hasSerial = p.hasSerial == true
                    )
                }
                val staffId = draft.staffId?.takeIf { id -> staff.any { it.id == id } }
                    ?: staff.firstOrNull()?.id
                _uiState.update {
                    it.copy(
                        loading = false,
                        rows = rows,
                        staff = staff,
                        staffId = staffId,
                        mode = draft.mode,
                        referenceNo = draft.referenceNo.ifBlank { defaultRef() },
                        countDate = draft.countDate.ifBlank { today() },
                        sessionNote = draft.sessionNote
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "ဒေတာဖတ်မရပါ") }
            }
        }
    }

    fun setStaff(id: Int) = persist { it.copy(staffId = id) }
    fun setMode(mode: EntryMode) = persist { it.copy(mode = mode) }
    fun setReferenceNo(v: String) = persist { it.copy(referenceNo = v) }
    fun setCountDate(v: String) = persist { it.copy(countDate = v) }
    fun setSessionNote(v: String) = persist { it.copy(sessionNote = v) }
    fun setQuery(v: String) = _uiState.update { it.copy(query = v) }
    fun setCategory(v: String) = _uiState.update { it.copy(category = v) }
    fun setStatus(v: StatusFilter) = _uiState.update { it.copy(status = v) }
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun setQty(productId: Int, value: String) = persist { state ->
        state.copy(rows = state.rows.map {
            if (it.productId == productId) it.copy(openingQty = value.filter { ch -> ch.isDigit() }, saved = false)
            else it
        })
    }

    fun clearQty(productId: Int) = persist { state ->
        state.copy(rows = state.rows.map {
            if (it.productId == productId) it.copy(openingQty = "", saved = false) else it
        })
    }

    fun resetEntries() {
        persist { it.copy(rows = it.rows.map { row -> row.copy(openingQty = "", saved = false) }) }
        draftStore.edit().remove(DRAFT_KEY).apply()
    }

    fun saveAll() {
        val s = _uiState.value
        val staffId = s.staffId
        if (staffId == null) {
            _uiState.update { it.copy(error = "Staff ရွေးပါ") }
            return
        }
        if (s.summary.invalid > 0) {
            _uiState.update { it.copy(status = StatusFilter.INVALID, error = "Qty မမှန်ပါ") }
            return
        }
        val toSave = s.rowsToSave
        if (toSave.isEmpty()) {
            _uiState.update { it.copy(error = "သိမ်းရန် ကနဦးလက်ကျန် မရှိပါ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, progressDone = 0, progressTotal = toSave.size) }
            var failed = 0
            val failedNames = mutableListOf<String>()
            val token = ApiClient.bearer(prefs.authToken)
            toSave.forEachIndexed { index, row ->
                val openingQty = row.openingQty.toIntOrNull() ?: 0
                val qtyChange = if (s.mode == EntryMode.TARGET_QTY) openingQty - row.currentStock else openingQty
                try {
                    val res = ApiClient.service.createStockAdjustment(
                        token,
                        StockAdjustmentDTO(
                            productId = row.productId,
                            adjustmentType = "CORRECTION",
                            qtyChange = qtyChange,
                            staffId = staffId,
                            reason = listOf(
                                "Opening Stock ${s.referenceNo}",
                                "Count date: ${s.countDate}",
                                "Physical: $openingQty",
                                "Before: ${row.currentStock}",
                                s.sessionNote.trim().takeIf { it.isNotEmpty() }?.let { "Note: $it" }
                            ).filterNotNull().joinToString(" | ")
                        )
                    )
                    if (res.isSuccessful) {
                        persist { state ->
                            state.copy(rows = state.rows.map {
                                if (it.productId == row.productId)
                                    it.copy(currentStock = openingQty, openingQty = "", saved = true)
                                else it
                            })
                        }
                    } else {
                        failed++
                        failedNames += "${row.productName}: ${res.body()?.message ?: res.code()}"
                    }
                } catch (e: Exception) {
                    failed++
                    failedNames += "${row.productName}: ${e.message ?: "error"}"
                }
                _uiState.update { it.copy(progressDone = index + 1) }
            }
            _uiState.update {
                it.copy(
                    saving = false,
                    progressTotal = 0,
                    status = StatusFilter.READY,
                    message = if (failed == 0) "ကနဦး ကုန်လက်ကျန် သိမ်းပြီးပါပြီ"
                    else "$failed ခု မအောင်မြင်ပါ\n${failedNames.joinToString("\n")}"
                )
            }
            if (failed == 0) draftStore.edit().remove(DRAFT_KEY).apply()
        }
    }

    private fun persist(block: (UiState) -> UiState) {
        _uiState.update { block(it).also { next -> writeDraft(next) } }
    }

    private fun writeDraft(state: UiState) {
        val entries = state.rows.filter { it.openingQty.isNotBlank() }
            .associate { it.productId.toString() to it.openingQty }
        draftStore.edit().putString(
            DRAFT_KEY,
            gson.toJson(
                Draft(
                    entries = entries,
                    mode = state.mode,
                    referenceNo = state.referenceNo,
                    countDate = state.countDate,
                    sessionNote = state.sessionNote,
                    staffId = state.staffId
                )
            )
        ).apply()
    }

    private fun readDraft(): Draft {
        val raw = draftStore.getString(DRAFT_KEY, null) ?: return Draft()
        return try {
            gson.fromJson(raw, object : TypeToken<Draft>() {}.type) ?: Draft()
        } catch (_: Exception) {
            Draft()
        }
    }

    data class StockRow(
        val productId: Int,
        val productCode: String,
        val productName: String,
        val category: String,
        val brand: String,
        val unit: String,
        val currentStock: Int,
        val costPrice: Double,
        val openingQty: String = "",
        val hasSerial: Boolean = false,
        val saved: Boolean = false
    ) {
        val hasCost get() = costPrice > 0
        fun isEditable(mode: EntryMode) =
            !hasSerial && hasCost && (mode == EntryMode.TARGET_QTY || currentStock == 0)
        fun parsedQty(): Int? {
            if (openingQty.isBlank()) return null
            return openingQty.toIntOrNull()
        }
    }

    enum class EntryMode { EMPTY_ONLY, TARGET_QTY }
    enum class StatusFilter { ALL, READY, ENTERED, EMPTY, SERIAL, EXISTING, NO_COST, INVALID }

    data class Summary(
        val ready: Int = 0,
        val entered: Int = 0,
        val totalQty: Int = 0,
        val existing: Int = 0,
        val noCost: Int = 0,
        val invalid: Int = 0,
        val serial: Int = 0,
        val total: Int = 0
    )

    data class Draft(
        val entries: Map<String, String> = emptyMap(),
        val mode: EntryMode = EntryMode.EMPTY_ONLY,
        val referenceNo: String = "",
        val countDate: String = "",
        val sessionNote: String = "",
        val staffId: Int? = null
    )

    data class UiState(
        val loading: Boolean = true,
        val saving: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val rows: List<StockRow> = emptyList(),
        val staff: List<StaffDTO> = emptyList(),
        val staffId: Int? = null,
        val mode: EntryMode = EntryMode.EMPTY_ONLY,
        val referenceNo: String = defaultRef(),
        val countDate: String = today(),
        val sessionNote: String = "",
        val query: String = "",
        val category: String = "ALL",
        val status: StatusFilter = StatusFilter.READY,
        val progressDone: Int = 0,
        val progressTotal: Int = 0
    ) {
        val categories: List<String>
            get() = rows.map { it.category }.distinct().sorted()

        val summary: Summary
            get() {
                val ready = rows.count { it.isEditable(mode) }
                val entered = rows.count { it.isEditable(mode) && it.openingQty.isNotBlank() }
                val invalid = rows.count { it.openingQty.isNotBlank() && it.parsedQty() == null }
                val totalQty = rows.sumOf { row ->
                    val v = row.parsedQty()
                    if (row.isEditable(mode) && v != null) v else 0
                }
                return Summary(
                    ready = ready,
                    entered = entered,
                    totalQty = totalQty,
                    existing = rows.count { !it.hasSerial && it.currentStock > 0 },
                    noCost = rows.count { !it.hasSerial && it.currentStock == 0 && !it.hasCost },
                    invalid = invalid,
                    serial = rows.count { it.hasSerial },
                    total = rows.size
                )
            }

        val rowsToSave: List<StockRow>
            get() = rows.filter { row ->
                val v = row.parsedQty() ?: return@filter false
                if (!row.isEditable(mode)) return@filter false
                if (mode == EntryMode.TARGET_QTY) v != row.currentStock else v > 0
            }

        val filteredRows: List<StockRow>
            get() {
                val keyword = query.trim().lowercase()
                return rows.filter { r ->
                    (category == "ALL" || r.category == category) &&
                        when (status) {
                            StatusFilter.ALL -> true
                            StatusFilter.READY -> r.isEditable(mode)
                            StatusFilter.ENTERED -> r.isEditable(mode) && r.openingQty.isNotBlank()
                            StatusFilter.EMPTY -> r.isEditable(mode) && r.openingQty.isBlank()
                            StatusFilter.SERIAL -> r.hasSerial
                            StatusFilter.EXISTING -> !r.hasSerial && r.currentStock > 0
                            StatusFilter.NO_COST -> !r.hasSerial && r.currentStock == 0 && !r.hasCost
                            StatusFilter.INVALID -> r.openingQty.isNotBlank() && r.parsedQty() == null
                        } &&
                        (keyword.isBlank() ||
                            r.productName.lowercase().contains(keyword) ||
                            r.productCode.lowercase().contains(keyword) ||
                            r.category.lowercase().contains(keyword) ||
                            r.brand.lowercase().contains(keyword))
                }
            }
    }

    companion object {
        private const val DRAFT_KEY = "draft.v1"
        fun today(): String = LocalDate.now().toString()
        fun defaultRef(): String = "OPN-STK-" + today().replace("-", "")
    }
}
