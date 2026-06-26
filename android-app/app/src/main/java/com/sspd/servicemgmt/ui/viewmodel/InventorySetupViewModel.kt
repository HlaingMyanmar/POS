package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.BrandDTO
import com.sspd.servicemgmt.api.CategoryDTO
import com.sspd.servicemgmt.api.UnitDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InventorySetupViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(InventorySetupUiState())
    val uiState: StateFlow<InventorySetupUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val brandsD = async { ApiClient.service.getBrands(token) }
                val catsD = async { ApiClient.service.getCategoryTree(token) }
                val unitsD = async { ApiClient.service.getUnits(token) }
                _uiState.update {
                    it.copy(
                        brands = brandsD.await().body()?.data ?: emptyList(),
                        categoryTree = catsD.await().body()?.data ?: emptyList(),
                        units = unitsD.await().body()?.data ?: emptyList(),
                        loading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "ဒေတာ မဖတ်နိုင်ပါ") }
            }
        }
    }

    fun saveBrand(id: Int?, name: String, active: Boolean, onDone: () -> Unit) {
        val clean = name.trim()
        if (clean.isBlank()) {
            _uiState.update { it.copy(error = "အမှတ်တံဆိပ်အမည် ဖြည့်ပါ") }
            return
        }
        viewModelScope.launch {
            saveCall(
                call = {
                    val body = BrandDTO(id = id, name = clean, isActive = active)
                    if (id == null) ApiClient.service.createBrand(ApiClient.bearer(prefs.authToken), body)
                    else ApiClient.service.updateBrand(ApiClient.bearer(prefs.authToken), id, body)
                },
                onDone = onDone
            )
        }
    }

    fun saveCategory(id: Int?, name: String, parentId: Int?, active: Boolean, onDone: () -> Unit) {
        val clean = name.trim()
        if (clean.isBlank()) {
            _uiState.update { it.copy(error = "အမျိုးအစားအမည် ဖြည့်ပါ") }
            return
        }
        viewModelScope.launch {
            saveCall(
                call = {
                    val body = CategoryDTO(id = id, name = clean, parentId = parentId, isActive = active)
                    if (id == null) ApiClient.service.createCategory(ApiClient.bearer(prefs.authToken), body)
                    else ApiClient.service.updateCategory(ApiClient.bearer(prefs.authToken), id, body)
                },
                onDone = onDone
            )
        }
    }

    fun saveUnit(id: Int?, name: String, symbol: String, description: String, active: Boolean, onDone: () -> Unit) {
        val clean = name.trim()
        if (clean.isBlank()) {
            _uiState.update { it.copy(error = "တိုင်းတာယူနစ်အမည် ဖြည့်ပါ") }
            return
        }
        viewModelScope.launch {
            saveCall(
                call = {
                    val body = UnitDTO(
                        id = id,
                        name = clean,
                        unitName = clean,
                        symbol = symbol.trim().ifBlank { null },
                        description = description.trim().ifBlank { null },
                        isActive = active
                    )
                    if (id == null) ApiClient.service.createUnit(ApiClient.bearer(prefs.authToken), body)
                    else ApiClient.service.updateUnit(ApiClient.bearer(prefs.authToken), id, body)
                },
                onDone = onDone
            )
        }
    }

    private suspend fun <T> saveCall(
        call: suspend () -> retrofit2.Response<com.sspd.servicemgmt.api.ApiResponse<T>>,
        onDone: () -> Unit
    ) {
        _uiState.update { it.copy(saving = true, error = null) }
        try {
            val res = call()
            if (res.isSuccessful) {
                _uiState.update { it.copy(saving = false, success = "သိမ်းပြီးပါပြီ") }
                load()
                onDone()
            } else {
                _uiState.update { it.copy(saving = false, error = res.body()?.message ?: "မသိမ်းနိုင်ပါ (${res.code()})") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(saving = false, error = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
        }
    }

    fun deleteBrand(item: BrandDTO) = deleteById(item.id) { token, id -> ApiClient.service.deleteBrand(token, id) }
    fun deleteCategory(item: CategoryDTO) = deleteById(item.id) { token, id -> ApiClient.service.deleteCategory(token, id) }
    fun deleteUnit(item: UnitDTO) = deleteById(item.id) { token, id -> ApiClient.service.deleteUnit(token, id) }

    private fun deleteById(
        id: Int?,
        call: suspend (String, Int) -> retrofit2.Response<com.sspd.servicemgmt.api.ApiResponse<Void>>
    ) {
        if (id == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true, error = null) }
            try {
                val res = call(ApiClient.bearer(prefs.authToken), id)
                if (res.isSuccessful) {
                    _uiState.update { it.copy(deleting = false, success = "ဖျက်ပြီးပါပြီ") }
                    load()
                } else {
                    _uiState.update { it.copy(deleting = false, error = res.body()?.message ?: "မဖျက်နိုင်ပါ (${res.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(deleting = false, error = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun clearMessages() = _uiState.update { it.copy(error = null, success = null) }

    data class InventorySetupUiState(
        val loading: Boolean = true,
        val saving: Boolean = false,
        val deleting: Boolean = false,
        val error: String? = null,
        val success: String? = null,
        val brands: List<BrandDTO> = emptyList(),
        val categoryTree: List<CategoryDTO> = emptyList(),
        val units: List<UnitDTO> = emptyList()
    )
}
