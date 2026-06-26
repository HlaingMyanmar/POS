package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.api.ApiClient
import com.sspd.servicemgmt.api.PurchaseDTO
import com.sspd.servicemgmt.api.PurchaseReturnDTO
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PurchaseReturnDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val prefs = PreferenceManager(application)
    private val returnId: Int = checkNotNull(savedStateHandle["returnId"])

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("PurchaseReturn", "Purchase", "Stock") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val ret = ApiClient.service.getPurchaseReturnById(token, returnId).body()?.data
                val purchase = ret?.purchaseId?.let { ApiClient.service.getPurchaseById(token, it).body()?.data }
                _uiState.update { it.copy(purchaseReturn = ret, purchase = purchase, loading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun voidReturn(reason: String) {
        val trimmed = reason.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(error = "Void အကြောင်းအရင်း ဖြည့်ပါ") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = true, error = null) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.voidPurchaseReturn(token, returnId, PurchaseReturnDTO(voidReason = trimmed))
                _uiState.update {
                    it.copy(
                        purchaseReturn = res.body()?.data ?: it.purchaseReturn,
                        actionLoading = false,
                        error = if (res.isSuccessful) null else res.body()?.message ?: "Void မအောင်မြင်ပါ (${res.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = false, error = e.message) }
            }
        }
    }

    data class UiState(
        val purchaseReturn: PurchaseReturnDTO? = null,
        val purchase: PurchaseDTO? = null,
        val loading: Boolean = true,
        val actionLoading: Boolean = false,
        val error: String? = null
    )
}

