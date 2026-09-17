package com.sspd.servicemgmt.feature.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.ProductSerialDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.sspd.servicemgmt.core.realtime.onDataEvent

class SerialRegistryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState(
        canEdit = prefs.hasPermission("CAN_ACCESS_PRODUCT_SERIAL_UPDATE")
            || prefs.hasPermission("ROLE_ADMINISTRATOR")
    ))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var allSerials: List<ProductSerialDTO> = emptyList()

    init {
        load()
        onDataEvent("Serial", "Product") { load() }
    }

    fun refresh() = load(fromPull = true)

    fun load(fromPull: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = !fromPull && (it.filtered.isEmpty() && it.loading),
                    refreshing = fromPull,
                    canEdit = prefs.hasPermission("CAN_ACCESS_PRODUCT_SERIAL_UPDATE")
                        || prefs.hasPermission("ROLE_ADMINISTRATOR")
                )
            }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.getAllProductSerials(token)
                allSerials = res.body()?.data ?: emptyList()
                applyFilter()
            } catch (_: Exception) {}
            _uiState.update { it.copy(loading = false, refreshing = false) }
        }
    }

    fun setSearch(q: String) {
        _uiState.update { it.copy(search = q) }
        applyFilter()
    }

    fun setStatusFilter(status: String?) {
        _uiState.update { it.copy(statusFilter = status) }
        applyFilter()
    }

    fun updateSerial(
        original: ProductSerialDTO,
        serialNumber: String,
        status: String,
        condition: String,
        warrantyMonths: Int,
        warrantyStartDate: String?,
        warrantyEndDate: String?,
        onDone: (String?) -> Unit
    ) {
        if (!_uiState.value.canEdit) {
            onDone("Serial ပြင်ဆင်ခွင့် မရှိပါ")
            return
        }
        val id = original.id
        val normalizedSerial = serialNumber.trim().uppercase()
        if (id == null) {
            onDone("Serial id မရှိပါ")
            return
        }
        if (normalizedSerial.isBlank()) {
            onDone("Serial number ဖြည့်ပါ")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val dto = original.copy(
                    serialNumber = normalizedSerial,
                    status = status,
                    condition = condition.ifBlank { null },
                    warrantyMonths = warrantyMonths.coerceAtLeast(0),
                    warrantyStartDate = warrantyStartDate?.takeIf { it.isNotBlank() },
                    warrantyEndDate = warrantyEndDate?.takeIf { it.isNotBlank() }
                )
                val res = ApiClient.service.updateProductSerial(token, id, dto)
                if (res.isSuccessful) {
                    load()
                    onDone(null)
                } else {
                    onDone(res.body()?.message ?: "Serial update မအောင်မြင်ပါ (${res.code()})")
                }
            } catch (e: Exception) {
                onDone(e.message ?: "ချိတ်ဆက်မှု မအောင်မြင်ပါ")
            } finally {
                _uiState.update { it.copy(saving = false) }
            }
        }
    }

    private fun applyFilter() {
        val s      = _uiState.value
        val query  = s.search.trim().lowercase()
        val status = s.statusFilter
        val result = allSerials.filter { serial ->
            val matchesSearch = query.isEmpty() ||
                serial.serialNumber.lowercase().contains(query) ||
                (serial.productName?.lowercase()?.contains(query) == true) ||
                (serial.productCode?.lowercase()?.contains(query) == true)
            val matchesStatus = status == null ||
                serial.status.equals(status, ignoreCase = true) ||
                serial.status?.uppercase() == status.uppercase() ||
                // API enum "Available" vs filter key "AVAILABLE"
                (status.equals("AVAILABLE", true) && serial.status.equals("Available", true)) ||
                (status.equals("SOLD", true) && serial.status.equals("Sold", true)) ||
                (status.equals("IN_SERVICE", true) && (
                    serial.status.equals("Used_In_Service", true) ||
                        serial.status?.contains("SERVICE", ignoreCase = true) == true
                    )) ||
                (status.equals("RETURNED", true) && serial.status?.contains("RETURN", ignoreCase = true) == true) ||
                (status.equals("DAMAGED", true) && serial.status.equals("Damaged", true))
            matchesSearch && matchesStatus
        }
        _uiState.update { it.copy(filtered = result) }
    }

    data class UiState(
        val filtered:     List<ProductSerialDTO> = emptyList(),
        val loading:      Boolean                = true,
        val refreshing:   Boolean                = false,
        val saving:       Boolean                = false,
        val search:       String                 = "",
        val statusFilter: String?                = null,
        val canEdit:      Boolean                = false
    )
}
