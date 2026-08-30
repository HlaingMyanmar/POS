package com.sspd.servicemgmt.feature.salereturn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.SaleReturnDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.sspd.servicemgmt.core.navigation.optionalId
import com.sspd.servicemgmt.core.realtime.onDataEvent

class SaleReturnDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val prefs    = PreferenceManager(application)
    private val returnId: Int = savedStateHandle.optionalId("returnId")

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        onDataEvent("Return") { load() }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res   = ApiClient.service.getSaleReturnById(token, returnId)
                _uiState.update { it.copy(saleReturn = res.body()?.data, loading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    data class UiState(
        val saleReturn: SaleReturnDTO? = null,
        val loading:    Boolean        = true
    )
}
