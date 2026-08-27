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

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var allSerials: List<ProductSerialDTO> = emptyList()

    init {
        load()
        onDataEvent("Serial", "Product") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.getAllProductSerials(token)
                allSerials = res.body()?.data ?: emptyList()
                applyFilter()
            } catch (_: Exception) {}
            _uiState.update { it.copy(loading = false) }
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
        onDone: (String?) -> Unit
    ) {
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
                    warrantyStartDate = warrantyStartDate?.takeIf { it.isNotBlank() }
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
            val matchesStatus = status == null || serial.status == status
            matchesSearch && matchesStatus
        }
        _uiState.update { it.copy(filtered = result) }
    }

    data class UiState(
        val filtered:     List<ProductSerialDTO> = emptyList(),
        val loading:      Boolean                = true,
        val saving:       Boolean                = false,
        val search:       String                 = "",
        val statusFilter: String?                = null
    )
}
