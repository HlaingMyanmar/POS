package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.ShelfLocationDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ShelfLocationViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Shelf") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.getShelfLocations(token)
                _uiState.update { it.copy(items = res.body()?.data ?: emptyList(), loading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun setSearch(q: String) = _uiState.update { it.copy(search = q) }

    // ── Dialog control ────────────────────────────────────────────────────────

    fun openAddDialog() =
        _uiState.update { it.copy(dialogTarget = null, showDialog = true, saveError = null) }

    fun openEditDialog(loc: ShelfLocationDTO) =
        _uiState.update { it.copy(dialogTarget = loc, showDialog = true, saveError = null) }

    fun closeDialog() =
        _uiState.update { it.copy(showDialog = false, dialogTarget = null, saveError = null) }

    // ── Save (create or update) ───────────────────────────────────────────────

    fun save(code: String, label: String, active: Boolean) {
        val existing = _uiState.value.dialogTarget
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val dto   = ShelfLocationDTO(
                    id     = existing?.id,
                    code   = code.trim().uppercase(),
                    label  = label.trim().ifBlank { null },
                    active = active
                )
                val res = if (existing?.id != null)
                    ApiClient.service.updateShelfLocation(token, existing.id, dto)
                else
                    ApiClient.service.createShelfLocation(token, dto)

                if (res.isSuccessful && res.body()?.data != null) {
                    val saved = res.body()!!.data!!
                    _uiState.update { state ->
                        val updated = if (existing?.id != null)
                            state.items.map { if (it.id == existing.id) saved else it }
                        else
                            state.items + saved
                        state.copy(items = updated, saving = false, showDialog = false, saveSuccess = if (existing != null) "ပြင်ဆင်မှု အောင်မြင်ပြီး" else "ထည့်သွင်းမှု အောင်မြင်ပြီး")
                    }
                } else {
                    val msg = res.body()?.message ?: "မအောင်မြင်ပါ (${res.code()})"
                    _uiState.update { it.copy(saving = false, saveError = msg) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, saveError = e.message ?: "ချိတ်ဆက်မှု ချို့ယွင်း") }
            }
        }
    }

    fun clearSaveSuccess() = _uiState.update { it.copy(saveSuccess = null) }

    data class UiState(
        val items:        List<ShelfLocationDTO> = emptyList(),
        val loading:      Boolean                = true,
        val search:       String                 = "",
        val showDialog:   Boolean                = false,
        val dialogTarget: ShelfLocationDTO?      = null,
        val saving:       Boolean                = false,
        val saveError:    String?                = null,
        val saveSuccess:  String?                = null
    )
}
